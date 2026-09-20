package aurick.opsec.mod.accounts;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.OpsecConstants;
import aurick.opsec.mod.mixin.client.MinecraftAccessor;
import aurick.opsec.mod.util.SkinRandomizer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.minecraft.UserApiService;
//? if <26.3
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a Minecraft account authenticated via session (access) token.
 * Handles token validation and account switching with proper chat signature support.
 */
public class SessionAccount implements Account {
    
    /**
     * Result of a validation attempt.
     */
    public enum ValidationResult {
        VALID,       // Token is valid
        INVALID,     // Token is expired/invalid (401, 403)
        RATE_LIMITED,// Rate limited by API (429)
        ERROR        // Network/other error
    }
    
    private String accessToken;
    private String refreshToken; // Optional - for automatic token renewal
    private String username;
    private String uuid;
    private long lastValidated;
    private boolean valid = true; // Assume valid until proven otherwise
    private String lastError = null; // Last error message for display in UI

    // Active skin/cape texture hash (last path segment of the textures.minecraft.net URL),
    // captured from the profile response. Used only for cross-account correlation warnings
    // (see AccountManager#checkSkinCorrelation) — never sent anywhere, never a fingerprint
    // we invent ourselves, just the same public texture id any server already sees.
    private String skinTextureId;
    private String capeTextureId;
    private java.util.List<CapeInfo> ownedCapes = new java.util.ArrayList<>();

    // Common Azure client IDs for Minecraft authentication
    // The refresh token must be used with the same client ID that issued it
    private static final String[] AZURE_CLIENT_IDS = {
            OpsecConstants.AzureClientIds.MINECRAFT_PUBLIC,
            OpsecConstants.AzureClientIds.XBOX_APP,
            OpsecConstants.AzureClientIds.MINECRAFT_IOS,
            OpsecConstants.AzureClientIds.LAUNCHER_CLIENT
    };

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(OpsecConstants.Retry.HTTP_TIMEOUT_SECONDS))
            .build();
    
    public SessionAccount(String accessToken) {
        this.accessToken = accessToken;
        this.refreshToken = null;
        this.username = "";
        this.uuid = "";
        this.lastValidated = 0;
    }
    
    public SessionAccount(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.username = "";
        this.uuid = "";
        this.lastValidated = 0;
    }
    
    public SessionAccount(String accessToken, String username, String uuid) {
        this.accessToken = accessToken;
        this.refreshToken = null;
        this.username = username;
        this.uuid = uuid;
        this.lastValidated = System.currentTimeMillis();
    }
    
    /**
     * Validates the access token by fetching the profile from Minecraft Services API.
     * Updates username and uuid if successful.
     * @return true if token is valid and profile was fetched
     */
    public boolean fetchInfo() {
        return fetchInfoWithResult() == ValidationResult.VALID;
    }
    
    /**
     * Validates with automatic retry for transient errors (rate limiting, connection issues).
     * Uses exponential backoff with jitter and respects Retry-After headers.
     * @return ValidationResult after up to MAX_RETRIES attempts
     */
    public ValidationResult fetchInfoWithRetry() {
        ValidationResult result = null;
        long retryAfterMs = 0; // Stores Retry-After header value if provided
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            var validationResult = fetchInfoWithResultAndRetryAfter();
            result = validationResult.result;
            retryAfterMs = validationResult.retryAfterMs;
            
            // Success or definitive failure - no retry needed
            if (result == ValidationResult.VALID || result == ValidationResult.INVALID) {
                return result;
            }
            
            // Transient error (rate limit or network) - retry with delay
            if (attempt < MAX_RETRIES) {
                long delayMs = calculateRetryDelay(attempt, result, retryAfterMs);
                Opsec.LOGGER.info("[OpSec] Validation attempt {} failed ({}), retrying in {}ms...", 
                        attempt, result, delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return result != null ? result : ValidationResult.ERROR;
    }
    
    /**
     * Calculates retry delay using exponential backoff with jitter.
     * Respects Retry-After header if provided, otherwise uses exponential backoff.
     */
    private long calculateRetryDelay(int attempt, ValidationResult result, long retryAfterMs) {
        // If server provided Retry-After header, use it (add small jitter)
        if (retryAfterMs > 0) {
            long jitter = (long)(Math.random() * 1000); // 0-1 second jitter
            return retryAfterMs + jitter;
        }
        
        // Exponential backoff: base * 2^(attempt-1) with jitter
        long baseDelay = (result == ValidationResult.RATE_LIMITED) 
                ? RATE_LIMIT_BASE_DELAY_MS 
                : BASE_RETRY_DELAY_MS;
        
        long exponentialDelay = baseDelay * (1L << (attempt - 1)); // 2^(attempt-1)
        long jitter = (long)(Math.random() * baseDelay); // Random jitter up to base delay
        
        return exponentialDelay + jitter;
    }
    
    /**
     * Result wrapper that includes both validation result and Retry-After header value.
     */
    private static class ValidationResultWithRetry {
        final ValidationResult result;
        final long retryAfterMs;
        
        ValidationResultWithRetry(ValidationResult result, long retryAfterMs) {
            this.result = result;
            this.retryAfterMs = retryAfterMs;
        }
    }
    
    /**
     * Validates the access token with detailed result.
     * @return ValidationResult indicating success, invalid token, rate limit, or error
     */
    public ValidationResult fetchInfoWithResult() {
        return fetchInfoWithResultAndRetryAfter().result;
    }
    
    /**
     * Validates the access token with detailed result and Retry-After header extraction.
     * @return ValidationResultWithRetry containing result and retry delay
     */
    private ValidationResultWithRetry fetchInfoWithResultAndRetryAfter() {
        if (accessToken == null || accessToken.isBlank()) {
            return new ValidationResultWithRetry(ValidationResult.INVALID, 0);
        }
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OpsecConstants.AuthUrls.PROFILE_URL))
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(Duration.ofSeconds(OpsecConstants.Retry.HTTP_TIMEOUT_SECONDS))
                    .GET()
                    .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            int statusCode = response.statusCode();
            long retryAfterMs = extractRetryAfter(response);
            
            if (statusCode == 429) {
                Opsec.LOGGER.warn("[OpSec] Rate limited while validating token for {} (Retry-After: {}ms)", 
                        username.isEmpty() ? "unknown" : username, retryAfterMs);
                return new ValidationResultWithRetry(ValidationResult.RATE_LIMITED, retryAfterMs);
            }
            
            if (statusCode == 401 || statusCode == 403) {
                Opsec.LOGGER.warn("[OpSec] Token invalid/expired for {}: HTTP {}", 
                        username.isEmpty() ? "unknown" : username, statusCode);
                return new ValidationResultWithRetry(ValidationResult.INVALID, 0);
            }
            
            if (statusCode != 200) {
                Opsec.LOGGER.warn("[OpSec] Failed to validate token: HTTP {}", statusCode);
                return new ValidationResultWithRetry(ValidationResult.ERROR, retryAfterMs);
            }
            
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            
            if (!json.has("id") || !json.has("name")) {
                Opsec.LOGGER.warn("[OpSec] Invalid profile response: missing id or name");
                return new ValidationResultWithRetry(ValidationResult.ERROR, 0);
            }
            
            this.uuid = formatUuid(json.get("id").getAsString());
            this.username = json.get("name").getAsString();
            this.lastValidated = System.currentTimeMillis();
            captureActiveTextures(json);

            Opsec.LOGGER.info("[OpSec] Validated account: {} ({})", username, uuid);
            return new ValidationResultWithRetry(ValidationResult.VALID, 0);
            
        } catch (Exception e) {
            Opsec.LOGGER.error("[OpSec] Failed to validate token: {}", e.getMessage());
            return new ValidationResultWithRetry(ValidationResult.ERROR, 0);
        }
    }
    
    /**
     * Extracts Retry-After header value from HTTP response.
     * Returns delay in milliseconds, or 0 if header not present/invalid.
     */
    private long extractRetryAfter(HttpResponse<?> response) {
        try {
            String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
            if (retryAfter == null || retryAfter.isEmpty()) {
                return 0;
            }
            
            // Try parsing as seconds (most common format)
            try {
                long seconds = Long.parseLong(retryAfter);
                return seconds * 1000; // Convert to milliseconds
            } catch (NumberFormatException e) {
                // Might be an HTTP date, but for simplicity, use default if parsing fails
                Opsec.LOGGER.debug("[OpSec] Retry-After header format not recognized: {}", retryAfter);
                return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }
    
    private static final int MAX_RETRIES = OpsecConstants.Retry.MAX_RETRIES;
    private static final long BASE_RETRY_DELAY_MS = OpsecConstants.Retry.BASE_RETRY_DELAY_MS;
    private static final long RATE_LIMIT_BASE_DELAY_MS = OpsecConstants.Retry.RATE_LIMIT_BASE_DELAY_MS;
    
    /**
     * Logs into this account by switching the Minecraft session and reinitializing services.
     * This properly handles chat signatures by regenerating profile keys.
     * Validates the token before switching to ensure it's still valid.
     * Includes retry logic for rate limiting and connection errors.
     * @return true if login was successful
     */
    @Override
    public boolean login() {
        if (accessToken == null || accessToken.isBlank()) {
            this.lastError = "Missing token";
            Opsec.LOGGER.error("[OpSec] Cannot login: missing token");
            return false;
        }
        
        // Validate token with retries for transient errors
        ValidationResult validationResult = fetchInfoWithRetry();
        if (validationResult != ValidationResult.VALID) {
            // Try to refresh if we have a refresh token and token is invalid
            if (validationResult == ValidationResult.INVALID && hasRefreshToken()) {
                Opsec.LOGGER.info("[OpSec] Token invalid, attempting refresh...");
                if (refreshAccessToken()) {
                    // Retry validation after refresh
                    validationResult = fetchInfoWithResult();
                }
            }
            
            // If still not valid, fail
            if (validationResult != ValidationResult.VALID) {
                this.valid = (validationResult != ValidationResult.INVALID);
                switch (validationResult) {
                    case INVALID -> this.lastError = hasRefreshToken() ? "Token refresh failed" : "Token expired or invalid";
                    case RATE_LIMITED -> this.lastError = "Rate limited - try again later";
                    case ERROR -> this.lastError = "Network error - check connection";
                    default -> this.lastError = "Validation failed";
                }
                Opsec.LOGGER.error("[OpSec] Cannot login: token validation failed ({})", validationResult);
                return false;
            }
        }
        this.valid = true;
        this.lastError = null; // Clear error on success
        
        if (uuid == null || uuid.isBlank()) {
            this.lastError = "Missing UUID after validation";
            Opsec.LOGGER.error("[OpSec] Cannot login: missing UUID after validation");
            return false;
        }
        
        try {
            Minecraft mc = Minecraft.getInstance();
            MinecraftAccessor accessor = (MinecraftAccessor) mc;
            
            // Create new User (session)
            // User.Type.MSA was removed in 1.21.9
            //? if >=1.21.9 {
            /*User newUser = new User(
                    username,
                    UUID.fromString(uuid),
                    accessToken,
                    Optional.empty(),
                    Optional.empty()
            );*/
            //?} else if >=1.20.2 {
            User newUser = new User(
                    username,
                    UUID.fromString(uuid),
                    accessToken,
                    Optional.empty(),
                    Optional.empty(),
                    User.Type.MSA
            );
            //?} else {
            /*User newUser = new User(
                    username,
                    uuid,
                    accessToken,
                    Optional.empty(),
                    Optional.empty(),
                    User.Type.MSA
            );
            *///?}
            
            // Set the new user/session
            accessor.opsec$setUser(newUser);
            
            // Reinitialize authentication services.
            // MC 1.26.3 bumped authlib 9.x->10.x, removing the yggdrasil package
            // entirely — MinecraftServicesDiscoveryService.create(proxy) is its
            // replacement and, conveniently, exposes the exact same
            // createUserApiService/createFriendsService method shapes, so `var`
            // lets everything below this line stay unchanged across the split.
            //? if >=26.3 {
            /*var authService = com.mojang.authlib.services.MinecraftServicesDiscoveryService.create(mc.getProxy());*/
            //?} else {
            var authService = new YggdrasilAuthenticationService(mc.getProxy());
            //?}

            // Reinitialize user API service (needed for chat signing)
            UserApiService userApiService = authService.createUserApiService(accessToken);
            accessor.opsec$setUserApiService(userApiService);
            
            // Reinitialize profile key pair manager (for chat signatures)
            // Signing OFF: never fetch/store a real chat-session key pair for this account.
            // The per-message signature is already stripped elsewhere (ServerboundChatPacketMixin,
            // ClientPacketListenerMixin) — this additionally skips the one-time Mojang key-service
            // round trip that would otherwise happen on first use, and the local key pair file.
            ProfileKeyPairManager profileKeyPairManager = OpsecConfig.getInstance().shouldNotSign()
                    ? ProfileKeyPairManager.EMPTY_KEY_MANAGER
                    : ProfileKeyPairManager.create(userApiService, newUser, mc.gameDirectory.toPath());
            accessor.opsec$setProfileKeyPairManager(profileKeyPairManager);
            
            // Reinitialize social manager
            //? if >=26.2 {
            /*var friendsService = authService.createFriendsService(accessToken);
            net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler updateHandler =
                    new net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler(friendsService, mc);
            PlayerSocialManager socialManager = new PlayerSocialManager(mc, userApiService, friendsService, updateHandler);*/
            //?} else {
            PlayerSocialManager socialManager = new PlayerSocialManager(mc, userApiService);
            //?}
            accessor.opsec$setPlayerSocialManager(socialManager);
            
            Opsec.LOGGER.info("[OpSec] Successfully logged in as: {}", username);
            return true;
            
        } catch (Exception e) {
            this.lastError = "Login error: " + e.getMessage();
            Opsec.LOGGER.error("[OpSec] Failed to login: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Records the active skin/cape texture ids from a {@code /minecraft/profile} response,
     * for the skin-correlation check in {@link AccountManager}. Best-effort: any malformed
     * or missing field just leaves the previous value in place.
     */
    private void captureActiveTextures(JsonObject profile) {
        this.skinTextureId = findActiveTextureId(profile, "skins");
        this.capeTextureId = findActiveTextureId(profile, "capes");
        this.ownedCapes = findOwnedCapes(profile);
    }

    /** One cape this account owns and can select, per the {@code /minecraft/profile} response. */
    public record CapeInfo(String id, String alias) {}

    private static java.util.List<CapeInfo> findOwnedCapes(JsonObject profile) {
        java.util.List<CapeInfo> capes = new java.util.ArrayList<>();
        if (!profile.has("capes") || !profile.get("capes").isJsonArray()) return capes;
        for (JsonElement element : profile.getAsJsonArray("capes")) {
            try {
                JsonObject entry = element.getAsJsonObject();
                if (!entry.has("id")) continue;
                String alias = entry.has("alias") ? entry.get("alias").getAsString() : entry.get("id").getAsString();
                capes.add(new CapeInfo(entry.get("id").getAsString(), alias));
            } catch (Exception ignored) {
                // Malformed entry — skip it rather than fail the whole validation.
            }
        }
        return capes;
    }

    private static String findActiveTextureId(JsonObject profile, String arrayField) {
        if (!profile.has(arrayField) || !profile.get(arrayField).isJsonArray()) return null;
        JsonArray array = profile.getAsJsonArray(arrayField);
        for (int i = 0; i < array.size(); i++) {
            try {
                JsonObject entry = array.get(i).getAsJsonObject();
                if (!entry.has("state") || !"ACTIVE".equals(entry.get("state").getAsString())) continue;
                if (!entry.has("url")) continue;
                String url = entry.get("url").getAsString();
                int lastSlash = url.lastIndexOf('/');
                return lastSlash >= 0 ? url.substring(lastSlash + 1) : url;
            } catch (Exception ignored) {
                // Malformed entry — skip it rather than fail the whole validation.
            }
        }
        return null;
    }

    /** Texture id (URL hash) of the currently active skin, or {@code null} if not yet known. */
    public String getSkinTextureId() { return skinTextureId; }

    /** Texture id (URL hash) of the currently active cape, or {@code null} if none/not yet known. */
    public String getCapeTextureId() { return capeTextureId; }

    /** Capes this account owns and can select. Populated from the last successful {@link #fetchInfo()}. */
    public java.util.List<CapeInfo> getOwnedCapes() { return java.util.Collections.unmodifiableList(ownedCapes); }

    /**
     * Uploads a procedurally-generated flat-color skin (see {@link SkinRandomizer})
     * as this account's active skin, via the official Minecraft Services skin
     * endpoint. Also fixes the mod's own Skin/Cape Correlation Alert when run on
     * two accounts that currently share a skin.
     * @return true on success; check {@link #getLastError()} on failure
     */
    public boolean randomizeSkin() {
        if (accessToken == null || accessToken.isBlank()) {
            this.lastError = "Missing token";
            return false;
        }
        try {
            byte[] png = SkinRandomizer.generateFlatColorSkinPng();
            String variant = SkinRandomizer.randomVariant();
            String boundary = "OpSecBoundary" + System.currentTimeMillis();
            byte[] body = buildSkinMultipartBody(boundary, variant, png);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OpsecConstants.AuthUrls.SKIN_URL))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(OpsecConstants.Retry.HTTP_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                this.lastError = "Skin upload failed: HTTP " + response.statusCode();
                Opsec.LOGGER.warn("[OpSec] {}", this.lastError);
                return false;
            }
            Opsec.LOGGER.info("[OpSec] Randomized skin for {} (variant={})", username, variant);
            return true;
        } catch (Exception e) {
            this.lastError = "Skin upload error: " + e.getMessage();
            Opsec.LOGGER.error("[OpSec] {}", this.lastError);
            return false;
        }
    }

    private static byte[] buildSkinMultipartBody(String boundary, String variant, byte[] pngBytes) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        String charset = "UTF-8";
        out.write(("--" + boundary + "\r\n").getBytes(charset));
        out.write(("Content-Disposition: form-data; name=\"variant\"\r\n\r\n").getBytes(charset));
        out.write((variant + "\r\n").getBytes(charset));
        out.write(("--" + boundary + "\r\n").getBytes(charset));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n").getBytes(charset));
        out.write(("Content-Type: image/png\r\n\r\n").getBytes(charset));
        out.write(pngBytes);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(charset));
        return out.toByteArray();
    }

    /**
     * Sets a random owned cape active (or clears the active cape entirely, one
     * of the random outcomes) via the official capes endpoint.
     * @return true on success; false with {@link #getLastError()} set if the
     *         account owns no capes or the request fails
     */
    public boolean randomizeCape() {
        if (accessToken == null || accessToken.isBlank()) {
            this.lastError = "Missing token";
            return false;
        }
        if (ownedCapes.isEmpty()) {
            this.lastError = "This account owns no capes to choose from";
            return false;
        }
        try {
            // Random pick among owned capes, plus one extra "no cape" slot.
            int pick = java.util.concurrent.ThreadLocalRandom.current().nextInt(ownedCapes.size() + 1);
            HttpRequest request;
            if (pick == ownedCapes.size()) {
                request = HttpRequest.newBuilder()
                        .uri(URI.create(OpsecConstants.AuthUrls.CAPE_ACTIVE_URL))
                        .header("Authorization", "Bearer " + accessToken)
                        .timeout(Duration.ofSeconds(OpsecConstants.Retry.HTTP_TIMEOUT_SECONDS))
                        .DELETE()
                        .build();
            } else {
                JsonObject body = new JsonObject();
                body.addProperty("capeId", ownedCapes.get(pick).id());
                request = HttpRequest.newBuilder()
                        .uri(URI.create(OpsecConstants.AuthUrls.CAPE_ACTIVE_URL))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(OpsecConstants.Retry.HTTP_TIMEOUT_SECONDS))
                        .PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();
            }

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 204) {
                this.lastError = "Cape change failed: HTTP " + response.statusCode();
                Opsec.LOGGER.warn("[OpSec] {}", this.lastError);
                return false;
            }
            Opsec.LOGGER.info("[OpSec] Randomized cape for {}", username);
            return true;
        } catch (Exception e) {
            this.lastError = "Cape change error: " + e.getMessage();
            Opsec.LOGGER.error("[OpSec] {}", this.lastError);
            return false;
        }
    }

    /**
     * Formats a UUID string from undashed to dashed format.
     */
    private String formatUuid(String undashed) {
        if (undashed == null || undashed.length() != 32) {
            return undashed;
        }
        return String.format("%s-%s-%s-%s-%s",
                undashed.substring(0, 8),
                undashed.substring(8, 12),
                undashed.substring(12, 16),
                undashed.substring(16, 20),
                undashed.substring(20, 32));
    }
    
    // Getters
    @Override public String getUsername() { return username; }
    @Override public String getUuid() { return uuid; }
    public long getLastValidated() { return lastValidated; }
    @Override public boolean isValid() { return valid; }
    @Override public String getLastError() { return lastError; }
    public boolean hasRefreshToken() { return refreshToken != null && !refreshToken.isBlank(); }

    @Override
    public boolean hasValidInfo() {
        return username != null && !username.isBlank() && uuid != null && !uuid.isBlank();
    }

    @Override
    public boolean isCracked() {
        return false;
    }
    
    /**
     * Attempts to refresh the access token using the stored refresh token.
     * This goes through the full Microsoft OAuth -> Xbox -> Minecraft auth chain.
     * Includes retry logic for transient errors.
     * @return true if refresh was successful
     */
    public boolean refreshAccessToken() {
        if (!hasRefreshToken()) {
            Opsec.LOGGER.debug("[OpSec] No refresh token available for {}", username);
            return false;
        }
        
        long lastRetryAfterMs = 0;
        boolean wasRateLimited = false;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Opsec.LOGGER.info("[OpSec] Attempting to refresh token for {} (attempt {})", username, attempt);
                
                // Step 1: Refresh Microsoft token
                var msResult = refreshMicrosoftTokenWithRetry();
                if (msResult.token == null) {
                    lastRetryAfterMs = msResult.retryAfterMs;
                    wasRateLimited = msResult.wasRateLimited;
                    if (attempt < MAX_RETRIES) {
                        long delay = calculateRetryDelay(attempt, 
                                wasRateLimited ? ValidationResult.RATE_LIMITED : ValidationResult.ERROR, 
                                lastRetryAfterMs);
                        Opsec.LOGGER.info("[OpSec] MS token refresh failed, retrying in {}ms...", delay);
                        Thread.sleep(delay);
                        continue;
                    }
                    this.lastError = "Failed to refresh Microsoft token";
                    return false;
                }
                
                // Step 2: Authenticate with Xbox Live
                var xblResult = authenticateXboxLiveWithRetry(msResult.token);
                if (xblResult.token == null) {
                    lastRetryAfterMs = xblResult.retryAfterMs;
                    wasRateLimited = xblResult.wasRateLimited;
                    if (attempt < MAX_RETRIES) {
                        long delay = calculateRetryDelay(attempt,
                                wasRateLimited ? ValidationResult.RATE_LIMITED : ValidationResult.ERROR,
                                lastRetryAfterMs);
                        Opsec.LOGGER.info("[OpSec] Xbox Live auth failed, retrying in {}ms...", delay);
                        Thread.sleep(delay);
                        continue;
                    }
                    this.lastError = "Failed Xbox Live auth";
                    return false;
                }
                
                // Step 3: Get XSTS token
                var xstsResult = authenticateXSTSWithRetry(xblResult.token);
                if (xstsResult.result == null) {
                    lastRetryAfterMs = xstsResult.retryAfterMs;
                    wasRateLimited = xstsResult.wasRateLimited;
                    if (attempt < MAX_RETRIES) {
                        long delay = calculateRetryDelay(attempt,
                                wasRateLimited ? ValidationResult.RATE_LIMITED : ValidationResult.ERROR,
                                lastRetryAfterMs);
                        Opsec.LOGGER.info("[OpSec] XSTS auth failed, retrying in {}ms...", delay);
                        Thread.sleep(delay);
                        continue;
                    }
                    this.lastError = "Failed XSTS auth";
                    return false;
                }
                String xstsToken = xstsResult.result[0];
                String userHash = xstsResult.result[1];
                
                // Step 4: Get Minecraft token
                var mcResult = authenticateMinecraftWithRetry(xstsToken, userHash);
                if (mcResult.token == null) {
                    lastRetryAfterMs = mcResult.retryAfterMs;
                    wasRateLimited = mcResult.wasRateLimited;
                    if (attempt < MAX_RETRIES) {
                        long delay = calculateRetryDelay(attempt,
                                wasRateLimited ? ValidationResult.RATE_LIMITED : ValidationResult.ERROR,
                                lastRetryAfterMs);
                        Opsec.LOGGER.info("[OpSec] Minecraft auth failed, retrying in {}ms...", delay);
                        Thread.sleep(delay);
                        continue;
                    }
                    this.lastError = "Failed Minecraft auth";
                    return false;
                }
                
                // Success! Update the access token
                this.accessToken = mcResult.token;
                this.lastValidated = System.currentTimeMillis();
                this.valid = true;
                this.lastError = null;
                
                Opsec.LOGGER.info("[OpSec] Successfully refreshed token for {}", username);
                return true;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                this.lastError = "Refresh interrupted";
                return false;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    long delay = calculateRetryDelay(attempt, ValidationResult.ERROR, 0);
                    Opsec.LOGGER.warn("[OpSec] Refresh attempt {} failed: {}, retrying in {}ms...", 
                            attempt, e.getMessage(), delay);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    this.lastError = "Refresh error: " + e.getMessage();
                    Opsec.LOGGER.error("[OpSec] Failed to refresh token: {}", e.getMessage());
                }
            }
        }
        return false;
    }
    
    /**
     * Result wrapper for token operations with retry information.
     */
    private static class TokenResult {
        final String token;
        final long retryAfterMs;
        final boolean wasRateLimited;
        
        TokenResult(String token, long retryAfterMs, boolean wasRateLimited) {
            this.token = token;
            this.retryAfterMs = retryAfterMs;
            this.wasRateLimited = wasRateLimited;
        }
    }
    
    private static class XSTSResult {
        final String[] result;
        final long retryAfterMs;
        final boolean wasRateLimited;
        
        XSTSResult(String[] result, long retryAfterMs, boolean wasRateLimited) {
            this.result = result;
            this.retryAfterMs = retryAfterMs;
            this.wasRateLimited = wasRateLimited;
        }
    }
    
    private TokenResult refreshMicrosoftTokenWithRetry() {
        long maxRetryAfter = 0;
        boolean wasRateLimited = false;
        
        // Try each known client ID since the refresh token is tied to the issuing client
        for (String clientId : AZURE_CLIENT_IDS) {
            var result = tryRefreshWithClientIdAndRetry(clientId);
            if (result.token != null) {
                Opsec.LOGGER.info("[OpSec] Token refresh succeeded with client ID: {}", clientId);
                return result;
            }
            if (result.retryAfterMs > maxRetryAfter) {
                maxRetryAfter = result.retryAfterMs;
            }
            if (result.wasRateLimited) {
                wasRateLimited = true;
            }
        }
        Opsec.LOGGER.warn("[OpSec] Token refresh failed with all known client IDs");
        return new TokenResult(null, maxRetryAfter, wasRateLimited);
    }
    
    private TokenResult tryRefreshWithClientIdAndRetry(String clientId) {
        // Try different scope combinations
        String[] scopes = {
                "XboxLive.signin%20XboxLive.offline_access",
                "XboxLive.signin%20offline_access", 
                "service::user.auth.xboxlive.com::MBI_SSL",
                "XboxLive.signin"
        };
        
        long maxRetryAfter = 0;
        boolean wasRateLimited = false;
        
        for (String scope : scopes) {
            TokenResult result = tryRefreshWithParamsAndRetry(clientId, scope);
            if (result.token != null) {
                return result;
            }
            if (result.retryAfterMs > maxRetryAfter) {
                maxRetryAfter = result.retryAfterMs;
            }
            if (result.wasRateLimited) {
                wasRateLimited = true;
            }
        }
        return new TokenResult(null, maxRetryAfter, wasRateLimited);
    }
    
    private TokenResult tryRefreshWithParamsAndRetry(String clientId, String scope) {
        try {
            String body = "client_id=" + clientId +
                    "&refresh_token=" + refreshToken +
                    "&grant_type=refresh_token" +
                    "&redirect_uri=https://login.live.com/oauth20_desktop.srf" +
                    "&scope=" + scope;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OpsecConstants.AuthUrls.MS_TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(OpsecConstants.Retry.HTTP_TIMEOUT_SECONDS))
                    .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            long retryAfterMs = extractRetryAfter(response);
            boolean wasRateLimited = response.statusCode() == 429;
            
            if (response.statusCode() != 200) {
                Opsec.LOGGER.debug("[OpSec] Refresh with client {} scope {} failed: HTTP {}", 
                        clientId, scope, response.statusCode());
                return new TokenResult(null, retryAfterMs, wasRateLimited);
            }
            
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            
            // Update refresh token if a new one is provided
            if (json.has("refresh_token")) {
                this.refreshToken = json.get("refresh_token").getAsString();
            }
            
            Opsec.LOGGER.debug("[OpSec] Refresh succeeded with client {} scope {}", clientId, scope);
            return new TokenResult(json.get("access_token").getAsString(), 0, false);
            
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Refresh with client {} scope {} error: {}", clientId, scope, e.getMessage());
            return new TokenResult(null, 0, false);
        }
    }
    
    private TokenResult authenticateXboxLiveWithRetry(String msToken) {
        // Try different RpsTicket formats
        String[] ticketFormats = {
                "d=" + msToken,     // Standard format for consumer accounts
                "t=" + msToken,     // Alternative format
                msToken             // Raw token
        };
        
        long maxRetryAfter = 0;
        boolean wasRateLimited = false;
        
        for (String rpsTicket : ticketFormats) {
            TokenResult result = tryXboxAuthWithTicketAndRetry(rpsTicket);
            if (result.token != null) {
                return result;
            }
            if (result.retryAfterMs > maxRetryAfter) {
                maxRetryAfter = result.retryAfterMs;
            }
            if (result.wasRateLimited) {
                wasRateLimited = true;
            }
        }
        return new TokenResult(null, maxRetryAfter, wasRateLimited);
    }
    
    private TokenResult tryXboxAuthWithTicketAndRetry(String rpsTicket) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("RelyingParty", "http://auth.xboxlive.com");
            body.addProperty("TokenType", "JWT");
            
            JsonObject properties = new JsonObject();
            properties.addProperty("AuthMethod", "RPS");
            properties.addProperty("SiteName", "user.auth.xboxlive.com");
            properties.addProperty("RpsTicket", rpsTicket);
            body.add("Properties", properties);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OpsecConstants.AuthUrls.XBOX_AUTH_URL))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(OpsecConstants.Retry.AUTH_TIMEOUT_SECONDS))
                    .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            long retryAfterMs = extractRetryAfter(response);
            boolean wasRateLimited = response.statusCode() == 429;
            
            if (response.statusCode() != 200) {
                String snippet = response.body().substring(0, Math.min(200, response.body().length()));
                Opsec.LOGGER.debug("[OpSec] Xbox Live auth with ticket format failed: HTTP {} - {}",
                        response.statusCode(), PrivacyLogger.redactSecrets(snippet));
                return new TokenResult(null, retryAfterMs, wasRateLimited);
            }
            
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            Opsec.LOGGER.debug("[OpSec] Xbox Live auth succeeded");
            return new TokenResult(json.get("Token").getAsString(), 0, false);
            
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Xbox Live auth error: {}", e.getMessage());
            return new TokenResult(null, 0, false);
        }
    }
    
    private XSTSResult authenticateXSTSWithRetry(String xblToken) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
            body.addProperty("TokenType", "JWT");
            
            JsonObject properties = new JsonObject();
            properties.addProperty("SandboxId", "RETAIL");
            com.google.gson.JsonArray userTokens = new com.google.gson.JsonArray();
            userTokens.add(xblToken);
            properties.add("UserTokens", userTokens);
            body.add("Properties", properties);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OpsecConstants.AuthUrls.XSTS_AUTH_URL))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(OpsecConstants.Retry.AUTH_TIMEOUT_SECONDS))
                    .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            long retryAfterMs = extractRetryAfter(response);
            boolean wasRateLimited = response.statusCode() == 429;
            
            if (response.statusCode() != 200) {
                Opsec.LOGGER.warn("[OpSec] XSTS auth failed: HTTP {}", response.statusCode());
                return new XSTSResult(null, retryAfterMs, wasRateLimited);
            }
            
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String token = json.get("Token").getAsString();
            String userHash = json.getAsJsonObject("DisplayClaims")
                    .getAsJsonArray("xui")
                    .get(0).getAsJsonObject()
                    .get("uhs").getAsString();
            
            return new XSTSResult(new String[]{token, userHash}, 0, false);
            
        } catch (Exception e) {
            Opsec.LOGGER.error("[OpSec] XSTS auth error: {}", e.getMessage());
            return new XSTSResult(null, 0, false);
        }
    }
    
    private TokenResult authenticateMinecraftWithRetry(String xstsToken, String userHash) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OpsecConstants.AuthUrls.MC_AUTH_URL))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .timeout(Duration.ofSeconds(OpsecConstants.Retry.AUTH_TIMEOUT_SECONDS))
                    .build();
            
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            
            long retryAfterMs = extractRetryAfter(response);
            boolean wasRateLimited = response.statusCode() == 429;
            
            if (response.statusCode() != 200) {
                Opsec.LOGGER.warn("[OpSec] Minecraft auth failed: HTTP {}", response.statusCode());
                return new TokenResult(null, retryAfterMs, wasRateLimited);
            }
            
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            return new TokenResult(json.get("access_token").getAsString(), 0, false);
            
        } catch (Exception e) {
            Opsec.LOGGER.error("[OpSec] Minecraft auth error: {}", e.getMessage());
            return new TokenResult(null, 0, false);
        }
    }
    
    /**
     * Revalidates the account by checking the token against Minecraft Services.
     * Updates the valid flag based on the result.
     * @return true if account is still valid
     */
    public boolean revalidate() {
        return revalidateWithResult() == ValidationResult.VALID;
    }
    
    /**
     * Revalidates the account with detailed result.
     * Only updates valid flag on definitive results (VALID/INVALID).
     * Rate limited or error results preserve the previous validity state.
     * @return ValidationResult
     */
    public ValidationResult revalidateWithResult() {
        ValidationResult result = fetchInfoWithResult();
        
        // Only update validity on definitive results
        if (result == ValidationResult.VALID) {
            this.valid = true;
        } else if (result == ValidationResult.INVALID) {
            this.valid = false;
        }
        // For RATE_LIMITED and ERROR, keep previous validity state
        
        return result;
    }
    
    // JSON serialization
    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "session");
        json.addProperty("accessToken", AccountCrypto.encrypt(accessToken));
        if (refreshToken != null && !refreshToken.isBlank()) {
            json.addProperty("refreshToken", AccountCrypto.encrypt(refreshToken));
        }
        json.addProperty("username", username);
        json.addProperty("uuid", uuid);
        json.addProperty("lastValidated", lastValidated);
        json.addProperty("valid", valid);
        if (skinTextureId != null) json.addProperty("skinTextureId", skinTextureId);
        if (capeTextureId != null) json.addProperty("capeTextureId", capeTextureId);
        return json;
    }

    public static SessionAccount fromJson(JsonObject json) {
        String token = json.has("accessToken") ? AccountCrypto.decrypt(json.get("accessToken").getAsString()) : "";
        String username = json.has("username") ? json.get("username").getAsString() : "";
        String uuid = json.has("uuid") ? json.get("uuid").getAsString() : "";

        SessionAccount account = new SessionAccount(token, username, uuid);
        if (json.has("refreshToken")) {
            account.refreshToken = AccountCrypto.decrypt(json.get("refreshToken").getAsString());
        }
        if (json.has("lastValidated")) {
            account.lastValidated = json.get("lastValidated").getAsLong();
        }
        if (json.has("valid")) {
            account.valid = json.get("valid").getAsBoolean();
        }
        if (json.has("skinTextureId")) {
            account.skinTextureId = json.get("skinTextureId").getAsString();
        }
        if (json.has("capeTextureId")) {
            account.capeTextureId = json.get("capeTextureId").getAsString();
        }
        return account;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionAccount other)) return false;
        return uuid != null && uuid.equals(other.uuid);
    }
    
    @Override
    public int hashCode() {
        return uuid != null ? uuid.hashCode() : 0;
    }
}

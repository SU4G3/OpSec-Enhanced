package aurick.opsec.mod.config;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.net.DpiEvasion;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Async update checker that queries the GitHub releases API to detect new versions.
 * All state fields are volatile for cross-thread visibility since the check runs
 * on a background thread and results are read on the render thread.
 */
public final class UpdateChecker {

    private static final String RELEASES_URL = "https://api.github.com/repos/aurickk/OpSec/releases/latest";
    // Used both when the (currently disabled, see OpsecClient) GitHub check has no
    // result yet, and as the "download official release" target on the tamper
    // warning screen — which checks JarIntegrityChecker's Modrinth listing, so this
    // needs to point at the same place regardless of the GitHub check's own state.
    private static final String FALLBACK_RELEASE_URL = "https://modrinth.com/mod/opsec-enhanced";

    private static volatile String latestVersion = null;
    private static volatile String releaseUrl = null;
    private static volatile boolean updateAvailable = false;
    private static volatile boolean checkComplete = false;
    private static volatile boolean shownThisSession = false;

    private UpdateChecker() {
        // Utility class
    }

    /**
     * Fires an async HTTP GET to the GitHub releases API to check for updates.
     * Non-blocking: runs on a daemon thread via CompletableFuture.
     */
    public static void checkForUpdate() {
        CompletableFuture.runAsync(() -> {
            try {
                String currentVersion = Opsec.getVersion();
                DpiEvasion.Result response = DpiEvasion.get(RELEASES_URL, Map.of(
                        "User-Agent", "OpSec-Mod/" + currentVersion,
                        "Accept", "application/vnd.github.v3+json"
                ), Duration.ofSeconds(10));

                if (response.status() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                    String tagName = json.has("tag_name") ? json.get("tag_name").getAsString() : null;
                    String htmlUrl = json.has("html_url") ? json.get("html_url").getAsString() : null;

                    if (tagName != null) {
                        // Strip leading "v" or "V" if present (e.g., "V1.0.5" -> "1.0.5")
                        String version = (tagName.startsWith("v") || tagName.startsWith("V")) ? tagName.substring(1) : tagName;
                        latestVersion = version;
                        releaseUrl = htmlUrl != null ? htmlUrl : FALLBACK_RELEASE_URL;

                        // Any difference means user should update
                        if (!version.equals(currentVersion)) {
                            updateAvailable = true;
                            Opsec.LOGGER.info("[OpSec] Update available: {} -> {} ({})", currentVersion, version, releaseUrl);
                        } else {
                            Opsec.LOGGER.debug("[OpSec] Mod is up to date ({})", currentVersion);
                        }
                    }
                } else {
                    Opsec.LOGGER.debug("[OpSec] GitHub API returned status {}", response.status());
                }
            } catch (Exception e) {
                Opsec.LOGGER.debug("[OpSec] Update check failed: {}", e.getMessage());
            } finally {
                checkComplete = true;
            }
        });
    }

    /**
     * Returns true if an update is available, the check is complete, the
     * update screen has not been shown this session, and the user hasn't
     * skipped this specific version.
     */
    public static boolean isUpdateAvailable() {
        return checkComplete && updateAvailable && !shownThisSession
                && !OpsecConfig.getInstance().getSettings().isVersionSkipped(latestVersion);
    }

    /**
     * Returns the latest version string from GitHub, or null if not yet checked.
     */
    public static String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Returns the URL to the latest release page on GitHub.
     * Falls back to the generic latest release URL if not available.
     */
    public static String getReleaseUrl() {
        return releaseUrl != null ? releaseUrl : FALLBACK_RELEASE_URL;
    }

    /**
     * Opens {@link #getReleaseUrl()} in the system browser, absorbing the
     * platform-open API split in one place instead of at each of this mod's
     * three call sites. MC 1.26.3's GLFW->SDL switch removed
     * {@code Util.OS.openUri(String)} entirely; {@code Blaze3D.openUri(URI)}
     * is its replacement there.
     */
    public static void openReleaseUrl() {
        try {
            //? if >=26.3 {
            /*com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(getReleaseUrl()));*/
            //?} elif >=1.21.11 {
            /*net.minecraft.util.Util.getPlatform().openUri(getReleaseUrl());*/
            //?} else {
            net.minecraft.Util.getPlatform().openUri(getReleaseUrl());
            //?}
        } catch (Exception e) {
            Opsec.LOGGER.warn("[OpSec] Failed to open release URL: {}", e.getMessage());
        }
    }

    /**
     * Marks the update notification as shown for this session.
     * Prevents the screen from appearing again even if user navigates back to title screen.
     */
    public static void markShown() {
        shownThisSession = true;
    }

    /**
     * Resets the session-shown flag so the update screen can appear again.
     * Called when the user resets all settings from the config screen.
     */
    public static void resetShown() {
        shownThisSession = false;
    }

    /**
     * Returns the current mod version. Delegates to {@link Opsec#getVersion()}.
     */
    public static String getCurrentVersion() {
        return Opsec.getVersion();
    }
}

package aurick.opsec.mod.accounts;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.mixin.client.MinecraftAccessor;
import com.google.gson.JsonObject;
import com.mojang.authlib.minecraft.UserApiService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents an offline/cracked Minecraft account that requires only a username.
 * Can connect to servers running in offline mode.
 */
public class CrackedAccount implements Account {

    private final String username;
    private final String uuid;
    private String lastError = null;

    public CrackedAccount(String username) {
        this.username = username;
        this.uuid = generateOfflineUuid(username).toString();
    }

    private static UUID generateOfflineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean login() {
        try {
            Minecraft mc = Minecraft.getInstance();
            MinecraftAccessor accessor = (MinecraftAccessor) mc;

            //? if >=1.21.9 {
            User newUser = new User(
                    username,
                    UUID.fromString(uuid),
                    "",
                    Optional.empty(),
                    Optional.empty()
            );
            //?} else if >=1.20.2 {
            /*User newUser = new User(
                    username,
                    UUID.fromString(uuid),
                    "",
                    Optional.empty(),
                    Optional.empty(),
                    User.Type.MSA
            );
            *///?} else {
            /*User newUser = new User(
                    username,
                    uuid,
                    "",
                    Optional.empty(),
                    Optional.empty(),
                    User.Type.MSA
            );
            *///?}

            accessor.opsec$setUser(newUser);

            // Use offline services - no authentication needed
            accessor.opsec$setUserApiService(UserApiService.OFFLINE);

            // Create profile key pair manager with offline service (no chat signing)
            // Offline accounts have no real chat-session key to fetch; skip the (otherwise
            // pointless) attempt entirely rather than relying on UserApiService.OFFLINE to no-op.
            ProfileKeyPairManager profileKeyPairManager = ProfileKeyPairManager.EMPTY_KEY_MANAGER;
            accessor.opsec$setProfileKeyPairManager(profileKeyPairManager);

            // Create social manager with offline service.
            // MC 1.26.3 bumped authlib 9.x->10.x, removing the yggdrasil package
            // entirely — MinecraftServicesDiscoveryService.create(proxy) replaces
            // YggdrasilAuthenticationService and exposes the same
            // createFriendsService(String) method shape, so `var` keeps the rest
            // of this block identical across the split.
            //? if >=26.3 {
            /*var authService = com.mojang.authlib.services.MinecraftServicesDiscoveryService.create(mc.getProxy());
            var friendsService = authService.createFriendsService("");
            net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler updateHandler =
                    new net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler(friendsService, mc);
            PlayerSocialManager socialManager = new PlayerSocialManager(mc, UserApiService.OFFLINE, friendsService, updateHandler);*/
            //?} elif >=26.2 {
            var authService = new com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService(mc.getProxy());
            var friendsService = authService.createFriendsService("");
            net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler updateHandler =
                    new net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler(friendsService, mc);
            PlayerSocialManager socialManager = new PlayerSocialManager(mc, UserApiService.OFFLINE, friendsService, updateHandler);
            //?} else {
            /*PlayerSocialManager socialManager = new PlayerSocialManager(mc, UserApiService.OFFLINE);
            *///?}
            accessor.opsec$setPlayerSocialManager(socialManager);

            this.lastError = null;
            Opsec.LOGGER.info("[OpSec] Successfully logged in as offline account: {}", username);
            return true;

        } catch (Exception e) {
            this.lastError = "Login error: " + e.getMessage();
            Opsec.LOGGER.error("[OpSec] Failed to login as offline account: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public String getLastError() {
        return lastError;
    }

    @Override
    public boolean hasValidInfo() {
        return username != null && !username.isBlank();
    }

    @Override
    public boolean isCracked() {
        return true;
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "cracked");
        json.addProperty("username", username);
        json.addProperty("uuid", uuid);
        return json;
    }

    public static CrackedAccount fromJson(JsonObject json) {
        String username = json.has("username") ? json.get("username").getAsString() : "";
        return new CrackedAccount(username);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CrackedAccount other)) return false;
        return uuid != null && uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return uuid != null ? uuid.hashCode() : 0;
    }
}

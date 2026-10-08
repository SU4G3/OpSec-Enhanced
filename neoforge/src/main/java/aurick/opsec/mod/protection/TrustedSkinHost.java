package aurick.opsec.mod.protection;

import java.net.URI;

/**
 * Vanilla fetches another player's (or a skull block/item's, or a Mannequin NPC's) skin/
 * cape texture URL with zero host or signature validation — a malicious/modified server
 * can point a visible player's GameProfile texture property at an attacker-controlled URL
 * and deanonymize that player's IP the instant they're rendered, with no interaction
 * required. See GitHub issues #12/#13/#14.
 *
 * <p>The only legitimate source for these texture URLs is Mojang's own texture CDN.
 * Blocking anything else has no legitimate downside: a real player's skin always resolves
 * through {@code textures.minecraft.net} via the signed Yggdrasil profile; a server-set
 * texture URL pointing elsewhere is never a normal case, only a bug or an attack.</p>
 */
public final class TrustedSkinHost {

    private static final String TRUSTED_HOST = "textures.minecraft.net";

    private TrustedSkinHost() {}

    public static boolean isTrusted(String urlString) {
        if (urlString == null) return false;
        try {
            String host = URI.create(urlString).getHost();
            return host != null && host.equalsIgnoreCase(TRUSTED_HOST);
        } catch (Exception e) {
            return false;
        }
    }
}

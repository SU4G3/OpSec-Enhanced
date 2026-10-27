package aurick.opsec.mod.util;

import aurick.opsec.mod.tracking.ModRegistry;

/**
 * The game's own built-in {@code en_us.json}, loaded independently of the player's actual
 * configured language ({@link net.minecraft.locale.Language#getInstance()}).
 *
 * <p>Used by Client Information Normalizer: that feature claims {@code language: "en_us"}
 * in the outgoing {@code ClientInformation}, but vanilla translation keys reached from a
 * server-authored component (a sign/anvil default-text probe, the same mechanism as the
 * mod-keybind sign exploit this mod already guards against — see wurst.wiki's
 * sign_translation_vulnerability) still resolve via the player's real configured language
 * unless something else intervenes. Resolving those specific lookups against this table
 * instead keeps the normalizer's language claim consistent with what a server can actually
 * observe, without touching the player's own non-packet-originated UI.</p>
 *
 * <p>Delegates to {@link ModRegistry#getVanillaEnUsValue}, which is populated from the real
 * vanilla-pack resource stack during normal language loading ({@code ClientLanguageMixin}).
 * This previously loaded a bundled copy via
 * {@code Class.getResourceAsStream("/assets/minecraft/lang/en_us.json")}, which only ever
 * found anything in a Loom dev workspace (Loom merges assets into the same jar as the
 * compiled classes there for convenience) — a real production launch never bundles assets
 * that way, so the lookup silently returned nothing for every real user, and every
 * normalized vanilla-key lookup fell back to the raw key itself and got flagged as a
 * fingerprinting probe that never happened (issue #25).</p>
 */
public final class VanillaEnUsLanguage {

    private VanillaEnUsLanguage() {
    }

    /** Returns the built-in en_us value for {@code key}, or {@code fallback} if not yet captured. */
    public static String get(String key, String fallback) {
        String value = ModRegistry.getVanillaEnUsValue(key);
        return value != null ? value : fallback;
    }
}

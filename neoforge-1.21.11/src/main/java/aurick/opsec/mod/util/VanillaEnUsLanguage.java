package aurick.opsec.mod.util;

import aurick.opsec.mod.Opsec;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
 */
public final class VanillaEnUsLanguage {

    private static volatile Map<String, String> table;

    private VanillaEnUsLanguage() {
    }

    /** Returns the built-in en_us value for {@code key}, or {@code fallback} if absent. */
    public static String get(String key, String fallback) {
        Map<String, String> loaded = table;
        if (loaded == null) {
            loaded = load();
            table = loaded;
        }
        String value = loaded.get(key);
        return value != null ? value : fallback;
    }

    private static Map<String, String> load() {
        try (InputStream in = VanillaEnUsLanguage.class.getResourceAsStream("/assets/minecraft/lang/en_us.json")) {
            if (in == null) {
                Opsec.LOGGER.debug("[OpSec] Built-in en_us.json not found on classpath");
                return Collections.emptyMap();
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> map = new HashMap<>();
            for (String key : json.keySet()) {
                map.put(key, json.get(key).getAsString());
            }
            return map;
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Failed to load built-in en_us.json: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}

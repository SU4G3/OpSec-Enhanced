package aurick.opsec.mod.streamer;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import net.minecraft.network.chat.Component;

import java.util.Random;
import java.util.UUID;

/**
 * Masks a player's displayed name for Streamer Mode. Applies to every player, including
 * the local one — the point is to keep a recording/stream from showing who's playing, not
 * just who else is on the server.
 *
 * <p>{@link SpoofSettings.NameMaskStyle#GLYPHS} derives the glyphs deterministically from
 * {@code (uuid, time bucket)} rather than caching anything — the same player gets the same
 * glyphs for the whole bucket window (so a tab list and a nametag drawn in the same instant
 * agree), and a fresh, unrelated set every {@link #ROTATE_MILLIS}, so a viewer can't read a
 * stable fake name off a clip and treat it as an identity.
 */
public final class StreamerModeNameMask {

    private static final long ROTATE_MILLIS = 3000L;
    private static final int GLYPH_COUNT = 6;
    // CJK Unified Ideographs block — same visual family Hypixel's own nick-disguise uses.
    private static final int GLYPH_RANGE_START = 0x4E00;
    private static final int GLYPH_RANGE_END = 0x9FFF;

    private StreamerModeNameMask() {}

    public static boolean isActive() {
        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        return settings.isStreamerModeEnabled() && settings.isStreamerMaskPlayerNames();
    }

    /** Masks a plain name string (tab list / scoreboard-style consumers). */
    public static String maskString(UUID playerId) {
        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        if (settings.getStreamerNameMaskStyle() == SpoofSettings.NameMaskStyle.HIDDEN) {
            return "Player";
        }
        return glyphs(playerId);
    }

    /** Masks a name as a {@link Component} (nametag / display-name consumers). */
    public static Component maskComponent(UUID playerId) {
        return Component.literal(maskString(playerId));
    }

    private static String glyphs(UUID playerId) {
        long bucket = System.currentTimeMillis() / ROTATE_MILLIS;
        long seed = playerId.getMostSignificantBits() ^ playerId.getLeastSignificantBits() ^ (bucket * 0x9E3779B97F4A7C15L);
        Random random = new Random(seed);
        StringBuilder sb = new StringBuilder(GLYPH_COUNT);
        for (int i = 0; i < GLYPH_COUNT; i++) {
            sb.appendCodePoint(GLYPH_RANGE_START + random.nextInt(GLYPH_RANGE_END - GLYPH_RANGE_START + 1));
        }
        return sb.toString();
    }
}

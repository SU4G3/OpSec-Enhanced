package aurick.opsec.mod.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Procedurally generates a flat-color 64x64 skin texture instead of pulling one
 * from a third-party gallery: no scraping fragility, no consent/licensing
 * question over reusing someone else's uploaded skin, and no dependency on any
 * external site staying up. A solid color is invariant to the skin template's
 * UV layout, so it's guaranteed to look correct (just plain) on every model
 * variant and Minecraft version, unlike a hand-mapped multi-region design.
 *
 * <p>Doubles as a fix for the mod's own Skin/Cape Correlation Alert: giving two
 * of your accounts distinct generated colors means they won't share a skin
 * hash and trip that alert against each other.</p>
 */
public final class SkinRandomizer {
    private SkinRandomizer() {}

    private static final int SIZE = 64;

    public static byte[] generateFlatColorSkinPng() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int r = 40 + rng.nextInt(180);
        int g = 40 + rng.nextInt(180);
        int b = 40 + rng.nextInt(180);
        int argb = 0xFF000000 | (r << 16) | (g << 8) | b;

        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                image.setRGB(x, y, argb);
            }
        }

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Random model variant, matching the {@code variant} field the skin-upload API expects. */
    public static String randomVariant() {
        return ThreadLocalRandom.current().nextBoolean() ? "classic" : "slim";
    }
}

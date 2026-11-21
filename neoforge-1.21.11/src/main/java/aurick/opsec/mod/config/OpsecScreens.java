package aurick.opsec.mod.config;

import net.minecraft.client.gui.screens.Screen;

/**
 * Recognises the screens this mod owns.
 *
 * <p>The pre-1.20.5 tab header fill is suppressed on OpSec screens, so that mixin
 * needs to know "an OpSec screen is open" without depending on one concrete class.
 * The rule editor is a Cloth Config screen, which this mod does not subclass, so its
 * instance is tracked here.</p>
 */
public final class OpsecScreens {

    /** Marker implemented by the screens this mod writes itself. */
    public interface Marker {
    }

    private static volatile Screen clothConfigScreen;

    private OpsecScreens() {
    }

    public static void registerClothConfigScreen(Screen screen) {
        clothConfigScreen = screen;
    }

    public static boolean isOpsecScreen(Screen screen) {
        return screen instanceof Marker || screen == clothConfigScreen;
    }
}

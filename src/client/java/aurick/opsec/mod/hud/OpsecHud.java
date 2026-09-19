package aurick.opsec.mod.hud;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
*/
//?} else {
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.GuiGraphics;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Small always-on-screen indicator of the current spoof brand, off by default
 * (Misc > Appearance > Show HUD Indicator). Purely cosmetic — has no effect on
 * any protection feature.
 *
 * <p>26.1+ moved Fabric API's HUD rendering to an extraction-based pipeline
 * ({@code HudElementRegistry} / {@code HudElement.extractRenderState}, mirroring
 * the {@code GuiGraphicsExtractor} split already used elsewhere in this codebase
 * for the config screen), replacing the classic {@code HudRenderCallback} this
 * uses on every earlier version.</p>
 */
public final class OpsecHud {
    private OpsecHud() {}

    public static void register() {
        //? if >=26.1 {
        /*HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("opsec", "hud"), OpsecHud::onExtract);*/
        //?} else {
        HudRenderCallback.EVENT.register(OpsecHud::onRender);
        //?}
    }

    //? if >=26.1 {
    /*private static void onExtract(GuiGraphicsExtractor graphics, Object tickCounter) {
        // No screen/hideGui guard here: HudElementRegistry's own pipeline already
        // skips every registered element (vanilla's included, per VanillaHudElements)
        // while a Screen covers the HUD or F1 hide-HUD is active, the same way it
        // already gates the vanilla hotbar/crosshair/etc. — Minecraft.screen and
        // Options.hideGui aren't public fields on this version to check ourselves.
        if (!OpsecConfig.getInstance().getSettings().isShowHudIndicator()) return;
        Minecraft mc = Minecraft.getInstance();
        graphics.text(mc.font, Component.literal(hudText()), 4, 4, 0xFFFFFF, true);
    }*/
    //?} else {
    private static void onRender(GuiGraphics graphics, Object tickCounter) {
        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        if (!settings.isShowHudIndicator()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.options.hideGui) return;
        graphics.drawString(mc.font, Component.literal(hudText()), 4, 4, 0xFFFFFF, true);
    }
    //?}

    private static String hudText() {
        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        return settings.getAccentColor().code() + "OpSec: " + settings.getEffectiveBrand();
    }
}

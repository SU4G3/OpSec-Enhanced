package aurick.opsec.mod.hud;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
//? if <26.1 {
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
 * <p><b>Not available on 26.1+.</b> Fabric API moved HUD rendering to a new
 * extraction-based pipeline there ({@code HudElementRegistry} /
 * {@code HudElement.extractRenderState}, mirroring the {@code GuiGraphicsExtractor}
 * split already used elsewhere in this codebase for the config screen). An
 * attempt to wire this up compiled cleanly and matched the API's bytecode
 * contract, but didn't actually render in-game for reasons that weren't
 * possible to track down without a runnable game client in the dev
 * environment — so rather than ship a silently-broken toggle, {@link #register()}
 * is a no-op there and the setting is hidden (see
 * {@link OpsecConfig#MC_VERSION_HAS_HUD_INDICATOR}). Tracked for a follow-up fix.</p>
 */
public final class OpsecHud {
    private OpsecHud() {}

    public static void register() {
        //? if <26.1 {
        HudRenderCallback.EVENT.register(OpsecHud::onRender);
        //?}
    }

    //? if <26.1 {
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

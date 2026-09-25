package aurick.opsec.mod.hud;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
//? if >=26.1 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

//?}

/**
 * Small always-on-screen indicator of the current spoof brand, off by default
 * (Misc &gt; Appearance &gt; Show HUD Indicator). Purely cosmetic — has no
 * effect on any protection feature.
 *
 * <p>On 1.20.1-1.21.11, this is a no-op: rendering happens via a direct Mixin
 * into vanilla's own {@code Gui.render(...)} instead (see {@code GuiHudMixin}).
 * {@link #shouldRender()} and {@link #hudText()} below are the shared logic
 * that mixin calls into.</p>
 *
 * <p>Root cause of the "renders nothing on 1.21.6+" regression, found by
 * actually testing in-game rather than guessing further: MC 1.21.6 made text
 * color full ARGB (earlier versions treated a bare RGB value as opaque
 * regardless of the top byte). {@code 0xFFFFFF} has {@code alpha=0x00} under
 * the new rule — fully transparent — so the draw call executed correctly the
 * whole time, it just drew invisible text. Confirmed by adding a diagnostic
 * {@code graphics.fill()} at the same call site (already using a correct
 * opaque ARGB shape), which rendered fine while the text didn't. Use
 * {@code 0xFFFFFFFF}, not {@code 0xFFFFFF}, for opaque white everywhere in
 * this codebase going forward.</p>
 *
 * <p>On 26.1+, vanilla's {@code Gui} class has no plain {@code render(...)}
 * method left at all (fully migrated to an extraction-based pipeline whose
 * exact shape has already changed between 26.1 and 26.2 at the vanilla level),
 * so a direct mixin there is too fragile to hand-maintain per patch. Uses
 * Fabric API's {@code HudElementRegistry} instead. This path had the same
 * transparent-color bug as above (now fixed) — that may well have been the
 * entire problem there too, but it hasn't been re-tested in-game since the
 * fix, so treat it as plausible-but-unconfirmed rather than done.</p>
 */
public final class OpsecHud {
    private OpsecHud() {}

    public static void register() {
        //? if >=26.1 {
        NeoForge.EVENT_BUS.addListener(OpsecHud::onRenderGui);
        //?}
        // <26.1: no registration needed here — GuiHudMixin applies automatically
        // once Mixin loads the config; see opsec.client.mixins.json.
    }

    //? if >=26.1 {
    private static void onRenderGui(RenderGuiEvent.Post event) {
        if (!shouldRender()) return;
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        graphics.text(mc.font, Component.literal(hudText()), 4, 4, 0xFFFFFFFF, true);
    }
    //?}

    public static boolean shouldRender() {
        return OpsecConfig.getInstance().getSettings().isShowHudIndicator();
    }

    public static String hudText() {
        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        return settings.getAccentColor().code() + "OpSec: " + settings.getEffectiveBrand();
    }
}

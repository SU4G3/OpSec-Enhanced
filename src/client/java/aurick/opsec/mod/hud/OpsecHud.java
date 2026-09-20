package aurick.opsec.mod.hud;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
*/
//?}

/**
 * Small always-on-screen indicator of the current spoof brand, off by default
 * (Misc &gt; Appearance &gt; Show HUD Indicator). Purely cosmetic — has no
 * effect on any protection feature.
 *
 * <p>On 1.20.1-1.21.11, this is a no-op: rendering happens via a direct Mixin
 * into vanilla's own {@code Gui.render(...)} instead (see {@code GuiHudMixin}),
 * which fixed a real regression where an earlier Fabric-API-event-based
 * implementation silently stopped firing on 1.21.11 specifically. {@link #shouldRender()}
 * and {@link #hudText()} below are the shared logic that mixin calls into.</p>
 *
 * <p>On 26.1+, vanilla's {@code Gui} class has no plain {@code render(...)}
 * method left at all (fully migrated to an extraction-based pipeline whose
 * exact shape has already changed between 26.1 and 26.2 at the vanilla level),
 * so a direct mixin there is too fragile to hand-maintain per patch. Uses
 * Fabric API's {@code HudElementRegistry} instead, which exists specifically
 * to absorb that kind of internal churn behind a stable interface — though
 * this path is less battle-tested in this codebase than the mixin path above,
 * having previously appeared to register successfully but not visibly render
 * in-game for reasons not yet diagnosed. If it still doesn't render, that's a
 * known open issue, not a silent failure to be quiet about.</p>
 */
public final class OpsecHud {
    private OpsecHud() {}

    public static void register() {
        //? if >=26.1 {
        /*HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("opsec", "hud"), OpsecHud::onExtract);*/
        //?}
        // <26.1: no registration needed here — GuiHudMixin applies automatically
        // once Mixin loads the config; see opsec.client.mixins.json.
    }

    //? if >=26.1 {
    /*private static void onExtract(GuiGraphicsExtractor graphics, Object tickCounter) {
        if (!shouldRender()) return;
        Minecraft mc = Minecraft.getInstance();
        graphics.text(mc.font, Component.literal(hudText()), 4, 4, 0xFFFFFF, true);
    }*/
    //?}

    public static boolean shouldRender() {
        return OpsecConfig.getInstance().getSettings().isShowHudIndicator();
    }

    public static String hudText() {
        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        return settings.getAccentColor().code() + "OpSec: " + settings.getEffectiveBrand();
    }
}

package aurick.opsec.mod.mixin.client;

//? if <26.1 {
import aurick.opsec.mod.hud.OpsecHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
//? if >=1.21.1
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the OpSec HUD indicator (see {@link OpsecHud}) directly into vanilla's
 * own {@code Gui.render(...)}, on the versions where that method still exists
 * in this classic form (1.20.1-1.21.11).
 *
 * <p>Replaces an earlier attempt that used Fabric API's {@code HudRenderCallback}
 * event: that compiled cleanly and matched the API's bytecode contract on every
 * version, but was confirmed (by actually testing in-game) to silently not fire
 * on 1.21.11 specifically, for reasons not diagnosable without a runnable game
 * client. Injecting directly into the vanilla method Fabric's own event
 * ultimately has to bridge to removes that unexplained middle layer, and
 * matches how the rest of this mod already hooks vanilla everywhere else.</p>
 *
 * <p>Stonecutter-gated {@code <26.1}: vanilla's {@code Gui} class has no plain
 * {@code render(...)} method left on 26.1+ at all (fully replaced by an
 * extraction-based pipeline whose exact shape has already changed between
 * 26.1 and 26.2 at the vanilla level) — see {@link OpsecHud}'s javadoc for
 * what's used instead there. The mixin entry in {@code opsec.client.mixins.json}
 * is gated by a matching {@code build.gradle} template-expand step.</p>
 */
@Mixin(Gui.class)
public class GuiHudMixin {

    //? if >=1.21.1 {
    @Inject(method = "render", at = @At("TAIL"))
    private void opsec$onRenderHud(GuiGraphics graphics, DeltaTracker tickTracker, CallbackInfo ci) {
        opsec$render(graphics);
    }
    //?} else {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void opsec$onRenderHud(GuiGraphics graphics, float tickDelta, CallbackInfo ci) {
        opsec$render(graphics);
    }
    *///?}

    private void opsec$render(GuiGraphics graphics) {
        if (!OpsecHud.shouldRender()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.options.hideGui) return;
        graphics.drawString(mc.font, Component.literal(OpsecHud.hudText()), 4, 4, 0xFFFFFF, true);
    }
}
//?} else {
/*public class GuiHudMixin {}
*///?}

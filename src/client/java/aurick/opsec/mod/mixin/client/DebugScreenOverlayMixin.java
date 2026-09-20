package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.config.OpsecConfig;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces the F3 debug screen off under Streamer Mode — it's the actual on-screen leak
 * (coordinates reveal your base, plus dimension/target/server-brand info), unlike OpSec's
 * own settings screen which a viewer isn't looking at during normal play. {@code
 * showDebugScreen()} is the single gate both the old direct-render pipeline (<26.1) and
 * the newer extraction-based one (26.1+, {@code extractRenderState}) check before drawing
 * anything, so hooking this one boolean covers every version without touching either
 * render path (the newer one uses unmapped synthetic internals that aren't safe to target
 * directly).
 */
@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {

    @Inject(method = "showDebugScreen", at = @At("HEAD"), cancellable = true)
    private void opsec$hideInStreamerMode(CallbackInfoReturnable<Boolean> cir) {
        if (OpsecConfig.getInstance().getSettings().isStreamerMode()) {
            cir.setReturnValue(false);
        }
    }
}

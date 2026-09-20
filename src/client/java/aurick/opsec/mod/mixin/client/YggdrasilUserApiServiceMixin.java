package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import com.mojang.authlib.minecraft.TelemetrySession;
//? if >=26.3 {
/*import com.mojang.authlib.services.MinecraftServicesUserApiService;
*/
//?} else {
import com.mojang.authlib.yggdrasil.YggdrasilUserApiService;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.Executor;

/**
 * Mixin to disable telemetry session creation.
 *
 * This prevents the creation of telemetry sessions that would send data to Mojang.
 * By returning TelemetrySession.DISABLED, no telemetry data is collected or sent.
 *
 * Based on No Chat Reports by Aizistral-Studios:
 * https://github.com/Aizistral-Studios/No-Chat-Reports
 *
 * Targets MinecraftServicesUserApiService from MC 1.26.3+ — that's authlib
 * 10.x's replacement for YggdrasilUserApiService (removed entirely in the
 * 9.x->10.x bump), keeping the exact same newTelemetrySession(Executor) shape.
 */
//? if >=26.3 {
/*@Mixin(value = MinecraftServicesUserApiService.class, remap = false)*/
//?} else {
@Mixin(value = YggdrasilUserApiService.class, remap = false)
//?}
public class YggdrasilUserApiServiceMixin {

    /**
     * Disable telemetry session creation by returning a disabled session.
     */
    @Inject(method = "newTelemetrySession", at = @At("HEAD"), cancellable = true)
    private void opsec$disableTelemetrySession(Executor executor, CallbackInfoReturnable<TelemetrySession> info) {
        if (OpsecConfig.getInstance().shouldDisableTelemetry()) {
            Opsec.LOGGER.debug("[OpSec] Returning disabled TelemetrySession");
            info.setReturnValue(TelemetrySession.DISABLED);
        }
    }
}

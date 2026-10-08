package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.proxy.CloudflarePingRelay;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.UnknownHostException;

/**
 * Blocks the automatic ping the multiplayer screen fires at every saved server on open
 * (that's a connection attempt — and therefore an IP leak — to servers the player never
 * chose to join). With this on, a saved server shows no MOTD/ping/player-count until
 * actually joined.
 *
 * <p>When a Cloudflare ping relay is configured (see #15 and
 * {@code cloudflare-worker/ping-relay.js}), backs the (otherwise-skipped) direct ping
 * with that relay instead of throwing outright, so saved servers still show live status
 * without a direct connection from this IP.</p>
 */
@Mixin(ServerStatusPinger.class)
public abstract class ServerStatusPingerMixin {

    @Inject(method = "pingServer(Lnet/minecraft/client/multiplayer/ServerData;Ljava/lang/Runnable;Ljava/lang/Runnable;)V",
        at = @At("HEAD"), cancellable = true)
    private void opsec$blockAutoPing(ServerData data, Runnable onSuccess, Runnable onFailure, CallbackInfo ci) throws UnknownHostException {
        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        if (!settings.isLazyServerPing()) return;

        String workerUrl = settings.getCloudflarePingRelayUrl();
        if (!settings.isCloudflarePingRelayEnabled() || workerUrl.isEmpty()) {
            throw new UnknownHostException("OpSec: Lazy Server List Ping is on, not auto-pinging");
        }

        ci.cancel();
        CloudflarePingRelay.ping(workerUrl, data).whenComplete((success, error) -> {
            if (error != null) {
                Opsec.LOGGER.debug("[OpSec] Cloudflare ping relay failed: {}", error.getMessage());
            }
            if (Boolean.TRUE.equals(success)) {
                onSuccess.run();
            } else {
                data.setState(ServerData.State.UNREACHABLE);
                onFailure.run();
            }
        });
    }
}

package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.proxy.CloudflarePingRelay;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
//? if >=1.21.11 {
/*import net.minecraft.server.network.EventLoopGroupHolder;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.UnknownHostException;

/**
 * Blocks the automatic ping the multiplayer screen fires at every saved server on open
 * (that's a connection attempt — and therefore an IP leak — to servers the player never
 * chose to join). {@code pingServer} is the single choke point regardless of whether the
 * caller is the per-row entry ({@code OnlineServerEntry.refreshStatus}, 1.20.6+) or the
 * screen itself ({@code JoinMultiplayerScreen.refreshServerList}, 1.20.1-1.20.4) — same
 * method name and only-overload on every supported version, so no version split needed.
 * With this on, a saved server shows no MOTD/ping/player-count until actually joined.
 *
 * <p>Throws {@link UnknownHostException} (which every version's {@code pingServer}
 * already declares) instead of cancelling normally — {@code refreshStatus()} only reads
 * {@code ServerData.state()} once, at construction, and never gets called again unless
 * the ping's own success/failure callback triggers a repaint. A plain {@code ci.cancel()}
 * skips those callbacks entirely, leaving the row's status icon stuck on "pinging..."
 * forever. Every caller already wraps this call in a try/catch that handles a resolution
 * failure gracefully — sets {@code ServerData.State.UNREACHABLE}, a "can't resolve"
 * status message, and schedules the repaint — which is exactly the accurate "not pinged"
 * outcome this feature wants, using vanilla's own real error-handling path instead of
 * this mod guessing at per-version callback parameter positions.</p>
 *
 * <p>On >=1.20.6, when a Cloudflare ping relay is configured (see #15 and
 * {@code cloudflare-worker/ping-relay.js}), this backs the (otherwise-skipped) direct
 * ping with that relay instead of throwing outright, so saved servers still show live
 * status without a direct connection from this IP. Below 1.20.6, {@code pingServer} only
 * takes a single {@code Runnable} and this mod doesn't special-case its exact semantics,
 * so the relay path is skipped there and Lazy Server List Ping just blocks as before.</p>
 */
@Mixin(ServerStatusPinger.class)
public abstract class ServerStatusPingerMixin {

    //? if >=1.20.6 {
    //? if >=1.21.11 {
    /*@Inject(method = "pingServer(Lnet/minecraft/client/multiplayer/ServerData;Ljava/lang/Runnable;Ljava/lang/Runnable;Lnet/minecraft/server/network/EventLoopGroupHolder;)V",
        at = @At("HEAD"), cancellable = true)
    private void opsec$blockAutoPing(ServerData data, Runnable onSuccess, Runnable onFailure, EventLoopGroupHolder group, CallbackInfo ci) throws UnknownHostException {
    *///?} else {
    @Inject(method = "pingServer(Lnet/minecraft/client/multiplayer/ServerData;Ljava/lang/Runnable;Ljava/lang/Runnable;)V",
        at = @At("HEAD"), cancellable = true)
    private void opsec$blockAutoPing(ServerData data, Runnable onSuccess, Runnable onFailure, CallbackInfo ci) throws UnknownHostException {
    //?}
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
    //?} else {
    /*@Inject(method = "pingServer", at = @At("HEAD"))
    private void opsec$blockAutoPing(CallbackInfo ci) throws UnknownHostException {
        if (OpsecConfig.getInstance().getSettings().isLazyServerPing()) {
            throw new UnknownHostException("OpSec: Lazy Server List Ping is on, not auto-pinging");
        }
    }
    *///?}
}

package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.config.OpsecConfig;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks the automatic ping the multiplayer screen fires at every saved server on open
 * (that's a connection attempt — and therefore an IP leak — to servers the player never
 * chose to join). {@code pingServer} is the single choke point regardless of whether the
 * caller is the per-row entry ({@code OnlineServerEntry.refreshStatus}, 1.20.6+) or the
 * screen itself ({@code JoinMultiplayerScreen.refreshServerList}, 1.20.1-1.20.4) — same
 * method name and only-overload on every supported version, so no version split needed.
 * With this on, a saved server shows no MOTD/ping/player-count until actually joined.
 */
@Mixin(ServerStatusPinger.class)
public abstract class ServerStatusPingerMixin {

    @Inject(method = "pingServer", at = @At("HEAD"), cancellable = true)
    private void opsec$blockAutoPing(CallbackInfo ci) {
        if (OpsecConfig.getInstance().getSettings().isLazyServerPing()) {
            ci.cancel();
        }
    }
}

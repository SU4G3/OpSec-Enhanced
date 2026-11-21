package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.net.ServerTargets;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Captures the address the player typed at the point where the client resolves it locally.
 *
 * <p>{@code ServerNameResolver.resolveAddress(ServerAddress)} is the single place where
 * every connection path (server list, direct connect, Realms, status ping) turns the
 * address the player entered into an address the client can dial. The {@link ServerAddress}
 * argument still holds the typed address at that moment, while the returned
 * {@link ResolvedServerAddress} holds what it resolved to — so this hook is the one place
 * where both are available at the same time.</p>
 *
 * <p>Capturing here rather than at the screen layer is what makes the captured address
 * independent of the connection: the same typed address is recorded whether the client
 * dials it directly, through a proxy, or after a server-side redirect.</p>
 */
@Mixin(ServerNameResolver.class)
public class ServerNameResolverMixin {

    @Inject(method = "resolveAddress", at = @At("RETURN"))
    private void opsec$captureTypedAddress(ServerAddress address, CallbackInfoReturnable<Optional<ResolvedServerAddress>> cir) {
        Optional<ResolvedServerAddress> resolved = cir.getReturnValue();
        if (resolved.isEmpty() || address == null) {
            return;
        }
        ServerTargets.record(address.getHost(), resolved.get().getPort(), resolved.get().getHostIp());
    }
}

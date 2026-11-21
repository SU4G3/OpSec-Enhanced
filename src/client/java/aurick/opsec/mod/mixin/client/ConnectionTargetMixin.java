package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.net.ServerTargets;
import io.netty.channel.ChannelFuture;
import net.minecraft.network.Connection;
//? if >=1.21.11 {
/*import net.minecraft.server.network.EventLoopGroupHolder;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;

/**
 * Binds the captured typed address to the connection that is actually being dialled.
 *
 * <p>{@code Connection.connect(...)} is the single point where every client connection is
 * opened, and it receives the address that is about to be dialled. The address typed by
 * the player was captured earlier by {@code ServerNameResolverMixin}; this hook pairs the
 * two and stores the result on the connection, so the login handler can read the typed
 * address back off its own connection instead of deriving it from the remote address.</p>
 */
@Mixin(Connection.class)
public class ConnectionTargetMixin {

    //? if >=1.21.11 {
    /*@Inject(method = "connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;",
        at = @At("HEAD"))
    private static void opsec$bindTarget(InetSocketAddress address, EventLoopGroupHolder group, Connection connection, CallbackInfoReturnable<ChannelFuture> cir) {
        opsec$bind(address, connection);
    }
    *///?} else {
    @Inject(method = "connect(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;",
        at = @At("HEAD"))
    private static void opsec$bindTarget(InetSocketAddress address, boolean useEpoll, Connection connection, CallbackInfoReturnable<ChannelFuture> cir) {
        opsec$bind(address, connection);
    }
    //?}

    @Unique
    private static void opsec$bind(InetSocketAddress address, Connection connection) {
        if (address == null || connection == null) {
            return;
        }
        String ip = address.getAddress() != null ? address.getAddress().getHostAddress() : null;
        ServerTargets.bind(connection, ip, address.getPort());
    }
}

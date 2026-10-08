package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import aurick.opsec.mod.proxy.HttpConnectProxyHandler;
import aurick.opsec.mod.proxy.PendingProxyTargets;
import aurick.opsec.mod.proxy.Socks5ConnectHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Routes the actual multiplayer game connection through a user-configured SOCKS5/HTTP
 * proxy (issue #11). The JVM's {@code -DsocksProxyHost} has zero effect on this because
 * {@code Connection.connect(...)} builds its own raw Netty {@link Bootstrap}, bypassing
 * {@code java.net}'s proxy selector entirely.
 *
 * <p>Vanilla's own pipeline construction, compression/serialization setup and worker-group
 * selection are left completely untouched -- only the actual TCP dial target is swapped to
 * the proxy's address (via {@link Redirect} on the single Netty
 * {@code Bootstrap.connect(InetAddress, int)} call), with a from-scratch SOCKS5/HTTP CONNECT
 * handshake handler ({@link Socks5ConnectHandler}/{@link HttpConnectProxyHandler}) prepended
 * to the pipeline (via the companion mixin below, on {@code Connection$1}) to tunnel to the
 * real destination transparently. Minecraft's bundled {@code netty-handler} jar excludes
 * {@code io.netty.handler.proxy.*}, so these handshakes are hand-written.</p>
 */
@Mixin(Connection.class)
public class GameConnectionProxyMixin {

    @Inject(method = "connect(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;",
        at = @At("HEAD"))
    private static void opsec$captureTarget(InetSocketAddress address, boolean useEpoll, Connection connection, CallbackInfo ci) {
        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        if (settings.getGameProxyType() == SpoofSettings.GameProxyType.NONE) return;
        if (settings.getGameProxyHost().isEmpty()) return;
        PendingProxyTargets.put(connection, address);
    }

    @Redirect(method = "connect(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;",
        at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/Bootstrap;connect(Ljava/net/InetAddress;I)Lio/netty/channel/ChannelFuture;"))
    private static ChannelFuture opsec$dialProxyInstead(Bootstrap bootstrap, InetAddress address, int port) {
        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        SpoofSettings.GameProxyType type = settings.getGameProxyType();
        String host = settings.getGameProxyHost();
        if (type == SpoofSettings.GameProxyType.NONE || host.isEmpty()) {
            return bootstrap.connect(address, port);
        }
        try {
            InetSocketAddress proxyAddress = new InetSocketAddress(host, settings.getGameProxyPort());
            Opsec.LOGGER.info("[OpSec] Routing game connection via {} proxy {}:{}", type, host, settings.getGameProxyPort());
            return bootstrap.connect(proxyAddress);
        } catch (Exception e) {
            Opsec.LOGGER.error("[OpSec] Failed to resolve game proxy {}:{}, connecting directly: {}",
                host, settings.getGameProxyPort(), e.getMessage());
            return bootstrap.connect(address, port);
        }
    }
}

/**
 * Companion to {@link GameConnectionProxyMixin}: prepends the SOCKS5/HTTP CONNECT handshake
 * handler as the very first pipeline handler, before vanilla's "timeout" handler is added,
 * so it runs before anything else on the connection.
 */
@Mixin(targets = "net.minecraft.network.Connection$1")
class GameConnectionProxyPipelineMixin {

    @Shadow
    @Final
    private Connection val$connection;

    @Inject(method = "initChannel", at = @At("HEAD"))
    private void opsec$installProxyHandshake(Channel channel, CallbackInfo ci) {
        InetSocketAddress real = PendingProxyTargets.take(val$connection);
        if (real == null) return;

        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        String username = settings.getGameProxyUsername();
        String password = settings.getGameProxyPassword();
        ChannelHandler handshake = settings.getGameProxyType() == SpoofSettings.GameProxyType.HTTP
            ? new HttpConnectProxyHandler(real, username, password)
            : new Socks5ConnectHandler(real, username, password);
        channel.pipeline().addFirst("opsec_proxy_handshake", handshake);
    }
}

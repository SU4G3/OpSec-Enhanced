package aurick.opsec.mod.proxy;

import net.minecraft.network.Connection;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges the two halves of the game-connection proxy mixin (issue #11): the outer
 * {@code Connection.connect(...)} injection records the real server address here
 * (keyed by the in-flight {@code Connection} instance) before the dial target gets
 * swapped to the proxy, and the {@code Connection$1} pipeline-initializer injection
 * reads it back to know what to hand the SOCKS5/HTTP CONNECT handshake.
 *
 * <p>Plain utility class rather than a {@code @Unique} mixin field so both mixin
 * classes (targeting different classes) can reference it at compile time.</p>
 */
public final class PendingProxyTargets {

    private static final Map<Connection, InetSocketAddress> TARGETS = new ConcurrentHashMap<>();

    private PendingProxyTargets() {}

    public static void put(Connection connection, InetSocketAddress realAddress) {
        TARGETS.put(connection, realAddress);
    }

    public static InetSocketAddress take(Connection connection) {
        return TARGETS.remove(connection);
    }
}

package aurick.opsec.mod.net;

import net.minecraft.network.Connection;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Carries the address the player typed from the local name resolution to the connection
 * that was opened for it.
 *
 * <p>The typed address is captured where the client resolves the name locally (see
 * {@code ServerNameResolverMixin}) and is bound to the concrete {@link Connection} when
 * that connection is dialled (see {@code ConnectionTargetMixin}). The login handler then
 * reads the bound address back off its own connection, so the rule a connection is matched
 * against is the address the player typed — independent of what the connection ended up
 * resolving to, of any proxy in between and of any redirect the server performed.</p>
 *
 * <p>The capture is held per thread rather than in a shared queue, because the client
 * always resolves the name and opens the connection in the same thread: the multiplayer
 * screen dials inside its own {@code Server Connector} thread, and the status pinger
 * resolves and dials on the thread it was called from. A shared queue would therefore let
 * a status ping of one server lend its typed address to a concurrent join of another
 * server, and would let a stale capture outlive the connection it belonged to. With the
 * capture scoped to the thread that produced it, a connection can only ever be bound to
 * the address that was resolved for it.</p>
 *
 * <p>Plain utility class rather than a {@code @Unique} mixin field, because the capture
 * and the binding happen in two different mixins targeting two different classes (same
 * pattern as {@code PendingProxyTargets}).</p>
 */
public final class ServerTargets {

    /** The address the player typed plus the address it locally resolved to. */
    public record Target(String host, int port, String ip) {
        /**
         * True when this capture is the one that produced {@code dialedIp:dialedPort}.
         *
         * <p>A capture is only ever valid for the address it was resolved for. The check
         * makes the binding exact rather than "the last resolution seen on this thread",
         * so a capture left behind by an earlier resolution can never be handed to a
         * connection that is dialling a different address.</p>
         */
        public boolean describes(String dialedIp, int dialedPort) {
            if (dialedIp == null || dialedIp.isEmpty() || ip == null || ip.isEmpty()) {
                return false;
            }
            if (!ip.equals(dialedIp)) {
                return false;
            }
            return dialedPort <= 0 || port <= 0 || port == dialedPort;
        }
    }

    /** Marker for a connection that was dialled without a matching capture. */
    private static final Target NONE = new Target(null, 0, null);

    /** The resolution captured on this thread, awaiting the connection it belongs to. */
    private static final ThreadLocal<Target> PENDING = new ThreadLocal<>();

    /** Address per in-flight connection, keyed by connection identity. */
    private static final Map<Connection, Target> BOUND =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ServerTargets() {
    }

    /** Records a local name resolution. */
    public static void record(String host, int port, String ip) {
        if (host == null || host.isEmpty() || ip == null || ip.isEmpty()) {
            return;
        }
        PENDING.set(new Target(host, port, ip));
    }

    /**
     * Binds the capture that was made on this thread to {@code connection} and returns it.
     *
     * <p>The capture is consumed in the process, so it can never be claimed twice: a
     * connection can only take the address that was resolved for it on its own thread.
     * When this thread has no capture, or the capture is for a different address than the
     * one being dialled, the connection is bound to "no target" and the caller has to fall
     * back to the global settings.</p>
     *
     * <p>Idempotent: a connection that is already bound keeps the target it was given, so
     * the connection-setup consumers can ask for the target without caring whether the
     * binding hook already ran.</p>
     */
    public static Target bind(Connection connection, String dialedIp, int dialedPort) {
        if (connection == null) {
            return null;
        }
        Target already = BOUND.get(connection);
        if (already != null) {
            return already == NONE ? null : already;
        }
        Target claimed = PENDING.get();
        PENDING.remove();
        if (claimed == null || !claimed.describes(dialedIp, dialedPort)) {
            BOUND.put(connection, NONE);
            return null;
        }
        BOUND.put(connection, claimed);
        return claimed;
    }

    /** The address bound to {@code connection}, or null when nothing was captured. */
    public static Target boundTo(Connection connection) {
        if (connection == null) {
            return null;
        }
        Target target = BOUND.get(connection);
        return target == null || target == NONE ? null : target;
    }

    /** Drops the binding for a finished connection. */
    public static void unbind(Connection connection) {
        if (connection != null) {
            BOUND.remove(connection);
        }
    }
}

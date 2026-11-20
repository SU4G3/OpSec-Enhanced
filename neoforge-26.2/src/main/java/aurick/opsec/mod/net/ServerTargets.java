package aurick.opsec.mod.net;

import net.minecraft.network.Connection;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
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
 * <p>Plain utility class rather than a {@code @Unique} mixin field, because the capture
 * and the binding happen in two different mixins targeting two different classes (same
 * pattern as {@code PendingProxyTargets}).</p>
 */
public final class ServerTargets {

    /** The address the player typed plus the address it locally resolved to. */
    public record Target(String host, int port, String ip) {
    }

    /** Resolutions that have not been claimed by a connection yet. */
    private static final Deque<Target> PENDING = new ArrayDeque<>();
    /** Address per in-flight connection, keyed by connection identity. */
    private static final Map<Connection, Target> BOUND =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** How many unresolved captures to keep around; a burst of pings must not grow it. */
    private static final int MAX_PENDING = 32;

    private ServerTargets() {
    }

    /** Records a local name resolution. */
    public static void record(String host, int port, String ip) {
        if (host == null || host.isEmpty()) {
            return;
        }
        Target target = new Target(host, port, ip);
        synchronized (PENDING) {
            PENDING.addLast(target);
            while (PENDING.size() > MAX_PENDING) {
                PENDING.removeFirst();
            }
        }
    }

    /**
     * Binds the pending capture to {@code connection}.
     *
     * <p>The capture whose resolved IP matches the address actually dialled is preferred;
     * when several servers were resolved in parallel the right one is picked by IP rather
     * than by arrival order. If no capture matches (the resolution was done elsewhere, or
     * the dialled address differs from the resolved one) the most recent capture is used
     * as a best effort.</p>
     */
    public static void bind(Connection connection, String dialedIp) {
        if (connection == null) {
            return;
        }
        Target target = claim(dialedIp);
        if (target != null) {
            BOUND.put(connection, target);
        }
    }

    private static Target claim(String dialedIp) {
        synchronized (PENDING) {
            if (PENDING.isEmpty()) {
                return null;
            }
            Target match = null;
            if (dialedIp != null) {
                for (Target candidate : PENDING) {
                    if (dialedIp.equals(candidate.ip())) {
                        match = candidate;
                        break;
                    }
                }
            }
            if (match == null) {
                match = PENDING.peekLast();
            }
            PENDING.remove(match);
            return match;
        }
    }

    /** The address bound to {@code connection}, or null when nothing was captured. */
    public static Target boundTo(Connection connection) {
        return connection == null ? null : BOUND.get(connection);
    }

    /** Drops the binding for a finished connection. */
    public static void unbind(Connection connection) {
        if (connection != null) {
            BOUND.remove(connection);
        }
    }
}

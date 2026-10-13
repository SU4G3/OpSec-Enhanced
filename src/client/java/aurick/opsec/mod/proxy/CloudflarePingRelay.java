package aurick.opsec.mod.proxy;

//? if >=1.20.6 {
import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.SpoofSettings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.status.ServerStatus;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Client for the user-deployed Cloudflare Worker ping relay (issue #15) --
 * see {@code cloudflare-worker/ping-relay.js}. Fetches the parsed Minecraft
 * server-status JSON through the worker instead of connecting to the target
 * server directly, then decodes it with vanilla's own {@link ServerStatus#CODEC}
 * so formatting matches whatever this build's status JSON shape actually is.
 *
 * <p>Only wired up for >=1.20.6 -- {@code ServerData.validateIcon}/{@code State} and
 * {@code ServerStatusPinger.formatPlayerCount} aren't public (or don't exist) before
 * that, and {@code ServerStatusPingerMixin} doesn't call this below 1.20.6 either.</p>
 *
 * <p>Uses {@link HttpURLConnection} rather than {@code java.net.http.HttpClient} so the
 * request can be routed through the user's configured Game Connection Proxy (#11, see
 * {@link #buildProxy}) -- {@code java.net.http.HttpClient} only honors HTTP-type proxies,
 * not SOCKS, while {@code HttpURLConnection} supports both at the socket layer. Without
 * this, turning on the game proxy to hide the real IP from the target server would still
 * leak it to Cloudflare on every saved-server ping.</p>
 */
public final class CloudflarePingRelay {

    private CloudflarePingRelay() {}

    /** Resolves true on a successful ping (ServerData already populated), false on failure. */
    public static CompletableFuture<Boolean> ping(String workerUrl, ServerData data) {
        String url = workerUrl.endsWith("/") ? workerUrl + "ping" : workerUrl + "/ping";
        String host;
        int port;
        try {
            net.minecraft.client.multiplayer.resolver.ServerAddress address =
                net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(data.ip);
            host = address.getHost();
            port = address.getPort();
        } catch (Exception e) {
            return CompletableFuture.completedFuture(false);
        }

        String query = url + "?host=" + urlEncode(host) + "&port=" + port;

        return CompletableFuture.supplyAsync(() -> fetch(query))
            .thenApply(body -> body != null && applyResponse(body, data))
            .exceptionally(e -> {
                Opsec.LOGGER.debug("[OpSec] Cloudflare ping relay request failed: {}", e.getMessage());
                return false;
            });
    }

    private static String fetch(String query) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(query).openConnection(buildProxy());
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("GET");
            if (connection.getResponseCode() != 200) return null;
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            return body.toString();
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Cloudflare ping relay request failed: {}", e.getMessage());
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // Mirrors the proxy settings GameConnectionProxyMixin uses for the actual multiplayer
    // connection (issue #11), so this request doesn't bypass them -- see the class javadoc.
    private static Proxy buildProxy() {
        SpoofSettings settings = OpsecConfig.getInstance().getSettings();
        SpoofSettings.GameProxyType type = settings.getGameProxyType();
        String host = settings.getGameProxyHost();
        if (type == SpoofSettings.GameProxyType.NONE || host.isEmpty()) return Proxy.NO_PROXY;

        InetSocketAddress proxyAddress = new InetSocketAddress(host, settings.getGameProxyPort());
        String username = settings.getGameProxyUsername();
        String password = settings.getGameProxyPassword();
        // HttpURLConnection only consults java.net.Authenticator for proxy credentials (there's
        // no per-connection API for it) -- scoped to RequestorType.PROXY so it never answers an
        // unrelated server-side auth challenge. Always re-set to match current settings exactly
        // (including clearing it when no username is configured) so a previous proxy's leftover
        // credentials can never be offered to a different proxy after the user changes settings.
        Authenticator.setDefault(username.isEmpty() ? null : new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() != RequestorType.PROXY) return null;
                return new PasswordAuthentication(username, password.toCharArray());
            }
        });

        return new Proxy(
            type == SpoofSettings.GameProxyType.HTTP ? Proxy.Type.HTTP : Proxy.Type.SOCKS,
            proxyAddress);
    }

    private static boolean applyResponse(String body, ServerData data) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) return false;
            JsonObject obj = root.getAsJsonObject();
            if (!obj.has("ok") || !obj.get("ok").getAsBoolean()) return false;

            JsonElement statusJson = obj.get("status");
            if (statusJson == null) return false;

            var parsed = ServerStatus.CODEC.parse(JsonOps.INSTANCE, statusJson);
            ServerStatus status = parsed.result().orElse(null);
            if (status == null) return false;

            data.motd = status.description();
            long latency = obj.has("latencyMs") ? obj.get("latencyMs").getAsLong() : 0L;
            data.ping = latency;

            status.players().ifPresentOrElse(players -> {
                data.status = net.minecraft.client.multiplayer.ServerStatusPinger.formatPlayerCount(players.online(), players.max());
                data.playerList = List.of();
            }, () -> data.playerList = List.of());

            status.version().ifPresent(version -> {
                data.version = net.minecraft.network.chat.Component.literal(version.name());
                data.protocol = version.protocol();
            });

            status.favicon().ifPresent(favicon -> {
                byte[] validated = ServerData.validateIcon(favicon.iconBytes());
                if (validated != null) data.setIconBytes(validated);
            });

            data.setState(ServerData.State.SUCCESSFUL);
            return true;
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Cloudflare ping relay response parse failed: {}", e.getMessage());
            return false;
        }
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
//?} else {
/*
public final class CloudflarePingRelay {
    private CloudflarePingRelay() {}
}
*///?}

package aurick.opsec.mod.proxy;

//? if >=1.20.6 {
import aurick.opsec.mod.Opsec;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.status.ServerStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
 */
public final class CloudflarePingRelay {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();

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
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(query))
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> applyResponse(response.body(), data))
            .exceptionally(e -> {
                Opsec.LOGGER.debug("[OpSec] Cloudflare ping relay request failed: {}", e.getMessage());
                return false;
            });
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

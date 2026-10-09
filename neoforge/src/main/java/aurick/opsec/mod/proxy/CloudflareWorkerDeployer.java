package aurick.opsec.mod.proxy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Deploys the bundled Cloudflare ping-relay worker (issue #15) to the user's own
 * Cloudflare account via their REST API, using an API token the user pastes in --
 * no Node.js/wrangler CLI required. Uses the same account the token is scoped to,
 * so the result is still "nobody but you runs the copy you deploy."
 *
 * <p>Needs a token with the "Workers Scripts:Edit" permission (the dashboard's
 * built-in "Edit Cloudflare Workers" template token covers this) and the target
 * account's ID. Neither is persisted by the mod -- used in memory for this one
 * deploy action only.</p>
 */
public final class CloudflareWorkerDeployer {

    private static final String SCRIPT_NAME = "opsec-ping-relay";
    private static final String API_BASE = "https://api.cloudflare.com/client/v4";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private CloudflareWorkerDeployer() {}

    /** Resolves to the deployed worker's https://...workers.dev URL, or fails with a DeployException. */
    public static CompletableFuture<String> deploy(String apiToken, String accountId) {
        String token = apiToken.trim();
        String account = accountId.trim();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return deployBlocking(token, account);
            } catch (DeployException e) {
                throw e;
            } catch (IOException | InterruptedException e) {
                throw new DeployException("Network error talking to the Cloudflare API: " + e.getMessage());
            }
        });
    }

    private static String deployBlocking(String token, String accountId) throws IOException, InterruptedException {
        String subdomain = getWorkersSubdomain(token, accountId);
        if (subdomain == null || subdomain.isEmpty()) {
            throw new DeployException("No workers.dev subdomain is registered for this Cloudflare account yet. "
                + "Go to dash.cloudflare.com -> Workers & Pages and set one up (one-time, free), then try again.");
        }

        uploadScript(token, accountId);
        enableSubdomainRoute(token, accountId);

        return "https://" + SCRIPT_NAME + "." + subdomain + ".workers.dev";
    }

    private static String getWorkersSubdomain(String token, String accountId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_BASE + "/accounts/" + accountId + "/workers/subdomain"))
            .header("Authorization", "Bearer " + token)
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject body = checkSuccess(response, "Failed to read your account's workers.dev subdomain");
        JsonObject result = body.has("result") && body.get("result").isJsonObject() ? body.getAsJsonObject("result") : null;
        if (result == null || !result.has("subdomain") || result.get("subdomain").isJsonNull()) return null;
        return result.get("subdomain").getAsString();
    }

    private static void uploadScript(String token, String accountId) throws IOException, InterruptedException {
        String script = readBundledScript();
        String boundary = "----OpSecBoundary" + UUID.randomUUID();

        JsonObject metadata = new JsonObject();
        metadata.addProperty("main_module", "worker.js");
        metadata.addProperty("compatibility_date", LocalDate.now().toString());

        String multipart = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"metadata\"\r\n"
            + "Content-Type: application/json\r\n\r\n"
            + metadata + "\r\n"
            + "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"worker.js\"; filename=\"worker.js\"\r\n"
            + "Content-Type: application/javascript+module\r\n\r\n"
            + script + "\r\n"
            + "--" + boundary + "--\r\n";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_BASE + "/accounts/" + accountId + "/workers/scripts/" + SCRIPT_NAME))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .timeout(Duration.ofSeconds(30))
            .PUT(HttpRequest.BodyPublishers.ofString(multipart, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        checkSuccess(response, "Failed to upload the worker script");
    }

    private static void enableSubdomainRoute(String token, String accountId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_BASE + "/accounts/" + accountId + "/workers/scripts/" + SCRIPT_NAME + "/subdomain"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofString("{\"enabled\":true}"))
            .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        checkSuccess(response, "Failed to enable the workers.dev route for the script");
    }

    private static JsonObject checkSuccess(HttpResponse<String> response, String context) {
        JsonObject body;
        try {
            body = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (Exception e) {
            throw new DeployException(context + " (HTTP " + response.statusCode() + ", unreadable response)");
        }
        boolean success = body.has("success") && body.get("success").getAsBoolean();
        if (!success || response.statusCode() >= 300) {
            String errors = body.has("errors") ? body.get("errors").toString() : body.toString();
            throw new DeployException(context + ": " + errors);
        }
        return body;
    }

    private static String readBundledScript() throws IOException {
        try (InputStream in = CloudflareWorkerDeployer.class.getResourceAsStream("/opsec/cloudflare-ping-relay.js")) {
            if (in == null) throw new IOException("Bundled worker script resource is missing from this build");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static final class DeployException extends RuntimeException {
        public DeployException(String message) {
            super(message);
        }
    }
}

package aurick.opsec.mod.config;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.net.DpiEvasion;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Async jar integrity checker that compares the running mod jar's SHA-512 hash
 * against the digest Modrinth publishes for the matching version/game-version
 * file, so a modified or repackaged jar circulating elsewhere gets flagged.
 * All state fields are volatile for cross-thread visibility since the check runs
 * on a background thread and results are read on the render thread.
 */
public final class JarIntegrityChecker {

    private static final String MODRINTH_PROJECT_SLUG = "opsec-enhanced";
    private static final String VERSIONS_URL = "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_SLUG + "/version";
    // Modrinth asks API consumers to identify themselves in the User-Agent — see
    // https://docs.modrinth.com/api-reference/ (Authentication & User-Agents).
    private static final String USER_AGENT = "aurickk/OpSec-Enhanced/" + Opsec.getVersion() + " (github.com/aurickk/OpSec)";

    private static volatile boolean tamperDetected = false;
    private static volatile boolean checkComplete = false;
    private static volatile boolean shownThisSession = false;
    private static volatile String expectedDigest = null;
    private static volatile String actualDigest = null;

    private JarIntegrityChecker() {
        // Utility class
    }

    /**
     * Fires an async integrity check that computes the local jar's SHA-512 and
     * compares it against the SHA-512 Modrinth lists for the release matching
     * this mod version and Minecraft version. Non-blocking: runs on a daemon
     * thread via CompletableFuture.
     */
    public static void checkIntegrity() {
        CompletableFuture.runAsync(() -> {
            try {
                // Step 1: Get jar path from FabricLoader
                Path jarPath = FabricLoader.getInstance()
                        .getModContainer(Opsec.MOD_ID)
                        .flatMap(mod -> mod.getOrigin().getPaths().stream().findFirst())
                        .orElse(null);

                if (jarPath == null || !Files.isRegularFile(jarPath)) {
                    Opsec.LOGGER.debug("[OpSec] Not running from jar, skipping integrity check");
                    return;
                }

                if (!jarPath.toString().endsWith(".jar")) {
                    Opsec.LOGGER.debug("[OpSec] Not running from jar file, skipping integrity check");
                    return;
                }

                // Step 2: Compute local SHA-512 (matches the hash Modrinth publishes per file)
                byte[] jarBytes = Files.readAllBytes(jarPath);
                MessageDigest localDigest = MessageDigest.getInstance("SHA-512");
                actualDigest = bytesToHex(localDigest.digest(jarBytes));

                // Step 3: Get Minecraft version
                String mcVersion = FabricLoader.getInstance()
                        .getModContainer("minecraft")
                        .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                        .orElse(null);

                if (mcVersion == null) {
                    Opsec.LOGGER.debug("[OpSec] Could not determine Minecraft version, skipping integrity check");
                    return;
                }

                // Step 4: Fetch this project's version list from Modrinth
                String currentVersion = Opsec.getVersion();
                DpiEvasion.Result response = DpiEvasion.get(VERSIONS_URL, Map.of(
                        "User-Agent", USER_AGENT,
                        "Accept", "application/json"
                ), Duration.ofSeconds(10));

                if (response.status() == 404) {
                    // Project not published yet, or slug changed — dev/unreleased build.
                    Opsec.LOGGER.debug("[OpSec] Modrinth project '{}' not found, skipping integrity check", MODRINTH_PROJECT_SLUG);
                    return;
                }

                if (response.status() != 200) {
                    Opsec.LOGGER.debug("[OpSec] Modrinth API returned status {}, skipping integrity check", response.status());
                    return;
                }

                JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
                JsonObject matchingFile = findMatchingFile(versions, currentVersion, mcVersion);

                if (matchingFile == null) {
                    Opsec.LOGGER.debug("[OpSec] No Modrinth release found for version {} / MC {}, skipping integrity check", currentVersion, mcVersion);
                    return;
                }

                // Step 5: Extract SHA-512 hash from the matching file entry
                JsonObject hashes = matchingFile.has("hashes") ? matchingFile.getAsJsonObject("hashes") : null;
                if (hashes == null || !hashes.has("sha512")) {
                    Opsec.LOGGER.debug("[OpSec] No SHA-512 hash on matching Modrinth file, skipping integrity check");
                    return;
                }

                expectedDigest = hashes.get("sha512").getAsString();

                // Step 6: Compare digests (constant-time comparison)
                if (!MessageDigest.isEqual(expectedDigest.getBytes(), actualDigest.getBytes())) {
                    tamperDetected = true;
                    Opsec.LOGGER.warn("[OpSec] JAR INTEGRITY CHECK FAILED - Expected: {}, Actual: {}", expectedDigest, actualDigest);
                } else {
                    Opsec.LOGGER.debug("[OpSec] Jar integrity verified against Modrinth");
                }
            } catch (Exception e) {
                Opsec.LOGGER.debug("[OpSec] Integrity check failed: {}", e.getMessage());
            } finally {
                checkComplete = true;
            }
        });
    }

    /**
     * Returns true once the integrity check has finished (regardless of result).
     */
    public static boolean isCheckComplete() {
        return checkComplete;
    }

    /**
     * Returns true if tamper was detected, the check is complete, and the
     * warning screen has not been shown this session.
     */
    public static boolean isTamperDetected() {
        return checkComplete && tamperDetected && !shownThisSession
                && !OpsecConfig.getInstance().getSettings().isTamperWarningDismissed();
    }

    /**
     * Marks the tamper warning as shown for this session.
     * Prevents the screen from appearing again even if user navigates back.
     */
    public static void markShown() {
        shownThisSession = true;
    }

    /**
     * Resets the shown flag. Used by the config screen reset button.
     */
    public static void resetShown() {
        shownThisSession = false;
    }

    /**
     * Returns the expected SHA-512 digest from the matching Modrinth file, or null if not yet checked.
     */
    public static String getExpectedDigest() {
        return expectedDigest;
    }

    /**
     * Returns the actual SHA-512 digest of the running jar, or null if not yet checked.
     */
    public static String getActualDigest() {
        return actualDigest;
    }

    /**
     * Finds the {@code files} entry (primary, else first) of the Modrinth version
     * whose {@code version_number} matches the running mod version and whose
     * {@code game_versions} list includes the running Minecraft version. Modrinth
     * carries this mapping explicitly per-version, so no name-parsing/range-guessing
     * is needed the way GitHub's asset-filename convention required.
     */
    private static JsonObject findMatchingFile(JsonArray versions, String modVersion, String mcVersion) {
        for (JsonElement versionElement : versions) {
            JsonObject version = versionElement.getAsJsonObject();

            String versionNumber = version.has("version_number") ? version.get("version_number").getAsString() : null;
            if (!modVersion.equals(versionNumber)) continue;

            if (!version.has("game_versions")) continue;
            boolean matchesMc = false;
            for (JsonElement gv : version.getAsJsonArray("game_versions")) {
                if (mcVersion.equals(gv.getAsString())) {
                    matchesMc = true;
                    break;
                }
            }
            if (!matchesMc) continue;

            if (!version.has("files")) continue;
            JsonArray files = version.getAsJsonArray("files");
            JsonObject firstFile = null;
            for (JsonElement fileElement : files) {
                JsonObject file = fileElement.getAsJsonObject();
                if (firstFile == null) firstFile = file;
                if (file.has("primary") && file.get("primary").getAsBoolean()) return file;
            }
            if (firstFile != null) return firstFile;
        }
        return null;
    }

    /**
     * Converts a byte array to a lowercase hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

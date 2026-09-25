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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Async jar integrity checker that compares the running mod jar's hash against
 * the digest published for the matching version/game-version file, so a
 * modified or repackaged jar circulating elsewhere gets flagged. Tries three
 * mirrors in order — Modrinth (SHA-512), GitHub Releases (SHA-256), CurseForge
 * (filesize only, no published hash without a CurseForge API key) — and stops
 * at the first one that yields a real verdict. All state fields are volatile
 * for cross-thread visibility since the check runs on a background thread and
 * results are read on the render thread.
 */
public final class JarIntegrityChecker {

    private static final String MODRINTH_PROJECT_SLUG = "opsec-enhanced";
    private static final String MODRINTH_VERSIONS_URL = "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_SLUG + "/version";

    private static final String GITHUB_OWNER = "SU4G3";
    private static final String GITHUB_REPO = "OpSec-Enhanced";
    private static final String GITHUB_RELEASE_URL_TEMPLATE =
            "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/tags/V%s";
    // Groups exclude '+' so the "+v" delimiter position is unambiguous — a plain (.+)\+v(.+)
    // is polynomial-time on adversarial input (e.g. GitHub release asset names) since the
    // engine can backtrack across every '+' in the string looking for a split point.
    private static final Pattern GITHUB_ASSET_NAME_PATTERN = Pattern.compile("^opsec-([^+]+)\\+v([^+]+)\\.jar$");

    private static final String CURSEFORGE_WIDGET_URL = "https://api.cfwidget.com/minecraft/mc-mods/" + MODRINTH_PROJECT_SLUG;

    // Modrinth/GitHub ask API consumers to identify themselves in the User-Agent.
    private static final String USER_AGENT = "SU4G3/OpSec-Enhanced/" + Opsec.getVersion() + " (github.com/SU4G3/OpSec-Enhanced)";

    private static volatile boolean tamperDetected = false;
    private static volatile boolean checkComplete = false;
    private static volatile boolean shownThisSession = false;
    private static volatile String expectedDigest = null;
    private static volatile String actualDigest = null;
    private static volatile String verifiedSource = null;

    private JarIntegrityChecker() {
        // Utility class
    }

    /**
     * Fires an async integrity check that computes the local jar's hash and
     * compares it against the hash published on the first mirror (Modrinth,
     * then GitHub, then CurseForge) that has a matching release. Non-blocking:
     * runs on a daemon thread via CompletableFuture.
     */
    public static void checkIntegrity() {
        CompletableFuture.runAsync(() -> {
            try {
                Path jarPath = FabricLoader.getInstance()
                        .getModContainer(Opsec.MOD_ID)
                        .flatMap(mod -> mod.getOrigin().getPaths().stream().findFirst())
                        .orElse(null);

                if (jarPath == null || !Files.isRegularFile(jarPath) || !jarPath.toString().endsWith(".jar")) {
                    Opsec.LOGGER.debug("[OpSec] Not running from jar, skipping integrity check");
                    return;
                }

                byte[] jarBytes = Files.readAllBytes(jarPath);
                String localSha512 = bytesToHex(MessageDigest.getInstance("SHA-512").digest(jarBytes));
                String localSha256 = bytesToHex(MessageDigest.getInstance("SHA-256").digest(jarBytes));
                long localSize = jarBytes.length;
                actualDigest = localSha512;

                String mcVersion = FabricLoader.getInstance()
                        .getModContainer("minecraft")
                        .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                        .orElse(null);
                String modVersion = Opsec.getVersion();

                if (mcVersion == null) {
                    Opsec.LOGGER.debug("[OpSec] Could not determine Minecraft version, skipping integrity check");
                    return;
                }

                if (checkModrinth(modVersion, mcVersion, localSha512)) return;
                if (checkGitHub(modVersion, mcVersion, localSha256)) return;
                checkCurseForge(modVersion, mcVersion, localSize);
            } catch (Exception e) {
                Opsec.LOGGER.debug("[OpSec] Integrity check failed: {}", e.getMessage());
            } finally {
                checkComplete = true;
            }
        });
    }

    /**
     * Checks the jar's SHA-512 against Modrinth. Returns true if this mirror
     * produced a verdict (match or mismatch) — false to fall through to the
     * next mirror (project unpublished, version not found on it, etc).
     */
    private static boolean checkModrinth(String modVersion, String mcVersion, String localSha512) {
        try {
            DpiEvasion.Result response = DpiEvasion.get(MODRINTH_VERSIONS_URL, Map.of(
                    "User-Agent", USER_AGENT,
                    "Accept", "application/json"
            ), Duration.ofSeconds(10));

            if (response.status() != 200) {
                Opsec.LOGGER.debug("[OpSec] Modrinth API returned status {}, trying next mirror", response.status());
                return false;
            }

            JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
            JsonObject matchingFile = findMatchingModrinthFile(versions, modVersion, mcVersion);
            if (matchingFile == null) {
                Opsec.LOGGER.debug("[OpSec] No Modrinth release found for version {} / MC {}, trying next mirror", modVersion, mcVersion);
                return false;
            }

            JsonObject hashes = matchingFile.has("hashes") ? matchingFile.getAsJsonObject("hashes") : null;
            if (hashes == null || !hashes.has("sha512")) {
                Opsec.LOGGER.debug("[OpSec] No SHA-512 hash on matching Modrinth file, trying next mirror");
                return false;
            }

            recordVerdict("Modrinth", hashes.get("sha512").getAsString(), localSha512);
            return true;
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Modrinth integrity check failed: {}, trying next mirror", e.getMessage());
            return false;
        }
    }

    /**
     * Checks the jar's SHA-256 against the matching asset on the GitHub release
     * tagged "V<modVersion>". Same true/false convention as {@link #checkModrinth}.
     */
    private static boolean checkGitHub(String modVersion, String mcVersion, String localSha256) {
        try {
            String url = String.format(GITHUB_RELEASE_URL_TEMPLATE, modVersion);
            DpiEvasion.Result response = DpiEvasion.get(url, Map.of(
                    "User-Agent", USER_AGENT,
                    "Accept", "application/vnd.github+json"
            ), Duration.ofSeconds(10));

            if (response.status() != 200) {
                Opsec.LOGGER.debug("[OpSec] GitHub API returned status {}, trying next mirror", response.status());
                return false;
            }

            JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!release.has("assets")) return false;

            for (JsonElement assetElement : release.getAsJsonArray("assets")) {
                JsonObject asset = assetElement.getAsJsonObject();
                String name = asset.has("name") ? asset.get("name").getAsString() : "";
                Matcher matcher = GITHUB_ASSET_NAME_PATTERN.matcher(name);
                if (!matcher.matches()) continue;

                String versionSuffix = matcher.group(1);
                String assetModVersion = matcher.group(2);
                if (!modVersion.equals(assetModVersion)) continue;
                if (!versionSuffixMatches(mcVersion, versionSuffix)) continue;

                if (!asset.has("digest") || asset.get("digest").isJsonNull()) {
                    Opsec.LOGGER.debug("[OpSec] Matching GitHub asset has no digest field, trying next mirror");
                    return false;
                }

                String digestField = asset.get("digest").getAsString(); // "sha256:<hex>"
                if (!digestField.startsWith("sha256:")) {
                    Opsec.LOGGER.debug("[OpSec] Matching GitHub asset digest isn't sha256 ({}), trying next mirror", digestField);
                    return false;
                }
                String expectedSha256 = digestField.substring(7);
                recordVerdict("GitHub", expectedSha256, localSha256);
                return true;
            }

            Opsec.LOGGER.debug("[OpSec] No matching GitHub release asset for version {} / MC {}, trying next mirror", modVersion, mcVersion);
            return false;
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] GitHub integrity check failed: {}, trying next mirror", e.getMessage());
            return false;
        }
    }

    /**
     * Last-resort mirror: CurseForge's public file listing (via the cfwidget.com
     * proxy, no API key required) does not publish per-file hashes — CurseForge's
     * own hash-bearing API requires a key tied to a CurseForge developer account.
     * This can therefore only catch a gross size mismatch; a size match is NOT
     * treated as a verified pass (no tamperDetected=false claim beyond the default).
     */
    private static void checkCurseForge(String modVersion, String mcVersion, long localSize) {
        try {
            DpiEvasion.Result response = DpiEvasion.get(CURSEFORGE_WIDGET_URL, Map.of(
                    "User-Agent", USER_AGENT,
                    "Accept", "application/json"
            ), Duration.ofSeconds(10));

            if (response.status() != 200) {
                Opsec.LOGGER.debug("[OpSec] CurseForge widget API returned status {}, no mirror available", response.status());
                return;
            }

            JsonObject widget = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!widget.has("files")) return;

            String suffix = "+v" + modVersion + ".jar";
            for (JsonElement fileElement : widget.getAsJsonArray("files")) {
                JsonObject file = fileElement.getAsJsonObject();
                String name = file.has("name") ? file.get("name").getAsString() : "";
                if (!name.endsWith(suffix)) continue;

                boolean matchesMc = false;
                if (file.has("versions")) {
                    for (JsonElement v : file.getAsJsonArray("versions")) {
                        if (mcVersion.equals(v.getAsString())) { matchesMc = true; break; }
                    }
                }
                if (!matchesMc) continue;
                if (!file.has("filesize")) continue;

                long remoteSize = file.get("filesize").getAsLong();
                if (remoteSize != localSize) {
                    tamperDetected = true;
                    verifiedSource = "CurseForge (filesize only)";
                    expectedDigest = "size:" + remoteSize;
                    actualDigest = "size:" + localSize;
                    Opsec.LOGGER.warn("[OpSec] JAR SIZE MISMATCH vs CurseForge - Expected: {} bytes, Actual: {} bytes", remoteSize, localSize);
                } else {
                    Opsec.LOGGER.debug("[OpSec] Jar size matches CurseForge listing (not a cryptographic guarantee — no hash published without a CurseForge API key)");
                }
                return;
            }

            Opsec.LOGGER.debug("[OpSec] No matching CurseForge file for version {} / MC {}, no mirror available", modVersion, mcVersion);
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] CurseForge integrity check failed: {}", e.getMessage());
        }
    }

    private static void recordVerdict(String source, String expected, String actual) {
        expectedDigest = expected;
        actualDigest = actual;
        verifiedSource = source;
        if (!MessageDigest.isEqual(expected.getBytes(), actual.getBytes())) {
            tamperDetected = true;
            Opsec.LOGGER.warn("[OpSec] JAR INTEGRITY CHECK FAILED against {} - Expected: {}, Actual: {}", source, expected, actual);
        } else {
            Opsec.LOGGER.debug("[OpSec] Jar integrity verified against {}", source);
        }
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
     * Returns the expected digest from whichever mirror produced a verdict, or null if not yet checked.
     */
    public static String getExpectedDigest() {
        return expectedDigest;
    }

    /**
     * Returns the actual digest of the running jar (same algorithm as {@link #getExpectedDigest()}), or null if not yet checked.
     */
    public static String getActualDigest() {
        return actualDigest;
    }

    /**
     * Returns which mirror (Modrinth/GitHub/CurseForge) produced the verdict, or null if none did.
     */
    public static String getVerifiedSource() {
        return verifiedSource;
    }

    /**
     * Finds the {@code files} entry (primary, else first) of the Modrinth version
     * whose {@code version_number} matches the running mod version and whose
     * {@code game_versions} list includes the running Minecraft version.
     */
    private static JsonObject findMatchingModrinthFile(JsonArray versions, String modVersion, String mcVersion) {
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
     * GitHub asset names encode either a single Minecraft version ("26.3") or a
     * dash-separated inclusive range ("1.20.3-1.20.4") in their version_suffix
     * component — mirrors the version_suffix values in each versions/*&#47;gradle.properties.
     * Minecraft version strings never contain '-', so splitting on the first
     * '-' unambiguously separates the range bounds.
     */
    private static boolean versionSuffixMatches(String mcVersion, String versionSuffix) {
        int dashIndex = versionSuffix.indexOf('-');
        if (dashIndex < 0) {
            return versionSuffix.equals(mcVersion);
        }
        String lo = versionSuffix.substring(0, dashIndex);
        String hi = versionSuffix.substring(dashIndex + 1);
        return compareVersions(mcVersion, lo) >= 0 && compareVersions(mcVersion, hi) <= 0;
    }

    /**
     * Numeric dotted-version comparator (e.g. "1.21.11" vs "1.21.9" — not lexical,
     * so "11" correctly sorts after "9").
     */
    private static int compareVersions(String a, String b) {
        String[] partsA = a.split("\\.");
        String[] partsB = b.split("\\.");
        int len = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < len; i++) {
            int va = i < partsA.length ? parseIntSafe(partsA[i]) : 0;
            int vb = i < partsB.length ? parseIntSafe(partsB[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
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

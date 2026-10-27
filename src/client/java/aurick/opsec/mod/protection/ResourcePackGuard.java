package aurick.opsec.mod.protection;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.config.OpsecConfig;
//? if >=1.20.5 {
import aurick.opsec.mod.mixin.client.DownloadedPackSourceAccessor;
import aurick.opsec.mod.mixin.client.MinecraftAccessor;
//?}
import net.minecraft.client.Minecraft;
//? if >=1.20.3 {
import net.minecraft.client.resources.server.DownloadedPackSource;
//?} else {
/*import net.minecraft.client.resources.DownloadedPackSource;
*///?}

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages resource pack cache and provides utilities for cache clearing.
 */
public class ResourcePackGuard {

    /**
     * Clear all resource pack caches for all accounts.
     * Clears both 'downloads' and 'server-resource-packs' folders,
     * and resets the in-memory download state.
     *
     * <p>Per-server-trusted buckets (issue #23) are skipped in 'downloads' -- see
     * {@link #hashServerBucket} for why this needs the trusted *addresses* hashed
     * fresh here rather than reading whatever server is currently connected (there
     * usually isn't one by the time this runs; see OpsecConfig#packCacheTrustedServers'
     * javadoc). 'server-resource-packs' predates per-server bucketing entirely (see
     * DownloadQueueMixin's Stonecutter gate) so there's nothing to exempt there.</p>
     */
    public static void clearAllCaches() {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
        int totalDeleted = 0;

        Set<String> trustedBuckets = OpsecConfig.getInstance().getPackCacheTrustedServers()
            .stream().map(ResourcePackGuard::hashServerBucket).collect(Collectors.toUnmodifiableSet());

        // Clear downloads folder (new cache location), preserving trusted servers' buckets
        totalDeleted += clearCacheFolder(gameDir, "downloads", trustedBuckets);

        // Clear server-resource-packs folder (legacy/alternative cache location) -- no
        // per-server bucketing to preserve here, see this method's javadoc.
        totalDeleted += clearCacheFolder(gameDir, "server-resource-packs", Set.of());

        // Try to reset the in-memory download state
        boolean memoryCleared = clearDownloadQueueState();

        Opsec.LOGGER.info("[OpSec] Clear pack cache: {} files removed, memoryCleared={}, {} server(s) exempted", totalDeleted, memoryCleared, trustedBuckets.size());
    }

    /**
     * Hashes a server address into the same 16-hex-char bucket name
     * {@code DownloadQueueMixin} isolates that server's pack cache under
     * ({@code downloads/<accountId>/<bucket>/...}) -- single source of truth so the
     * two can never drift apart (DownloadQueueMixin delegates here instead of hashing
     * its own copy).
     */
    public static String hashServerBucket(String server) {
        if (server == null || server.isBlank()) return "unknown";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(server.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8); // 16 hex chars — plenty to avoid collisions, short for a path segment
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Failed to hash server address for cache bucketing: {}", e.getMessage());
            return "unknown";
        }
    }
    
    /**
     * Attempts to clear the in-memory download queue state.
     * This simulates what happens when disconnecting from a server.
     * @return true if successfully cleared, false otherwise
     */
    private static boolean clearDownloadQueueState() {
        //? if >=1.20.5 {
        try {
            Minecraft mc = Minecraft.getInstance();
            DownloadedPackSource packSource = ((MinecraftAccessor) mc).opsec$getDownloadedPackSource();

            if (packSource != null) {
                ((DownloadedPackSourceAccessor) packSource).opsec$invokeCleanupAfterDisconnect();
                Opsec.LOGGER.debug("[OpSec] Cleared download queue state via cleanupAfterDisconnect");
                return true;
            }
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Could not clear download queue state: {}", e.getMessage());
        }
        return false;
        //?} else if >=1.20.3 {
        /*// 1.20.3-1.20.4: cleanupAfterDisconnect is public — call it directly.
        try {
            DownloadedPackSource packSource = Minecraft.getInstance().getDownloadedPackSource();
            if (packSource != null) {
                packSource.cleanupAfterDisconnect();
                Opsec.LOGGER.debug("[OpSec] Cleared download queue state via cleanupAfterDisconnect");
                return true;
            }
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Could not clear download queue state: {}", e.getMessage());
        }
        return false;
        *///?} else {
        /*// 1.20.1 / 1.20.2: legacy DownloadedPackSource exposes public clearServerPack().
        try {
            DownloadedPackSource packSource = Minecraft.getInstance().getDownloadedPackSource();
            if (packSource != null) {
                packSource.clearServerPack();
                Opsec.LOGGER.debug("[OpSec] Cleared download queue state via clearServerPack");
                return true;
            }
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Could not clear download queue state: {}", e.getMessage());
        }
        return false;
        *///?}
    }
    
    /**
     * Helper to clear a specific cache folder, preserving any subtree whose path
     * segment at depth 2 (i.e. {@code <folderName>/<accountId>/<bucket>/...}, matching
     * {@code DownloadQueueMixin}'s layout) is in {@code trustedBuckets}.
     * @return number of entries deleted
     */
    private static int clearCacheFolder(Path gameDir, String folderName, Set<String> trustedBuckets) {
        Path cachePath = gameDir.resolve(folderName).normalize();

        if (!cachePath.startsWith(gameDir)) {
            Opsec.LOGGER.error("[OpSec] Security: {} path outside game directory", folderName);
            return 0;
        }

        if (!Files.exists(cachePath)) {
            return 0;
        }

        int deleted = 0;
        try {
            for (Path p : Files.walk(cachePath).sorted(Comparator.reverseOrder()).toList()) {
                if (p.equals(cachePath)) continue;
                if (!trustedBuckets.isEmpty() && opsec$isUnderTrustedBucket(cachePath, p, trustedBuckets)) continue;
                try {
                    if (Files.deleteIfExists(p)) {
                        deleted++;
                    }
                } catch (java.nio.file.DirectoryNotEmptyException e) {
                    // Expected when a trusted bucket's parent (the account-id folder)
                    // still holds that preserved subtree — not a real failure.
                    if (trustedBuckets.isEmpty()) {
                        Opsec.LOGGER.warn("[OpSec] Failed to delete cache entry {}: directory not empty", p.getFileName());
                    }
                } catch (IOException e) {
                    Opsec.LOGGER.warn("[OpSec] Failed to delete cache entry {}: {}", p.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            Opsec.LOGGER.error("[OpSec] Failed to walk cache folder {}: {}", folderName, e.getMessage());
        }

        return deleted;
    }

    /** True if {@code path} is, or is nested under, {@code <cachePath>/<accountId>/<trustedBucket>}. */
    private static boolean opsec$isUnderTrustedBucket(Path cachePath, Path path, Set<String> trustedBuckets) {
        Path relative = cachePath.relativize(path);
        if (relative.getNameCount() < 2) return false;
        return trustedBuckets.contains(relative.getName(1).toString());
    }
}

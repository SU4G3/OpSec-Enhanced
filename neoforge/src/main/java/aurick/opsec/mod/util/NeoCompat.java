package aurick.opsec.mod.util;

import net.neoforged.fml.ModList;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Centralizes the small set of loader-glue calls this codebase used to make via
 * {@code FabricLoader.getInstance()} — config/game dirs, "is mod X loaded", and a
 * mod's own jar path — behind NeoForge's {@link ModList}/{@code FMLPaths} equivalents,
 * so the rest of the port doesn't need to touch Fabric-specific APIs file by file.
 */
public final class NeoCompat {

    private NeoCompat() {
    }

    public static Path getConfigDir() {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
    }

    public static Path getGameDir() {
        return net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
    }

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /** The running mod's own version string, or "unknown" if not found. */
    public static String getModVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(mod -> mod.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    /** The on-disk jar path for a loaded mod, if it has one (dev environments may not). */
    public static Optional<Path> getModJarPath(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(mod -> mod.getModInfo().getOwningFile().getFile().getFilePath());
    }
}

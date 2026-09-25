package aurick.opsec.mod.util;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

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
        try {
            return ModList.get().isLoaded(modId);
        } catch (NullPointerException | IllegalStateException e) {
            // ModList isn't populated yet — this can be called very early
            // (e.g. via a mixin hit during Main.main's CrashReport.preload()
            // self-test, before FML has finished mod discovery).
            return false;
        }
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

    /**
     * The Fabric-port equivalent of {@code FabricLoader.getInstance().getModContainer(modId)},
     * returning the metadata-level {@link IModInfo} rather than the lower-level
     * {@code ModContainer} (which mostly exposes lifecycle/event-bus internals ModRegistry
     * doesn't need).
     */
    public static Optional<IModInfo> getModInfo(String modId) {
        return ModList.get().getModContainerById(modId).map(mod -> mod.getModInfo());
    }
}

package aurick.opsec.mod.mixin.client;

/**
 * Dropped for the NeoForge port (informational only, feeds {@code /opsec info}, which is
 * itself stubbed out — see {@code OpsecCommand}). Targets Fabric API's internal builtin-pack
 * registration impl ({@code ResourceManagerHelperImpl}/{@code ResourceLoaderImpl}), which has
 * no NeoForge equivalent — NeoForge mods register built-in datapacks/resourcepacks through a
 * different mechanism entirely.
 */
public class ResourceLoaderBuiltinPackMixin {
}

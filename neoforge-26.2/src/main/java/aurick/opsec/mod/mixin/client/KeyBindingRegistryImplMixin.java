package aurick.opsec.mod.mixin.client;

/**
 * Dropped for the NeoForge port (whitelist-bookkeeping only, not a protection feature):
 * tracks which mod registered a given keybind, for Mod Whitelist's dropped AUTO mode and
 * the {@code /opsec info} debug command's per-mod keybind listing. Targeted Fabric's
 * internal keybind registry ({@code KeyBindingRegistryImpl}), which has no NeoForge
 * equivalent — NeoForge mods register key mappings via {@code RegisterKeyMappingsEvent}
 * with no comparable "who registered this" introspection point.
 */
public class KeyBindingRegistryImplMixin {
}

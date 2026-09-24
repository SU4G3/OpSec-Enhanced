package aurick.opsec.mod.mixin.client;

/**
 * TODO (NeoForge port, real protection gap — see {@code AbstractChanneledNetworkAddonMixin}'s
 * javadoc, this is the other half of the same channel-fingerprint defense). Targets Fabric's
 * internal {@code PayloadTypeRegistryImpl}, which has no NeoForge equivalent — not investigated
 * yet what NeoForge's own inbound-payload-codec-lookup class is, if there's an equivalent
 * single choke point at all.
 */
public class PayloadTypeRegistryImplMixin {
}

package aurick.opsec.mod.mixin.client;

/**
 * TODO (NeoForge port, real protection gap — not a whitelist-bookkeeping drop):
 * on Fabric this is half of the "channel-fingerprint defense" (with
 * {@code PayloadTypeRegistryImplMixin}) — it makes the client silently ignore a
 * non-whitelisted inbound custom-payload channel the way vanilla would, instead of
 * whatever Fabric's own dispatch does when a mod channel is decoded but the
 * client is spoofing as vanilla (a ClassCastException, in Fabric's case, which
 * would itself out the client as modded).
 *
 * <p>Ported to nothing yet: this mixin targets Fabric's own internal networking
 * implementation ({@code AbstractChanneledNetworkAddon}), which has no NeoForge
 * equivalent — NeoForge's payload registration/dispatch internals are a
 * different set of classes entirely and haven't been investigated yet. Until
 * this is done, a server probing a non-whitelisted channel on this NeoForge
 * build may behave differently than the Fabric build under the same probe —
 * the actual behavior difference (if any) hasn't been verified.</p>
 */
public class AbstractChanneledNetworkAddonMixin {
}

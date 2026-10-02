package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.tracking.ModRegistry;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.payload.ModdedNetworkQueryPayload;
import net.neoforged.neoforge.network.registration.PayloadRegistration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.HashMap;
import java.util.Map;

/**
 * Channel Spoofing covers ordinary {@code minecraft:register}-style channel registration
 * (see {@link ModRegistry#isWhitelistedChannel}), but NeoForge also runs its own, earlier
 * handshake: on connecting to a NeoForge server, the client responds to a
 * {@code ModdedNetworkQueryPayload} with one of its own, built by
 * {@code ModdedNetworkQueryPayload.fromRegistry(...)} from literally every registered mod
 * channel on the classpath — this ships regardless of Client Spoofer or Mod Whitelist,
 * because it's built straight from NeoForge's own {@code PAYLOAD_REGISTRATIONS}, a
 * different code path than the one those features already filter.
 *
 * <p>Confirmed by decompiling the real NeoForge jar: {@code ClientConfigurationPacketListenerImpl
 * .handleCustomPayload} calls {@code NetworkRegistry.onNetworkQuery} on receiving the server's
 * query, which sends back {@code ModdedNetworkQueryPayload.fromRegistry(PAYLOAD_REGISTRATIONS)}
 * — the full map, unfiltered. Only reachable between two NeoForge peers (a non-NeoForge server
 * never sends the initial query), but on a NeoForge server it bypasses both protections entirely.</p>
 *
 * <p>This filters the registration map the same way {@code isWhitelistedChannel} already
 * filters ordinary channel registration, so a mod excluded from one is excluded from both.</p>
 */
@Mixin(ModdedNetworkQueryPayload.class)
public class ModdedNetworkQueryPayloadMixin {

    @ModifyVariable(method = "fromRegistry", at = @At("HEAD"), argsOnly = true)
    private static Map<ConnectionProtocol, Map<ResourceLocation, PayloadRegistration<?>>> opsec$filterRegistrations(
            Map<ConnectionProtocol, Map<ResourceLocation, PayloadRegistration<?>>> registrations) {
        Map<ConnectionProtocol, Map<ResourceLocation, PayloadRegistration<?>>> filtered = new HashMap<>();
        for (Map.Entry<ConnectionProtocol, Map<ResourceLocation, PayloadRegistration<?>>> protocolEntry : registrations.entrySet()) {
            Map<ResourceLocation, PayloadRegistration<?>> channels = new HashMap<>();
            for (Map.Entry<ResourceLocation, PayloadRegistration<?>> channelEntry : protocolEntry.getValue().entrySet()) {
                if (ModRegistry.isWhitelistedChannel(channelEntry.getKey())) {
                    channels.put(channelEntry.getKey(), channelEntry.getValue());
                }
            }
            filtered.put(protocolEntry.getKey(), channels);
        }
        return filtered;
    }
}

package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.tracking.ModRegistry;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.payload.ModdedNetworkQueryPayload;
import net.neoforged.neoforge.network.registration.PayloadRegistration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.HashMap;
import java.util.Map;

/**
 * Channel Spoofing covers ordinary minecraft:register-style channel registration
 * (see {@link ModRegistry#isWhitelistedChannel}), but NeoForge also runs its own, earlier
 * handshake: on connecting to a NeoForge server, the client responds to a
 * ModdedNetworkQueryPayload with one of its own, built by
 * ModdedNetworkQueryPayload.fromRegistry(...) from literally every registered mod
 * channel on the classpath -- this ships regardless of Client Spoofer or Mod Whitelist,
 * because it is built straight from NeoForge is own PAYLOAD_REGISTRATIONS, a
 * different code path than the one those features already filter.
 *
 * Confirmed by decompiling the real NeoForge jar: ClientConfigurationPacketListenerImpl
 * .handleCustomPayload calls NetworkRegistry.onNetworkQuery on receiving the server is
 * query, which sends back ModdedNetworkQueryPayload.fromRegistry(PAYLOAD_REGISTRATIONS)
 * -- the full map, unfiltered. Only reachable between two NeoForge peers (a non-NeoForge server
 * never sends the initial query), but on a NeoForge server it bypasses both protections entirely.
 *
 * This filters the registration map the same way isWhitelistedChannel already
 * filters ordinary channel registration, so a mod excluded from one is excluded from both.
 */
@Mixin(ModdedNetworkQueryPayload.class)
public class ModdedNetworkQueryPayloadMixin {

    @ModifyVariable(method = "fromRegistry", at = @At("HEAD"), argsOnly = true)
    private static Map<ConnectionProtocol, Map<Identifier, PayloadRegistration<?>>> opsec$filterRegistrations(
            Map<ConnectionProtocol, Map<Identifier, PayloadRegistration<?>>> registrations) {
        Map<ConnectionProtocol, Map<Identifier, PayloadRegistration<?>>> filtered = new HashMap<>();
        for (Map.Entry<ConnectionProtocol, Map<Identifier, PayloadRegistration<?>>> protocolEntry : registrations.entrySet()) {
            Map<Identifier, PayloadRegistration<?>> channels = new HashMap<>();
            for (Map.Entry<Identifier, PayloadRegistration<?>> channelEntry : protocolEntry.getValue().entrySet()) {
                if (ModRegistry.isWhitelistedChannel(channelEntry.getKey())) {
                    channels.put(channelEntry.getKey(), channelEntry.getValue());
                }
            }
            filtered.put(protocolEntry.getKey(), channels);
        }
        return filtered;
    }
}

package aurick.opsec.mod.protection;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Own implementation of the {@code minecraft:register}/{@code minecraft:unregister} channel-list
 * payload — Fabric's own {@code net.fabricmc.fabric.impl.networking.RegistrationPayload} isn't
 * available on NeoForge, but the wire format itself is loader-independent (a de-facto standard
 * predating both loaders' typed-payload systems): a run of channel identifiers written as ASCII
 * text, separated by single {@code 0x00} bytes, filling the whole payload. Verified against
 * Fabric's real source (github.com/FabricMC/fabric,
 * fabric-networking-api-v1/.../impl/networking/RegistrationPayload.java) rather than guessed —
 * this needs to be byte-exact, since a malformed re-encode would itself be a detectable tell.
 *
 * <p>Used purely as a data holder for re-encoding a filtered channel list before resending —
 * not registered with any payload registrar, since nothing needs to <em>dispatch</em> to it
 * ({@code minecraft:register} is server-bound only, and OpSec is the one constructing it, not
 * receiving it as an unknown inbound type).</p>
 */
public record RegistrationPayload(CustomPacketPayload.Type<RegistrationPayload> type, List<Identifier> channels)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RegistrationPayload> REGISTER =
            new CustomPacketPayload.Type<>(Identifier.withDefaultNamespace("register"));
    public static final CustomPacketPayload.Type<RegistrationPayload> UNREGISTER =
            new CustomPacketPayload.Type<>(Identifier.withDefaultNamespace("unregister"));

    public static final StreamCodec<FriendlyByteBuf, RegistrationPayload> REGISTER_CODEC = codec(REGISTER);
    public static final StreamCodec<FriendlyByteBuf, RegistrationPayload> UNREGISTER_CODEC = codec(UNREGISTER);

    private void write(FriendlyByteBuf buf) {
        boolean first = true;
        for (Identifier channel : channels) {
            if (first) {
                first = false;
            } else {
                buf.writeByte(0);
            }
            buf.writeBytes(channel.toString().getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static List<Identifier> read(FriendlyByteBuf buf) {
        List<Identifier> ids = new ArrayList<>();
        StringBuilder active = new StringBuilder();

        while (buf.isReadable()) {
            byte b = buf.readByte();
            if (b != 0) {
                active.append((char) (b & 0xFF));
            } else {
                addId(ids, active);
                active = new StringBuilder();
            }
        }
        addId(ids, active);

        return Collections.unmodifiableList(ids);
    }

    private static void addId(List<Identifier> ids, StringBuilder sb) {
        Identifier parsed = Identifier.tryParse(sb.toString());
        if (parsed != null) ids.add(parsed);
    }

    private static StreamCodec<FriendlyByteBuf, RegistrationPayload> codec(CustomPacketPayload.Type<RegistrationPayload> type) {
        return CustomPacketPayload.codec(RegistrationPayload::write, buf -> new RegistrationPayload(type, read(buf)));
    }
}

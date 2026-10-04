package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.multiplayer.ChunkBatchSizeCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code getDesiredChunksPerTick()} reports a live wall-clock timing of how fast this
 * specific machine processes chunks (exponentially averaged nanoseconds/chunk), sent to
 * the server on every chunk batch via {@code ServerboundChunkBatchReceivedPacket}. Unlike
 * a one-shot setting, this is a continuous per-session (and roughly stable cross-session,
 * per physical machine) hardware performance oracle — the same class of signal as a
 * browser timing fingerprint, usable to correlate alt accounts run from the same box.
 *
 * <p>Rounding to the nearest whole chunk-per-tick preserves the server's actual use of the
 * value (coarse adaptive pacing of how many chunks to send at once) while destroying the
 * sub-integer precision that makes it a stable per-machine fingerprint.
 */
@Mixin(ChunkBatchSizeCalculator.class)
public class ChunkBatchSizeCalculatorMixin {

    @ModifyReturnValue(method = "getDesiredChunksPerTick", at = @At("RETURN"))
    private float opsec$roundDesiredChunksPerTick(float original) {
        return Math.round(original);
    }
}

package aurick.opsec.mod.mixin.client;

//? if >=1.20.2 {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.multiplayer.ChunkBatchSizeCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.ThreadLocalRandom;

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
 * sub-integer precision that makes it a stable per-machine fingerprint. A fixed +-1 jitter,
 * rolled once per {@code ChunkBatchSizeCalculator} instance (i.e. once per world/server
 * join), is added on top so repeated joins from the same machine don't keep landing on the
 * exact same integer — otherwise a server logging this value across sessions could still
 * correlate alts by the band of 1-2 integers each machine consistently rounds to.
 */
@Mixin(ChunkBatchSizeCalculator.class)
public class ChunkBatchSizeCalculatorMixin {

    @Unique
    private int opsec$jitter = Integer.MIN_VALUE;

    @ModifyReturnValue(method = "getDesiredChunksPerTick", at = @At("RETURN"))
    private float opsec$roundDesiredChunksPerTick(float original) {
        if (this.opsec$jitter == Integer.MIN_VALUE) {
            this.opsec$jitter = ThreadLocalRandom.current().nextInt(-1, 2);
        }
        return Math.max(1, Math.round(original) + this.opsec$jitter);
    }
}
//?} else {
/*public class ChunkBatchSizeCalculatorMixin {}
*///?}

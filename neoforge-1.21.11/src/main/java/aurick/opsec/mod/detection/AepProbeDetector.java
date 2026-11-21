package aurick.opsec.mod.detection;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.config.OpsecConstants;
import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Detects AntiExploitPreventer's TranslationChecker probe.
 *
 * <p>AEP plants a ghost sign on the player's own feet block, opens the sign editor at
 * that position and then reads the sign text the client sends back. A vanilla client
 * never has a sign on the block it stands in, so "a sign appeared on the player's feet
 * block" is a server-fabricated sign with no legitimate cause — unlike the looser
 * "any sign block update" heuristic, which false-positives on signs the player placed
 * next to themselves.</p>
 *
 * <p>The sign editor is deliberately <em>not</em> cancelled. Cancelling makes the client
 * behave in a way no vanilla client can: a vanilla client always sends the sign update
 * when the editor closes, so staying silent is itself a fingerprint — and it breaks the
 * probe in a way the server can see (AEP's checker waits for the reply with no timeout,
 * so a silent client pins one server-side async thread per probed key).</p>
 *
 * <p>The probe's actual payload — the resolved translation text — is already neutralised
 * upstream: the sign editor resolves the translatable component through
 * {@link aurick.opsec.mod.mixin.client.TranslatableContentsMixin}, which returns the raw
 * key for blocked mod keys, exactly like a vanilla client without the mod. All this
 * detector contributes is the user-visible "you are being probed" alert.</p>
 */
public final class AepProbeDetector {

    private static final String CATEGORY = "aep_ghost_sign";

    /** AEP sends the editor open in the same tick as the block update, so the mark only has to survive a network round trip. */
    private static final long GHOST_SIGN_TTL_MS = 2_000L;

    private static BlockPos ghostSignPos;
    private static long ghostSignAt;

    private AepProbeDetector() {
    }

    /**
     * Records a sign that appeared on the player's feet block. The previous state is read
     * from the world because the block update packet only carries the new state.
     */
    public static void onBlockUpdate(BlockPos pos, BlockState newState) {
        if (!(newState.getBlock() instanceof SignBlock)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;

        BlockState oldState = mc.level.getBlockState(pos);
        if (oldState.getBlock() instanceof SignBlock) return;
        if (!mc.player.blockPosition().equals(pos)) return;

        ghostSignPos = pos;
        ghostSignAt = System.currentTimeMillis();
    }

    /**
     * Records that the sign editor was opened for the fabricated sign.
     *
     * <p>Blocks nothing and returns nothing on purpose — see the class javadoc for why
     * cancelling the editor would be the more detectable behaviour.</p>
     */
    public static void onOpenSignEditor(BlockPos pos) {
        if (ghostSignPos == null || !ghostSignPos.equals(pos)) return;
        if (System.currentTimeMillis() - ghostSignAt > GHOST_SIGN_TTL_MS) {
            ghostSignPos = null;
            return;
        }
        ghostSignPos = null;

        Opsec.LOGGER.warn("[OpSec] AntiExploitPreventer ghost-sign probe at {}", pos);
        PrivacyLogger.alertThrottled(PrivacyLogger.AlertType.DANGER, CATEGORY,
                OpsecLang.tr(OpsecStrings.ALERT_AEP_GHOST_SIGN));
        PrivacyLogger.toastWithCooldown(PrivacyLogger.AlertType.DANGER,
                OpsecLang.tr(OpsecStrings.TOAST_AEP_PROBING),
                CATEGORY, OpsecConstants.Timeouts.EXPLOIT_TOAST_COOLDOWN_MS);
    }

    public static void reset() {
        ghostSignPos = null;
    }
}

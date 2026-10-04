package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.streamer.StreamerModeNameMask;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Streamer Mode &gt; Mask Player Names. Covers the above-head nametag, for every player
 * entity actually loaded/rendered in the world — the local player included, since the
 * point of Streamer Mode is hiding who's playing, not just who else is on the server.
 *
 * <p>Hooks {@code getDisplayName()} directly, not just {@code getName()} — vanilla's
 * {@code getDisplayName()} wraps the name with scoreboard team prefixes/suffixes/color
 * (server-set rank tags), which would otherwise leak right back through even with the bare
 * name masked. Short-circuiting at the very head skips that wrapping entirely, and also
 * sidesteps the fact that vanilla caches its own computed {@code displayname} field after
 * the first call (masking only {@code getName()} would miss that cache on the first paint
 * of a session). {@code getName()} is masked too, for anything that reads it directly
 * instead. Functional lookups (scoreboard, commands) go through
 * {@code getGameProfile()}/{@code getScoreboardName()}, not either of these, so masking
 * here is display-only.</p>
 */
@Mixin(Player.class)
public class PlayerNameMaskMixin {

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void opsec$maskName(CallbackInfoReturnable<Component> cir) {
        if (!StreamerModeNameMask.isActive()) return;
        Player self = (Player) (Object) this;
        cir.setReturnValue(StreamerModeNameMask.maskComponent(self.getUUID()));
    }

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void opsec$maskDisplayName(CallbackInfoReturnable<Component> cir) {
        if (!StreamerModeNameMask.isActive()) return;
        Player self = (Player) (Object) this;
        cir.setReturnValue(StreamerModeNameMask.maskComponent(self.getUUID()));
    }
}

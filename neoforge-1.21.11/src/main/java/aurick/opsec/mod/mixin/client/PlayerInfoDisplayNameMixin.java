package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.streamer.StreamerModeNameMask;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Streamer Mode &gt; Mask Player Names, tab-list half — the real hook, lower-level than
 * {@link PlayerTabOverlayMixin}. Vanilla's own {@code PlayerTabOverlay.getNameForDisplay}
 * reads {@code PlayerInfo.getTabListDisplayName()} first and only falls back to the raw
 * profile name when it's null; third-party tab-list replacement mods (BetterTab, Tabby and
 * similar, which reimplement the tab list's own rendering independently of
 * {@code PlayerTabOverlay} the same way BetterF3 reimplements the F3 overlay) follow the
 * exact same {@code getTabListDisplayName() != null ? ... : profile name} pattern, since
 * it's the one server-overridable field every compliant tab-list consumer is expected to
 * check. Forcing this getter to always return the masked name when active — instead of
 * only patching vanilla's own render method — means both vanilla tab list AND any
 * well-behaved reimplementation pick up the mask, closing the gap
 * {@link PlayerTabOverlayMixin} alone left open for mods like BetterTab.
 */
@Mixin(PlayerInfo.class)
public class PlayerInfoDisplayNameMixin {

    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void opsec$maskTabListDisplayName(CallbackInfoReturnable<Component> cir) {
        if (!StreamerModeNameMask.isActive()) return;
        PlayerInfo self = (PlayerInfo) (Object) this;
        cir.setReturnValue(StreamerModeNameMask.maskComponent(self.getProfile().id()));
    }
}

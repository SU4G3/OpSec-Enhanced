package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.streamer.StreamerModeNameMask;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Streamer Mode &gt; Mask Player Names, tab-list half. {@code PlayerInfo}/{@code GameProfile}
 * is a separate data path from the live {@code Player} entity {@link PlayerNameMaskMixin}
 * covers (tab-list entries exist even for players outside render distance), so it needs
 * its own hook.
 */
@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

    @Inject(method = "getNameForDisplay", at = @At("HEAD"), cancellable = true)
    private void opsec$maskTabListName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        if (!StreamerModeNameMask.isActive()) return;
        cir.setReturnValue(StreamerModeNameMask.maskComponent(info.getProfile().id()));
    }
}

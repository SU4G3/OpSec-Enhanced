package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.streamer.StreamerModeNameMask;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Streamer Mode &gt; Mask Player Names, tab-list half. {@code PlayerInfo}/{@code GameProfile}
 * is a separate data path from the live {@code Player} entity {@link PlayerNameMaskMixin}
 * covers (tab-list entries exist even for players outside render distance), so it needs
 * its own hook. {@code getNameForDisplay(PlayerInfo)} has had this exact name/signature
 * since at least 1.20.1, confirmed against decompiled source at both ends of the supported
 * range — no version gate needed on the injection itself, only on the
 * {@code GameProfile} UUID accessor rename below ({@code getId()} &rarr; {@code id()},
 * confirmed via authlib jar inspection to land between 1.21.6 (authlib 6.0.58, old-style)
 * and 1.21.9 (authlib 7.0.61, record-style) — NOT the 1.21.11 boundary used elsewhere in
 * this codebase for the unrelated ResourceLocation/Identifier rename).
 */
@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

    @Inject(method = "getNameForDisplay", at = @At("HEAD"), cancellable = true)
    private void opsec$maskTabListName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        if (!StreamerModeNameMask.isActive()) return;
        //? if <1.21.9 {
        UUID id = info.getProfile().getId();
        //?} else {
        /*UUID id = info.getProfile().id();
        *///?}
        cir.setReturnValue(StreamerModeNameMask.maskComponent(id));
    }
}

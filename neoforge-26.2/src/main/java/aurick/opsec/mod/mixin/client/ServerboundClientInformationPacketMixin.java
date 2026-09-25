package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.config.OpsecConfig;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
//? if >=1.20.2 {
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.server.level.ClientInformation;
//?} else {
/*import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
*///?}
//? if >=1.21.4 {
import net.minecraft.server.level.ParticleStatus;
//?}
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the outgoing {@code ClientInformation} (language, view distance, chat mode,
 * skin-layer bitmask, main hand, ...) with a fixed, common baseline when Client Info
 * Normalizer is on, instead of whatever the player actually has configured — this
 * combination is a fingerprint (see the packet-leak writeup this feature came from).
 * The chosen values are vanilla's actual fresh-install defaults (not the degenerate
 * {@code ClientInformation.createDefault()} fallback, which reports a suspiciously low
 * view distance of 2 and all skin layers off — reporting that would stand out MORE than
 * reporting nothing, the same "spoofing must be realistic" rule the client-brand spoof
 * follows).
 */
@Mixin(ServerboundClientInformationPacket.class)
public abstract class ServerboundClientInformationPacketMixin {

    //? if >=1.21.4 {
    @Final
    @Mutable
    @Shadow
    private ClientInformation information;

    @Inject(method = "<init>(Lnet/minecraft/server/level/ClientInformation;)V", at = @At("TAIL"))
    private void opsec$normalize(ClientInformation information, CallbackInfo ci) {
        if (OpsecConfig.getInstance().getSettings().isNormalizeClientInfo()) {
            this.information = new ClientInformation("en_us", 10, ChatVisiblity.FULL, true,
                    0x7F, HumanoidArm.RIGHT, false, true, ParticleStatus.ALL);
        }
    }
    //?} elif >=1.20.2 {
    /*@Final
    @Mutable
    @Shadow
    private ClientInformation information;

    @Inject(method = "<init>(Lnet/minecraft/server/level/ClientInformation;)V", at = @At("TAIL"))
    private void opsec$normalize(ClientInformation information, CallbackInfo ci) {
        if (OpsecConfig.getInstance().getSettings().isNormalizeClientInfo()) {
            this.information = new ClientInformation("en_us", 10, ChatVisiblity.FULL, true,
                    0x7F, HumanoidArm.RIGHT, false, true);
        }
    }
    *///?} else {
    /*// 1.20.1: the packet carries the fields directly, no ClientInformation wrapper yet.
    @Final
    @Mutable
    @Shadow
    private String language;
    @Final
    @Mutable
    @Shadow
    private int viewDistance;
    @Final
    @Mutable
    @Shadow
    private ChatVisiblity chatVisibility;
    @Final
    @Mutable
    @Shadow
    private boolean chatColors;
    @Final
    @Mutable
    @Shadow
    private int modelCustomisation;
    @Final
    @Mutable
    @Shadow
    private HumanoidArm mainHand;
    @Final
    @Mutable
    @Shadow
    private boolean textFilteringEnabled;
    @Final
    @Mutable
    @Shadow
    private boolean allowsListing;

    @Inject(method = "<init>(Ljava/lang/String;ILnet/minecraft/world/entity/player/ChatVisiblity;ZILnet/minecraft/world/entity/HumanoidArm;ZZ)V", at = @At("TAIL"))
    private void opsec$normalize(String language, int viewDistance, ChatVisiblity chatVisibility, boolean chatColors,
            int modelCustomisation, HumanoidArm mainHand, boolean textFilteringEnabled, boolean allowsListing, CallbackInfo ci) {
        if (OpsecConfig.getInstance().getSettings().isNormalizeClientInfo()) {
            this.language = "en_us";
            this.viewDistance = 10;
            this.chatVisibility = ChatVisiblity.FULL;
            this.chatColors = true;
            this.modelCustomisation = 0x7F;
            this.mainHand = HumanoidArm.RIGHT;
            this.textFilteringEnabled = false;
            this.allowsListing = true;
        }
    }
    *///?}
}

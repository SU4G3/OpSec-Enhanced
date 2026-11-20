package aurick.opsec.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.detection.AepProbeDetector;
import aurick.opsec.mod.detection.PacketContext;
import aurick.opsec.mod.net.ServerTargets;
import aurick.opsec.mod.protection.PackStripHandler;
import aurick.opsec.mod.protection.PackStripOverlay;
import aurick.opsec.mod.protection.TranslationProtectionHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.SignedMessageChain;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
//? if <1.20.2 {
/*import aurick.opsec.mod.detection.TrackPackDetector;
import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import net.minecraft.network.protocol.game.ClientboundResourcePackPacket;
*///?}
//? if <1.20.5 {
/*import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tracks server connection events for OpSec.
 *
 * <p>Chat-signing scope here is limited to a single mechanism: the
 * {@code opsec$skipSigningEncoder} {@code @WrapOperation} on the
 * {@code SignedMessageChain.Encoder.pack(...)} call inside {@code sendChat},
 * which prevents signing-chain advancement whenever the user has signing OFF.
 * No server-state-triggered upgrades, no system-chat reactions, no auto-resends
 * — those were removed because they uniformly fingerprinted OpSec users.</p>
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Unique
    private static volatile ScheduledExecutorService opsec$scheduler;

    @Unique
    private static volatile ScheduledFuture<?> opsec$pendingTask;

    @Unique
    private static synchronized ScheduledExecutorService opsec$getScheduler() {
        if (opsec$scheduler == null || opsec$scheduler.isShutdown()) {
            opsec$scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "OpSec-Scheduler");
            t.setDaemon(true);
            return t;
        });
        }
        return opsec$scheduler;
    }

    @Unique
    private static synchronized void opsec$shutdownScheduler() {
        if (opsec$pendingTask != null) {
            opsec$pendingTask.cancel(false);
            opsec$pendingTask = null;
        }
        if (opsec$scheduler != null && !opsec$scheduler.isShutdown()) {
            opsec$scheduler.shutdownNow();
            opsec$scheduler = null;
        }
    }

    /**
     * Wrap sub-packet handle() calls inside handleBundlePacket so each sub-packet
     * gets its own packet name context. Without this, all sub-packets inherit the
     * bundle's name since they bypass PacketProcessor$ListenerAndPacket.
     */
    @WrapOperation(
        method = "handleBundlePacket",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V")
    )
    private <T extends PacketListener> void opsec$wrapBundleSubPacketHandle(
            Packet<T> instance, T listener, Operation<Void> original) {
        TranslationProtectionHandler.clearDedup();
        PacketContext.setPacketName(instance);
        original.call(instance, listener);
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void onLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        ClientPacketListener listener = (ClientPacketListener)(Object)this;
        Connection connection = listener.getConnection();

        String ip = null;
        int port = 0;
        SocketAddress remote = connection.getRemoteAddress();
        if (remote instanceof InetSocketAddress inet) {
            if (inet.getAddress() != null) {
                ip = inet.getAddress().getHostAddress();
            }
            port = inet.getPort();
        }

        ServerTargets.Target target = ServerTargets.boundTo(connection);
        String host = target != null && target.host() != null && !target.host().isEmpty() ? target.host() : ip;

        OpsecConfig.getInstance().setCurrentServer(host, port, ip);

        // Schedule port scan summary after 2 seconds
        opsec$pendingTask = opsec$getScheduler().schedule(() -> {
            Minecraft.getInstance().execute(PrivacyLogger::showPortScanSummary);
        }, 2, TimeUnit.SECONDS);
    }

    /**
     * AntiExploitPreventer's TranslationChecker plants a sign on the player's feet block
     * and then opens the sign editor at that position to harvest the text the client
     * sends back. The editor is left alone on purpose — cancelling it would make this
     * client behave unlike any vanilla client and pin the server's checker thread; the
     * text the probe is after is already reduced to the raw key by the translation
     * protection.
     */
    @Inject(method = "handleBlockUpdate", at = @At("HEAD"))
    private void opsec$onBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        AepProbeDetector.onBlockUpdate(packet.getPos(), packet.getBlockState());
    }

    @Inject(method = "handleOpenSignEditor", at = @At("HEAD"))
    private void opsec$onOpenSignEditor(ClientboundOpenSignEditorPacket packet, CallbackInfo ci) {
        //? if >=26.3 {
        /*BlockPos pos = packet.pos();*/
        //?} else {
        BlockPos pos = packet.getPos();
        //?}
        AepProbeDetector.onOpenSignEditor(pos);
    }

    //? if <1.20.2 {
    /*// <1.20.2: single-pack server packs come through ClientboundResourcePackPacket on
    // the game listener; on 1.20.2+ this is ClientCommonPacketListenerImplMixin instead.
    // Drives TrackPack detection and pack-strip state (keyed by LEGACY_SERVER_PACK_UUID).
    @Inject(method = "handleResourcePack", at = @At("HEAD"))
    private void opsec$onLegacyResourcePack(ClientboundResourcePackPacket packet, CallbackInfo ci) {
        String url = packet.getUrl();
        String hash = packet.getHash();

        TrackPackDetector.recordRequest(url, hash);

        if (TrackPackDetector.isFingerprinting() && TrackPackDetector.consumeNotifyPatternOnce()) {
            PrivacyLogger.alert(PrivacyLogger.AlertType.DANGER,
                OpsecLang.tr(OpsecStrings.ALERT_TRACKPACK_PATTERN));
            PrivacyLogger.toast(PrivacyLogger.AlertType.DANGER,
                OpsecLang.tr(OpsecStrings.TOAST_TRACKPACK));
        }

        PackStripHandler.onPackPush(PackStripHandler.LEGACY_SERVER_PACK_UUID, url, packet.isRequired());
    }
    *///?}

    /**
     * Wrap the signing encoder call in sendChat() to prevent chain advancement when not signing.
     * Returns null instead of calling the encoder, keeping the chain clean.
     */
    @WrapOperation(
            method = "sendChat",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/SignedMessageChain$Encoder;pack(Lnet/minecraft/network/chat/SignedMessageBody;)Lnet/minecraft/network/chat/MessageSignature;")
    )
    private MessageSignature opsec$skipSigningEncoder(SignedMessageChain.Encoder encoder, SignedMessageBody body, Operation<MessageSignature> original) {
        if (OpsecConfig.getInstance().shouldNotSign()) {
            Opsec.LOGGER.debug("[OpSec] Skipping message signing (chain preserved)");
            return null;
        }
        return original.call(encoder, body);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        opsec$shutdownScheduler();
        PrivacyLogger.resetPortScanTracking();
        PrivacyLogger.resetBurstTracking();
        PrivacyLogger.clearCooldowns();
        AepProbeDetector.reset();
        OpsecConfig.getInstance().setCurrentServer(null, 0, null);
        ServerTargets.unbind(((ClientPacketListener)(Object)this).getConnection());
        PackStripHandler.clearAll();
        PackStripOverlay.clearQueue();
    }
}

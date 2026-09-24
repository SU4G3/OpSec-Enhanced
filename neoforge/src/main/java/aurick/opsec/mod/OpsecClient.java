package aurick.opsec.mod;

import aurick.opsec.mod.accounts.AccountManager;
import aurick.opsec.mod.command.OpsecCommand;
import aurick.opsec.mod.config.JarIntegrityChecker;
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.hud.OpsecHud;
import aurick.opsec.mod.protection.PackStripOverlay;
import aurick.opsec.mod.protection.ResourcePackGuard;
import aurick.opsec.mod.protection.ShaderStripTracker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NeoForge entry point for OpSec — the NeoForge counterpart of the Fabric
 * {@code OpsecClient implements ClientModInitializer}.
 *
 * <p><b>Known gap vs. the Fabric build:</b> Mod Whitelist's AUTO mode (scanning every
 * other mod's registered network channels to auto-exempt anything with real server
 * communication) has no NeoForge equivalent — there is no global registry of
 * registered payload channels the way Fabric's {@code ClientPlayNetworking.getGlobalReceivers()}
 * is. NeoForge mods register payloads per-mod on their own event bus with no
 * central introspection point. Whitelist on this build is therefore BLOCK ALL /
 * CUSTOM (manual) only — see {@code SpoofSettings.WhitelistMode}, AUTO is not offered.
 *
 * <p>This version also targets MC {@code 1.21.1} only (single version, no Stonecutter
 * multiplexing yet) — this file's Fabric counterpart has stonecutter branches for 12
 * MC versions this doesn't replicate.
 */
@Mod(Opsec.MOD_ID)
public class OpsecClient {

    private static final AtomicBoolean startedOnce = new AtomicBoolean(false);

    public OpsecClient(IEventBus modEventBus) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != Dist.CLIENT) {
            return; // This mod is client-only; do nothing on a dedicated server.
        }

        Opsec.LOGGER.info("{} v{} - Privacy protection for Minecraft", Opsec.MOD_NAME, Opsec.getVersion());
        Opsec.LOGGER.info("Protecting against: TrackPack, Key Resolution Exploit, Client Fingerprinting");

        OpsecConfig.getInstance();
        OpsecCommand.register();
        AccountManager.getInstance(); // Load saved accounts
        OpsecHud.register();

        if (OpsecConfig.getInstance().getSettings().isIntegrityCheckEnabled()) {
            JarIntegrityChecker.checkIntegrity();
        }

        // NeoForge 21.1.176 has no ClientStartedEvent/ClientStoppingEvent (added in a
        // later NeoForge release) — a one-shot flag on the first client tick is the
        // portable stand-in for "client fully started", and a JVM shutdown hook (fires
        // regardless of how the window closes) stands in for "client stopping".
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        Runtime.getRuntime().addShutdownHook(new Thread(this::onShutdown, "OpSec-Shutdown"));

        Opsec.LOGGER.info("OpSec client protection initialized");
    }

    private void onClientTick(ClientTickEvent.Post event) {
        if (startedOnce.compareAndSet(false, true)) {
            // Fabric's build also scans every other mod's registered network channels
            // here (ModRegistry.inferJijNamespaceAliases / scanRegisteredChannels) to
            // drive Mod Whitelist's AUTO mode. Not ported — see the class javadoc.
            Opsec.LOGGER.debug("[OpSec] First client tick reached");
        }

        PackStripOverlay.tryShowNext(net.minecraft.client.Minecraft.getInstance());
        ShaderStripTracker.flushPending();
    }

    private void onShutdown() {
        try {
            if (OpsecConfig.getInstance().getSettings().isAutoPurgePackCache()) {
                ResourcePackGuard.clearAllCaches();
            }
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Shutdown cleanup failed: {}", e.getMessage());
        }
    }
}

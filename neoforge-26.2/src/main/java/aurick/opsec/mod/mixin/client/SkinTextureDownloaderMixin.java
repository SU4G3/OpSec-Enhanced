package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.protection.TrustedSkinHost;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Closes #12/#13/#14 — vanilla fetches another player's (or a skull block/item's, or a
 * Mannequin NPC's) skin/cape texture URL with zero host or signature validation, letting a
 * malicious server point it at an attacker-controlled host and deanonymize that player's IP
 * with no interaction. Rejects the fetch before it opens a connection when the texture isn't
 * already cached locally AND the URL's host isn't Mojang's own texture CDN.
 */
@Mixin(SkinTextureDownloader.class)
public class SkinTextureDownloaderMixin {

    @Inject(method = "downloadSkin", at = @At("HEAD"), cancellable = true)
    private void opsec$blockUntrustedSkinHost(Path path, String url, CallbackInfoReturnable<NativeImage> cir) throws IOException {
        if (Files.isRegularFile(path)) return;
        if (!TrustedSkinHost.isTrusted(url)) {
            throw new IOException("[OpSec] Blocked skin/cape texture fetch to untrusted host");
        }
    }
}

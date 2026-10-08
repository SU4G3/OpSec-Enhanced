package aurick.opsec.mod.mixin.client;

//? if >=1.21.4 {
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
 * Closes #12/#13/#14 (see {@link aurick.opsec.mod.protection.TrustedSkinHost}). Rejects the
 * fetch before it opens a connection when the texture isn't already cached locally AND the
 * URL's host isn't Mojang's own texture CDN — a local-cache hit never touches the network,
 * so it's always allowed regardless of host.
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
//?} else {
/*public class SkinTextureDownloaderMixin {}
*///?}

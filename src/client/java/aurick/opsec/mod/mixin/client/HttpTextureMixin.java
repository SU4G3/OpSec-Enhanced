package aurick.opsec.mod.mixin.client;

//? if >=1.21.4 {
public class HttpTextureMixin {}
//?} else {
/*
import aurick.opsec.mod.protection.TrustedSkinHost;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

// Pre-1.21.4 equivalent of SkinTextureDownloaderMixin -- same fix (#12/#13/#14), older
// vanilla shape (HttpTexture.load(ResourceManager) fetches inline rather than through a
// separate downloader class). A local cache hit never touches the network, so it's always
// allowed regardless of host.
@Mixin(HttpTexture.class)
public class HttpTextureMixin {

    @Shadow
    private File file;

    @Shadow
    private String urlString;

    @Inject(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;)V", at = @At("HEAD"), cancellable = true)
    private void opsec$blockUntrustedSkinHost(ResourceManager resourceManager, CallbackInfo ci) {
        boolean hasLocalCache = this.file != null && this.file.isFile();
        if (hasLocalCache) return;
        if (!TrustedSkinHost.isTrusted(this.urlString)) {
            ci.cancel();
        }
    }
}
*///?}

package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.protection.TrustedSkinHost;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

/**
 * Closes #12/#13/#14 — vanilla fetches another player's (or a skull block/item's) skin/cape
 * texture URL with zero host or signature validation, letting a malicious server point it
 * at an attacker-controlled host and deanonymize that player's IP with no interaction.
 * Rejects the fetch before it opens a connection when the texture isn't already cached
 * locally AND the URL's host isn't Mojang's own texture CDN.
 */
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

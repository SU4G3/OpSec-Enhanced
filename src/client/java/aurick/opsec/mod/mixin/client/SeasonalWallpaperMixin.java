package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.OpsecConfigScreen;
import aurick.opsec.mod.util.SeasonalTheme;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
//? if >=1.21.6 {
import net.minecraft.client.renderer.RenderPipelines;
//?} elif >=1.21.4 {
/*import net.minecraft.client.renderer.RenderType;
*///?}
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} else {
import net.minecraft.resources.ResourceLocation;
//?}
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a purely cosmetic seasonal background gradient plus a few scattered icon sprites
 * (pumpkin / christmas tree, 32x32, generated flat pixel-art) behind the OpSec settings
 * screen. No effect on any protection feature.
 *
 * <p>Both the render injection point (26.1+'s extraction pipeline) and the texture-blit
 * call shape changed repeatedly across this mod's supported range — each branch below was
 * confirmed against the real client jar / decompiled vanilla source for that range rather
 * than assumed from an adjacent version, since this API churned more than once:</p>
 * <ul>
 *   <li>&lt;1.21.4 — {@code GuiGraphics.blit(ResourceLocation, x, y, u, v, width, height)}</li>
 *   <li>1.21.4 — {@code GuiGraphics.blit(Function<ResourceLocation,RenderType>, ResourceLocation, x, y, u, v, width, height, texW, texH)}</li>
 *   <li>1.21.6-1.21.11 — {@code GuiGraphics.blit(RenderPipeline, (ResourceLocation|Identifier), x, y, u, v, width, height, texW, texH)}</li>
 *   <li>26.1+ — same pipeline-based call, but on {@code GuiGraphicsExtractor.extractRenderState(...)} instead of {@code GuiGraphics.render(...)}</li>
 * </ul>
 */
@Mixin(Screen.class)
public class SeasonalWallpaperMixin {

    //? if >=1.21.11 {
    /*private static final Identifier PUMPKIN_TEXTURE = Identifier.fromNamespaceAndPath("opsec", "textures/gui/wallpaper_pumpkin.png");
    private static final Identifier TREE_TEXTURE = Identifier.fromNamespaceAndPath("opsec", "textures/gui/wallpaper_tree.png");
    *///?} else {
    private static final ResourceLocation PUMPKIN_TEXTURE = ResourceLocation.tryParse("opsec:textures/gui/wallpaper_pumpkin.png");
    private static final ResourceLocation TREE_TEXTURE = ResourceLocation.tryParse("opsec:textures/gui/wallpaper_tree.png");
    //?}

    private static final int ICON_SIZE = 32;

    //? if >=26.1 {
    /*@Inject(method = "extractRenderState", at = @At("HEAD"))
    private void opsec$drawSeasonalWallpaper(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!((Object) this instanceof OpsecConfigScreen)) return;
        SeasonalTheme.Season season = OpsecConfig.getInstance().getSettings().getActiveWallpaperSeason();
        if (season == SeasonalTheme.Season.NONE) return;
        Screen self = (Screen) (Object) this;
        int[] gradientColors = opsec$gradientFor(season);
        graphics.fillGradient(0, 0, self.width, self.height, gradientColors[0], gradientColors[1]);
        Identifier icon = season == SeasonalTheme.Season.HALLOWEEN ? PUMPKIN_TEXTURE : TREE_TEXTURE;
        for (int[] pos : opsec$iconPositions(self.width, self.height)) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, icon, pos[0], pos[1], 0.0f, 0.0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        }
    }
    *///?} else {
    @Inject(method = "render", at = @At("HEAD"))
    private void opsec$drawSeasonalWallpaper(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!((Object) this instanceof OpsecConfigScreen)) return;
        SeasonalTheme.Season season = OpsecConfig.getInstance().getSettings().getActiveWallpaperSeason();
        if (season == SeasonalTheme.Season.NONE) return;
        Screen self = (Screen) (Object) this;
        int[] gradientColors = opsec$gradientFor(season);
        graphics.fillGradient(0, 0, self.width, self.height, gradientColors[0], gradientColors[1]);
        var icon = season == SeasonalTheme.Season.HALLOWEEN ? PUMPKIN_TEXTURE : TREE_TEXTURE;
        for (int[] pos : opsec$iconPositions(self.width, self.height)) {
            //? if >=1.21.6 {
            graphics.blit(RenderPipelines.GUI_TEXTURED, icon, pos[0], pos[1], 0.0f, 0.0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            //?} elif >=1.21.4 {
            /*graphics.blit(RenderType::guiTextured, icon, pos[0], pos[1], 0.0f, 0.0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            *///?} else {
            /*graphics.blit(icon, pos[0], pos[1], 0, 0, ICON_SIZE, ICON_SIZE);
            *///?}
        }
    }
    //?}

    private static int[] opsec$gradientFor(SeasonalTheme.Season season) {
        return switch (season) {
            case HALLOWEEN -> new int[]{0xB2241500, 0xE6000000};
            case CHRISTMAS -> new int[]{0xB20B2E13, 0xE6300000};
            case NONE -> new int[]{0, 0};
        };
    }

    /** Three corners, clear of the title (top-left) and leaving the center free for widgets. */
    private static int[][] opsec$iconPositions(int width, int height) {
        int margin = 8;
        return new int[][]{
                {width - ICON_SIZE - margin, margin},
                {margin, height - ICON_SIZE - margin},
                {width - ICON_SIZE - margin, height - ICON_SIZE - margin}
        };
    }
}

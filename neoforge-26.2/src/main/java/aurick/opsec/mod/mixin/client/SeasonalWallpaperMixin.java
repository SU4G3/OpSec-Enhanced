package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.OpsecConfigScreen;
import aurick.opsec.mod.util.SeasonalTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a purely cosmetic seasonal background gradient plus a few scattered icon sprites
 * (pumpkin / christmas tree, 32x32) behind the OpSec settings screen. MC 26.2 replaced
 * Screen.render(GuiGraphics,...) with an extraction-based
 * extractRenderState(GuiGraphicsExtractor,...) pipeline; blit itself still takes the same
 * pipeline-based shape as 1.21.11 — both confirmed against this version's own decompiled
 * vanilla source (e.g. BookViewScreen, AdvancementsScreen).
 */
@Mixin(Screen.class)
public class SeasonalWallpaperMixin {

    private static final Identifier PUMPKIN_TEXTURE = Identifier.fromNamespaceAndPath("opsec", "textures/gui/wallpaper_pumpkin.png");
    private static final Identifier TREE_TEXTURE = Identifier.fromNamespaceAndPath("opsec", "textures/gui/wallpaper_tree.png");
    private static final int ICON_SIZE = 32;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
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

    private static int[] opsec$gradientFor(SeasonalTheme.Season season) {
        return switch (season) {
            case HALLOWEEN -> new int[]{0xB2241500, 0xE6000000};
            case CHRISTMAS -> new int[]{0xB20B2E13, 0xE6300000};
            case NONE -> new int[]{0, 0};
        };
    }

    private static int[][] opsec$iconPositions(int width, int height) {
        int margin = 8;
        return new int[][]{
                {width - ICON_SIZE - margin, margin},
                {margin, height - ICON_SIZE - margin},
                {width - ICON_SIZE - margin, height - ICON_SIZE - margin}
        };
    }
}

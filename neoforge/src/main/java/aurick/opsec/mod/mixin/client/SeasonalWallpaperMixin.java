package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.config.OpsecConfigScreen;
import aurick.opsec.mod.util.SeasonalTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a purely cosmetic seasonal background gradient plus a few scattered icon sprites
 * (pumpkin / christmas tree, 32x32) behind the OpSec settings screen. MC 1.21.1: plain
 * GuiGraphics.blit(ResourceLocation, x, y, u, v, width, height), confirmed against the
 * real client jar.
 */
@Mixin(Screen.class)
public class SeasonalWallpaperMixin {

    private static final ResourceLocation PUMPKIN_TEXTURE = ResourceLocation.tryParse("opsec:textures/gui/wallpaper_pumpkin.png");
    private static final ResourceLocation TREE_TEXTURE = ResourceLocation.tryParse("opsec:textures/gui/wallpaper_tree.png");
    private static final int ICON_SIZE = 32;

    @Inject(method = "render", at = @At("HEAD"))
    private void opsec$drawSeasonalWallpaper(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!((Object) this instanceof OpsecConfigScreen)) return;
        SeasonalTheme.Season season = OpsecConfig.getInstance().getSettings().getActiveWallpaperSeason();
        if (season == SeasonalTheme.Season.NONE) return;
        Screen self = (Screen) (Object) this;
        int[] gradientColors = opsec$gradientFor(season);
        graphics.fillGradient(0, 0, self.width, self.height, gradientColors[0], gradientColors[1]);
        ResourceLocation icon = season == SeasonalTheme.Season.HALLOWEEN ? PUMPKIN_TEXTURE : TREE_TEXTURE;
        for (int[] pos : opsec$iconPositions(self.width, self.height)) {
            graphics.blit(icon, pos[0], pos[1], 0, 0, ICON_SIZE, ICON_SIZE);
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

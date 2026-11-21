package aurick.opsec.mod.config;

import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import aurick.opsec.mod.mixin.client.ScreenAccessor;
import aurick.opsec.mod.protection.ResourcePackGuard;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The global settings screen.
 *
 * <p>Everything here is a client-local preference: how OpSec looks, how it behaves
 * while streaming, and how much it logs. None of it changes what is sent to a server,
 * so none of it belongs to a rule — a rule describes one server's profile, and a
 * wallpaper has no server to describe.</p>
 *
 * <p>The values live in the ANY rule's {@link SpoofSettings}, which is the global
 * baseline ({@link OpsecConfig#getGlobalSettings()}). Editing them here therefore
 * changes the baseline every rule falls back to, and the rule editor does not show
 * them at all, so there is exactly one place to change each one.</p>
 */
public final class SettingsScreen {

    private SettingsScreen() {
    }

    /**
     * Builds the global settings screen.
     *
     * @param parent the screen to return to
     * @return the Cloth Config screen, ready to be shown
     */
    public static Screen create(Screen parent) {
        OpsecConfig config = OpsecConfig.getInstance();
        SpoofSettings settings = config.getGlobalSettings();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(OpsecLang.component(OpsecStrings.SCREEN_SETTINGS_TITLE))
                .setSavingRunnable(config::save)
                .setAfterInitConsumer(screen -> injectGlobalActions(screen));

        ConfigCategory category = builder.getOrCreateCategory(
                OpsecLang.component(OpsecStrings.SCREEN_SETTINGS_TITLE));
        addAppearanceEntries(builder, category, settings);
        addStreamerEntries(builder, category, settings);
        addDiagnosticsEntries(builder, category, settings);

        if (config.hasActiveServer()) {
            category.addEntry(builder.entryBuilder()
                    .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_PACK_CACHE_TRUST),
                            config.isCurrentServerPackCacheTrusted())
                    .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_PACK_CACHE_TRUST))
                    .setSaveConsumer(config::setCurrentServerPackCacheTrusted)
                    .build());
        }

        Screen screen = builder.build();
        OpsecScreens.registerClothConfigScreen(screen);
        return screen;
    }


    private static void injectGlobalActions(Screen screen) {
        if (!(screen instanceof ClothConfigScreen cc)) {
            return;
        }
        ScreenAccessor accessor = (ScreenAccessor) cc;

        int listLeft = cc.listWidget.left;
        int listWidth = cc.listWidget.width;
        int listTop = cc.listWidget.top;
        int listBottom = cc.listWidget.bottom - 24;
        int listHeight = cc.listWidget.height - 24;
        cc.listWidget.updateSize(listWidth, listHeight, listTop, listBottom);
        cc.listWidget.setLeftPos(listLeft);

        accessor.opsec$addRenderableWidget(Button
                .builder(OpsecLang.component(OpsecStrings.OPTION_CLEAR_CACHE),
                        button -> ResourcePackGuard.clearAllCaches())
                .bounds(cc.width / 2 - 100, cc.listWidget.bottom, 200, 20)
                .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_CLEAR_CACHE)))
                .build());
    }


    private static void addAppearanceEntries(ConfigBuilder builder, ConfigCategory category, SpoofSettings settings) {
        SubCategoryBuilder group = builder.entryBuilder()
                .startSubCategory(OpsecLang.component(OpsecStrings.SECTION_APPEARANCE));
        group.setExpanded(true);

        group.add(builder.entryBuilder()
                .startSelector(OpsecLang.component(OpsecStrings.OPTION_ACCENT_COLOR),
                        SpoofSettings.AccentColor.values(), settings.getAccentColor())
                .setNameProvider(SettingsScreen::accentName)
                .setSaveConsumer(settings::setAccentColor)
                .build());

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_COMPACT_LAYOUT), settings.isCompactLayout())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_COMPACT_LAYOUT))
                .setSaveConsumer(settings::setCompactLayout)
                .build());

        if (OpsecConfig.MC_VERSION_HAS_HUD_INDICATOR) {
            group.add(builder.entryBuilder()
                    .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_SHOW_HUD), settings.isShowHudIndicator())
                    .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_SHOW_HUD))
                    .setSaveConsumer(settings::setShowHudIndicator)
                    .build());
        }

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_SHOW_MULTIPLAYER_BUTTON),
                        settings.isShowMultiplayerScreenButton())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_SHOW_MULTIPLAYER_BUTTON))
                .setSaveConsumer(settings::setShowMultiplayerScreenButton)
                .build());

        category.addEntry(group.build());
    }


    private static void addStreamerEntries(ConfigBuilder builder, ConfigCategory category, SpoofSettings settings) {
        SubCategoryBuilder group = builder.entryBuilder()
                .startSubCategory(OpsecLang.component(OpsecStrings.SECTION_STREAMER_MODE));
        group.setExpanded(false);

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_STREAMER_MODE), settings.isStreamerModeEnabled())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_STREAMER_MODE))
                .setSaveConsumer(settings::setStreamerModeEnabled)
                .build());

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_STREAMER_HIDE_COORDS), settings.isStreamerHideCoordinates())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_STREAMER_HIDE_COORDS))
                .setSaveConsumer(settings::setStreamerHideCoordinates)
                .build());

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_STREAMER_MASK_NAMES), settings.isStreamerMaskPlayerNames())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_STREAMER_MASK_NAMES))
                .setSaveConsumer(settings::setStreamerMaskPlayerNames)
                .build());

        group.add(builder.entryBuilder()
                .startSelector(OpsecLang.component(OpsecStrings.OPTION_STREAMER_NAME_STYLE),
                        SpoofSettings.NameMaskStyle.values(), settings.getStreamerNameMaskStyle())
                .setNameProvider(SettingsScreen::nameMaskName)
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_STREAMER_NAME_STYLE))
                .setSaveConsumer(settings::setStreamerNameMaskStyle)
                .build());

        category.addEntry(group.build());
    }


    private static void addDiagnosticsEntries(ConfigBuilder builder, ConfigCategory category, SpoofSettings settings) {
        SubCategoryBuilder group = builder.entryBuilder()
                .startSubCategory(OpsecLang.component(OpsecStrings.SECTION_ALERTS));
        group.setExpanded(false);

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_SHOW_ALERTS), settings.isShowAlerts())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_SHOW_ALERTS))
                .setSaveConsumer(settings::setShowAlerts)
                .build());

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_SHOW_TOASTS), settings.isShowToasts())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_SHOW_TOASTS))
                .setSaveConsumer(settings::setShowToasts)
                .build());

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_LOG_DETECTIONS), settings.isLogDetections())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_LOG_DETECTIONS))
                .setSaveConsumer(settings::setLogDetections)
                .build());

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_LOG_SCRUBBER), settings.isLogScrubberEnabled())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_LOG_SCRUBBER))
                .setSaveConsumer(settings::setLogScrubberEnabled)
                .build());

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_INTEGRITY_CHECK), settings.isIntegrityCheckEnabled())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_INTEGRITY_CHECK))
                .setSaveConsumer(settings::setIntegrityCheckEnabled)
                .build());

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_DEBUG_ALERTS), settings.isDebugAlerts())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_DEBUG_ALERTS))
                .setSaveConsumer(settings::setDebugAlerts)
                .build());

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_DEBUG_COMMAND), settings.isDebugCommand())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_DEBUG_COMMAND))
                .setSaveConsumer(settings::setDebugCommand)
                .build());

        category.addEntry(group.build());
    }


    private static Component accentName(SpoofSettings.AccentColor color) {
        return Component.literal(color.code() + color.name());
    }

    private static Component nameMaskName(SpoofSettings.NameMaskStyle style) {
        return switch (style) {
            case HIDDEN -> OpsecLang.component(OpsecStrings.NAME_STYLE_HIDDEN);
            case GLYPHS -> OpsecLang.component(OpsecStrings.NAME_STYLE_GLYPHS);
        };
    }
}

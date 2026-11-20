package aurick.opsec.mod.config;

import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import aurick.opsec.mod.mixin.MeteorMixinCanceller;
import aurick.opsec.mod.mixin.client.ScreenAccessor;
import aurick.opsec.mod.tracking.ModRegistry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigScreen;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The rule editor.
 *
 * <p>The editing target is always the rule the user picked in the left pane — never
 * {@code config.getSettings()}, which reflects the currently connected server. Every
 * entry writes straight into that rule's {@link SpoofSettings}, so a rule is a
 * self-contained profile.</p>
 *
 * <p>The option list itself comes from Cloth Config: the three collapsible groups are
 * sub-categories, so Protection / Whitelist / Misc can be folded independently. The
 * rule list and the rule actions are our own widgets, injected into the Cloth Config
 * screen through {@link ScreenAccessor} after the screen has been initialised.</p>
 */
public final class RuleSettingsScreen {

    private RuleSettingsScreen() {
    }

    /**
     * Builds the editor for {@code rule}.
     *
     * @param parent the screen to return to
     * @param rule   the rule being edited — its settings are the edit target
     * @return the Cloth Config screen, ready to be shown
     */
    public static Screen create(Screen parent, ServerRule rule) {
        OpsecConfig config = OpsecConfig.getInstance();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(OpsecLang.component(OpsecStrings.SCREEN_RULE_SETTINGS_TITLE))
                .setSavingRunnable(config::save)
                .setAfterInitConsumer(screen -> injectRulePane(screen, parent, rule));

        ConfigCategory category = builder.getOrCreateCategory(OpsecLang.component(OpsecStrings.SCREEN_RULE_SETTINGS_TITLE));
        addIdentityEntries(builder, category, rule);
        addProtectionEntries(builder, category, rule);
        addWhitelistEntries(builder, category, rule);
        addMiscEntries(builder, category, rule);
        Screen screen = builder.build();
        OpsecScreens.registerClothConfigScreen(screen);
        return screen;
    }


    private static void injectRulePane(Screen screen, Screen parent, ServerRule rule) {
        if (!(screen instanceof ClothConfigScreen cc)) {
            return;
        }
        OpsecConfig config = OpsecConfig.getInstance();
        Minecraft minecraft = Minecraft.getInstance();
        ScreenAccessor accessor = (ScreenAccessor) cc;

        int screenWidth = cc.width;
        int screenHeight = cc.height;
        int paneWidth = Math.max(110, Math.min(180, screenWidth * 26 / 100));
        int paneX = 4;
        int gap = 6;
        int listLeft = paneX + paneWidth + gap;

        // Reserve a row at the bottom of the right pane for the rule-scoped sub-screens.
        int toolsHeight = 24;
        int ccListTop = cc.listWidget.top;
        int ccListBottom = cc.listWidget.bottom - toolsHeight;
        int ccListHeight = cc.listWidget.height - toolsHeight;
        cc.listWidget.updateSize(screenWidth - listLeft, ccListHeight, ccListTop, ccListBottom);
        // updateSize() resets left to 0 — the position must be applied afterwards.
        cc.listWidget.setLeftPos(listLeft);

        int paneBottom = ccListBottom;
        int bannerY = 24;
        int listTop = bannerY + 16;

        // "Editing: <rule>" — the edit target is never implicit.
        String bannerText = OpsecLang.tr(OpsecStrings.RULE_EDITING_BANNER, ruleLabelText(rule));
        StringWidget banner = new StringWidget(minecraft.font.width(bannerText), 10,
                Component.literal(bannerText), minecraft.font);
        banner.setX(paneX);
        banner.setY(bannerY);
        accessor.opsec$addRenderableWidget(banner);

        List<AbstractWidget> ruleButtons = new ArrayList<>();
        for (ServerRule listed : config.getServerRules()) {
            Button button = Button.builder(ruleLabel(listed, listed == rule), btn -> switchRule(cc, parent, listed))
                    .size(paneWidth - 12, 20)
                    .build();
            ruleButtons.add(button);
        }
        int ruleListHeight = Math.max(40, paneBottom - 46 - listTop);
        ScrollableWidgetList ruleList = new ScrollableWidgetList(minecraft, paneWidth - 8, ruleListHeight,
                listTop, 22, ruleButtons);
        ruleList.setBounds(paneX, listTop, paneWidth - 8, ruleListHeight);
        accessor.opsec$addRenderableWidget(ruleList);

        int half = (paneWidth - 8 - 4) / 2;
        int actionY = paneBottom - 44;
        accessor.opsec$addRenderableWidget(Button
                .builder(OpsecLang.component(OpsecStrings.RULE_ADD), btn -> addRule(cc, parent))
                .bounds(paneX, actionY, half, 20)
                .build());
        accessor.opsec$addRenderableWidget(Button
                .builder(OpsecLang.component(OpsecStrings.RULE_DELETE), btn -> deleteRule(cc, parent, rule))
                .bounds(paneX + half + 4, actionY, half, 20)
                .build());
        accessor.opsec$addRenderableWidget(Button
                .builder(OpsecLang.component(OpsecStrings.RULE_MOVE_UP), btn -> moveRule(cc, parent, rule, -1))
                .bounds(paneX, actionY + 22, half, 20)
                .build());
        accessor.opsec$addRenderableWidget(Button
                .builder(OpsecLang.component(OpsecStrings.RULE_MOVE_DOWN), btn -> moveRule(cc, parent, rule, 1))
                .bounds(paneX + half + 4, actionY + 22, half, 20)
                .build());

        // Rule-scoped sub-screens. Cloth Config has no button entry type, so these live
        // in the option pane's own button row.
        int toolsWidth = screenWidth - listLeft - 12;
        int third = (toolsWidth - 8) / 3;
        int toolsY = ccListBottom + 2;
        accessor.opsec$addRenderableWidget(Button
                .builder(OpsecLang.component(OpsecStrings.RULE_TOOLS_LUNAR), btn -> openLunar(cc, rule))
                .bounds(listLeft, toolsY, third, 20)
                .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.RULE_TOOLS_LUNAR)))
                .build());
        accessor.opsec$addRenderableWidget(Button
                .builder(OpsecLang.component(OpsecStrings.RULE_TOOLS_RELAY), btn -> openRelay(cc, rule))
                .bounds(listLeft + third + 4, toolsY, third, 20)
                .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.RULE_TOOLS_RELAY)))
                .build());
        accessor.opsec$addRenderableWidget(Button
                .builder(OpsecLang.component(OpsecStrings.RULE_TOOLS_PROXY), btn -> openProxy(cc, rule))
                .bounds(listLeft + (third + 4) * 2, toolsY, third, 20)
                .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.RULE_TOOLS_PROXY)))
                .build());
    }

    private static void switchRule(ConfigScreen cc, Screen parent, ServerRule target) {
        commit(cc);
        show(create(parent, target));
    }

    private static void addRule(ConfigScreen cc, Screen parent) {
        commit(cc);
        OpsecConfig config = OpsecConfig.getInstance();
        ServerRule rule = new ServerRule(ServerRule.MatchType.EXACT, "");
        rule.copySettingsFrom(config.getGlobalSettings());
        config.addServerRule(rule);
        show(create(parent, rule));
    }

    private static void deleteRule(ConfigScreen cc, Screen parent, ServerRule rule) {
        commit(cc);
        OpsecConfig config = OpsecConfig.getInstance();
        config.removeServerRule(rule);
        show(create(parent, config.getAnyRule()));
    }

    private static void moveRule(ConfigScreen cc, Screen parent, ServerRule rule, int delta) {
        commit(cc);
        OpsecConfig config = OpsecConfig.getInstance();
        int index = config.getServerRules().indexOf(rule);
        if (index < 0) {
            return;
        }
        config.moveServerRule(index, delta);
        show(create(parent, rule));
    }

    /** Writes the pending option changes into the rule and persists the config. */
    private static void commit(ConfigScreen cc) {
        if (cc.isEdited()) {
            // saveAll(false) persists the entries without jumping back to the parent.
            cc.saveAll(false);
        }
        OpsecConfig.getInstance().save();
    }

    private static void openLunar(ConfigScreen cc, ServerRule rule) {
        commit(cc);
        show(new LunarVersionSettingsScreen(currentScreen(cc),
                OpsecConfig.getInstance(), rule.getSettings()));
    }

    private static void openRelay(ConfigScreen cc, ServerRule rule) {
        commit(cc);
        show(new CloudflareRelaySettingsScreen(currentScreen(cc),
                OpsecConfig.getInstance(), rule.getSettings()));
    }

    private static void openProxy(ConfigScreen cc, ServerRule rule) {
        commit(cc);
        show(new GameProxySettingsScreen(currentScreen(cc),
                OpsecConfig.getInstance(), rule.getSettings()));
    }

    private static Screen currentScreen(ConfigScreen cc) {
        // The Cloth Config screen itself is the parent of the sub-screens: closing them
        // returns to the rule editor, not to the hub.
        return (Screen) cc;
    }

    private static void show(Screen screen) {
        //? if >=26.2 {
        Minecraft.getInstance().setScreenAndShow(screen);
        //?} else {
        /*Minecraft.getInstance().setScreen(screen);
        *///?}
    }

    private static String ruleLabelText(ServerRule rule) {
        if (rule.isAnyRule()) {
            return OpsecLang.tr(OpsecStrings.RULE_ANY_LABEL);
        }
        String pattern = rule.getPattern();
        if (pattern.length() > 24) {
            pattern = pattern.substring(0, 23) + "…";
        }
        return rule.getMatchType().name() + "  " + pattern;
    }

    private static Component ruleLabel(ServerRule rule, boolean selected) {
        String text = ruleLabelText(rule);
        if (!rule.isEnabled() && !rule.isAnyRule()) {
            text = OpsecLang.tr(OpsecStrings.RULE_DISABLED_PREFIX) + text;
        }
        return Component.literal((selected ? "§a" : "§f") + text);
    }


    private static void addIdentityEntries(ConfigBuilder builder, ConfigCategory category, ServerRule rule) {
        if (rule.isAnyRule()) {
            category.addEntry(builder.entryBuilder()
                    .startTextDescription(OpsecLang.component(OpsecStrings.RULE_ANY_DESCRIPTION))
                    .build());
            return;
        }

        ServerRule.MatchType[] types = {
                ServerRule.MatchType.EXACT,
                ServerRule.MatchType.REGEX,
                ServerRule.MatchType.IP
        };
        category.addEntry(builder.entryBuilder()
                .startSelector(OpsecLang.component(OpsecStrings.RULE_MATCH_TYPE), types, rule.getMatchType())
                .setNameProvider(type -> Component.literal(type.name()))
                .setTooltip(OpsecLang.component(OpsecStrings.RULE_MATCH_TYPE_TOOLTIP))
                .setSaveConsumer(rule::setMatchType)
                .build());

        category.addEntry(builder.entryBuilder()
                .startStrField(OpsecLang.component(OpsecStrings.RULE_APPLIES_TO), rule.getPattern())
                .setErrorSupplier(value -> validatePattern(value, rule))
                .setTooltip(OpsecLang.component(OpsecStrings.RULE_APPLIES_TO_TOOLTIP))
                .setSaveConsumer(rule::setPattern)
                .build());

        category.addEntry(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.RULE_ENABLED), rule.isEnabled())
                .setSaveConsumer(rule::setEnabled)
                .build());
    }

    private static Optional<Component> validatePattern(String value, ServerRule rule) {
        if (value == null || value.isBlank()) {
            return Optional.of(Component.literal(OpsecLang.tr(OpsecStrings.RULE_ERROR_NO_PATTERN)));
        }
        if (rule.getMatchType() != ServerRule.MatchType.REGEX) {
            return Optional.empty();
        }
        for (String pattern : ServerRule.splitPatterns(value)) {
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                return Optional.of(Component.literal(OpsecLang.tr(OpsecStrings.RULE_ERROR_BAD_REGEX, pattern)));
            }
        }
        return Optional.empty();
    }


    private static void addProtectionEntries(ConfigBuilder builder, ConfigCategory category, ServerRule rule) {
        SpoofSettings settings = rule.getSettings();
        SubCategoryBuilder group = builder.entryBuilder()
                .startSubCategory(OpsecLang.component(OpsecStrings.SECTION_PROTECTION));
        group.setExpanded(true);

        if (OpsecConfig.EXPLOIT_PREVENTER_LOADED) {
            group.add(builder.entryBuilder()
                    .startTextDescription(OpsecLang.component(OpsecStrings.EP_MANAGED_HEADER))
                    .build());
        } else {
            group.add(builder.entryBuilder()
                    .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_SPOOF_AS_VANILLA), settings.isSpoofAsVanilla())
                    .setTooltip(OpsecLang.component(OpsecStrings.OPTION_SPOOF_AS_VANILLA_TOOLTIP))
                    .setSaveConsumer(settings::setSpoofAsVanilla)
                    .build());
        }

        group.add(builder.entryBuilder()
                .startSelector(OpsecLang.component(OpsecStrings.OPTION_BRAND_OVERRIDE),
                        SpoofSettings.BrandOverride.values(), settings.getBrandOverride())
                .setNameProvider(RuleSettingsScreen::brandName)
                .setTooltipSupplier(value -> Optional.of(new Component[]{brandTooltip(value)}))
                .setSaveConsumer(settings::setBrandOverride)
                .build());

        if (OpsecConfig.EXPLOIT_PREVENTER_LOADED) {
            group.add(builder.entryBuilder()
                    .startTextDescription(OpsecLang.component(OpsecStrings.EP_MANAGED_HEADER))
                    .build());
        } else {
            if (OpsecConfig.MC_VERSION_HAS_MULTI_PACK) {
                group.add(builder.entryBuilder()
                        .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_ISOLATE_PACK_CACHE), settings.isIsolatePackCache())
                        .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_ISOLATE_PACK_CACHE))
                        .setSaveConsumer(settings::setIsolatePackCache)
                        .build());
            }
            if (OpsecConfig.MC_VERSION_HAS_BLOCK_LOCAL_URLS) {
                group.add(builder.entryBuilder()
                        .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_BLOCK_LOCAL_PACK_URLS), settings.isBlockLocalPackUrls())
                        .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_BLOCK_LOCAL_PACK_URLS))
                        .setSaveConsumer(settings::setBlockLocalPackUrls)
                        .build());
            }
            group.add(builder.entryBuilder()
                    .startSelector(OpsecLang.component(OpsecStrings.OPTION_PACK_STRIP_MODE),
                            SpoofSettings.StripMode.values(), settings.getPackStripMode())
                    .setNameProvider(RuleSettingsScreen::stripModeName)
                    .setTooltipSupplier(value -> Optional.of(new Component[]{stripModeTooltip(value)}))
                    .setSaveConsumer(settings::setPackStripMode)
                    .build());
        }

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_STRIP_MOD_SHADERS), settings.isStripModShaders())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_STRIP_MOD_SHADERS))
                .setSaveConsumer(settings::setStripModShaders)
                .build());

        //? if >=1.20.3 {
        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_SCRUB_PACK_HEADERS), settings.isScrubPackHeaders())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_SCRUB_PACK_HEADERS))
                .setSaveConsumer(settings::setScrubPackHeaders)
                .build());
        //?}

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_AUTO_PURGE_PACK_CACHE), settings.isAutoPurgePackCache())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_AUTO_PURGE_PACK_CACHE))
                .setSaveConsumer(settings::setAutoPurgePackCache)
                .build());

        if (OpsecConfig.EXPLOIT_PREVENTER_LOADED) {
            group.add(builder.entryBuilder()
                    .startTextDescription(OpsecLang.component(OpsecStrings.EP_MANAGED_HEADER))
                    .build());
        } else {
            group.add(builder.entryBuilder()
                    .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_KEY_RESOLUTION_SPOOFING), settings.isTranslationProtectionEnabled())
                    .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_KEY_RESOLUTION_SPOOFING))
                    .setSaveConsumer(settings::setTranslationProtection)
                    .build());

            group.add(builder.entryBuilder()
                    .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_FAKE_DEFAULT_KEYBINDS), settings.isFakeDefaultKeybinds())
                    .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_FAKE_DEFAULT_KEYBINDS))
                    .setSaveConsumer(settings::setFakeDefaultKeybinds)
                    .build());

            //? if <26.1 {
            /*group.add(builder.entryBuilder()
                    .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_METEOR_FIX), settings.isMeteorFix())
                    .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_METEOR_FIX))
                    .setSaveConsumer(settings::setMeteorFix)
                    .build());

            if (MeteorMixinCanceller.needsRestart(settings.isMeteorFix())) {
                group.add(builder.entryBuilder()
                        .startTextDescription(OpsecLang.component(OpsecStrings.SECTION_RESTART_WARNING))
                        .build());
            }
            *///?}
        }

        category.addEntry(group.build());
    }


    private static void addWhitelistEntries(ConfigBuilder builder, ConfigCategory category, ServerRule rule) {
        SpoofSettings settings = rule.getSettings();
        SubCategoryBuilder group = builder.entryBuilder()
                .startSubCategory(OpsecLang.component(OpsecStrings.SECTION_MOD_WHITELIST));
        group.setExpanded(false);

        if (OpsecConfig.EXPLOIT_PREVENTER_LOADED) {
            group.add(builder.entryBuilder()
                    .startTextDescription(OpsecLang.component(OpsecStrings.EP_MANAGED_HEADER))
                    .build());
            category.addEntry(group.build());
            return;
        }

        SpoofSettings.WhitelistMode initialMode = settings.getWhitelistMode() == SpoofSettings.WhitelistMode.CUSTOM
                ? SpoofSettings.WhitelistMode.CUSTOM
                : SpoofSettings.WhitelistMode.AUTO;
        group.add(builder.entryBuilder()
                .startSelector(OpsecLang.component(OpsecStrings.OPTION_WHITELIST_MODE),
                        new SpoofSettings.WhitelistMode[]{
                                SpoofSettings.WhitelistMode.AUTO,
                                SpoofSettings.WhitelistMode.CUSTOM
                        }, initialMode)
                .setNameProvider(RuleSettingsScreen::whitelistModeName)
                .setTooltipSupplier(value -> Optional.of(new Component[]{whitelistModeTooltip(value)}))
                .setSaveConsumer(value -> {
                    // Spoof-as-vanilla implies block-all; the mode must stay OFF there.
                    if (!settings.isSpoofAsVanilla()) {
                        settings.setWhitelistMode(value);
                    }
                })
                .build());

        Set<String> installed = installedModIds();
        Set<String> recorded = new LinkedHashSet<>(settings.getWhitelistedMods());

        for (ModRegistry.InstalledMod mod : ModRegistry.getInstalledMods()) {
            String modId = mod.getModId();
            String modName = mod.getDisplayName();

            if (ModRegistry.isInDependencyClosure(modId)) {
                // Pulled in by another mod: it is always allowed and cannot be turned off.
                group.add(builder.entryBuilder()
                        .startTextDescription(Component.literal("§7" + modName + " "
                                + OpsecLang.tr(OpsecStrings.WHITELIST_SUFFIX_REQUIRED,
                                        ModRegistry.resolveRequiringModName(modId))))
                        .build());
                continue;
            }

            boolean isExplicit = recorded.contains(modId);
            group.add(builder.entryBuilder()
                    .startBooleanToggle(Component.literal(modName), isExplicit)
                    .setTooltip(Component.literal(modId))
                    .setSaveConsumer(value -> {
                        if (value) {
                            settings.getWhitelistedMods().add(modId);
                        } else {
                            settings.getWhitelistedMods().remove(modId);
                        }
                    })
                    .build());
        }

        // Mods that were whitelisted but are no longer installed: they are listed so the
        // user can see and remove them.
        List<String> notInstalled = new ArrayList<>();
        for (String modId : recorded) {
            if (!installed.contains(modId)) {
                notInstalled.add(modId);
            }
        }
        if (!notInstalled.isEmpty()) {
            group.add(builder.entryBuilder()
                    .startStrList(OpsecLang.component(OpsecStrings.WHITELIST_NOT_INSTALLED_TITLE),
                            new ArrayList<>(notInstalled))
                    .setDeleteButtonEnabled(true)
                    .setSaveConsumer(list -> {
                        for (String modId : notInstalled) {
                            if (!list.contains(modId)) {
                                settings.getWhitelistedMods().remove(modId);
                            }
                        }
                    })
                    .build());
        }

        category.addEntry(group.build());
    }

    private static Set<String> installedModIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (ModRegistry.InstalledMod mod : ModRegistry.getInstalledMods()) {
            ids.add(mod.getModId());
        }
        return ids;
    }


    /**
     * The per-rule options that are neither protection nor whitelist.
     *
     * <p>Bucket rule: anything that changes what OpSec does <em>towards the server</em>
     * belongs to a rule; client-local preferences live in {@link SettingsScreen}.</p>
     */
    private static void addMiscEntries(ConfigBuilder builder, ConfigCategory category, ServerRule rule) {
        SpoofSettings settings = rule.getSettings();
        SubCategoryBuilder group = builder.entryBuilder()
                .startSubCategory(OpsecLang.component(OpsecStrings.SECTION_MISC));
        group.setExpanded(false);

        if (OpsecConfig.CHAT_SIGNING_MANAGED_EXTERNALLY) {
            group.add(builder.entryBuilder()
                    .startTextDescription(OpsecLang.component(OpsecStrings.COMPAT_MANAGED_HEADER,
                            OpsecConfig.chatSigningManagerName()))
                    .build());
        } else {
            group.add(builder.entryBuilder()
                    .startSelector(OpsecLang.component(OpsecStrings.OPTION_CHAT_SIGNING),
                            SpoofSettings.SigningMode.values(), settings.getSigningMode())
                    .setNameProvider(RuleSettingsScreen::signingName)
                    .setTooltipSupplier(value -> Optional.of(new Component[]{signingTooltip(value)}))
                    .setSaveConsumer(settings::setSigningMode)
                    .build());
        }

        if (OpsecConfig.TELEMETRY_MANAGED_EXTERNALLY) {
            group.add(builder.entryBuilder()
                    .startTextDescription(OpsecLang.component(OpsecStrings.COMPAT_MANAGED_HEADER,
                            OpsecConfig.telemetryManagerName()))
                    .build());
        } else {
            group.add(builder.entryBuilder()
                    .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_DISABLE_TELEMETRY), settings.isDisableTelemetry())
                    .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_DISABLE_TELEMETRY))
                    .setSaveConsumer(settings::setDisableTelemetry)
                    .build());
        }

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_GUARD_CHAT_LINKS), settings.isGuardChatLinks())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_GUARD_CHAT_LINKS))
                .setSaveConsumer(settings::setGuardChatLinks)
                .build());

        //? if >=1.20.2 {
        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_COMMAND_HISTORY_GUARD), settings.isCommandHistoryGuard())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_COMMAND_HISTORY_GUARD))
                .setSaveConsumer(settings::setCommandHistoryGuard)
                .build());
        //?}

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_DPI_FRAGMENT_TLS), settings.isDpiFragmentTlsHello())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_DPI_FRAGMENT_TLS))
                .setSaveConsumer(settings::setDpiFragmentTlsHello)
                .build());

        //? if >=1.20.5 {
        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_BLOCK_COOKIES), settings.isBlockCookies())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_BLOCK_COOKIES))
                .setSaveConsumer(settings::setBlockCookies)
                .build());

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_CONFIRM_TRANSFER), settings.isConfirmTransfer())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_CONFIRM_TRANSFER))
                .setSaveConsumer(settings::setConfirmTransfer)
                .build());
        //?}

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_NORMALIZE_CLIENT_INFO), settings.isNormalizeClientInfo())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_NORMALIZE_CLIENT_INFO))
                .setSaveConsumer(settings::setNormalizeClientInfo)
                .build());

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.OPTION_LAZY_SERVER_PING), settings.isLazyServerPing())
                .setTooltip(OpsecLang.component(OpsecStrings.TOOLTIP_LAZY_SERVER_PING))
                .setSaveConsumer(settings::setLazyServerPing)
                .build());

        group.add(builder.entryBuilder()
                .startBooleanToggle(OpsecLang.component(OpsecStrings.RULE_CLOUDFLARE_RELAY), settings.isCloudflarePingRelayEnabled())
                .setSaveConsumer(settings::setCloudflarePingRelayEnabled)
                .build());

        group.add(builder.entryBuilder()
                .startSelector(OpsecLang.component(OpsecStrings.RULE_GAME_PROXY),
                        SpoofSettings.GameProxyType.values(), settings.getGameProxyType())
                .setNameProvider(RuleSettingsScreen::proxyName)
                .setSaveConsumer(settings::setGameProxyType)
                .build());

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


    private static Component brandName(SpoofSettings.BrandOverride mode) {
        return switch (mode) {
            case DEFAULT -> OpsecLang.component(OpsecStrings.BRAND_OVERRIDE_VANILLA);
            case LUNAR_CLIENT -> OpsecLang.component(OpsecStrings.BRAND_OVERRIDE_LUNAR);
            case BADLION -> OpsecLang.component(OpsecStrings.BRAND_OVERRIDE_BADLION);
        };
    }

    private static Component brandTooltip(SpoofSettings.BrandOverride mode) {
        return switch (mode) {
            case DEFAULT -> OpsecLang.component(OpsecStrings.BRAND_OVERRIDE_VANILLA_TOOLTIP);
            case LUNAR_CLIENT -> OpsecLang.component(OpsecStrings.BRAND_OVERRIDE_LUNAR_TOOLTIP);
            case BADLION -> OpsecLang.component(OpsecStrings.BRAND_OVERRIDE_BADLION_TOOLTIP);
        };
    }

    private static Component signingName(SpoofSettings.SigningMode mode) {
        return switch (mode) {
            case SIGN -> Component.literal("§aON");
            case OFF -> Component.literal("§eOFF");
        };
    }

    private static Component signingTooltip(SpoofSettings.SigningMode mode) {
        return switch (mode) {
            case SIGN -> OpsecLang.component(OpsecStrings.CHATSIGNING_SIGN_TOOLTIP);
            case OFF -> OpsecLang.component(OpsecStrings.CHATSIGNING_OFF_TOOLTIP);
        };
    }

    private static Component whitelistModeName(SpoofSettings.WhitelistMode mode) {
        return switch (mode) {
            case OFF -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_BLOCK_ALL);
            case AUTO -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_AUTO);
            case CUSTOM -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_CUSTOM);
        };
    }

    private static Component whitelistModeTooltip(SpoofSettings.WhitelistMode mode) {
        return switch (mode) {
            case OFF -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_BLOCK_ALL_TOOLTIP);
            case AUTO -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_AUTO_TOOLTIP);
            case CUSTOM -> OpsecLang.component(OpsecStrings.WHITELIST_MODE_CUSTOM_TOOLTIP);
        };
    }

    private static Component proxyName(SpoofSettings.GameProxyType type) {
        return switch (type) {
            case NONE -> Component.literal("Off");
            case SOCKS5 -> Component.literal("SOCKS5");
            case HTTP -> Component.literal("HTTP");
        };
    }

    private static Component stripModeName(SpoofSettings.StripMode mode) {
        return switch (mode) {
            case MANUAL -> OpsecLang.component(OpsecStrings.PACKSTRIP_MODE_MANUAL);
            case ASK -> OpsecLang.component(OpsecStrings.PACKSTRIP_MODE_ASK);
            case ALWAYS_ON -> OpsecLang.component(OpsecStrings.PACKSTRIP_MODE_ALWAYS_ON);
        };
    }

    private static Component stripModeTooltip(SpoofSettings.StripMode mode) {
        return switch (mode) {
            case MANUAL -> OpsecLang.component(OpsecStrings.PACKSTRIP_MODE_MANUAL_TOOLTIP);
            case ASK -> OpsecLang.component(OpsecStrings.PACKSTRIP_MODE_ASK_TOOLTIP);
            case ALWAYS_ON -> OpsecLang.component(OpsecStrings.PACKSTRIP_MODE_ALWAYS_ON_TOOLTIP);
        };
    }
}

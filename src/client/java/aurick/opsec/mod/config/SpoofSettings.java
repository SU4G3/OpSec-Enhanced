package aurick.opsec.mod.config;

import aurick.opsec.mod.accounts.AccountCrypto;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;

import static aurick.opsec.mod.config.OpsecConstants.Brands.*;

/**
 * Settings for client spoofing and privacy protection.
 */
public class SpoofSettings {
    
    /**
     * Signing modes for chat messages.
     * Controls whether messages are cryptographically signed.
     *
     * <p>ON_DEMAND was removed: it could not be implemented without either a
     * server-detectable fingerprint (auto-upgrade based on attacker-controlled
     * system chat) or breaking on every proxy network (login flag is set by the
     * proxy, not the backend). Users who want maximum privacy set OFF and
     * accept that some servers will reject their chats; users who want chat to
     * just work set SIGN. The mod no longer tries to straddle the two.</p>
     */
    public enum SigningMode {
        /** Always sign messages (chat reportable to Mojang). Default. */
        SIGN,
        /** Never sign messages (may break on strict servers). */
        OFF
    }

    /**
     * Whitelist modes for mod filtering.
     * Controls which mods are allowed through the whitelist.
     */
    public enum WhitelistMode {
        /** Whitelist disabled - all mod content blocked */
        OFF,
        /** Auto-whitelist mods that have registered network channels */
        AUTO,
        /** Manual whitelist - only explicitly selected mods allowed */
        CUSTOM
    }

    /**
     * Which brand string to advertise while {@link #spoofAsVanilla} is on. Only
     * changes the outbound {@code brand} value — channel/known-pack/key-resolution
     * behavior stays identical to plain vanilla mode (block everything non-vanilla)
     * in every case, because that's what the sourcing below shows these clients
     * actually do on the wire:
     *
     * <ul>
     *   <li>Badlion Client registers no plugin channels of its own; server-side
     *       brand allowlists match it on prefix {@code "badlion"}/{@code "BLC"}.
     *       No version component, so this option can't go stale.</li>
     *   <li>Lunar Client's brand is {@code "lunarclient:v<version>,fabric"}; some
     *       servers additionally look for a {@code lunar.*} plugin channel or
     *       validate the version format. OpSec does not implement Lunar's
     *       proprietary cosmetics protocol behind that channel, so this option
     *       only matches brand-string allowlists, not a strict per-channel check.
     *       The version suffix is user-editable ({@link #lunarVersionSuffix})
     *       instead of hardcoded, since a stale build number is itself a tell.</li>
     * </ul>
     *
     * Picking one of these over plain vanilla only helps against a server that
     * denies the generic vanilla brand but allowlists specific "known" clients —
     * an uncommon setup. When in doubt, plain vanilla (DEFAULT) is the safer,
     * always-accurate choice.
     */
    public enum BrandOverride {
        DEFAULT,
        LUNAR_CLIENT,
        BADLION
    }

    /**
     * Bypass Server Pack Requirement modes.
     * Controls whether server-pushed resource packs have their visual/audio content
     * stripped (textures/sounds/models/fonts removed; lang data still applied so
     * translation-key probes resolve vanilla-identically).
     * In all modes the user can toggle any server pack in the resource pack menu;
     * the mode only decides the initial state and whether a consent overlay appears.
     */
    public enum StripMode {
        /** Default vanilla behavior on push. User may still toggle the pack off via the
         *  resource pack menu to strip it while keeping lang loaded. */
        MANUAL,
        /** Consent screen asks on push. Pack is stripped until the user opts in. */
        ASK,
        /** No consent screen. Pack is always stripped; user may still toggle it on. */
        ALWAYS_ON
    }

    /** Proxy protocol used for the actual multiplayer game connection (see #11). */
    public enum GameProxyType { NONE, SOCKS5, HTTP }

    /** How a masked player name is displayed in Streamer Mode. */
    public enum NameMaskStyle {
        /** Replace with a fixed placeholder ("Player"). */
        HIDDEN,
        /** Replace with pseudo-random CJK-style glyphs that rotate every few seconds
         *  (same look as Hypixel's own nick-disguise), so a viewer can't read a stable
         *  fake name off-stream and correlate it across the recording. */
        GLYPHS
    }

    /** Accent color applied to the multiplayer-screen "OpSec" button and the config screen title. */
    public enum AccentColor {
        CYAN("§b"),
        GREEN("§a"),
        PURPLE("§d"),
        ORANGE("§6"),
        RED("§c");

        private final String code;
        AccentColor(String code) { this.code = code; }
        public String code() { return code; }
    }

    /**
     * Controls the seasonal background gradient on the OpSec settings screen (see
     * {@code SeasonalWallpaperMixin}). OFF/HALLOWEEN/CHRISTMAS are manual overrides —
     * HALLOWEEN/CHRISTMAS show that theme immediately regardless of the real date, so
     * it can be previewed without waiting for the calendar window.
     */
    public enum WallpaperMode { OFF, AUTO, HALLOWEEN, CHRISTMAS }

    // Brand spoofing — when true, the client advertises a vanilla brand and blocks
    // ALL outbound custom payloads. When false, the natural Fabric brand passes
    // through and channels are filtered via the Whitelist tab (Block All / Auto /
    // Custom). The UI prevents spoofAsVanilla=true while the whitelist is in
    // Auto/Custom — the resulting state would be incoherent (vanilla brand
    // advertising selective mod channels).
    private boolean spoofAsVanilla = false;

    // Which brand string to send while spoofAsVanilla is on — see BrandOverride javadoc.
    private BrandOverride brandOverride = BrandOverride.DEFAULT;
    private String lunarVersionSuffix = OpsecConstants.Brands.LUNAR_CLIENT_DEFAULT_SUFFIX;

    // Fragments the TLS ClientHello of the mod's own update-check / integrity-check
    // HTTPS requests across multiple TCP segments, to dodge naive SNI-based DPI
    // filtering (relevant in RU/CN/IR). Off by default: it's a narrow, speculative
    // fix for a regional problem most users don't have, and only covers requests
    // that go through DpiEvasion (not the Microsoft/Xbox login flow — see its
    // javadoc for why that path can't use this technique).
    private boolean dpiFragmentTlsHello = false;

    // Appearance — purely cosmetic, no effect on protection behavior.
    private AccentColor accentColor = AccentColor.CYAN;
    private boolean compactLayout = false;
    private boolean showHudIndicator = false;

    // Resource pack protection
    private boolean isolatePackCache = true;
    private boolean blockLocalPackUrls = true;
    // Strip server-pack shader overrides of non-whitelisted mods (defeats GPU-DoS / GUI-crash shaders).
    private boolean stripModShaders = true;

    // Key resolution protection
    private boolean translationProtection = true;
    private boolean fakeDefaultKeybinds = true;  // Spoof vanilla keybinds to default values
    private boolean meteorFix = true;  // Block Meteor's broken key resolution protection

    // Strip Server Pack
    // Pack is still downloaded + cached by vanilla (fingerprint-normal), lang is loaded
    // into ClientLanguage as a vanilla client would; only textures/sounds/models/fonts
    // are filtered out via LangOnlyPackResources.
    private StripMode packStripMode = StripMode.MANUAL;
    
    // Alerts
    private boolean showAlerts = true;
    private boolean showToasts = true;
    private boolean logDetections = true;
    private boolean debugAlerts = false;

    // Debug — when false the /opsec client command is not registered at all.
    private boolean debugCommand = false;

    // Chat Signing
    private SigningMode signingMode = SigningMode.SIGN;
    
    // Privacy
    private boolean disableTelemetry = true;
    // Require confirmation before a server-sent chat/sign/book click event copies to the
    // clipboard or runs a client command — vanilla executes both with no prompt (unlike
    // open-URL, which already confirms), so a crafted message can silently overwrite the
    // clipboard (e.g. a scam address) or fire a command click.
    private boolean guardChatLinks = true;
    // Block ClientboundStoreCookiePacket outright (1.20.5+) — cookies persist across
    // reconnects/Transfer hops, a ready-made cross-session/cross-server tracker.
    private boolean blockCookies = true;
    // Replace the outgoing ClientInformation (language/view-distance/chat-mode/skin-layers/
    // main-hand) with a fixed common baseline instead of the player's real settings.
    private boolean normalizeClientInfo = false;
    // Redact OS username, IP addresses, and the player's name from crash reports.
    private boolean logScrubberEnabled = true;
    // Skip the automatic ping of every saved server when the multiplayer screen opens.
    private boolean lazyServerPing = false;
    // When Lazy Server List Ping is on, optionally back the (skipped) direct ping with a
    // user-deployed Cloudflare Worker relay instead (see #15) -- the worker does the raw
    // TCP handshake server-side and hands back only the parsed status JSON, so saved
    // servers still show live MOTD/player-count without a direct connection from this IP.
    // Requires >=1.20.6 (ServerStatusPingerMixin's relay path isn't wired below that).
    private boolean cloudflarePingRelayEnabled = false;
    private String cloudflarePingRelayUrl = "";
    // Replace X-Minecraft-Username/UUID/Version/Version-ID/Pack-Format and User-Agent
    // with neutral values on server-pack downloads — the pack host sees these even
    // when it's a third-party CDN, not just the game server.
    private boolean scrubPackHeaders = true;
    // Redact the arguments of /login, /register etc. before CommandHistory persists them.
    private boolean commandHistoryGuard = true;
    // Wipe downloads/ and server-resource-packs/ on client shutdown.
    private boolean autoPurgePackCache = false;
    // Randomize a newly-added session account's skin/cape right away, so an alt doesn't
    // start out sharing a skin/cape with another saved account (see Skin/Cape Correlation Alerts).
    private boolean autoRandomizeNewAccount = false;
    // Purely cosmetic seasonal accent-color override, see SeasonalTheme. Each defaults on.
    private boolean halloweenThemeEnabled = true;
    private boolean christmasThemeEnabled = true;
    // Seasonal background gradient on the settings screen — independent of the accent-color
    // toggles above, so one can be on while the other is off.
    private WallpaperMode wallpaperMode = WallpaperMode.AUTO;
    // Require confirmation before following a server-sent Transfer (1.20.5+) to a
    // different host/port — an unrequested redirect is also an IP disclosure.
    private boolean confirmTransfer = true;
    // The mod's own startup jar-hash check calls out to Modrinth/GitHub/CurseForge.
    // A privacy tool should let you turn off its own network calls too.
    private boolean integrityCheckEnabled = true;

    // Game connection proxy (#11) — unlike every other outbound HTTP call this mod already
    // threads a Proxy through, the actual multiplayer TCP connection (Connection.java) is a
    // raw Netty Bootstrap with no proxy concept at all; JVM-level -DsocksProxyHost etc. have
    // zero effect on it. This routes that one specific connection through a user-configured
    // SOCKS5/HTTP proxy via a from-scratch handshake (Minecraft's bundled netty-handler jar
    // doesn't include io.netty.handler.proxy.*). Off by default — this is a real behavior
    // change to the connection itself, not a passive redaction, so it stays opt-in.
    private GameProxyType gameProxyType = GameProxyType.NONE;
    private String gameProxyHost = "";
    private int gameProxyPort = 1080;
    private String gameProxyUsername = "";
    private String gameProxyPassword = ""; // encrypted at rest via AccountCrypto, same as account secrets

    // Streamer Mode — purely visual redaction for content creators, no effect on
    // what's sent to the server (that's already covered by the rest of the mod).
    private boolean streamerModeEnabled = false;
    private boolean streamerHideCoordinates = true;
    private boolean streamerMaskPlayerNames = true;
    private NameMaskStyle streamerNameMaskStyle = NameMaskStyle.GLYPHS;
    private boolean streamerHideServerAddress = true;


    // UI Settings
    private int buttonX = -1;
    private int buttonY = -1;

    // Update notification
    private String skippedUpdateVersion = "";
    private boolean tamperWarningDismissed = false;

    // One-time hints (persisted, never shown again)
    private boolean alertHintShown = false;
    
    // Whitelist settings
    private WhitelistMode whitelistMode = WhitelistMode.AUTO;
    // Snapshot of whitelistMode taken when spoofAsVanilla flipped on, so we can
    // restore it when the user toggles spoofAsVanilla off. Null when spoofAsVanilla
    // is currently false. Block-All (WhitelistMode.OFF) is the implicit override
    // used while spoofAsVanilla is on and is not user-selectable from the UI.
    private WhitelistMode previousWhitelistMode = null;
    private Set<String> whitelistedMods = new HashSet<>();

    public SpoofSettings() {}

    public boolean isSpoofAsVanilla() { return spoofAsVanilla; }
    public void setSpoofAsVanilla(boolean spoofAsVanilla) {
        if (this.spoofAsVanilla == spoofAsVanilla) return;
        if (spoofAsVanilla) {
            // Off -> On: remember the user's current whitelist choice so we can
            // restore it later, then force the Block-All override.
            if (this.whitelistMode != WhitelistMode.OFF) {
                this.previousWhitelistMode = this.whitelistMode;
            }
            this.whitelistMode = WhitelistMode.OFF;
        } else {
            // On -> Off: restore the user's prior whitelist choice. Default to
            // AUTO if there is no remembered choice (or it was somehow OFF) so
            // the user is never left in the now-unreachable Block-All state.
            WhitelistMode restored = this.previousWhitelistMode;
            if (restored == null || restored == WhitelistMode.OFF) {
                restored = WhitelistMode.AUTO;
            }
            this.whitelistMode = restored;
            this.previousWhitelistMode = null;
        }
        this.spoofAsVanilla = spoofAsVanilla;
    }

    public BrandOverride getBrandOverride() { return brandOverride; }
    public void setBrandOverride(BrandOverride override) { this.brandOverride = override != null ? override : BrandOverride.DEFAULT; }

    public String getLunarVersionSuffix() { return lunarVersionSuffix; }
    public void setLunarVersionSuffix(String suffix) {
        this.lunarVersionSuffix = (suffix == null || suffix.isBlank())
                ? OpsecConstants.Brands.LUNAR_CLIENT_DEFAULT_SUFFIX
                : suffix.trim();
    }

    public boolean isDpiFragmentTlsHello() { return dpiFragmentTlsHello; }
    public void setDpiFragmentTlsHello(boolean enabled) { this.dpiFragmentTlsHello = enabled; }

    public AccentColor getAccentColor() { return accentColor; }
    public void setAccentColor(AccentColor color) { this.accentColor = color != null ? color : AccentColor.CYAN; }

    public boolean isCompactLayout() { return compactLayout; }
    public void setCompactLayout(boolean compact) { this.compactLayout = compact; }

    public boolean isShowHudIndicator() { return showHudIndicator; }
    public void setShowHudIndicator(boolean show) { this.showHudIndicator = show; }

    public boolean isIsolatePackCache() { return isolatePackCache; }
    public void setIsolatePackCache(boolean isolatePackCache) { this.isolatePackCache = isolatePackCache; }
    
    public boolean isBlockLocalPackUrls() { return blockLocalPackUrls; }
    public void setBlockLocalPackUrls(boolean blockLocalPackUrls) { this.blockLocalPackUrls = blockLocalPackUrls; }

    public boolean isStripModShaders() { return stripModShaders; }
    public void setStripModShaders(boolean stripModShaders) { this.stripModShaders = stripModShaders; }

    public boolean isTranslationProtectionEnabled() { return translationProtection; }
    public void setTranslationProtection(boolean enabled) { this.translationProtection = enabled; }
    
    public boolean isFakeDefaultKeybinds() { return fakeDefaultKeybinds; }
    public void setFakeDefaultKeybinds(boolean enabled) { this.fakeDefaultKeybinds = enabled; }
    
    public boolean isMeteorFix() { return meteorFix; }
    public void setMeteorFix(boolean enabled) { this.meteorFix = enabled; }

    public StripMode getPackStripMode() { return packStripMode; }
    public void setPackStripMode(StripMode mode) { this.packStripMode = mode != null ? mode : StripMode.MANUAL; }
    
    public boolean isShowAlerts() { return showAlerts; }
    public void setShowAlerts(boolean showAlerts) { this.showAlerts = showAlerts; }
    
    public boolean isShowToasts() { return showToasts; }
    public void setShowToasts(boolean showToasts) { this.showToasts = showToasts; }
    
    public boolean isLogDetections() { return logDetections; }
    public void setLogDetections(boolean logDetections) { this.logDetections = logDetections; }

    public boolean isDebugAlerts() { return debugAlerts; }
    public void setDebugAlerts(boolean debugAlerts) { this.debugAlerts = debugAlerts; }

    public boolean isDebugCommand() { return debugCommand; }
    public void setDebugCommand(boolean debugCommand) { this.debugCommand = debugCommand; }

    // Signing mode methods
    public SigningMode getSigningMode() { return signingMode; }
    public void setSigningMode(SigningMode mode) { this.signingMode = mode; }

    public boolean shouldNotSign() {
        return signingMode == SigningMode.OFF;
    }

    public boolean isDisableTelemetry() { return disableTelemetry; }
    public void setDisableTelemetry(boolean disableTelemetry) { this.disableTelemetry = disableTelemetry; }

    public boolean isGuardChatLinks() { return guardChatLinks; }
    public void setGuardChatLinks(boolean guardChatLinks) { this.guardChatLinks = guardChatLinks; }

    public boolean isBlockCookies() { return blockCookies; }
    public void setBlockCookies(boolean blockCookies) { this.blockCookies = blockCookies; }

    public boolean isNormalizeClientInfo() { return normalizeClientInfo; }
    public void setNormalizeClientInfo(boolean normalizeClientInfo) { this.normalizeClientInfo = normalizeClientInfo; }

    public boolean isLogScrubberEnabled() { return logScrubberEnabled; }
    public void setLogScrubberEnabled(boolean logScrubberEnabled) { this.logScrubberEnabled = logScrubberEnabled; }

    public boolean isLazyServerPing() { return lazyServerPing; }
    public void setLazyServerPing(boolean lazyServerPing) { this.lazyServerPing = lazyServerPing; }

    public boolean isCloudflarePingRelayEnabled() { return cloudflarePingRelayEnabled; }
    public void setCloudflarePingRelayEnabled(boolean enabled) { this.cloudflarePingRelayEnabled = enabled; }

    public String getCloudflarePingRelayUrl() { return cloudflarePingRelayUrl; }
    public void setCloudflarePingRelayUrl(String url) { this.cloudflarePingRelayUrl = url != null ? url.trim() : ""; }

    public boolean isScrubPackHeaders() { return scrubPackHeaders; }
    public void setScrubPackHeaders(boolean scrubPackHeaders) { this.scrubPackHeaders = scrubPackHeaders; }

    public boolean isCommandHistoryGuard() { return commandHistoryGuard; }
    public void setCommandHistoryGuard(boolean commandHistoryGuard) { this.commandHistoryGuard = commandHistoryGuard; }

    public boolean isAutoPurgePackCache() { return autoPurgePackCache; }
    public void setAutoPurgePackCache(boolean autoPurgePackCache) { this.autoPurgePackCache = autoPurgePackCache; }

    public boolean isAutoRandomizeNewAccount() { return autoRandomizeNewAccount; }
    public void setAutoRandomizeNewAccount(boolean autoRandomizeNewAccount) { this.autoRandomizeNewAccount = autoRandomizeNewAccount; }

    public boolean isHalloweenThemeEnabled() { return halloweenThemeEnabled; }
    public void setHalloweenThemeEnabled(boolean halloweenThemeEnabled) { this.halloweenThemeEnabled = halloweenThemeEnabled; }

    public boolean isChristmasThemeEnabled() { return christmasThemeEnabled; }
    public void setChristmasThemeEnabled(boolean christmasThemeEnabled) { this.christmasThemeEnabled = christmasThemeEnabled; }

    public WallpaperMode getWallpaperMode() { return wallpaperMode; }
    public void setWallpaperMode(WallpaperMode wallpaperMode) { this.wallpaperMode = wallpaperMode != null ? wallpaperMode : WallpaperMode.AUTO; }

    /** Resolves {@link #wallpaperMode} to an actual season to render, applying AUTO's calendar check. */
    public aurick.opsec.mod.util.SeasonalTheme.Season getActiveWallpaperSeason() {
        return switch (wallpaperMode) {
            case OFF -> aurick.opsec.mod.util.SeasonalTheme.Season.NONE;
            case HALLOWEEN -> aurick.opsec.mod.util.SeasonalTheme.Season.HALLOWEEN;
            case CHRISTMAS -> aurick.opsec.mod.util.SeasonalTheme.Season.CHRISTMAS;
            case AUTO -> aurick.opsec.mod.util.SeasonalTheme.currentSeason(true, true);
        };
    }

    /**
     * {@link #getAccentColor()}, unless a seasonal theme is currently active and enabled —
     * used for actual rendering (HUD, multiplayer button); the settings widget itself still
     * reads {@link #getAccentColor()} directly so it shows the user's real stored preference.
     */
    public AccentColor getEffectiveAccentColor() {
        aurick.opsec.mod.util.SeasonalTheme.Season season =
                aurick.opsec.mod.util.SeasonalTheme.currentSeason(halloweenThemeEnabled, christmasThemeEnabled);
        return switch (season) {
            case HALLOWEEN -> AccentColor.ORANGE;
            case CHRISTMAS -> AccentColor.RED;
            case NONE -> accentColor;
        };
    }

    public boolean isConfirmTransfer() { return confirmTransfer; }
    public void setConfirmTransfer(boolean confirmTransfer) { this.confirmTransfer = confirmTransfer; }

    public boolean isIntegrityCheckEnabled() { return integrityCheckEnabled; }
    public void setIntegrityCheckEnabled(boolean integrityCheckEnabled) { this.integrityCheckEnabled = integrityCheckEnabled; }

    public GameProxyType getGameProxyType() { return gameProxyType; }
    public void setGameProxyType(GameProxyType type) { this.gameProxyType = type != null ? type : GameProxyType.NONE; }

    public String getGameProxyHost() { return gameProxyHost; }
    public void setGameProxyHost(String host) { this.gameProxyHost = host != null ? host.trim() : ""; }

    public int getGameProxyPort() { return gameProxyPort; }
    public void setGameProxyPort(int port) { this.gameProxyPort = port; }

    public String getGameProxyUsername() { return gameProxyUsername; }
    public void setGameProxyUsername(String username) { this.gameProxyUsername = username != null ? username : ""; }

    public String getGameProxyPassword() { return gameProxyPassword; }
    public void setGameProxyPassword(String password) { this.gameProxyPassword = password != null ? password : ""; }

    public boolean isStreamerModeEnabled() { return streamerModeEnabled; }
    public void setStreamerModeEnabled(boolean enabled) { this.streamerModeEnabled = enabled; }

    public boolean isStreamerHideCoordinates() { return streamerHideCoordinates; }
    public void setStreamerHideCoordinates(boolean enabled) { this.streamerHideCoordinates = enabled; }

    public boolean isStreamerMaskPlayerNames() { return streamerMaskPlayerNames; }
    public void setStreamerMaskPlayerNames(boolean enabled) { this.streamerMaskPlayerNames = enabled; }

    public NameMaskStyle getStreamerNameMaskStyle() { return streamerNameMaskStyle; }
    public void setStreamerNameMaskStyle(NameMaskStyle style) { this.streamerNameMaskStyle = style != null ? style : NameMaskStyle.GLYPHS; }

    public boolean isStreamerHideServerAddress() { return streamerHideServerAddress; }
    public void setStreamerHideServerAddress(boolean enabled) { this.streamerHideServerAddress = enabled; }


    public int[] getButtonPosition() {
        if (buttonX < 0 || buttonY < 0) return null;
        return new int[] { buttonX, buttonY };
    }
    
    public void setButtonPosition(int x, int y) {
        this.buttonX = x;
        this.buttonY = y;
    }
    
    // Alert hint methods
    public boolean isAlertHintShown() { return alertHintShown; }
    public void setAlertHintShown(boolean shown) { this.alertHintShown = shown; }

    // Whitelist methods
    public WhitelistMode getWhitelistMode() { return whitelistMode; }
    public void setWhitelistMode(WhitelistMode mode) { this.whitelistMode = mode; }

    /** Convenience: returns true when whitelist is active (AUTO or ON) */
    public boolean isWhitelistEnabled() { return whitelistMode != WhitelistMode.OFF; }

    public Set<String> getWhitelistedMods() { return whitelistedMods; }
    public void setWhitelistedMods(Set<String> mods) { this.whitelistedMods = mods != null ? mods : new HashSet<>(); }

    /** Returns true only in ON mode when mod is in the manual set */
    public boolean isModWhitelisted(String modId) {
        return whitelistMode == WhitelistMode.CUSTOM && modId != null && whitelistedMods.contains(modId);
    }

    // Update notification methods
    public String getSkippedUpdateVersion() { return skippedUpdateVersion; }
    public void setSkippedUpdateVersion(String version) { this.skippedUpdateVersion = version != null ? version : ""; }
    public boolean isVersionSkipped(String version) { return version != null && version.equals(skippedUpdateVersion); }

    public boolean isTamperWarningDismissed() { return tamperWarningDismissed; }
    public void setTamperWarningDismissed(boolean dismissed) { this.tamperWarningDismissed = dismissed; }

    public String getEffectiveBrand() {
        if (!spoofAsVanilla) return FABRIC;
        return switch (brandOverride) {
            case LUNAR_CLIENT -> OpsecConstants.Brands.LUNAR_CLIENT_PREFIX + lunarVersionSuffix;
            case BADLION -> OpsecConstants.Brands.BADLION_CLIENT;
            case DEFAULT -> VANILLA;
        };
    }

    public boolean isVanillaMode() {
        return spoofAsVanilla;
    }

    public boolean isFabricMode() {
        return !spoofAsVanilla;
    }
    
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("spoofAsVanilla", spoofAsVanilla);
        json.addProperty("brandOverride", brandOverride.name());
        json.addProperty("lunarVersionSuffix", lunarVersionSuffix);
        json.addProperty("dpiFragmentTlsHello", dpiFragmentTlsHello);
        json.addProperty("accentColor", accentColor.name());
        json.addProperty("compactLayout", compactLayout);
        json.addProperty("showHudIndicator", showHudIndicator);
        json.addProperty("isolatePackCache", isolatePackCache);
        json.addProperty("blockLocalPackUrls", blockLocalPackUrls);
        json.addProperty("stripModShaders", stripModShaders);
        json.addProperty("translationProtection", translationProtection);
        json.addProperty("fakeDefaultKeybinds", fakeDefaultKeybinds);
        json.addProperty("meteorFix", meteorFix);
        json.addProperty("packStripMode", packStripMode.name());
        json.addProperty("showAlerts", showAlerts);
        json.addProperty("showToasts", showToasts);
        json.addProperty("logDetections", logDetections);
        json.addProperty("debugAlerts", debugAlerts);
        json.addProperty("debugCommand", debugCommand);
        json.addProperty("signingMode", signingMode.name());
        json.addProperty("disableTelemetry", disableTelemetry);
        json.addProperty("guardChatLinks", guardChatLinks);
        json.addProperty("blockCookies", blockCookies);
        json.addProperty("normalizeClientInfo", normalizeClientInfo);
        json.addProperty("logScrubberEnabled", logScrubberEnabled);
        json.addProperty("lazyServerPing", lazyServerPing);
        json.addProperty("cloudflarePingRelayEnabled", cloudflarePingRelayEnabled);
        json.addProperty("cloudflarePingRelayUrl", cloudflarePingRelayUrl);
        json.addProperty("scrubPackHeaders", scrubPackHeaders);
        json.addProperty("commandHistoryGuard", commandHistoryGuard);
        json.addProperty("autoPurgePackCache", autoPurgePackCache);
        json.addProperty("autoRandomizeNewAccount", autoRandomizeNewAccount);
        json.addProperty("halloweenThemeEnabled", halloweenThemeEnabled);
        json.addProperty("christmasThemeEnabled", christmasThemeEnabled);
        json.addProperty("wallpaperMode", wallpaperMode.name());
        json.addProperty("confirmTransfer", confirmTransfer);
        json.addProperty("integrityCheckEnabled", integrityCheckEnabled);
        json.addProperty("gameProxyType", gameProxyType.name());
        json.addProperty("gameProxyHost", gameProxyHost);
        json.addProperty("gameProxyPort", gameProxyPort);
        json.addProperty("gameProxyUsername", gameProxyUsername);
        json.addProperty("gameProxyPassword", AccountCrypto.encrypt(gameProxyPassword));
        json.addProperty("streamerModeEnabled", streamerModeEnabled);
        json.addProperty("streamerHideCoordinates", streamerHideCoordinates);
        json.addProperty("streamerMaskPlayerNames", streamerMaskPlayerNames);
        json.addProperty("streamerNameMaskStyle", streamerNameMaskStyle.name());
        json.addProperty("streamerHideServerAddress", streamerHideServerAddress);
        json.addProperty("buttonX", buttonX);
        json.addProperty("buttonY", buttonY);
        json.addProperty("skippedUpdateVersion", skippedUpdateVersion);
        json.addProperty("tamperWarningDismissed", tamperWarningDismissed);
        json.addProperty("alertHintShown", alertHintShown);

        // Whitelist settings
        json.addProperty("whitelistMode", whitelistMode.name());
        if (previousWhitelistMode != null) {
            json.addProperty("previousWhitelistMode", previousWhitelistMode.name());
        }
        JsonArray modsArray = new JsonArray();
        for (String modId : whitelistedMods) {
            modsArray.add(modId);
        }
        json.add("whitelistedMods", modsArray);

        return json;
    }
    
    public static SpoofSettings fromJson(JsonObject json) {
        SpoofSettings s = new SpoofSettings();
        if (json.has("spoofAsVanilla")) {
            s.spoofAsVanilla = json.get("spoofAsVanilla").getAsBoolean();
        } else if (json.has("spoofBrand")) {
            // Legacy migration: old spoofBrand + customBrand collapse into a single toggle.
            // spoofAsVanilla=true iff the old config was actively spoofing AND spoofing to vanilla.
            boolean sb = json.get("spoofBrand").getAsBoolean();
            String cb = json.has("customBrand") ? json.get("customBrand").getAsString() : FABRIC;
            s.spoofAsVanilla = sb && VANILLA.equalsIgnoreCase(cb);
        }
        if (json.has("brandOverride")) {
            try {
                s.brandOverride = BrandOverride.valueOf(json.get("brandOverride").getAsString());
            } catch (IllegalArgumentException e) {
                s.brandOverride = BrandOverride.DEFAULT;
            }
        }
        if (json.has("lunarVersionSuffix")) s.setLunarVersionSuffix(json.get("lunarVersionSuffix").getAsString());
        if (json.has("dpiFragmentTlsHello")) s.dpiFragmentTlsHello = json.get("dpiFragmentTlsHello").getAsBoolean();
        if (json.has("accentColor")) {
            try {
                s.accentColor = AccentColor.valueOf(json.get("accentColor").getAsString());
            } catch (IllegalArgumentException e) {
                s.accentColor = AccentColor.CYAN;
            }
        }
        if (json.has("compactLayout")) s.compactLayout = json.get("compactLayout").getAsBoolean();
        if (json.has("showHudIndicator")) s.showHudIndicator = json.get("showHudIndicator").getAsBoolean();
        if (json.has("isolatePackCache")) s.isolatePackCache = json.get("isolatePackCache").getAsBoolean();
        if (json.has("blockLocalPackUrls")) s.blockLocalPackUrls = json.get("blockLocalPackUrls").getAsBoolean();
        if (json.has("stripModShaders")) s.stripModShaders = json.get("stripModShaders").getAsBoolean();
        // Legacy support
        if (json.has("spoofLocalPackUrls")) s.blockLocalPackUrls = json.get("spoofLocalPackUrls").getAsBoolean();
        if (json.has("translationProtection")) s.translationProtection = json.get("translationProtection").getAsBoolean();
        // Legacy support
        if (json.has("blockTranslationExploit")) s.translationProtection = json.get("blockTranslationExploit").getAsBoolean();
        if (json.has("fakeDefaultKeybinds")) s.fakeDefaultKeybinds = json.get("fakeDefaultKeybinds").getAsBoolean();
        if (json.has("meteorFix")) s.meteorFix = json.get("meteorFix").getAsBoolean();
        // Bypass Server Pack Requirement (tri-state, with backward-compat migrations)
        if (json.has("packStripMode")) {
            String raw = json.get("packStripMode").getAsString();
            // Legacy OFF/ON enum values from the unreleased staged version.
            s.packStripMode = switch (raw) {
                case "OFF" -> StripMode.MANUAL;
                case "ON"  -> StripMode.ALWAYS_ON;
                default    -> {
                    try {
                        yield StripMode.valueOf(raw);
                    } catch (IllegalArgumentException e) {
                        yield StripMode.MANUAL;
                    }
                }
            };
        } else if (json.has("fakeAcceptResourcePack")) {
            boolean legacyEnabled = json.get("fakeAcceptResourcePack").getAsBoolean();
            boolean legacyOverlay = !json.has("showPackAcceptOverlay")
                    || json.get("showPackAcceptOverlay").getAsBoolean();
            s.packStripMode = !legacyEnabled ? StripMode.MANUAL
                    : (legacyOverlay ? StripMode.ASK : StripMode.ALWAYS_ON);
        }
        if (json.has("showAlerts")) s.showAlerts = json.get("showAlerts").getAsBoolean();
        if (json.has("showToasts")) s.showToasts = json.get("showToasts").getAsBoolean();
        if (json.has("logDetections")) s.logDetections = json.get("logDetections").getAsBoolean();
        if (json.has("debugAlerts")) s.debugAlerts = json.get("debugAlerts").getAsBoolean();
        if (json.has("debugCommand")) s.debugCommand = json.get("debugCommand").getAsBoolean();
        if (json.has("signingMode")) {
            String mode = json.get("signingMode").getAsString();
            // Legacy migrations:
            //   NO_KEY / NO_SIGN  → OFF  (old enum values)
            //   ON_DEMAND         → SIGN (removed: see SigningMode javadoc)
            //   unknown           → SIGN (safe default — chat just works)
            if ("NO_KEY".equals(mode) || "NO_SIGN".equals(mode) || "OFF".equals(mode)) {
                s.signingMode = SigningMode.OFF;
            } else {
                s.signingMode = SigningMode.SIGN;
            }
        }
        if (json.has("disableTelemetry")) s.disableTelemetry = json.get("disableTelemetry").getAsBoolean();
        if (json.has("guardChatLinks")) s.guardChatLinks = json.get("guardChatLinks").getAsBoolean();
        if (json.has("blockCookies")) s.blockCookies = json.get("blockCookies").getAsBoolean();
        if (json.has("normalizeClientInfo")) s.normalizeClientInfo = json.get("normalizeClientInfo").getAsBoolean();
        if (json.has("logScrubberEnabled")) s.logScrubberEnabled = json.get("logScrubberEnabled").getAsBoolean();
        if (json.has("lazyServerPing")) s.lazyServerPing = json.get("lazyServerPing").getAsBoolean();
        if (json.has("cloudflarePingRelayEnabled")) s.cloudflarePingRelayEnabled = json.get("cloudflarePingRelayEnabled").getAsBoolean();
        if (json.has("cloudflarePingRelayUrl")) s.cloudflarePingRelayUrl = json.get("cloudflarePingRelayUrl").getAsString();
        if (json.has("scrubPackHeaders")) s.scrubPackHeaders = json.get("scrubPackHeaders").getAsBoolean();
        if (json.has("commandHistoryGuard")) s.commandHistoryGuard = json.get("commandHistoryGuard").getAsBoolean();
        if (json.has("autoPurgePackCache")) s.autoPurgePackCache = json.get("autoPurgePackCache").getAsBoolean();
        if (json.has("autoRandomizeNewAccount")) s.autoRandomizeNewAccount = json.get("autoRandomizeNewAccount").getAsBoolean();
        if (json.has("halloweenThemeEnabled")) s.halloweenThemeEnabled = json.get("halloweenThemeEnabled").getAsBoolean();
        if (json.has("christmasThemeEnabled")) s.christmasThemeEnabled = json.get("christmasThemeEnabled").getAsBoolean();
        if (json.has("wallpaperMode")) {
            try {
                s.wallpaperMode = WallpaperMode.valueOf(json.get("wallpaperMode").getAsString());
            } catch (IllegalArgumentException e) {
                s.wallpaperMode = WallpaperMode.AUTO;
            }
        }
        if (json.has("confirmTransfer")) s.confirmTransfer = json.get("confirmTransfer").getAsBoolean();
        if (json.has("integrityCheckEnabled")) s.integrityCheckEnabled = json.get("integrityCheckEnabled").getAsBoolean();
        if (json.has("gameProxyType")) {
            try {
                s.gameProxyType = GameProxyType.valueOf(json.get("gameProxyType").getAsString());
            } catch (IllegalArgumentException e) {
                s.gameProxyType = GameProxyType.NONE;
            }
        }
        if (json.has("gameProxyHost")) s.gameProxyHost = json.get("gameProxyHost").getAsString();
        if (json.has("gameProxyPort")) s.gameProxyPort = json.get("gameProxyPort").getAsInt();
        if (json.has("gameProxyUsername")) s.gameProxyUsername = json.get("gameProxyUsername").getAsString();
        if (json.has("gameProxyPassword")) s.gameProxyPassword = AccountCrypto.decrypt(json.get("gameProxyPassword").getAsString());
        if (json.has("streamerModeEnabled")) s.streamerModeEnabled = json.get("streamerModeEnabled").getAsBoolean();
        if (json.has("streamerHideCoordinates")) s.streamerHideCoordinates = json.get("streamerHideCoordinates").getAsBoolean();
        if (json.has("streamerMaskPlayerNames")) s.streamerMaskPlayerNames = json.get("streamerMaskPlayerNames").getAsBoolean();
        if (json.has("streamerNameMaskStyle")) {
            try {
                s.streamerNameMaskStyle = NameMaskStyle.valueOf(json.get("streamerNameMaskStyle").getAsString());
            } catch (IllegalArgumentException e) {
                s.streamerNameMaskStyle = NameMaskStyle.GLYPHS;
            }
        }
        if (json.has("streamerHideServerAddress")) s.streamerHideServerAddress = json.get("streamerHideServerAddress").getAsBoolean();
        if (json.has("buttonX")) s.buttonX = json.get("buttonX").getAsInt();
        if (json.has("buttonY")) s.buttonY = json.get("buttonY").getAsInt();
        if (json.has("skippedUpdateVersion")) s.skippedUpdateVersion = json.get("skippedUpdateVersion").getAsString();
        if (json.has("tamperWarningDismissed")) s.tamperWarningDismissed = json.get("tamperWarningDismissed").getAsBoolean();
        if (json.has("alertHintShown")) s.alertHintShown = json.get("alertHintShown").getAsBoolean();

        // Whitelist settings (new tri-state, with backward compat for old boolean)
        if (json.has("whitelistMode")) {
            String modeStr = json.get("whitelistMode").getAsString();
            if ("ON".equals(modeStr)) modeStr = "CUSTOM";
            try {
                s.whitelistMode = WhitelistMode.valueOf(modeStr);
            } catch (IllegalArgumentException e) {
                s.whitelistMode = WhitelistMode.OFF;
            }
        } else if (json.has("whitelistEnabled")) {
            s.whitelistMode = json.get("whitelistEnabled").getAsBoolean() ? WhitelistMode.CUSTOM : WhitelistMode.OFF;
        }
        if (json.has("previousWhitelistMode")) {
            String prevStr = json.get("previousWhitelistMode").getAsString();
            try {
                WhitelistMode prev = WhitelistMode.valueOf(prevStr);
                // OFF is never a meaningful "previous" (it's the override state) — drop it.
                if (prev != WhitelistMode.OFF) {
                    s.previousWhitelistMode = prev;
                }
            } catch (IllegalArgumentException ignored) {
                // unknown enum value — leave null
            }
        }
        if (json.has("whitelistedMods")) {
            JsonArray modsArray = json.getAsJsonArray("whitelistedMods");
            s.whitelistedMods = new HashSet<>();
            for (int i = 0; i < modsArray.size(); i++) {
                s.whitelistedMods.add(modsArray.get(i).getAsString());
            }
        }

        return s;
    }
    
    public void copyFrom(SpoofSettings other) {
        this.spoofAsVanilla = other.spoofAsVanilla;
        this.brandOverride = other.brandOverride;
        this.lunarVersionSuffix = other.lunarVersionSuffix;
        this.dpiFragmentTlsHello = other.dpiFragmentTlsHello;
        this.accentColor = other.accentColor;
        this.compactLayout = other.compactLayout;
        this.showHudIndicator = other.showHudIndicator;
        this.isolatePackCache = other.isolatePackCache;
        this.blockLocalPackUrls = other.blockLocalPackUrls;
        this.stripModShaders = other.stripModShaders;
        this.translationProtection = other.translationProtection;
        this.fakeDefaultKeybinds = other.fakeDefaultKeybinds;
        this.meteorFix = other.meteorFix;
        this.packStripMode = other.packStripMode;
        this.showAlerts = other.showAlerts;
        this.showToasts = other.showToasts;
        this.logDetections = other.logDetections;
        this.debugAlerts = other.debugAlerts;
        this.debugCommand = other.debugCommand;
        this.signingMode = other.signingMode;
        this.disableTelemetry = other.disableTelemetry;
        this.guardChatLinks = other.guardChatLinks;
        this.blockCookies = other.blockCookies;
        this.normalizeClientInfo = other.normalizeClientInfo;
        this.logScrubberEnabled = other.logScrubberEnabled;
        this.lazyServerPing = other.lazyServerPing;
        this.cloudflarePingRelayEnabled = other.cloudflarePingRelayEnabled;
        this.cloudflarePingRelayUrl = other.cloudflarePingRelayUrl;
        this.scrubPackHeaders = other.scrubPackHeaders;
        this.commandHistoryGuard = other.commandHistoryGuard;
        this.autoPurgePackCache = other.autoPurgePackCache;
        this.autoRandomizeNewAccount = other.autoRandomizeNewAccount;
        this.halloweenThemeEnabled = other.halloweenThemeEnabled;
        this.christmasThemeEnabled = other.christmasThemeEnabled;
        this.wallpaperMode = other.wallpaperMode;
        this.confirmTransfer = other.confirmTransfer;
        this.integrityCheckEnabled = other.integrityCheckEnabled;
        this.streamerModeEnabled = other.streamerModeEnabled;
        this.streamerHideCoordinates = other.streamerHideCoordinates;
        this.streamerMaskPlayerNames = other.streamerMaskPlayerNames;
        this.streamerNameMaskStyle = other.streamerNameMaskStyle;
        this.streamerHideServerAddress = other.streamerHideServerAddress;
        this.gameProxyType = other.gameProxyType;
        this.gameProxyHost = other.gameProxyHost;
        this.gameProxyPort = other.gameProxyPort;
        this.gameProxyUsername = other.gameProxyUsername;
        this.gameProxyPassword = other.gameProxyPassword;
        this.buttonX = other.buttonX;
        this.buttonY = other.buttonY;
        this.skippedUpdateVersion = other.skippedUpdateVersion;
        this.tamperWarningDismissed = other.tamperWarningDismissed;
        this.alertHintShown = other.alertHintShown;
        this.whitelistMode = other.whitelistMode;
        this.previousWhitelistMode = other.previousWhitelistMode;
        this.whitelistedMods = new HashSet<>(other.whitelistedMods);
    }
}

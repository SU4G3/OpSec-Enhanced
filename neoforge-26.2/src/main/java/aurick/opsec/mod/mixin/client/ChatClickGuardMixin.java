package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <1.21.6 {
/*import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

/**
 * Requires confirmation before a clicked text component copies to the clipboard or runs a
 * client command. Vanilla executes {@code RUN_COMMAND} and {@code COPY_TO_CLIPBOARD} the
 * instant they're clicked with no prompt — unlike {@code OPEN_URL}, which already confirms —
 * so a crafted chat message, sign, or book can silently overwrite the clipboard (e.g. swap in
 * a scam payment address) or fire a command click the moment the player clicks what looks like
 * ordinary text.
 *
 * <p>Newer vanilla ({@code isAllowedFromServer}, added ~1.20.5) already blocks these two
 * actions from server-authored sign/book NBT specifically. This guard is broader: it covers
 * every click path (including chat) and every supported version, including the pre-1.20.5
 * clients that predate that vanilla filter entirely.</p>
 *
 * <p>Also adds one extra confirmation in front of {@code OPEN_URL} links whose host looks
 * like a phishing/IP-grabber pattern (raw IP literal, punycode, or a non-ASCII homograph
 * domain) — see {@link #opsec$isSuspiciousUrl}. Vanilla's own link confirmation still runs
 * afterward with the real URL, so this only adds a heads-up, never a block.</p>
 *
 * <p>Implementation note: rather than reimplementing vanilla's click handling, this cancels
 * the very first call, shows a confirmation screen, and on "yes" re-invokes the exact same
 * method with a one-shot suppression flag so vanilla's own logic runs unmodified.</p>
 */
//? if >=1.21.6 {
@Mixin(Screen.class)
public abstract class ChatClickGuardMixin {

    @Unique
    private static volatile boolean opsec$suppressGuard = false;

    @Inject(method = "defaultHandleClickEvent", at = @At("HEAD"), cancellable = true)
    private static void opsec$guardClick(ClickEvent event, Minecraft minecraft, Screen screen, CallbackInfo ci) {
        if (opsec$suppressGuard || event == null) return;
        if (!OpsecConfig.getInstance().shouldGuardChatLinks()) return;
        if (!opsec$isGuarded(event)) return;

        ci.cancel();
        opsec$showConfirm(minecraft, screen, opsec$describe(event), () -> {
            opsec$suppressGuard = true;
            try {
                defaultHandleClickEvent(event, minecraft, screen);
            } finally {
                opsec$suppressGuard = false;
            }
        });
    }

    @Inject(method = "defaultHandleGameClickEvent", at = @At("HEAD"), cancellable = true)
    private static void opsec$guardGameClick(ClickEvent event, Minecraft minecraft, Screen screen, CallbackInfo ci) {
        if (opsec$suppressGuard || event == null) return;
        if (!OpsecConfig.getInstance().shouldGuardChatLinks()) return;
        if (!opsec$isGuarded(event)) return;

        ci.cancel();
        opsec$showConfirm(minecraft, screen, opsec$describe(event), () -> {
            opsec$suppressGuard = true;
            try {
                defaultHandleGameClickEvent(event, minecraft, screen);
            } finally {
                opsec$suppressGuard = false;
            }
        });
    }

    @Unique
    private static boolean opsec$isGuarded(ClickEvent event) {
        if (event instanceof ClickEvent.RunCommand || event instanceof ClickEvent.CopyToClipboard) return true;
        if (event instanceof ClickEvent.OpenUrl openUrl) return opsec$isSuspiciousUrl(openUrl.uri());
        return false;
    }

    @Unique
    private static Component opsec$describe(ClickEvent event) {
        if (event instanceof ClickEvent.RunCommand runCommand) {
            return OpsecLang.component(OpsecStrings.CHATGUARD_MESSAGE_COMMAND, runCommand.command());
        }
        if (event instanceof ClickEvent.CopyToClipboard copyToClipboard) {
            return OpsecLang.component(OpsecStrings.CHATGUARD_MESSAGE_CLIPBOARD, copyToClipboard.value());
        }
        if (event instanceof ClickEvent.OpenUrl openUrl) {
            return OpsecLang.component(OpsecStrings.CHATGUARD_MESSAGE_SUSPICIOUS_URL, openUrl.uri().toString());
        }
        return Component.empty();
    }

    /**
     * Heuristic-only phishing/IP-grabber flag for a link's host: punycode/IDN (can render as a
     * different domain than it decodes to), a raw IP literal (common in "IP grabber" links —
     * see the README's Hall of Shame reference), or any non-ASCII character (homograph domains
     * mixing look-alike scripts). This never blocks anything — vanilla's own link confirmation
     * still runs afterward with the real URL shown in full; it just adds one extra "are you
     * sure" in front of links shaped like known scam/tracking patterns.
     */
    @Unique
    private static boolean opsec$isSuspiciousUrl(java.net.URI uri) {
        String host = uri.getHost();
        if (host == null) return false;
        String lower = host.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("xn--")) return true;
        if (lower.matches("\\d{1,3}(\\.\\d{1,3}){3}")) return true;
        if (lower.indexOf(':') >= 0) return true; // IPv6 literal
        for (int i = 0; i < host.length(); i++) {
            if (host.charAt(i) > 127) return true;
        }
        return false;
    }

    // References to Screen's own static methods — Mixin resolves these against the real
    // target method at merge time; the bodies here are never executed.
    @Shadow
    private static void defaultHandleClickEvent(ClickEvent event, Minecraft minecraft, Screen screen) {
        throw new UnsupportedOperationException();
    }

    @Shadow
    private static void defaultHandleGameClickEvent(ClickEvent event, Minecraft minecraft, Screen screen) {
        throw new UnsupportedOperationException();
    }

    @Unique
    private static void opsec$showConfirm(Minecraft minecraft, Screen previous, Component message, Runnable onConfirm) {
        ConfirmScreen confirmScreen = new ConfirmScreen(confirmed -> {
            //? if >=26.2 {
            minecraft.setScreenAndShow(previous);
            //?} else {
            /*minecraft.setScreen(previous);
            *///?}
            if (confirmed) onConfirm.run();
        }, OpsecLang.component(OpsecStrings.CHATGUARD_TITLE), message);
        //? if >=26.2 {
        minecraft.setScreenAndShow(confirmScreen);
        //?} else {
        /*minecraft.setScreen(confirmScreen);
        *///?}
    }
}
//?} else {
/*
@Mixin(Screen.class)
public abstract class ChatClickGuardMixin {

    @Unique
    private static volatile boolean opsec$suppressGuard = false;

    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void opsec$guardClick(Style style, CallbackInfoReturnable<Boolean> cir) {
        if (opsec$suppressGuard || style == null) return;
        if (!OpsecConfig.getInstance().shouldGuardChatLinks()) return;

        ClickEvent event = style.getClickEvent();
        if (event == null) return;
        ClickEvent.Action action = event.getAction();
        if (action != ClickEvent.Action.RUN_COMMAND && action != ClickEvent.Action.COPY_TO_CLIPBOARD) return;

        cir.setReturnValue(true);
        Screen self = (Screen) (Object) this;
        Minecraft minecraft = Minecraft.getInstance();
        Component message = action == ClickEvent.Action.RUN_COMMAND
                ? OpsecLang.component(OpsecStrings.CHATGUARD_MESSAGE_COMMAND, event.getValue())
                : OpsecLang.component(OpsecStrings.CHATGUARD_MESSAGE_CLIPBOARD, event.getValue());

        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(self);
            if (confirmed) {
                opsec$suppressGuard = true;
                try {
                    self.handleComponentClicked(style);
                } finally {
                    opsec$suppressGuard = false;
                }
            }
        }, OpsecLang.component(OpsecStrings.CHATGUARD_TITLE), message));
    }
}
*///?}

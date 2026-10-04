package aurick.opsec.mod.mixin.client;

//? if >=1.21.6 {
import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code DialogScreen.handleDialogClickEvent} special-cases {@code ClickEvent.RunCommand}
 * BEFORE falling through to {@code Screen.defaultHandleClickEvent} — the method
 * {@link ChatClickGuardMixin} guards — so a command wired to a dialog button (MC 1.21.6+
 * server-sent custom UI) currently runs the instant it's clicked, with no confirmation and
 * no preview of the actual command string. Unlike a chat/sign/book link, the player doesn't
 * even see visible link text to eyeball first — just a server-chosen button label (e.g.
 * "Claim Reward" silently running an economy/permission command). Same confirm-and-re-invoke
 * pattern as {@link ChatClickGuardMixin}, scoped to this one bypass.
 */
@Mixin(DialogScreen.class)
public abstract class DialogScreenMixin {

    @Unique
    private static volatile boolean opsec$suppressGuard = false;

    @Inject(method = "handleDialogClickEvent", at = @At("HEAD"), cancellable = true)
    private void opsec$guardDialogCommand(ClickEvent clickEvent, @Nullable Screen previousScreen, CallbackInfo ci) {
        if (opsec$suppressGuard || !(clickEvent instanceof ClickEvent.RunCommand runCommand)) return;
        if (!OpsecConfig.getInstance().shouldGuardChatLinks()) return;

        ci.cancel();
        Screen self = (Screen) (Object) this;
        Minecraft minecraft = Minecraft.getInstance();
        Component message = OpsecLang.component(OpsecStrings.CHATGUARD_MESSAGE_COMMAND, runCommand.command());
        ConfirmScreen confirmScreen = new ConfirmScreen(confirmed -> {
            //? if >=26.2 {
            /*minecraft.setScreenAndShow(self);*/
            //?} else {
            minecraft.setScreen(self);
            //?}
            if (confirmed) {
                opsec$suppressGuard = true;
                try {
                    this.handleDialogClickEvent(clickEvent, previousScreen);
                } finally {
                    opsec$suppressGuard = false;
                }
            }
        }, OpsecLang.component(OpsecStrings.CHATGUARD_TITLE), message);
        //? if >=26.2 {
        /*minecraft.setScreenAndShow(confirmScreen);*/
        //?} else {
        minecraft.setScreen(confirmScreen);
        //?}
    }

    @Shadow
    private void handleDialogClickEvent(ClickEvent clickEvent, @Nullable Screen previousScreen) {
        throw new UnsupportedOperationException();
    }
}
//?} else {
/*public class DialogScreenMixin {}
*///?}

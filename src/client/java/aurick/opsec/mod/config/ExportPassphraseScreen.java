package aurick.opsec.mod.config;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Minimal passphrase prompt reused for both encrypted account export and import.
 * Plain {@link Component#literal} strings — new translation keys aren't added here since
 * the user maintains ru_ru.json by hand; this pilot keeps the string surface it touches small.
 */
public class ExportPassphraseScreen extends Screen {

    private final Screen parent;
    private final boolean allowSkip;
    private final Consumer<char[]> onConfirm;
    private final Runnable onSkip;
    private EditBox passphraseInput;
    private StringWidget statusLabel;

    /**
     * @param allowSkip true for export (plaintext export remains possible), false for import
     *                  of a file that's already encrypted (a passphrase is mandatory there).
     * @param onSkip    only invoked when {@code allowSkip} is true.
     */
    public ExportPassphraseScreen(Screen parent, boolean allowSkip, Consumer<char[]> onConfirm, Runnable onSkip) {
        super(Component.literal(allowSkip ? "Encrypt Export (optional)" : "Enter Passphrase"));
        this.parent = parent;
        this.allowSkip = allowSkip;
        this.onConfirm = onConfirm;
        this.onSkip = onSkip;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int titleWidth = this.font.width(this.title);
        this.addRenderableWidget(new StringWidget(centerX - titleWidth / 2, centerY - 55, titleWidth, 20, this.title, this.font));

        this.passphraseInput = new EditBox(this.font, centerX - 150, centerY - 30, 300, 20,
                Component.literal("Passphrase"));
        this.passphraseInput.setMaxLength(256);
        this.passphraseInput.setHint(Component.literal(
                allowSkip ? "Leave blank to export as plaintext" : "Passphrase used when this was exported"));
        this.addRenderableWidget(this.passphraseInput);

        this.statusLabel = new StringWidget(centerX - 150, centerY + 35, 300, 20, Component.literal(""), this.font);
        this.addRenderableWidget(this.statusLabel);

        this.addRenderableWidget(Button.builder(Component.literal("Confirm"), btn -> confirm())
                .bounds(centerX - 105, centerY + 5, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> this.onClose())
                .bounds(centerX + 5, centerY + 5, 100, 20).build());

        this.setInitialFocus(this.passphraseInput);
    }

    private void confirm() {
        String value = passphraseInput.getValue();
        if (value.isEmpty()) {
            if (allowSkip) {
                onSkip.run();
                return;
            }
            statusLabel.setMessage(Component.literal("Passphrase is required for an encrypted file"));
            return;
        }
        onConfirm.accept(value.toCharArray());
    }

    //? if <1.21.9 {
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257) { // GLFW_KEY_ENTER
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    //?}

    @Override
    public void onClose() {
        //? if >=26.2 {
        /*this.minecraft.setScreenAndShow(parent);*/
        //?} else {
        this.minecraft.setScreen(parent);
        //?}
    }
}

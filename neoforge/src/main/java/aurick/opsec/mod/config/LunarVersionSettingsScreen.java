package aurick.opsec.mod.config;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Lunar Client version-suffix entry for the brand spoof (issue #17) -- see
 * {@link SpoofSettings#getLunarVersionSuffix()} and {@link SpoofSettings#getLunarBrandHistory()}.
 *
 * <p>There's deliberately no "fetch latest" button here: the URL issue #17 suggested
 * ({@code launcherupdates.lunarclientcdn.com/latest.yml}) reports Lunar's Windows
 * *installer* version, not the in-game brand string's version scheme, and Lunar
 * publishes no live endpoint for the latter -- see
 * {@link OpsecConstants.Brands#LUNAR_CLIENT_DEFAULT_SUFFIX}'s javadoc. The history
 * list below is maintainer-curated instead, re-verified by hand each OpSec release.
 */
public class LunarVersionSettingsScreen extends Screen {

    private final Screen parent;
    private final OpsecConfig config;
    private EditBox suffixInput;
    private StringWidget statusLabel;

    public LunarVersionSettingsScreen(Screen parent, OpsecConfig config) {
        super(Component.literal("Lunar Client Version"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int titleWidth = this.font.width(this.title);
        this.addRenderableWidget(new StringWidget(centerX - titleWidth / 2, centerY - 135, titleWidth, 20, this.title, this.font));

        this.addRenderableWidget(new StringWidget(centerX - 150, centerY - 112, 300, 10,
                Component.literal("Version suffix (sent after \"lunarclient:\")"), this.font));
        this.suffixInput = new EditBox(this.font, centerX - 150, centerY - 100, 300, 20, Component.literal("Version suffix"));
        this.suffixInput.setMaxLength(128);
        this.suffixInput.setValue(config.getSettings().getLunarVersionSuffix());
        this.suffixInput.setHint(Component.literal(OpsecConstants.Brands.LUNAR_CLIENT_DEFAULT_SUFFIX));
        this.addRenderableWidget(this.suffixInput);

        this.addRenderableWidget(new StringWidget(centerX - 150, centerY - 72, 300, 10,
                Component.literal("Known versions — click one, then Save"), this.font));

        List<String> history = config.getSettings().getLunarBrandHistory();
        int y = centerY - 58;
        for (int i = 0; i < history.size() && i < 5; i++) {
            String suffix = history.get(i);
            this.addRenderableWidget(Button.builder(Component.literal(suffix), btn -> suffixInput.setValue(suffix))
                    .bounds(centerX - 150, y, 300, 16).build());
            y += 18;
        }

        this.statusLabel = new StringWidget(centerX - 150, centerY + 34, 300, 10, Component.literal(""), this.font);
        this.addRenderableWidget(this.statusLabel);

        this.addRenderableWidget(Button.builder(Component.literal("Save"), btn -> save())
                .bounds(centerX - 105, centerY + 82, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> this.onClose())
                .bounds(centerX + 5, centerY + 82, 100, 20).build());

        this.setInitialFocus(this.suffixInput);
    }

    private void save() {
        String suffix = suffixInput.getValue().trim();
        if (suffix.contains(" ")) {
            statusLabel.setMessage(Component.literal("Version suffix can't contain spaces"));
            return;
        }
        config.getSettings().setLunarVersionSuffix(suffix);
        config.save();
        this.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257) { // GLFW_KEY_ENTER
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}

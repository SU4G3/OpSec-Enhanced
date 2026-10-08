package aurick.opsec.mod.config;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Host/port/credentials entry for the game-connection SOCKS5/HTTP proxy (issue #11).
 * Plain {@link Component#literal} strings, same pilot-stage call as
 * {@link ExportPassphraseScreen} -- no new translation keys for a screen this narrow
 * and optional (the user maintains ru_ru.json by hand).
 */
public class GameProxySettingsScreen extends Screen {

    private final Screen parent;
    private final OpsecConfig config;
    private EditBox hostInput;
    private EditBox portInput;
    private EditBox usernameInput;
    private EditBox passwordInput;
    private StringWidget statusLabel;

    public GameProxySettingsScreen(Screen parent, OpsecConfig config) {
        super(Component.literal("Game Connection Proxy"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        SpoofSettings settings = config.getSettings();

        int titleWidth = this.font.width(this.title);
        this.addRenderableWidget(new StringWidget(centerX - titleWidth / 2, centerY - 85, titleWidth, 20, this.title, this.font));

        this.addRenderableWidget(new StringWidget(centerX - 150, centerY - 62, 300, 10, Component.literal("Proxy host"), this.font));
        this.hostInput = new EditBox(this.font, centerX - 150, centerY - 50, 300, 20, Component.literal("Host"));
        this.hostInput.setMaxLength(256);
        this.hostInput.setValue(settings.getGameProxyHost());
        this.hostInput.setHint(Component.literal("e.g. 127.0.0.1"));
        this.addRenderableWidget(this.hostInput);

        this.addRenderableWidget(new StringWidget(centerX - 150, centerY - 27, 300, 10, Component.literal("Proxy port"), this.font));
        this.portInput = new EditBox(this.font, centerX - 150, centerY - 15, 300, 20, Component.literal("Port"));
        this.portInput.setMaxLength(5);
        this.portInput.setValue(String.valueOf(settings.getGameProxyPort()));
        this.addRenderableWidget(this.portInput);

        this.addRenderableWidget(new StringWidget(centerX - 150, centerY + 8, 300, 10, Component.literal("Username (optional)"), this.font));
        this.usernameInput = new EditBox(this.font, centerX - 150, centerY + 20, 300, 20, Component.literal("Username"));
        this.usernameInput.setMaxLength(256);
        this.usernameInput.setValue(settings.getGameProxyUsername());
        this.addRenderableWidget(this.usernameInput);

        this.addRenderableWidget(new StringWidget(centerX - 150, centerY + 43, 300, 10, Component.literal("Password (optional)"), this.font));
        this.passwordInput = new EditBox(this.font, centerX - 150, centerY + 55, 300, 20, Component.literal("Password"));
        this.passwordInput.setMaxLength(256);
        this.passwordInput.setValue(settings.getGameProxyPassword());
        this.addRenderableWidget(this.passwordInput);

        this.statusLabel = new StringWidget(centerX - 150, centerY + 80, 300, 10, Component.literal(""), this.font);
        this.addRenderableWidget(this.statusLabel);

        this.addRenderableWidget(Button.builder(Component.literal("Save"), btn -> save())
                .bounds(centerX - 105, centerY + 95, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> this.onClose())
                .bounds(centerX + 5, centerY + 95, 100, 20).build());

        this.setInitialFocus(this.hostInput);
    }

    private void save() {
        int port;
        try {
            port = Integer.parseInt(portInput.getValue().trim());
            if (port <= 0 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            statusLabel.setMessage(Component.literal("Port must be a number between 1 and 65535"));
            return;
        }

        SpoofSettings settings = config.getSettings();
        settings.setGameProxyHost(hostInput.getValue());
        settings.setGameProxyPort(port);
        settings.setGameProxyUsername(usernameInput.getValue());
        settings.setGameProxyPassword(passwordInput.getValue());
        config.save();
        this.onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
    }
}

package aurick.opsec.mod.config;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Worker URL entry for the Cloudflare ping relay (issue #15) -- see
 * {@code cloudflare-worker/ping-relay.js} and its setup guide. Same pilot-stage
 * plain-{@link Component#literal} call as {@link ExportPassphraseScreen}.
 */
public class CloudflareRelaySettingsScreen extends Screen {

    private final Screen parent;
    private final OpsecConfig config;
    private EditBox urlInput;
    private StringWidget statusLabel;

    public CloudflareRelaySettingsScreen(Screen parent, OpsecConfig config) {
        super(Component.literal("Cloudflare Ping Relay"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int titleWidth = this.font.width(this.title);
        this.addRenderableWidget(new StringWidget(centerX - titleWidth / 2, centerY - 45, titleWidth, 20, this.title, this.font));

        this.addRenderableWidget(new StringWidget(centerX - 150, centerY - 22, 300, 10, Component.literal("Worker URL"), this.font));
        this.urlInput = new EditBox(this.font, centerX - 150, centerY - 10, 300, 20, Component.literal("Worker URL"));
        this.urlInput.setMaxLength(512);
        this.urlInput.setValue(config.getSettings().getCloudflarePingRelayUrl());
        this.urlInput.setHint(Component.literal("https://your-worker.workers.dev"));
        this.addRenderableWidget(this.urlInput);

        this.statusLabel = new StringWidget(centerX - 150, centerY + 15, 300, 10, Component.literal(""), this.font);
        this.addRenderableWidget(this.statusLabel);

        this.addRenderableWidget(Button.builder(Component.literal("Save"), btn -> save())
                .bounds(centerX - 105, centerY + 30, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> this.onClose())
                .bounds(centerX + 5, centerY + 30, 100, 20).build());

        this.setInitialFocus(this.urlInput);
    }

    private void save() {
        String url = urlInput.getValue().trim();
        if (!url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
            statusLabel.setMessage(Component.literal("URL must start with http:// or https://"));
            return;
        }
        config.getSettings().setCloudflarePingRelayUrl(url);
        config.save();
        this.onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}

package aurick.opsec.mod.config;

import aurick.opsec.mod.proxy.CloudflareWorkerDeployer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Worker URL entry for the Cloudflare ping relay (issue #15) -- see
 * {@code cloudflare-worker/ping-relay.js} and its setup guide. Same pilot-stage
 * plain-{@link Component#literal} call as {@link ExportPassphraseScreen}.
 *
 * <p>Also offers an auto-deploy path: paste a Cloudflare API token (the dashboard's
 * built-in "Edit Cloudflare Workers" template token) and account ID, and the mod
 * deploys the bundled worker to that account via Cloudflare's REST API itself --
 * no Node.js/wrangler CLI needed. Neither value is persisted; they're only used in
 * memory for this one deploy action.</p>
 */
public class CloudflareRelaySettingsScreen extends Screen {

    private final Screen parent;
    private final OpsecConfig config;
    private EditBox apiTokenInput;
    private EditBox accountIdInput;
    private EditBox urlInput;
    private StringWidget deployStatusLabel;
    private StringWidget statusLabel;
    private Button deployButton;
    private boolean deploying = false;

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
        this.addRenderableWidget(new StringWidget(centerX - titleWidth / 2, centerY - 135, titleWidth, 20, this.title, this.font));

        this.addRenderableWidget(new StringWidget(centerX - 150, centerY - 110, 300, 10,
                Component.literal("Auto-deploy (optional) — no CLI needed"), this.font));

        this.addRenderableWidget(new StringWidget(centerX - 150, centerY - 95, 300, 10, Component.literal("Cloudflare API Token"), this.font));
        this.apiTokenInput = new EditBox(this.font, centerX - 150, centerY - 83, 300, 20, Component.literal("API Token"));
        this.apiTokenInput.setMaxLength(256);
        this.apiTokenInput.setHint(Component.literal("\"Edit Cloudflare Workers\" template token"));
        this.addRenderableWidget(this.apiTokenInput);

        this.addRenderableWidget(new StringWidget(centerX - 150, centerY - 60, 300, 10, Component.literal("Account ID"), this.font));
        this.accountIdInput = new EditBox(this.font, centerX - 150, centerY - 48, 300, 20, Component.literal("Account ID"));
        this.accountIdInput.setMaxLength(64);
        this.accountIdInput.setHint(Component.literal("from your dash.cloudflare.com URL"));
        this.addRenderableWidget(this.accountIdInput);

        this.deployButton = Button.builder(Component.literal("Deploy Worker"), btn -> deploy())
                .bounds(centerX - 150, centerY - 24, 300, 20).build();
        this.addRenderableWidget(this.deployButton);

        this.deployStatusLabel = new StringWidget(centerX - 150, centerY - 1, 300, 10, Component.literal(""), this.font);
        this.addRenderableWidget(this.deployStatusLabel);

        this.addRenderableWidget(new StringWidget(centerX - 150, centerY + 15, 300, 10,
                Component.literal("— or paste a worker URL you already deployed —"), this.font));

        this.addRenderableWidget(new StringWidget(centerX - 150, centerY + 30, 300, 10, Component.literal("Worker URL"), this.font));
        this.urlInput = new EditBox(this.font, centerX - 150, centerY + 42, 300, 20, Component.literal("Worker URL"));
        this.urlInput.setMaxLength(512);
        this.urlInput.setValue(config.getSettings().getCloudflarePingRelayUrl());
        this.urlInput.setHint(Component.literal("https://your-worker.workers.dev"));
        this.addRenderableWidget(this.urlInput);

        this.statusLabel = new StringWidget(centerX - 150, centerY + 67, 300, 10, Component.literal(""), this.font);
        this.addRenderableWidget(this.statusLabel);

        this.addRenderableWidget(Button.builder(Component.literal("Save"), btn -> save())
                .bounds(centerX - 105, centerY + 82, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> this.onClose())
                .bounds(centerX + 5, centerY + 82, 100, 20).build());

        this.setInitialFocus(this.apiTokenInput);
    }

    private void deploy() {
        if (deploying) return;
        String token = apiTokenInput.getValue().trim();
        String accountId = accountIdInput.getValue().trim();
        if (token.isEmpty() || accountId.isEmpty()) {
            deployStatusLabel.setMessage(Component.literal("Enter both an API token and an account ID first"));
            return;
        }

        deploying = true;
        deployButton.active = false;
        deployStatusLabel.setMessage(Component.literal("Deploying..."));

        CloudflareWorkerDeployer.deploy(token, accountId).whenComplete((url, error) ->
            Minecraft.getInstance().execute(() -> {
                deploying = false;
                deployButton.active = true;
                if (error != null) {
                    Throwable cause = error.getCause() != null ? error.getCause() : error;
                    deployStatusLabel.setMessage(Component.literal("Failed: " + cause.getMessage()));
                    return;
                }
                urlInput.setValue(url);
                SpoofSettings settings = config.getSettings();
                settings.setCloudflarePingRelayUrl(url);
                settings.setCloudflarePingRelayEnabled(true);
                settings.setLazyServerPing(true);
                config.save();
                deployStatusLabel.setMessage(Component.literal("Deployed! Relay is now enabled."));
            })
        );
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

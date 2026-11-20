package aurick.opsec.mod.config;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.accounts.Account;
import aurick.opsec.mod.accounts.AccountManager;
import aurick.opsec.mod.accounts.SessionAccount;
import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import org.lwjgl.system.MemoryStack;
//? if <26.3 {
import org.lwjgl.PointerBuffer;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
//?}

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The account manager.
 *
 * <p>Fully independent from the rule editor: it never reads or writes a server rule's
 * settings. The one option that is account-scoped rather than server-scoped (auto
 * randomize a new account) is written to the global baseline, which is the ANY rule's
 * settings.</p>
 */
public class AccountManagerScreen extends Screen implements OpsecScreens.Marker {

    // Track which account is currently logging in (null = none)
    private static volatile String loggingInAccountUuid = null;
    // Track if we're currently logging out
    private static volatile boolean isLoggingOut = false;
    // Animation ticks for loading indicators - volatile for thread safety
    private static volatile int animationTicks = 0;

    private final Screen parent;
    private final OpsecConfig config;
    private ScrollableWidgetList body;

    public AccountManagerScreen(Screen parent) {
        super(OpsecLang.component(OpsecStrings.SCREEN_ACCOUNT_MANAGER_TITLE));
        this.parent = parent;
        this.config = OpsecConfig.getInstance();
    }

    public Screen getParent() {
        return parent;
    }

    // Several widgets below poll raw left-mouse-button state (click events don't
    // reach them reliably through the normal listener chain on 1.21.9+ / 26.1+).
    // MC 26.3 switched windowing from GLFW to SDL, dropping the lwjgl-glfw module
    // this used to query with, so this helper absorbs that split.
    private static boolean opsec$isLeftMouseButtonDown() {
        //? if >=26.3 {
        /*return (org.lwjgl.sdl.SDLMouse.SDL_GetMouseState(null, null) & org.lwjgl.sdl.SDLMouse.SDL_BUTTON_LMASK) != 0;*/
        //?} else if >=1.21.9 {
        long windowHandle = Minecraft.getInstance().getWindow().handle();
        return org.lwjgl.glfw.GLFW.glfwGetMouseButton(windowHandle, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        //?} else {
        /*long windowHandle = Minecraft.getInstance().getWindow().getWindow();
        return org.lwjgl.glfw.GLFW.glfwGetMouseButton(windowHandle, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        *///?}
    }

    /** Raw screen-space cursor position (unscaled by GUI scale). */
    private static double[] opsec$getCursorPos() {
        //? if >=26.3 {
        /*try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.FloatBuffer x = stack.mallocFloat(1);
            java.nio.FloatBuffer y = stack.mallocFloat(1);
            org.lwjgl.sdl.SDLMouse.SDL_GetMouseState(x, y);
            return new double[]{x.get(0), y.get(0)};
        }*/
        //?} else if >=1.21.9 {
        long windowHandle = Minecraft.getInstance().getWindow().handle();
        double[] mx = new double[1], my = new double[1];
        org.lwjgl.glfw.GLFW.glfwGetCursorPos(windowHandle, mx, my);
        return new double[]{mx[0], my[0]};
        //?} else {
        /*long windowHandle = Minecraft.getInstance().getWindow().getWindow();
        double[] mx = new double[1], my = new double[1];
        org.lwjgl.glfw.GLFW.glfwGetCursorPos(windowHandle, mx, my);
        return new double[]{mx[0], my[0]};
        *///?}
    }

    @Override
    protected void init() {
        List<AbstractWidget> widgets = new ArrayList<>();
        AccountManager accountManager = AccountManager.getInstance();

        widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.SECTION_ACCOUNTS)));

        String currentUser = Minecraft.getInstance().getUser().getName();
        widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.ACCOUNT_CURRENT, currentUser)));

        widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.SECTION_APPEARANCE_RANDOMIZER)));
        java.util.Optional<SessionAccount> activeSession = accountManager.getAccounts().stream()
                .filter(a -> a instanceof SessionAccount && a.getUsername().equals(currentUser))
                .map(a -> (SessionAccount) a)
                .findFirst();

        if (activeSession.isPresent()) {
            SessionAccount active = activeSession.get();

            Button randomizeSkinButton = Button.builder(OpsecLang.component(OpsecStrings.BUTTON_RANDOMIZE_SKIN), button -> {
                button.active = false;
                CompletableFuture.supplyAsync(active::randomizeSkin).whenComplete((success, error) ->
                        Minecraft.getInstance().execute(this::refreshScreen));
            }).bounds(0, 0, 230, 20)
              .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_RANDOMIZE_SKIN_TOOLTIP)))
              .build();
            widgets.add(randomizeSkinButton);

            Button randomizeCapeButton = Button.builder(OpsecLang.component(OpsecStrings.BUTTON_RANDOMIZE_CAPE), button -> {
                button.active = false;
                CompletableFuture.supplyAsync(active::randomizeCape).whenComplete((success, error) ->
                        Minecraft.getInstance().execute(this::refreshScreen));
            }).bounds(0, 0, 230, 20)
              .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_RANDOMIZE_CAPE_TOOLTIP)))
              .build();
            randomizeCapeButton.active = !active.getOwnedCapes().isEmpty();
            widgets.add(randomizeCapeButton);

            if (active.getLastError() != null && !active.getLastError().isEmpty()) {
                widgets.add(createSectionHeader("§c" + active.getLastError()));
            }
        } else {
            widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.APPEARANCE_RANDOMIZER_UNAVAILABLE)));
        }

        if (!accountManager.getAccounts().isEmpty()) {
            widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.SECTION_SAVED_ACCOUNTS)));

            for (Account account : accountManager.getAccounts()) {
                String displayName = account.getUsername() + (account.isCracked() ? " §7(offline)" : "");
                boolean isLoggedIn = currentUser.equals(account.getUsername());
                boolean isValid = account.isValid();
                boolean isLoggingIn = account.getUuid() != null && account.getUuid().equals(loggingInAccountUuid);

                String buttonText;
                if (isLoggingIn) {
                    String dots = ".".repeat((animationTicks / 5) % 4);
                    buttonText = "§e" + displayName + " §7(logging in" + dots + ")";
                } else if (isLoggedIn && isLoggingOut) {
                    String dots = ".".repeat((animationTicks / 5) % 4);
                    buttonText = "§e" + displayName + " §7(logging out" + dots + ")";
                } else if (!isValid) {
                    buttonText = "§c" + displayName + " §8(invalid)";
                } else if (isLoggedIn) {
                    buttonText = "§a" + displayName;
                } else {
                    buttonText = displayName;
                }

                String refreshInfo = (account instanceof SessionAccount sa && sa.hasRefreshToken()) ? "\n§2Auto-refresh enabled" : "";
                String tooltip;
                if (!isValid) {
                    String errorMsg = account.getLastError();
                    if (errorMsg != null && !errorMsg.isEmpty()) {
                        tooltip = "§c" + errorMsg + "\n§7Click to try login anyway" + refreshInfo;
                    } else {
                        tooltip = "§cToken expired or invalid\n§7Click to try login anyway" + refreshInfo;
                    }
                } else if (isLoggedIn) {
                    tooltip = "Currently logged in\nClick to logout to original account" + refreshInfo;
                } else {
                    String errorMsg = account.getLastError();
                    if (errorMsg != null && !errorMsg.isEmpty()) {
                        tooltip = "§cLast error: " + errorMsg + "\n§7Click to login as " + displayName + refreshInfo;
                    } else {
                        tooltip = "Click to login as " + displayName + refreshInfo;
                    }
                }

                widgets.add(new AccountRowWidget(
                        buttonText,
                        tooltip,
                        isLoggedIn,
                        () -> {
                            if (isLoggingIn || isLoggingOut) {
                                return;
                            }
                            if (isLoggedIn) {
                                isLoggingOut = true;
                                Opsec.LOGGER.info("[OpSec] Logging out of account: {}", account.getUsername());
                                CompletableFuture.runAsync(() -> {
                                    accountManager.logout();
                                }).whenComplete((result, error) -> {
                                    Minecraft.getInstance().execute(() -> {
                                        isLoggingOut = false;
                                        refreshScreen();
                                    });
                                });
                            } else {
                                loggingInAccountUuid = account.getUuid();
                                Opsec.LOGGER.info("[OpSec] Logging into account: {}", account.getUsername());
                                CompletableFuture.runAsync(() -> {
                                    boolean success = account.login();
                                    if (success) {
                                        accountManager.setActiveAccountUuid(account.getUuid());
                                    }
                                }).whenComplete((result, error) -> {
                                    Minecraft.getInstance().execute(() -> {
                                        loggingInAccountUuid = null;
                                        refreshScreen();
                                    });
                                });
                            }
                        },
                        () -> {
                            if (isLoggingIn || isLoggingOut) {
                                return;
                            }
                            if (isLoggedIn) {
                                isLoggingOut = true;
                                CompletableFuture.runAsync(() -> {
                                    accountManager.logout();
                                }).whenComplete((result, error) -> {
                                    Minecraft.getInstance().execute(() -> {
                                        isLoggingOut = false;
                                        accountManager.remove(account);
                                        refreshScreen();
                                    });
                                });
                            } else {
                                accountManager.remove(account);
                                refreshScreen();
                            }
                        }
                ));
            }

            Button refreshButton = Button.builder(OpsecLang.component(
                    accountManager.isRefreshing() ? OpsecStrings.BUTTON_REFRESHING : OpsecStrings.BUTTON_REFRESH_ALL
            ), button -> {
                if (accountManager.isRefreshing()) {
                    return;
                }
                button.setMessage(OpsecLang.component(OpsecStrings.BUTTON_REFRESHING));
                button.active = false;
                accountManager.refreshAllAccounts((valid, invalid) -> {
                    refreshScreen();
                });
            }).size(230, 20)
              .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_REFRESH_TOOLTIP)))
              .build();

            if (accountManager.isRefreshing()) {
                refreshButton.active = false;
            }

            widgets.add(refreshButton);
        }

        widgets.add(createSectionHeader(OpsecLang.tr(OpsecStrings.SECTION_ADD_ACCOUNT)));

        // Auto-randomize is account-scoped behaviour, not server-scoped: it is written
        // to the global baseline (the ANY rule) rather than to any server rule.
        SpoofSettings globalSettings = config.getGlobalSettings();
        Button autoRandomizeButton = Button.builder(autoRandomizeLabel(globalSettings.isAutoRandomizeNewAccount()), button -> {
            boolean next = !globalSettings.isAutoRandomizeNewAccount();
            globalSettings.setAutoRandomizeNewAccount(next);
            config.save();
            button.setMessage(autoRandomizeLabel(next));
        }).size(230, 20)
          .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.TOOLTIP_AUTO_RANDOMIZE_NEW_ACCOUNT)))
          .build();
        widgets.add(autoRandomizeButton);

        widgets.add(Button.builder(OpsecLang.component(OpsecStrings.ACCOUNT_ADD_SESSION), button -> {
            //? if >=26.2 {
            this.minecraft.setScreenAndShow(new AddAccountScreen(this));
            //?} else {
            /*this.minecraft.setScreen(new AddAccountScreen(this));
            *///?}
        }).size(230, 20)
          .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.ACCOUNT_ADD_SESSION_TOOLTIP)))
          .build());

        widgets.add(Button.builder(OpsecLang.component(OpsecStrings.ACCOUNT_ADD_OFFLINE), button -> {
            //? if >=26.2 {
            this.minecraft.setScreenAndShow(new AddCrackedAccountScreen(this));
            //?} else {
            /*this.minecraft.setScreen(new AddCrackedAccountScreen(this));
            *///?}
        }).size(230, 20)
          .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.ACCOUNT_ADD_OFFLINE_TOOLTIP)))
          .build());

        widgets.add(new ImportExportRowWidget(this::openImportDialog, this::openExportDialog));

        int bodyTop = 30;
        int bodyBottom = this.height - 32;
        int bodyHeight = Math.max(40, bodyBottom - bodyTop);
        this.body = new ScrollableWidgetList(this.minecraft, this.width, bodyHeight, bodyTop,
                rowHeight(), widgets);
        this.body.setBounds(0, bodyTop, this.width, bodyHeight);
        this.addRenderableWidget(this.body);

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20)
                .build());
    }

    private static int rowHeight() {
        return OpsecConfig.getInstance().getGlobalSettings().isCompactLayout() ? 21 : 25;
    }

    private static Component autoRandomizeLabel(boolean value) {
        return Component.literal(OpsecLang.tr(OpsecStrings.OPTION_AUTO_RANDOMIZE_NEW_ACCOUNT)
                + ": " + (value ? "§aON" : "§cOFF"));
    }

    private StringWidget createSectionHeader(String text) {
        Component component = Component.literal(text);
        int textWidth = Minecraft.getInstance().font.width(component);
        return new StringWidget(textWidth, 20, component, Minecraft.getInstance().font);
    }

    private void refreshScreen() {
        //? if >=26.2 {
        this.minecraft.setScreenAndShow(new AccountManagerScreen(this.parent));
        //?} else {
        /*this.minecraft.setScreen(new AccountManagerScreen(this.parent));
        *///?}
    }

    @Override
    public void tick() {
        super.tick();
        if (loggingInAccountUuid != null || isLoggingOut) {
            animationTicks++;
            if (animationTicks % 5 == 0) {
                refreshScreen();
            }
        } else {
            animationTicks = 0;
        }
    }

    @Override
    public void onClose() {
        config.save();
        //? if >=26.2 {
        this.minecraft.setScreenAndShow(this.parent);
        //?} else {
        /*this.minecraft.setScreen(this.parent);
        *///?}
    }

    private void openImportDialog() {
        //? if >=26.3 {
        /*// lwjgl-tinyfd (native file picker) doesn't exist on 26.3+ — MC switched
        // its windowing backend from GLFW to SDL there and dropped that module.
        // Hand-rolling raw SDL dialog interop isn't something to ship unverified in a
        // sandbox with no runnable game client, so this reads a fixed, documented path
        // instead — the feature is a convenience, not core protection.
        new Thread(() -> {
            try {
                java.nio.file.Path file = OpsecConfig.getInstance().getConfigDir().resolve("opsec-accounts-import.json");
                if (!Files.exists(file)) {
                    Opsec.LOGGER.warn("[OpSec] No import file found at {} — place the JSON there and try again", file);
                    return;
                }
                String content = Files.readString(file, StandardCharsets.UTF_8);
                finishImport(content, file.toString());
            } catch (Exception e) {
                Opsec.LOGGER.error("[OpSec] Failed to import accounts: {}", e.getMessage());
            }
        }, "OpSec-Import-Thread").start();
        *///?} else {
        new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.json"));
                filters.flip();

                String result = TinyFileDialogs.tinyfd_openFileDialog(
                    "Import Accounts",
                    "",
                    filters,
                    "JSON Files (*.json)",
                    false
                );

                if (result == null) return;

                File file = new File(result);
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                finishImport(content, file.getName());
            } catch (Exception e) {
                Opsec.LOGGER.error("[OpSec] Failed to import accounts: {}", e.getMessage());
            }
        }, "OpSec-Import-Thread").start();
        //?}
    }

    /** Imports {@code content}, prompting for a passphrase first if it's an encrypted export envelope. */
    private void finishImport(String content, String sourceLabel) {
        if (aurick.opsec.mod.accounts.AccountExportCrypto.isEncryptedEnvelope(content)) {
            ExportPassphraseScreen prompt = new ExportPassphraseScreen(
                    this, false,
                    passphrase -> new Thread(() -> {
                        try {
                            int imported = AccountManager.getInstance().importFromJson(content, passphrase);
                            Minecraft.getInstance().execute(() -> {
                                if (imported > 0) {
                                    Opsec.LOGGER.info("[OpSec] Imported {} accounts from {}", imported, sourceLabel);
                                    refreshScreen();
                                }
                            });
                        } catch (Exception e) {
                            Opsec.LOGGER.error("[OpSec] Failed to decrypt import (wrong passphrase?): {}", e.getMessage());
                        }
                    }, "OpSec-Import-Thread").start(),
                    () -> {}
            );
            Minecraft.getInstance().execute(() -> {
                //? if >=26.2 {
                this.minecraft.setScreenAndShow(prompt);
                //?} else {
                /*this.minecraft.setScreen(prompt);
                *///?}
            });
            return;
        }
        int imported = AccountManager.getInstance().importFromJson(content);
        Minecraft.getInstance().execute(() -> {
            if (imported > 0) {
                Opsec.LOGGER.info("[OpSec] Imported {} accounts from {}", imported, sourceLabel);
            }
            refreshScreen();
        });
    }

    private void openExportDialog() {
        ExportPassphraseScreen prompt = new ExportPassphraseScreen(
                this, true,
                passphrase -> writeExportFile(AccountManager.getInstance().exportToJsonEncrypted(passphrase)),
                () -> writeExportFile(AccountManager.getInstance().exportToJson())
        );
        //? if >=26.2 {
        this.minecraft.setScreenAndShow(prompt);
        //?} else {
        /*this.minecraft.setScreen(prompt);
        *///?}
    }

    private void writeExportFile(String json) {
        //? if >=26.3 {
        /*// See openImportDialog() for why this uses a fixed path on 26.3+.
        new Thread(() -> {
            try {
                java.nio.file.Path file = OpsecConfig.getInstance().getConfigDir().resolve("opsec-accounts-export.json");
                Files.writeString(file, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                Opsec.LOGGER.info("[OpSec] Exported accounts to {}", file);
                Minecraft.getInstance().execute(() -> this.minecraft.setScreenAndShow(this));
            } catch (Exception e) {
                Opsec.LOGGER.error("[OpSec] Failed to export accounts: {}", e.getMessage());
            }
        }, "OpSec-Export-Thread").start();
        *///?} else {
        new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.json"));
                filters.flip();

                String result = TinyFileDialogs.tinyfd_saveFileDialog(
                    "Export Accounts",
                    "opsec-accounts-export.json",
                    filters,
                    "JSON Files (*.json)"
                );

                if (result == null) return;

                File file = new File(result);
                if (!file.getName().toLowerCase().endsWith(".json")) {
                    file = new File(file.getParentFile(), file.getName() + ".json");
                }

                Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                Opsec.LOGGER.info("[OpSec] Exported accounts to {}", file.getPath());
                Minecraft.getInstance().execute(() -> {
                    //? if >=26.2 {
                    this.minecraft.setScreenAndShow(this);
                    //?} else {
                    /*this.minecraft.setScreen(this);
                    *///?}
                });
            } catch (Exception e) {
                Opsec.LOGGER.error("[OpSec] Failed to export accounts: {}", e.getMessage());
            }
        }, "OpSec-Export-Thread").start();
        //?}
    }

    // Custom widget for account row (account button + remove button side by side)
    private static class AccountRowWidget extends AbstractWidget {
        private final Button accountButton;
        private final Button removeButton;
        private final Runnable onAccountClick;
        private final Runnable onRemoveClick;
        //? if >=1.21.9 {
        private boolean wasMouseDown = false;
        //?}

        public AccountRowWidget(String accountName, String tooltip, boolean isActive, Runnable onAccountClick, Runnable onRemoveClick) {
            super(0, 0, 230, 20, Component.empty());
            this.onAccountClick = onAccountClick;
            this.onRemoveClick = onRemoveClick;

            this.accountButton = Button.builder(Component.literal(accountName), btn -> onAccountClick.run())
                    .size(150, 20)
                    .tooltip(Tooltip.create(Component.literal(tooltip)))
                    .build();

            this.removeButton = Button.builder(OpsecLang.component(OpsecStrings.BUTTON_REMOVE), btn -> onRemoveClick.run())
                    .size(55, 20)
                    .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_REMOVE_TOOLTIP)))
                    .build();
        }

        @Override
        public void setX(int x) {
            super.setX(x);
            updateButtonPositions();
        }

        @Override
        public void setY(int y) {
            super.setY(y);
            updateButtonPositions();
        }

        private void updateButtonPositions() {
            int totalWidth = 150 + 5 + 55;
            int startX = getX() + (getWidth() - totalWidth) / 2;
            accountButton.setX(startX);
            accountButton.setY(getY());
            removeButton.setX(startX + 150 + 5);
            removeButton.setY(getY());
        }

        //? if >=26.1 {
        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            accountButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
            removeButton.extractRenderState(graphics, mouseX, mouseY, partialTick);

            boolean isMouseDown = opsec$isLeftMouseButtonDown();

            if (!isMouseDown && wasMouseDown) {
                if (accountButton.isMouseOver(mouseX, mouseY)) {
                    onAccountClick.run();
                } else if (removeButton.isMouseOver(mouseX, mouseY)) {
                    onRemoveClick.run();
                }
            }
            wasMouseDown = isMouseDown;
        }
        //?} elif >=1.21.9 {
        /*@Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            accountButton.render(graphics, mouseX, mouseY, partialTick);
            removeButton.render(graphics, mouseX, mouseY, partialTick);

            boolean isMouseDown = opsec$isLeftMouseButtonDown();

            if (!isMouseDown && wasMouseDown) {
                if (accountButton.isMouseOver(mouseX, mouseY)) {
                    onAccountClick.run();
                } else if (removeButton.isMouseOver(mouseX, mouseY)) {
                    onRemoveClick.run();
                }
            }
            wasMouseDown = isMouseDown;
        }*/
        //?} else {
        /*@Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            accountButton.render(graphics, mouseX, mouseY, partialTick);
            removeButton.render(graphics, mouseX, mouseY, partialTick);
        }
        *///?}

        // Forward mouse clicks to child buttons
        //? if <1.21.9 {
        /*@Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                if (accountButton.isMouseOver(mouseX, mouseY)) {
                    onAccountClick.run();
                    return true;
                } else if (removeButton.isMouseOver(mouseX, mouseY)) {
                    onRemoveClick.run();
                    return true;
                }
            }
            return false;
        }
        *///?}

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            accountButton.updateNarration(output);
        }
    }

    // Custom widget for import/export row
    private static class ImportExportRowWidget extends AbstractWidget {
        private final Button importButton;
        private final Button exportButton;
        private final Runnable onImportAction;
        private final Runnable onExportAction;

        public ImportExportRowWidget(Runnable onImport, Runnable onExport) {
            super(0, 0, 230, 20, Component.empty());
            this.onImportAction = onImport;
            this.onExportAction = onExport;

            this.importButton = Button.builder(OpsecLang.component(OpsecStrings.BUTTON_IMPORT), btn -> onImport.run())
                    .size(100, 20)
                    .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_IMPORT_TOOLTIP)))
                    .build();

            this.exportButton = Button.builder(OpsecLang.component(OpsecStrings.BUTTON_EXPORT), btn -> onExport.run())
                    .size(100, 20)
                    .tooltip(Tooltip.create(OpsecLang.component(OpsecStrings.BUTTON_EXPORT_TOOLTIP)))
                    .build();
        }

        @Override
        public void setX(int x) {
            super.setX(x);
            updateButtonPositions();
        }

        @Override
        public void setY(int y) {
            super.setY(y);
            updateButtonPositions();
        }

        private void updateButtonPositions() {
            int totalWidth = 100 + 5 + 100;
            int startX = getX() + (getWidth() - totalWidth) / 2;
            importButton.setX(startX);
            importButton.setY(getY());
            exportButton.setX(startX + 100 + 5);
            exportButton.setY(getY());
        }

        //? if >=26.1 {
        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            importButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
            exportButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
        //?} elif >=1.21.9 {
        /*@Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            importButton.render(graphics, mouseX, mouseY, partialTick);
            exportButton.render(graphics, mouseX, mouseY, partialTick);
        }*/
        //?} else {
        /*@Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            updateButtonPositions();
            importButton.render(graphics, mouseX, mouseY, partialTick);
            exportButton.render(graphics, mouseX, mouseY, partialTick);
        }
        *///?}

        // Forward mouse clicks to child buttons
        //? if <1.21.9 {
        /*@Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                if (importButton.isMouseOver(mouseX, mouseY)) {
                    onImportAction.run();
                    return true;
                } else if (exportButton.isMouseOver(mouseX, mouseY)) {
                    onExportAction.run();
                    return true;
                }
            }
            return false;
        }
        *///?}

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            importButton.updateNarration(output);
        }
    }
}

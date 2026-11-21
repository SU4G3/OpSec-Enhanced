package aurick.opsec.mod.mixin.client;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@link Screen#addRenderableWidget} so widgets can be injected into a screen
 * this mod does not own.
 *
 * <p>Cloth Config builds the option list for the rule editor, but the rule list and the
 * rule actions are our own widgets. {@code addRenderableWidget} is protected on every
 * supported Minecraft version (same descriptor and access modifier from 1.20.1 through
 * 26.3), so no version fence is needed for this accessor.</p>
 */
@Mixin(Screen.class)
public interface ScreenAccessor {

    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T opsec$addRenderableWidget(T widget);
}

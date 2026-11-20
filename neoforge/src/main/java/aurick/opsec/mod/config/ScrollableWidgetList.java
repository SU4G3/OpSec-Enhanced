package aurick.opsec.mod.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

import java.util.List;

/**
 * A vertically scrolling list of plain widgets.
 *
 * <p>Extracted from the old tabbed config screen so that the rule list and the account
 * list can both use it. Each row hosts exactly one widget, which is centred inside the
 * row's horizontal band.</p>
 *
 * <p>On 26.3 the list wraps its children in an entry that dispatches the new
 * event-object signatures ({@code mouseClicked(MouseButtonEvent, boolean)},
 * {@code keyPressed(KeyEvent)}, ...). A custom entry that only implements the pre-1.21.9
 * signatures would forward no events at all and leave every child widget dead, so the
 * entry below implements the new signatures where the version demands them.</p>
 */
public class ScrollableWidgetList extends ContainerObjectSelectionList<ScrollableWidgetList.WidgetEntry> {

    /**
     * @param rowHeight the height of one row; the compact layout uses a smaller one
     */
    public ScrollableWidgetList(Minecraft minecraft, int width, int height, int top, int rowHeight, List<AbstractWidget> widgets) {
        //? if >=1.20.3 {
        super(minecraft, width, height, top, rowHeight);
        //?} else {
        /*// 1.20.1 / 1.20.2: 6-arg constructor including y1 (top + height).
        super(minecraft, width, height, top, top + height, rowHeight);
        *///?}
        this.centerListVertically = false;
        //? if <1.20.2 {
        /*// AbstractSelectionList draws fading dark strips above y0 and below y1
        // by default. Above y0 = tabBottom would obscure the TabNavigationBar's
        // header bar; disable so the header and footer render unobscured.
        this.setRenderTopAndBottom(false);
        *///?}

        for (AbstractWidget widget : widgets) {
            //? if >=1.21.6
            //this.addEntry(new WidgetEntry(widget, this));
            //? if <1.21.6
            this.addEntry(new WidgetEntry(widget));
        }
    }

    //? if <1.21.6 {
    private double trackedScroll = 0;

    @Override
    public void setScrollAmount(double scrollAmount) {
        super.setScrollAmount(scrollAmount);
        this.trackedScroll = scrollAmount;
    }
    //?}

    public double currentScrollAmount() {
        //? if >=1.21.6
        //return this.scrollAmount();
        //? if <1.21.6
        return this.trackedScroll;
    }

    /**
     * Positions and resizes the list.
     *
     * <p>1.20.3 introduced the {@code getX}/{@code getWidth} accessors on the selection
     * list; before that the position lived in protected fields, so the list positions
     * itself here instead of the caller.</p>
     */
    public void setBounds(int x, int y, int width, int height) {
        //? if >=1.20.3 {
        this.setPosition(x, y);
        this.setSize(width, height);
        //?} else {
        /*this.updateSize(width, height, y, y + height);
        this.x0 = x;
        *///?}
    }

    @Override
    public int getRowWidth() {
        return 220;
    }

    //? if >=1.21.2 {
    /*@Override
    protected int scrollBarX() {
        return this.getX() + this.getWidth() / 2 + 124;
    }
    *///?} else if >=1.20.3 {
    @Override
    protected int getScrollbarPosition() {
        return this.getX() + this.getWidth() / 2 + 124;
    }
    //?} else {
    /*@Override
    protected int getScrollbarPosition() {
        // 1.20.1 / 1.20.2: AbstractSelectionList uses protected x0/width fields directly,
        // not getX()/getWidth() accessors (those came in 1.20.3 with the AbstractWidget
        // layout API alignment).
        return this.x0 + this.width / 2 + 124;
    }
    *///?}

    /** One row: a single widget, centred horizontally. */
    public static class WidgetEntry extends ContainerObjectSelectionList.Entry<WidgetEntry> {
        private final AbstractWidget widget;
        //? if >=1.21.6 {
        /*private final ScrollableWidgetList parentList;
        *///?}

        //? if >=1.21.6 {
        /*public WidgetEntry(AbstractWidget widget, ScrollableWidgetList parentList) {
            this.widget = widget;
            this.parentList = parentList;
        }
        *///?} else {
        WidgetEntry(AbstractWidget widget) {
            this.widget = widget;
        }
        //?}

        //? if >=26.1 {
        /*@Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            // Center the widget horizontally inside the list, not on the screen: the
            // list may only occupy a part of the screen (the rule pane).
            int listWidth = parentList.getWidth();
            int centerX = parentList.getX() + (listWidth - widget.getWidth()) / 2;
            widget.setX(centerX);
            widget.setY(this.getContentY());
            widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }*/
        //?} elif >=1.21.9 {
        /*@Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            // Center the widget horizontally inside the list, not on the screen: the
            // list may only occupy a part of the screen (the rule pane).
            int listWidth = parentList.getWidth();
            int centerX = parentList.getX() + (listWidth - widget.getWidth()) / 2;
            widget.setX(centerX);
            widget.setY(this.getContentY());
            widget.render(graphics, mouseX, mouseY, partialTick);
        }*/
        //?} else {
        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            widget.setX(left + (width - widget.getWidth()) / 2);
            widget.setY(top);
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
        //?}

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(widget);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(widget);
        }

        //? if <1.21.9 {
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return widget.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return widget.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            return widget.charTyped(chr, modifiers);
        }
        //?}
    }
}

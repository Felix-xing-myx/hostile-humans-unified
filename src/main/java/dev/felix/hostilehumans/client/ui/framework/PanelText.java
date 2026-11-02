package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.IntSupplier;

/** Noninteractive text participates in measurement, never in the tab chain. */
public class PanelText extends AbstractWidget {
    private final IntSupplier color;
    private List<FormattedCharSequence> lines = List.of();

    public PanelText(Component text, IntSupplier color) {
        super(0, 0, 1, 1, text);
        this.color = color;
        active = false;
    }

    public int measureHeight(int width) {
        lines = Minecraft.getInstance().font.split(getMessage(), Math.max(1, width));
        return Math.max(10, lines.size() * 10);
    }

    public void place(UiRect b) {
        setX(b.x());
        setY(b.y());
        setWidth(b.width());
        setHeight(b.height());
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        int y = getY();
        for (var line : lines) {
            g.drawString(Minecraft.getInstance().font, line, getX(), y, color.getAsInt(), false);
            y += 10;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}
}

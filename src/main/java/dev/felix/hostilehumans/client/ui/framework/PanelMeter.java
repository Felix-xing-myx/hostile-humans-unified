package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/** Noninteractive proportional progress with 8px divisions, separate from absolute health ticks. */
public final class PanelMeter extends AbstractWidget {
    public static final int HEIGHT = 4, RIGHT_INSET = 30;

    private final DoubleSupplier value, maximum;
    private final IntSupplier color;
    private boolean segmented = true;

    public PanelMeter(DoubleSupplier value, DoubleSupplier maximum, IntSupplier color) {
        super(0, 0, 1, HEIGHT, Component.empty());
        this.value = value;
        this.maximum = maximum;
        this.color = color;
        active = false;
    }

    public PanelMeter segmented(boolean value) {
        segmented = value;
        return this;
    }

    public int measureHeight(int width) {
        return HEIGHT + 4;
    }

    public void place(UiRect b) {
        setX(b.x());
        setY(b.y());
        setWidth(b.width());
        setHeight(HEIGHT);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        UiDraw.meter(
                g,
                getX(),
                getY() + 2,
                Math.max(1, width - RIGHT_INSET),
                HEIGHT,
                value.getAsDouble(),
                maximum.getAsDouble(),
                color.getAsInt(),
                segmented);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}
}
package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/** Noninteractive proportional progress, separate from absolute health ticks. */
public final class PanelMeter extends AbstractWidget {
    private final DoubleSupplier value, maximum;
    private final IntSupplier color;

    public PanelMeter(DoubleSupplier value, DoubleSupplier maximum, IntSupplier color) {
        super(0, 0, 1, 6, Component.empty());
        this.value = value;
        this.maximum = maximum;
        this.color = color;
        active = false;
    }

    public int measureHeight(int width) {
        return 6;
    }

    public void place(UiRect b) {
        setX(b.x());
        setY(b.y());
        setWidth(b.width());
        setHeight(b.height());
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        UiDraw.meter(
                g,
                getX(),
                getY() + 1,
                width,
                3,
                value.getAsDouble(),
                maximum.getAsDouble(),
                color.getAsInt());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}
}

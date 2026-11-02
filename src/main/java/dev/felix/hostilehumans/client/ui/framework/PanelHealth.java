package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Shared noninteractive health ticks; current and maximum always come from a server snapshot. */
public final class PanelHealth extends AbstractWidget {
    private double current, maximum;
    private boolean heading = true;

    public PanelHealth() {
        super(0, 0, 1, 12, Component.empty());
        active = false;
    }

    /** Header variant uses 2px ticks in 9px cells; the row variant is the compact 1px/8px form. */
    public PanelHealth heading(boolean value) {
        heading = value;
        return this;
    }

    public void update(double current, double maximum) {
        this.current = current;
        this.maximum = maximum;
    }

    public int measureHeight(int width) {
        return heading ? 12 : 11;
    }

    public void place(UiRect b) {
        setX(b.x());
        setY(b.y());
        setWidth(b.width());
        setHeight(b.height());
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        UiDraw.health(g, getX(), getY() + 2, current, maximum, heading, 0);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}
}
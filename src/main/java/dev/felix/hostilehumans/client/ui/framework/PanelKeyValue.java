package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Read-only label/value line: caption left, value right, hairline separator underneath. */
public final class PanelKeyValue extends AbstractWidget {
    public static final int HEIGHT = 16;

    private final Supplier<Component> label, value;
    private final IntSupplier color;
    private final Supplier<UiTheme> theme;

    public PanelKeyValue(
            Supplier<Component> label,
            Supplier<Component> value,
            IntSupplier color,
            Supplier<UiTheme> theme) {
        super(0, 0, 1, HEIGHT, Component.empty());
        this.label = label;
        this.value = value;
        this.color = color;
        this.theme = theme;
        active = false;
    }

    public int measureHeight(int width) {
        return HEIGHT;
    }

    public void place(UiRect bounds) {
        setX(bounds.x());
        setY(bounds.y());
        setWidth(bounds.width());
        setHeight(HEIGHT);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        var font = Minecraft.getInstance().font;
        UiTheme t = theme.get();
        int y = getY() + 4;
        g.drawString(font, label.get(), getX() + 4, y, t.muted(), false);
        Component right = value.get();
        int width = Math.min(font.width(right), Math.max(0, getWidth() / 2 + 24));
        String clipped = font.plainSubstrByWidth(right.getString(), Math.max(1, width));
        g.drawString(
                font, clipped, getX() + getWidth() - 4 - font.width(clipped), y, color.getAsInt(), false);
        UiDraw.rect(g, getX(), getY() + HEIGHT - 1, getWidth(), 1, UiColor.alpha(0xFFFFFFFF, .035F));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}
}
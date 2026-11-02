package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Local page draft, committed on release; server still owns page contents. */
public final class PanelPageSlider extends AbstractSliderButton {
    private final int maximum;
    private final IntConsumer request;
    private final Supplier<UiTheme> theme;
    private Supplier<UiRect> viewport;
    private boolean dragging;

    public PanelPageSlider(int page, int maximum, IntConsumer request, Supplier<UiTheme> theme) {
        super(0, 0, 1, 24, Component.empty(), maximum > 0 ? page / (double) maximum : 0);
        this.maximum = maximum;
        this.request = request;
        this.theme = theme;
        updateMessage();
    }

    public void viewport(Supplier<UiRect> viewport) {
        this.viewport = viewport;
    }

    private int page() {
        return Math.max(0, Math.min(maximum, (int) Math.round(value * maximum)));
    }

    @Override
    protected void updateMessage() {
        setMessage(
                Component.translatable("gui.hostile_humans_unified.page", page() + 1, maximum + 1));
    }

    @Override
    protected void applyValue() {
        updateMessage();
    }

    @Override
    protected boolean clicked(double x, double y) {
        return (viewport == null || viewport.get().contains(x, y)) && super.clicked(x, y);
    }

    @Override
    public boolean isMouseOver(double x, double y) {
        return (viewport == null || viewport.get().contains(x, y)) && super.isMouseOver(x, y);
    }

    @Override
    public void onClick(double x, double y) {
        dragging = true;
        super.onClick(x, y);
    }

    @Override
    public void onRelease(double x, double y) {
        super.onRelease(x, y);
        if (dragging) {
            dragging = false;
            request.accept(page());
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean handled = super.keyPressed(key, scan, mods);
        if (handled && !dragging) request.accept(page());
        return handled;
    }

    public boolean editing() {
        return dragging;
    }

    public int measureHeight(int width) {
        return 28;
    }

    public void place(UiRect b) {
        setX(b.x());
        setY(b.y());
        setWidth(b.width());
        setHeight(b.height());
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        UiTheme t = theme.get();
        g.drawString(Minecraft.getInstance().font, getMessage(), getX(), getY(), t.muted(), false);
        UiDraw.rect(g, getX(), getY() + 18, width, 2, t.edge());
        UiDraw.roundRect(
                g,
                getX() + (int) Math.round(value * Math.max(0, width - 8)),
                getY() + 14,
                8,
                10,
                2,
                t.accent());
        if (isFocused()) UiDraw.outline(g, getX(), getY(), width, height, t.text());
    }
}

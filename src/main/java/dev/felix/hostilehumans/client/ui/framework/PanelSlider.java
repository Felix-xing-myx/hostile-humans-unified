package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/** Native slider draft: mouse commits only on release, keyboard commits accepted steps. */
public final class PanelSlider extends AbstractSliderButton {
    private final Supplier<UiTheme> theme;
    private final IntConsumer commit;
    private final IntFunction<Component> label;
    private Supplier<UiRect> viewport;
    private boolean dragging, mixed, changed;
    private int accepted;

    public PanelSlider(
            int current,
            IntFunction<Component> label,
            IntConsumer commit,
            Supplier<UiTheme> theme) {
        super(0, 0, 1, 24, Component.empty(), (current - 8) / 88.0);
        this.accepted = current;
        this.label = label;
        this.commit = commit;
        this.theme = theme;
        updateMessage();
    }

    public void viewport(Supplier<UiRect> viewport) {
        this.viewport = viewport;
    }

    public void accept(int value, boolean mixed) {
        if (!dragging) {
            accepted = value;
            this.value = (value - 8) / 88.0;
            this.mixed = mixed;
            changed = false;
            updateMessage();
        }
    }

    public boolean editing() {
        return dragging;
    }

    private int amount() {
        return Math.max(8, Math.min(96, 8 + (int) Math.round(value * 88)));
    }

    @Override
    protected void updateMessage() {
        if (label != null) {
            Component message = label.apply(amount());
            if (mixed && !dragging && !changed)
                message =
                        message.copy()
                                .append(" · ")
                                .append(
                                        Component.translatable(
                                                "screen.hostile_humans_unified.command.mixed"));
            setMessage(message);
        }
    }

    @Override
    protected void applyValue() {
        changed = true;
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
            submit();
        }
    }

    private void submit() {
        int submitted = amount();
        if (mixed || submitted != accepted) commit.accept(submitted);
        value = (accepted - 8) / 88.0;
        changed = false;
        updateMessage();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean result = super.keyPressed(key, scan, mods);
        if (result && changed && !dragging) submit();
        return result;
    }

    public int measureHeight(int width) {
        return 32;
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
        int x = getX(), y = getY(), w = getWidth();
        g.drawString(Minecraft.getInstance().font, getMessage(), x, y, t.muted(), false);
        UiDraw.rect(g, x, y + 20, w, 2, t.edge());
        int knob = x + (int) Math.round(value * Math.max(0, w - 6));
        UiDraw.rect(g, x, y + 20, knob - x + 3, 2, t.accent());
        UiDraw.roundRect(g, knob, y + 15, 6, 12, 2, t.accent());
        if (isFocused()) UiDraw.outline(g, x, y, w, getHeight(), t.text());
    }
}

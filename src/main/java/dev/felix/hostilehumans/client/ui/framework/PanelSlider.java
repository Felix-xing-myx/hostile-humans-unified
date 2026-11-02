package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Native slider draft: mouse commits only on release, keyboard commits accepted steps. The caption
 * is drawn left and the committed value right, matching the reference terminal rows.
 */
public final class PanelSlider extends AbstractSliderButton {
    public static final int HEIGHT = 24;

    private final Supplier<UiTheme> theme;
    private final IntConsumer commit;
    private final IntFunction<Component> name;
    private IntFunction<Component> valueText;
    private Supplier<UiRect> viewport;
    private boolean dragging, mixed, changed;
    private int accepted;

    public PanelSlider(
            int current, IntFunction<Component> name, IntConsumer commit, Supplier<UiTheme> theme) {
        super(0, 0, 1, HEIGHT, Component.empty(), (current - 8) / 88.0);
        this.accepted = current;
        this.name = name;
        this.commit = commit;
        this.theme = theme;
        updateMessage();
    }

    /** Optional right-hand value, e.g. {@code v -> Component.literal(v + " 格")}. */
    public PanelSlider value(IntFunction<Component> text) {
        this.valueText = text;
        updateMessage();
        return this;
    }

    public void viewport(Supplier<UiRect> viewport) {
        this.viewport = viewport;
    }

    public void accept(int amount, boolean mixed) {
        if (!dragging) {
            accepted = amount;
            this.value = (amount - 8) / 88.0;
            this.mixed = mixed;
            changed = false;
            updateMessage();
        }
    }

    public boolean editing() {
        return dragging;
    }

    private int amount() {
        return Math.max(8, Math.min(96, 8 + (int) Math.round(this.value * 88)));
    }

    @Override
    protected void updateMessage() {
        if (name == null) return;
        Component message = name.apply(amount());
        if (valueText != null) message = message.copy().append(" · ").append(valueText.apply(amount()));
        if (mixed && !dragging && !changed)
            message =
                    message
                            .copy()
                            .append(" · ")
                            .append(
                                    Component.translatable(
                                            "screen.hostile_humans_unified.command.mixed"));
        setMessage(message);
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
        return HEIGHT;
    }

    public void place(UiRect b) {
        setX(b.x());
        setY(b.y());
        setWidth(b.width());
        setHeight(b.height());
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        UiTheme t = theme.get();
        var font = Minecraft.getInstance().font;
        g.drawString(font, name.apply(amount()), getX(), getY() + 1, t.muted(), false);
        if (valueText != null) {
            Component text = valueText.apply(amount());
            g.drawString(font, text, getX() + width - font.width(text), getY() + 1, t.text(), false);
        }
        UiDraw.track(g, getX(), getY() + 13, width, 11, this.value, t);
        if (isFocused()) UiDraw.outline(g, getX() - 1, getY() + 11, width + 2, 15, t.accent());
    }
}
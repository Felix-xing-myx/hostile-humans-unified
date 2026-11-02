package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Supplier;

/** Vanilla input, focus and narration, with cached text and viewport-aware clicks. */
public class PanelButton extends Button {
    protected final Supplier<UiTheme> theme;
    private Supplier<UiRect> viewport;
    private List<FormattedCharSequence> lines = List.of();
    private Component measuredMessage;
    private int measuredWidth = -1;
    private boolean selected, danger;

    public PanelButton(Component message, OnPress press, Supplier<UiTheme> theme) {
        super(0, 0, 1, 20, message, press, DEFAULT_NARRATION);
        this.theme = theme;
    }

    public PanelButton selected(boolean value) {
        selected = value;
        return this;
    }

    public PanelButton danger(boolean value) {
        danger = value;
        return this;
    }

    public void viewport(Supplier<UiRect> value) {
        viewport = value;
    }

    protected boolean inViewport(double x, double y) {
        return viewport == null || viewport.get().contains(x, y);
    }

    @Override
    protected boolean clicked(double x, double y) {
        return inViewport(x, y) && super.clicked(x, y);
    }

    @Override
    public boolean isMouseOver(double x, double y) {
        return inViewport(x, y) && super.isMouseOver(x, y);
    }

    public int measureHeight(int width) {
        if (width != measuredWidth || !getMessage().equals(measuredMessage)) {
            measuredWidth = width;
            measuredMessage = getMessage();
            lines = Minecraft.getInstance().font.split(getMessage(), Math.max(1, width - 12));
        }
        return Math.max(20, lines.size() * 10 + 8);
    }

    public void place(UiRect b) {
        setX(b.x());
        setY(b.y());
        setWidth(b.width());
        setHeight(b.height());
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        MutableComponent message = super.createNarrationMessage();
        return selected
                ? message.append(". ")
                        .append(Component.translatable("gui.hostile_humans_unified.selected"))
                : message;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        UiTheme t = theme.get();
        UiDraw.control(
                g,
                getX(),
                getY(),
                width,
                height,
                t,
                isHovered && inViewport(mx, my),
                active,
                selected,
                danger,
                isFocused());
        int color = !active ? t.disabled() : danger ? t.danger() : selected ? t.accent() : t.text();
        int y = getY() + (height - lines.size() * 10) / 2;
        for (var line : lines) {
            g.drawString(Minecraft.getInstance().font, line, getX() + 6, y, color, false);
            y += 10;
        }
    }
}

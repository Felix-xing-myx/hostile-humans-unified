package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Native IME/editor and keyboard semantics, with the shared surface and clipping contract. */
public final class PanelEditBox extends EditBox {
    private final Supplier<UiTheme> theme;
    private Supplier<UiRect> viewport;
    private BooleanSupplier valid = () -> true;

    public PanelEditBox(Font font, Component label, Supplier<UiTheme> theme) {
        super(font, 0, 0, 1, 20, label);
        this.theme = theme;
        setBordered(false);
        setMaxLength(64);
    }

    public void validation(BooleanSupplier valid) {
        this.valid = valid;
    }

    public void viewport(Supplier<UiRect> value) {
        viewport = value;
    }

    public boolean fullyVisible() {
        UiRect clip = viewport == null ? null : viewport.get();
        return clip == null || (getY() >= clip.y() && getY() + getHeight() <= clip.bottom());
    }

    public int measureHeight(int width) {
        return 24;
    }

    public void place(UiRect b) {
        setX(b.x() + 5);
        setY(b.y() + 5);
        setWidth(Math.max(1, b.width() - 10));
        setHeight(Math.max(1, b.height() - 10));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return fullyVisible()
                && (viewport == null || viewport.get().contains(x, y))
                && super.mouseClicked(x, y, button);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        UiTheme t = theme.get();
        UiDraw.control(
                g,
                getX() - 5,
                getY() - 5,
                width + 10,
                height + 10,
                t,
                false,
                true,
                false,
                false,
                isFocused());
        setTextColor(valid.getAsBoolean() ? t.text() : t.danger());
        super.renderWidget(g, mx, my, partial);
    }
}

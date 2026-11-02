package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** Checkbox row: 8px well plus label, painted on the shared control surface. */
public final class PanelCheck extends PanelButton {
    private boolean checked;

    public PanelCheck(Component label, boolean checked, OnPress press, Supplier<UiTheme> theme) {
        super(label, press, theme);
        this.checked = checked;
    }

    public PanelCheck checked(boolean value) {
        checked = value;
        return this;
    }

    public boolean checked() {
        return checked;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        UiTheme t = theme.get();
        boolean hover = isHovered && inViewport(mx, my);
        if (!active)
            UiDraw.control(
                    g, getX(), getY(), width, height, t, false, false, false, false, isFocused());
        else UiDraw.checkbox(g, getX(), getY(), width, height, t, checked, hover);
        if (isFocused() && active) UiDraw.outline(g, getX(), getY(), width, height, t.accent());
        g.drawString(
                Minecraft.getInstance().font,
                getMessage(),
                getX() + 20,
                getY() + (height - 8) / 2,
                !active ? t.disabled() : t.text(),
                false);
    }
}
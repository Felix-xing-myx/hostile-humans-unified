package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * Group header inside a panel body: band, accent tick, optional 8px marker and muted caption. It
 * is not focusable, so it never competes with the controls it introduces.
 */
public final class PanelGroup extends AbstractWidget {
    public static final int HEIGHT = 16;

    private final ResourceLocation icon;
    private final Supplier<UiTheme> theme;

    public PanelGroup(Component caption, ResourceLocation icon, Supplier<UiTheme> theme) {
        super(0, 0, 1, HEIGHT, caption);
        this.icon = icon;
        this.theme = theme;
        active = false;
    }

    public ResourceLocation icon() {
        return icon;
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
        UiTheme t = theme.get();
        UiDraw.groupBand(g, getX(), getY(), width, height, t, icon);
        g.drawString(
                Minecraft.getInstance().font,
                getMessage(),
                getX() + UiDraw.groupTextX(0, icon),
                getY() + (height - 8) / 2,
                t.muted(),
                false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}
}
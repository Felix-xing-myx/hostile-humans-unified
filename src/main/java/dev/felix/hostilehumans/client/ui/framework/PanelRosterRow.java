package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** Fixed left name/health columns, with a second metadata line and explicit stale label. */
public final class PanelRosterRow extends PanelButton {
    private final String name, metadata, healthText;
    private final int health, maximum;
    private final boolean current, marked;
    private String clippedName = "", clippedMetadata = "";

    public PanelRosterRow(
            String name,
            String metadata,
            int health,
            int maximum,
            boolean current,
            boolean marked,
            Component narration,
            OnPress press,
            Supplier<UiTheme> theme) {
        super(narration, press, theme);
        this.name = name;
        this.metadata = metadata;
        this.health = health;
        this.maximum = maximum;
        this.current = current;
        this.marked = marked;
        healthText = current && health >= 0 ? health + "/" + maximum : "—";
    }

    @Override
    public int measureHeight(int width) {
        var font = Minecraft.getInstance().font;
        clippedName = font.plainSubstrByWidth(name, Math.min(68, Math.max(1, width - 166)));
        clippedMetadata =
                font.plainSubstrByWidth((marked ? "☑ " : "") + metadata, Math.max(1, width - 12));
        return 32;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        super.renderWidget(g, mx, my, partial);
        var font = Minecraft.getInstance().font;
        UiTheme t = theme.get();
        int x = getX() + 6, y = getY() + 5;
        UiDraw.rect(g, x, y + 2, 3, 3, current ? 0xFF7FC08C : 0xFF5F707C);
        g.drawString(font, clippedName, x + 6, y, current ? t.text() : t.muted(), false);
        if (width >= 220 && current && health >= 0)
            UiDraw.health(g, x + 74, y + 1, health, maximum, false, 0);
        g.drawString(
                font, healthText, getX() + width - 6 - font.width(healthText), y, t.muted(), false);
        g.drawString(font, clippedMetadata, x, y + 13, t.muted(), false);
    }
}

package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** Roster line: status dot, fixed name column, health ticks, numeric health and a metadata line. */
public final class PanelRosterRow extends PanelButton {
    public static final int HEIGHT = 32, NAME = 68, HEALTH = 112, NUMBER = 36;

    private final String name, metadata, healthText;
    private final int health, maximum;
    private final boolean current, marked;
    private String clippedName = "", clippedMetadata = "";
    private int tone = -1;

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

    /** Explicit status colour; -1 keeps the default online/offline derivation. */
    public PanelRosterRow tone(int color) {
        tone = color;
        return this;
    }

    @Override
    public int measureHeight(int width) {
        var font = Minecraft.getInstance().font;
        clippedName = font.plainSubstrByWidth(name, Math.min(NAME, Math.max(1, width - 24)));
        clippedMetadata =
                font.plainSubstrByWidth((marked ? "☑ " : "") + metadata, Math.max(1, width - 20));
        return HEIGHT;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        UiTheme t = theme.get();
        boolean hover = isHovered && inViewport(mx, my);
        int background =
                selected()
                        ? UiColor.mix(t.inset(), 0xFFFFFFFF, .05F)
                        : hover ? UiColor.mix(t.inset(), 0xFFFFFFFF, .028F) : t.inset();
        UiDraw.rect(g, getX(), getY(), width, height, background);
        UiDraw.rect(g, getX(), getY() + height - 1, width, 1, UiColor.alpha(0xFFFFFFFF, .04F));
        if (selected()) UiDraw.rect(g, getX(), getY() + 3, 2, height - 6, t.accent());
        if (isFocused()) UiDraw.outline(g, getX(), getY(), width, height, t.accent());

        var font = Minecraft.getInstance().font;
        int dot = tone >= 0 ? tone : current ? 0xFF8FC7A6 : 0xFF4A5560;
        int x = getX() + 10, y = getY() + 5;
        UiDraw.rect(g, x, y + 4, 4, 4, dot);
        g.drawString(font, clippedName, x + 8, y, current ? t.text() : t.disabled(), false);
        // Fixed columns: the ticks and the number keep the same left edge on every row.
        int numberRight = getX() + width - 6, numberLeft = numberRight - NUMBER;
        int barX = x + 8 + NAME + 6;
        int barWidth = Math.min(HEALTH, Math.max(0, numberLeft - 6 - barX));
        if (current && health >= 0 && barWidth > 12)
            UiDraw.health(g, barX, y + 1, health, maximum, false, barWidth);
        g.drawString(font, healthText, numberRight - font.width(healthText), y + 1, t.muted(), false);
        g.drawString(font, clippedMetadata, x, y + 13, t.disabled(), false);
    }
}
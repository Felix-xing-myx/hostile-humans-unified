package dev.felix.hostilehumans.client.ui;

import dev.felix.hostilehumans.client.ui.framework.UiDraw;
import dev.felix.hostilehumans.client.ui.framework.UiTheme;
import dev.felix.hostilehumans.client.ui.framework.UiThemes;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Compatibility facade for HUD/container callers; all painting is owned by the shared theme. */
public final class CommandPanelChrome {
    private CommandPanelChrome() {}

    public static int textColor() {
        return UiThemes.faction("").text();
    }

    public static int accent(String faction) {
        return UiThemes.faction(faction).accent();
    }

    static Component factionName(String faction) {
        return UiThemes.factionName(faction);
    }

    public static void frame(GuiGraphics g, int x, int y, int w, int h, int accent) {
        UiDraw.frame(g, x, y, w, h, UiThemes.colored(accent));
    }

    public static void badge(GuiGraphics g, int x, int y, String faction) {
        UiDraw.badge(g, x, y, UiThemes.emblem(faction), 12, 16, accent(faction));
    }

    static void meter(GuiGraphics g, int x, int y, int w, double value, double maximum, int color) {
        UiDraw.meter(g, x, y, w, 3, value, maximum, color);
    }

    public static void control(
            GuiGraphics g,
            Font font,
            Component label,
            int x,
            int y,
            int w,
            int h,
            boolean hovered,
            boolean active,
            boolean selected,
            boolean danger,
            int accent) {
        UiTheme t = UiThemes.colored(accent);
        UiDraw.control(g, x, y, w, h, t, hovered, active, selected, danger, false);
        var lines = font.split(label, Math.max(1, w - 12));
        int ty = y + (h - lines.size() * 10) / 2;
        int color = !active ? t.disabled() : danger ? t.danger() : selected ? t.accent() : t.text();
        for (var line : lines) {
            g.drawString(font, line, x + 6, ty, color, false);
            ty += 10;
        }
    }
}

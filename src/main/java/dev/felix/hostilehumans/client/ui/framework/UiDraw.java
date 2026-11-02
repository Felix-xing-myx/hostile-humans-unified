package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Stateless field-terminal primitives. Caller owns scissor and pose lifetime. */
public final class UiDraw {
    private static final ResourceLocation SURFACE =
            new ResourceLocation("hostile_humans_unified", "textures/gui/panel/surface.png");
    private static final ResourceLocation SHADOW =
            new ResourceLocation("hostile_humans_unified", "textures/gui/panel/shadow.png");
    private static final int[][] INSETS = new int[5][];

    static {
        for (int r = 0; r < INSETS.length; r++) {
            INSETS[r] = new int[r];
            for (int i = 0; i < r; i++)
                INSETS[r][i] = r - (int) Math.round(Math.sqrt(r * r - Math.pow(r - i - .5, 2)));
        }
    }

    private UiDraw() {}

    public static void rect(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0) g.fill(x, y, x + w, y + h, color);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        rect(g, x, y, w, 1, color);
        rect(g, x, y + h - 1, w, 1, color);
        rect(g, x, y, 1, h, color);
        rect(g, x + w - 1, y, 1, h, color);
    }

    public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        int r = Math.max(0, Math.min(4, Math.min(radius, Math.min(w, h) / 2)));
        rect(g, x, y + r, w, h - 2 * r, color);
        for (int i = 0; i < r; i++) {
            int in = INSETS[r][i];
            rect(g, x + in, y + i, w - 2 * in, 1, color);
            rect(g, x + in, y + h - 1 - i, w - 2 * in, 1, color);
        }
    }

    public static void rule(GuiGraphics g, int x, int y, int w, int dark, int light) {
        rect(g, x, y, w, 1, dark);
        rect(g, x, y + 1, w, 1, light);
    }

    public static void ruleFade(GuiGraphics g, int x, int y, int w, int color) {
        for (int i = 0; i < 6; i++)
            rect(
                    g,
                    x + w * i / 6,
                    y,
                    w * (i + 1) / 6 - w * i / 6,
                    1,
                    (color & 0xFFFFFF) | ((color >>> 24) * (6 - i) / 6 << 24));
    }

    public static void nineSlice(
            GuiGraphics g,
            ResourceLocation texture,
            int x,
            int y,
            int w,
            int h,
            int border,
            int sourceWidth,
            int sourceHeight) {
        if (w <= 0 || h <= 0) return;
        int b = Math.min(border, Math.min(w, h) / 2);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++) {
                int dx = col == 0 ? x : col == 1 ? x + b : x + w - b,
                        dy = row == 0 ? y : row == 1 ? y + b : y + h - b;
                int dw = col == 1 ? w - 2 * b : b, dh = row == 1 ? h - 2 * b : b;
                int sx = col == 0 ? 0 : col == 1 ? border : sourceWidth - border,
                        sy = row == 0 ? 0 : row == 1 ? border : sourceHeight - border;
                int sw = col == 1 ? sourceWidth - 2 * border : border,
                        sh = row == 1 ? sourceHeight - 2 * border : border;
                if (dw > 0 && dh > 0)
                    g.blit(
                            texture,
                            dx,
                            dy,
                            dw,
                            dh,
                            (float) sx,
                            (float) sy,
                            sw,
                            sh,
                            sourceWidth,
                            sourceHeight);
            }
    }

    public static void frame(GuiGraphics g, UiRect b, UiTheme t) {
        frame(g, b.x(), b.y(), b.width(), b.height(), t);
    }

    public static void frame(GuiGraphics g, int x, int y, int width, int height, UiTheme t) {
        if (UiThemes.shadowAvailable())
            nineSlice(g, SHADOW, x - 12, y - 10, width + 24, height + 24, 12, 48, 48);
        roundRect(g, x - 3, y - 3, width + 6, height + 6, 4, 0x9505090D);
        roundRect(g, x, y, width, height, 3, t.edge());
        roundRect(g, x + 1, y + 1, width - 2, height - 2, 2, t.bottom());
        if (width > 4 && height > 4)
            g.fillGradient(x + 2, y + 2, (x + width) - 2, (y + height) - 2, t.top(), t.bottom());
        rect(
                g,
                x + 2,
                y + 2,
                Math.max(0, width - 4),
                Math.min(24, Math.max(0, height - 4)),
                (t.accent() & 0xFFFFFF) | 0x16000000);
        if (UiThemes.surfaceAvailable() && width > 4 && height > 4) {
            g.setColor(1F, 1F, 1F, .16F);
            try {
                g.blit(SURFACE, x + 2, y + 2, width - 4, height - 4, 0F, 0F, 64, 64, 64, 64);
            } finally {
                g.setColor(1F, 1F, 1F, 1F);
            }
        }
        if (height > 29) rule(g, x + 12, y + 27, Math.max(0, width - 24), 0xFF070D12, t.edge());
    }

    public static void watermark(GuiGraphics g, UiRect frame, ResourceLocation emblem) {
        if (emblem == null) return;
        g.setColor(1F, 1F, 1F, .045F);
        try {
            g.blit(
                    emblem,
                    Math.max(frame.x(), frame.right() - 118),
                    Math.max(frame.y(), frame.bottom() - 176),
                    96,
                    120,
                    0F,
                    0F,
                    96,
                    96,
                    96,
                    96);
        } finally {
            g.setColor(1F, 1F, 1F, 1F);
        }
    }

    public static void control(
            GuiGraphics g,
            int x,
            int y,
            int w,
            int h,
            UiTheme t,
            boolean hover,
            boolean active,
            boolean selected,
            boolean danger,
            boolean focused) {
        roundRect(g, x, y, w, h, 2, selected ? t.accent() : focused ? t.text() : 0xFF35444F);
        roundRect(g, x + 1, y + 1, w - 2, h - 2, 2, hover && active ? 0xFF2B3C49 : t.surface());
        if (selected) rect(g, x + 1, y + 2, 2, h - 4, t.accent());
        if (focused) rect(g, x + 5, y + h - 3, Math.max(0, w - 10), 1, t.accent());
    }

    public static void meter(
            GuiGraphics g, int x, int y, int w, int h, double value, double max, int color) {
        double ratio =
                Double.isFinite(value) && Double.isFinite(max) && max > 0
                        ? Math.max(0, Math.min(1, value / max))
                        : 0;
        rect(g, x, y, w, h, 0xFF26333C);
        rect(g, x, y, (int) Math.round(w * ratio), h, color);
    }

    public static void health(
            GuiGraphics g,
            int x,
            int y,
            double current,
            double maximum,
            boolean heading,
            int fixedWidth) {
        int cells = PanelGeometry.healthCells(maximum),
                count = cells * 5,
                h = heading ? 7 : 6,
                gap = heading ? 4 : 3,
                tickWidth = heading ? 2 : 1;
        double ratio = maximum > 0 ? current / maximum : 0;
        int color = ratio > .5 ? 0xFF7FC08C : ratio >= .2 ? 0xFFDBB25F : 0xFFDC7F6F;
        int natural = cells * (tickWidth * 5 + 4) + (cells - 1) * gap;
        if (fixedWidth > 0) h = 8;
        for (int i = 0; i < count; i++) {
            int start = i / 5 * (tickWidth * 5 + 4 + gap) + (i % 5) * (tickWidth + 1);
            int left =
                    fixedWidth > 0
                            ? x + (int) Math.floor(start * (double) fixedWidth / natural)
                            : x + start;
            int right =
                    fixedWidth > 0
                            ? x
                                    + (int)
                                            Math.floor(
                                                    (start + tickWidth)
                                                            * (double) fixedWidth
                                                            / natural)
                            : left + tickWidth;
            int width = Math.max(1, right - left);
            rect(g, left, y, width, h, 0xFF28353C);
            double fill = PanelGeometry.healthTick(current, maximum, i);
            if (fill > 0) rect(g, left, y, Math.max(1, (int) Math.round(width * fill)), h, color);
        }
    }

    public static void badge(
            GuiGraphics g, int x, int y, ResourceLocation icon, int width, int height, int accent) {
        if (icon != null) g.blit(icon, x, y, width, height, 0F, 0F, 96, 96, 96, 96);
        else {
            outline(g, x, y, width, height, accent);
            rect(g, x + width / 2, y + 3, 1, Math.max(1, height - 6), accent);
        }
    }
}

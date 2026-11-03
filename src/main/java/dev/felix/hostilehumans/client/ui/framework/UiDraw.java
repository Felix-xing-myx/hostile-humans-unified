package dev.felix.hostilehumans.client.ui.framework;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Stateless field-terminal primitives for the shared 'battlefield terminal' chrome.
 *
 * <p>Every method paints with explicit colours and restores any GL state it touches, so one
 * component can never leave a tint behind for the next. Caller owns scissor and pose lifetime.
 * Geometry follows the reference overview exactly: 1px + 3px outer rings, 2px cut corners,
 * a four-stop vertical gradient, a 3px faction spine, a 28px header band and a 12px footer fade.
 */
public final class UiDraw {
    /** Tile size of the grain overlay; must stay equal to the texture size to avoid stretching. */
    public static final int GRAIN = 64;

    private static final ResourceLocation GRAIN_TEXTURE =
            new ResourceLocation("hostile_humans_unified", "textures/gui/panel/surface.png");
    private static final int[][] INSETS = new int[5][];

    static {
        for (int r = 0; r < INSETS.length; r++) {
            INSETS[r] = new int[r];
            for (int i = 0; i < r; i++)
                INSETS[r][i] = r - (int) Math.round(Math.sqrt(r * r - Math.pow(r - i - .5, 2)));
        }
    }

    private UiDraw() {}

    // ---- basic fills ----------------------------------------------------------

    public static void rect(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0) g.fill(x, y, x + w, y + h, color);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
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

    // ---- panel chrome ---------------------------------------------------------

    /** Soft procedural drop shadow: concentric translucent rings, biased downward. */
    public static void shadow(GuiGraphics g, int x, int y, int w, int h) {
        for (int i = 5; i >= 1; i--) {
            int alpha = 10 + (6 - i) * 9;
            int color = UiColor.alpha(0xFF000000, alpha / 255F);
            int ox = x - 3 - i, oy = y - 3 - i + i / 2;
            int ow = w + 6 + 2 * i, oh = h + 6 + 2 * i;
            rect(g, ox, oy, ow, 1, color);
            rect(g, ox, oy + oh - 1, ow, 1, color);
            rect(g, ox, oy, 1, oh, color);
            rect(g, ox + ow - 1, oy, 1, oh, color);
        }
    }

    /** Panel spine, rings, gradient body and inner bevel. */
    public static void frame(GuiGraphics g, UiRect bounds, UiTheme t) {
        frame(g, bounds.x(), bounds.y(), bounds.width(), bounds.height(), t);
    }

    public static void frame(GuiGraphics g, int x, int y, int width, int height, UiTheme t) {
        if (width <= 0 || height <= 0) return;
        shadow(g, x, y, width, height);
        // 3px outer rule ring, then the 1px dark outline drawn over its inner edge.
        int ring = t.ring();
        rect(g, x - 3, y - 3, width + 6, 3, ring);
        rect(g, x - 3, y + height, width + 6, 3, ring);
        rect(g, x - 3, y, 3, height, ring);
        rect(g, x + width, y, 3, height, ring);
        outline(g, x - 1, y - 1, width + 2, height + 2, t.outline());

        int a = Math.min(height, Math.max(0, Math.round(height * .18F)));
        int b = Math.min(height, Math.max(a, Math.round(height * .58F)));
        int cut = 3;
        // Top and bottom rows carry both the 3px radius and the 2px diagonal cut.
        rect(g, x + 2, y, width - 2, 1, t.head());
        rect(g, x + 1, y + 1, width - 1, 1, UiColor.mix(t.head(), t.top(), .5F));
        rect(g, x, y + 2, width, 1, t.top());
        if (b > cut) {
            g.fillGradient(x, y + cut, x + width, y + a, t.head(), t.top());
            g.fillGradient(x, y + a, x + width, y + b, t.top(), t.mid());
            g.fillGradient(x, y + b, x + width, y + height - cut, t.mid(), t.bottom());
        } else {
            g.fillGradient(x, y + cut, x + width, y + height - cut, t.head(), t.bottom());
        }
        rect(g, x, y + height - 3, width - 1, 1, t.bottom());
        rect(g, x, y + height - 2, width - 1, 1, t.bottom());
        rect(g, x, y + height - 1, width - 2, 1, t.bottom());
        spine(g, x, y, height, t);
        // Inner bevel from the reference ::before ring; the left edge sits under the spine.
        rect(g, x + 3, y + 1, width - 4, 1, UiColor.alpha(0xFFFFFFFF, .16F));
        rect(g, x + 2, y + height - 2, width - 4, 1, UiColor.alpha(0xFF000000, .45F));
        grain(g, x, y, width, height);
    }

    /** 3px faction spine running the full panel height, cut with the top-left corner. */
    private static void spine(GuiGraphics g, int x, int y, int height, UiTheme t) {
        int top = UiColor.mix(t.head(), t.accent(), .8F);
        int mid = UiColor.mix(t.mid(), t.accent(), .8F);
        int low = UiColor.mix(t.bottom(), t.accent(), .8F);
        int a = Math.max(2, Math.round(height * .18F)), b = Math.max(a, Math.round(height * .58F));
        g.fillGradient(x, y + 2, x + 3, y + a, top, mid);
        g.fillGradient(x, y + a, x + 3, y + b, mid, mid);
        g.fillGradient(x, y + b, x + 3, y + height - 1, mid, low);
        rect(g, x + 2, y, 1, 2, top);
        rect(g, x + 1, y + 1, 2, 1, top);
        rect(g, x + 3, y + 2, 1, height - 3, UiColor.mix(t.bottom(), t.accent(), .35F));
    }

    /** 64px tile of the pack grain. Stretched blits were the source of the white speckle. */
    public static void grain(GuiGraphics g, int x, int y, int width, int height) {
        grain(g, x, y, width, height, x, y);
    }

    /**
     * Grain clipped to a sub-rectangle but tiled from {@code originX/originY}, so chrome drawn over
     * the panel (action bar, scrim) keeps the same 64px phase as the panel behind it.
     */
    public static void grain(
            GuiGraphics g, int x, int y, int width, int height, int originX, int originY) {
        if (!UiThemes.surfaceAvailable() || width <= 0 || height <= 0) return;
        int phaseX = Math.floorMod(x - originX, GRAIN), phaseY = Math.floorMod(y - originY, GRAIN);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1F, 1F, 1F, .30F);
        g.enableScissor(x, y, x + width, y + height);
        try {
            for (int ty = -phaseY; ty < height; ty += GRAIN)
                for (int tx = -phaseX; tx < width; tx += GRAIN) {
                    int sx = Math.max(0, -tx), sy = Math.max(0, -ty);
                    int w = Math.min(GRAIN - sx, width - Math.max(0, tx));
                    int h = Math.min(GRAIN - sy, height - Math.max(0, ty));
                    if (w <= 0 || h <= 0) continue;
                    g.blit(
                            GRAIN_TEXTURE,
                            x + Math.max(0, tx),
                            y + Math.max(0, ty),
                            w,
                            h,
                            (float) sx,
                            (float) sy,
                            w,
                            h,
                            GRAIN,
                            GRAIN);
                }
        } finally {
            g.setColor(1F, 1F, 1F, 1F);
            g.disableScissor();
            RenderSystem.disableBlend();
        }
    }

    /** Faction header wash: 28px tall, accent on the left fading out at 78% of the width. */
    public static void headerBand(GuiGraphics g, int x, int y, int width, int height, UiTheme t) {
        if (width <= 0 || height <= 0) return;
        int fade = Math.max(1, width * 78 / 100);
        int steps = Math.min(26, Math.max(1, fade / 4));
        for (int i = 0; i < steps; i++) {
            int x0 = fade * i / steps, x1 = fade * (i + 1) / steps;
            float position = (i + 1F) / steps;
            float alpha = position < .54F ? .22F + (.06F - .22F) * (position / .54F)
                    : .06F * (1F - (position - .54F) / .46F);
            rect(g, x + x0, y, x1 - x0, height, UiColor.alpha(t.accent(), alpha));
        }
    }

    /** Recessed content shelf. */
    public static void shelf(GuiGraphics g, int x, int y, int width, int height, UiTheme t) {
        if (width <= 0 || height <= 0) return;
        rect(g, x, y, width, height, t.inset());
        rect(g, x, y, width, 1, t.bodyShadow());
        outline(g, x, y, width, height, t.bodyEdge());
    }

    /** Height of the soft scrim the action bar fades out of. */
    public static final int SCRIM = 14;

    /**
     * Footer action bar. A hard 5px ring used to read as a black slab glued onto the textured
     * panel; the bar now fades out of a gradient scrim and keeps a translucent fill so the grain
     * underneath stays continuous.
     */
    public static void actionBar(GuiGraphics g, int x, int y, int width, int height, UiTheme t) {
        if (width <= 0 || height <= 0) return;
        for (int i = 0; i < SCRIM; i++)
            rect(
                    g,
                    x,
                    y - SCRIM + i,
                    width,
                    1,
                    UiColor.alpha(0xFF000000, .34F * (i + 1) / SCRIM));
        rect(g, x, y, width, height, t.actionBar());
        rect(g, x, y, width, 1, UiColor.alpha(0xFFFFFFFF, .05F));
    }

    public static int healthColor(double ratio) {
        return ratio > .5 ? 0xFF7FC08C : ratio >= .2 ? 0xFFDBB25F : 0xFFDC7F6F;
    }

    /** Bottom fade above the action bar; ends at 85% so it never becomes a flat dead band. */
    public static void fade(GuiGraphics g, int x, int y, int width, int height, UiTheme t) {
        if (width <= 0 || height <= 0) return;
        for (int i = 0; i < height; i++)
            rect(
                    g,
                    x,
                    y + i,
                    width,
                    1,
                    UiColor.alpha(t.inset(), .85F * (i + 1) / height));
    }

    // ---- rows and groups ------------------------------------------------------

    /** Group header band: 2x7 accent tick, optional 8px icon, muted caption, fading underline. */
    public static void groupBand(
            GuiGraphics g, int x, int y, int width, int height, UiTheme t, ResourceLocation icon) {
        if (width <= 0 || height <= 0) return;
        rect(g, x, y, width, height, t.groupBand());
        rect(g, x, y + 1, 2, 7, UiColor.alpha(t.accent(), .9F));
        if (icon != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1F, 1F, 1F, .85F);
            try {
                g.blit(icon, x + 7, y + (height - 8) / 2, 8, 8, 0F, 0F, 16, 16, 16, 16);
            } finally {
                g.setColor(1F, 1F, 1F, 1F);
                RenderSystem.disableBlend();
            }
        }
        ruleFade(g, x, y + height - 1, width - 10, UiColor.alpha(0xFF000000, .6F));
    }

    public static int groupTextX(int x, ResourceLocation icon) {
        return icon == null ? x + 7 : x + 17;
    }

    // ---- controls -------------------------------------------------------------

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
        control(g, x, y, w, h, t, hover, active, selected, danger, focused, 1F);
    }

    /** {@code sheen} scales the selected tint; 0 keeps a selected control flat. */
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
            boolean focused,
            float sheen) {
        if (w <= 0 || h <= 0) return;
        int fill, edge;
        if (!active) {
            fill = t.disabledFace();
            edge = t.disabledEdge();
        } else if (danger) {
            fill = t.dangerFace();
            edge = t.dangerEdge();
        } else if (selected) {
            fill = t.selectedFace();
            edge = t.selectedEdge();
        } else {
            fill = hover ? t.controlHover() : t.controlFace();
            edge = focused ? t.text() : t.controlEdge();
        }
        rect(g, x, y, w, h, edge);
        rect(g, x + 1, y + 1, w - 2, h - 2, fill);
        if (selected && active) {
            if (sheen > 0F)
                rect(
                        g,
                        x + 2,
                        y + 1,
                        w - 3,
                        h - 2,
                        UiColor.alpha(t.accent(), .16F * Math.min(1F, sheen)));
            rect(g, x, y, 2, h, t.accent());
        }
        if (focused && active) rect(g, x + 1, y + h - 2, w - 2, 1, t.accent());
    }

    /** Checkbox row: 8x8 well on the left, label after it. */
    public static void checkbox(
            GuiGraphics g, int x, int y, int w, int h, UiTheme t, boolean checked, boolean hover) {
        if (w <= 0 || h <= 0) return;
        rect(g, x, y, w, h, UiColor.mix(t.inset(), 0xFFFFFFFF, hover ? .05F : .03F));
        rect(g, x, y, w, 1, UiColor.mix(t.inset(), 0xFFFFFFFF, .06F));
        rect(g, x, y + h - 1, w, 1, UiColor.mix(t.inset(), 0xFFFFFFFF, .06F));
        int box = y + (h - 8) / 2;
        rect(g, x + 6, box, 8, 8, UiColor.mix(t.inset(), 0xFFFFFFFF, .28F));
        rect(g, x + 7, box + 1, 6, 6, t.inset());
        if (checked) rect(g, x + 8, box + 2, 4, 4, t.accent());
    }

    /** Slider track with rail, filled portion and knob. */
    public static void track(GuiGraphics g, int x, int y, int w, int h, double ratio, UiTheme t) {
        if (w <= 0 || h <= 0) return;
        int rail = Math.max(1, h - 8), top = y + (h - rail) / 2;
        rect(g, x, top, w, rail, t.trackEdge());
        rect(g, x + 1, top + 1, Math.max(0, w - 2), Math.max(0, rail - 2), t.track());
        int knobs = Math.max(1, w - 6);
        int at = x + (int) Math.round(Math.max(0, Math.min(1, ratio)) * knobs);
        rect(g, x, top + 1, Math.max(0, at - x), Math.max(1, rail - 2), t.accent());
        rect(g, at, y + 1, 6, h - 2, t.accent());
    }

    /** Segmented meter: 1px well gaps every 8px of fill. */
    public static void meter(
            GuiGraphics g, int x, int y, int w, int h, double value, double maximum, int color) {
        meter(g, x, y, w, h, value, maximum, color, true);
    }

    public static void meter(
            GuiGraphics g,
            int x,
            int y,
            int w,
            int h,
            double value,
            double maximum,
            int color,
            boolean segmented) {
        if (w <= 0 || h <= 0) return;
        double ratio =
                Double.isFinite(value) && Double.isFinite(maximum) && maximum > 0
                        ? Math.max(0, Math.min(1, value / maximum))
                        : 0;
        rect(g, x, y, w, h, 0xFF070D12);
        int filled = (int) Math.round(w * ratio);
        if (filled > 0) rect(g, x, y, filled, h, color);
        if (segmented)
            for (int cut = 8; cut < filled; cut += 8) rect(g, x + cut, y, 1, h, 0xFF0B141A);
        outline(g, x, y, w, h, UiColor.alpha(0xFFFFFFFF, .09F));
    }

    /**
     * Cell based health bar. Every cell holds {@link PanelGeometry#TICKS_PER_CELL} fine ticks and
     * the bar is proportional to the actual maximum, so full health always paints full.
     */
    public static void health(
            GuiGraphics g,
            int x,
            int y,
            double current,
            double maximum,
            boolean heading,
            int fixedWidth) {
        int cells = PanelGeometry.healthCells(maximum),
                perCell = PanelGeometry.TICKS_PER_CELL,
                count = cells * perCell,
                h = heading ? 9 : 8,
                gap = heading ? 4 : 3,
                tick = heading ? 2 : 1,
                inner = 1,
                cell = tick * perCell + inner * (perCell - 1),
                natural = cells * cell + (cells - 1) * gap;
        double ratio = maximum > 0 ? current / maximum : 0;
        int color = healthColor(ratio);
        for (int i = 0; i < count; i++) {
            int start = i / perCell * (cell + gap) + (i % perCell) * (tick + inner);
            int left =
                    fixedWidth > 0
                            ? x + (int) Math.floor(start * (double) fixedWidth / natural)
                            : x + start;
            int right =
                    fixedWidth > 0
                            ? x + (int) Math.floor((start + tick) * (double) fixedWidth / natural)
                            : left + tick;
            int width = Math.max(1, right - left);
            rect(g, left, y, width, h, 0xFF21303A);
            double fill = PanelGeometry.healthTick(current, maximum, i);
            if (fill > 0) rect(g, left, y, Math.max(1, (int) Math.round(width * fill)), h, color);
        }
    }

    // ---- identity -------------------------------------------------------------

    /** Faction emblem in its 14x18 badge well. Always square so the art never stretches. */
    public static void badge(
            GuiGraphics g, int x, int y, ResourceLocation icon, int width, int height, int accent) {
        if (width <= 0 || height <= 0) return;
        rect(g, x, y, width, height, UiTheme.DEFAULT.badgeEdge());
        rect(g, x + 1, y + 1, width - 2, height - 2, UiTheme.DEFAULT.badgeFace());
        if (icon == null) {
            rect(g, x + width / 2, y + 4, 1, Math.max(1, height - 8), accent);
            return;
        }
        int size = Math.min(width - 2, height - 2);
        if (size > 12) size = 12;
        int left = x + (width - size) / 2, top = y + (height - size) / 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1F, 1F, 1F, 1F);
        try {
            g.blit(icon, left, top, size, size, 0F, 0F, 96, 96, 96, 96);
        } finally {
            g.setColor(1F, 1F, 1F, 1F);
            RenderSystem.disableBlend();
        }
    }

    /** Low intensity faction watermark, square, anchored above the action bar. */
    public static void watermark(GuiGraphics g, UiRect frame, ResourceLocation emblem) {
        if (emblem == null) return;
        int size = 86;
        int x = frame.right() - 12 - size, y = frame.bottom() - 40 - size - 6;
        if (x < frame.x() || y < frame.y()) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1F, 1F, 1F, .14F);
        try {
            g.blit(emblem, x, y, size, size, 0F, 0F, 96, 96, 96, 96);
        } finally {
            g.setColor(1F, 1F, 1F, 1F);
            RenderSystem.disableBlend();
        }
    }

    /** Two pixel scroll thumb for a bounded region. */
    public static void scrollbar(
            GuiGraphics g, int x, int y, int height, int scroll, int content, UiTheme t) {
        if (height <= 0 || content <= 0) return;
        int thumb = Math.max(12, height * height / Math.max(1, content));
        thumb = Math.min(height, thumb);
        int travel = height - thumb;
        int at = y + (travel <= 0 ? 0 : travel * scroll / Math.max(1, content - height));
        rect(g, x, y, 2, height, UiColor.alpha(0xFF000000, .25F));
        rect(g, x, at, 2, thumb, UiColor.mix(t.inset(), t.accent(), .55F));
    }
}
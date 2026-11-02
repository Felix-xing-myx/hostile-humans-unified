package dev.felix.hostilehumans.client.ui.framework;

/** Size classes and scrolling math, independent of Minecraft for boundary tests. */
public final class PanelGeometry {
    public enum Size {
        S(400, 420),
        M(600, 480),
        L(720, 540);
        final int width, height;

        Size(int w, int h) {
            width = w;
            height = h;
        }
    }

    private PanelGeometry() {}

    public static UiRect frame(Size size, int screenWidth, int screenHeight) {
        int w = Math.max(1, Math.min(size.width, screenWidth - 24)),
                h = Math.max(1, Math.min(size.height, screenHeight - 24));
        return new UiRect(
                Math.max(0, (screenWidth - w) / 2), Math.max(0, (screenHeight - h) / 2), w, h);
    }

    public static int clampScroll(int scroll, int contentHeight, int viewHeight) {
        return Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewHeight)));
    }

    public static int reveal(int scroll, int itemTop, int itemBottom, int viewHeight) {
        if (itemTop < scroll) return Math.max(0, itemTop);
        if (itemBottom > scroll + viewHeight)
            return Math.max(
                    0, itemBottom - itemTop > viewHeight ? itemTop : itemBottom - viewHeight);
        return scroll;
    }

    public static int healthCells(double maximum) {
        return Math.min(8, Math.max(1, (int) Math.ceil(Math.max(0, maximum) / 25)));
    }

    public static double healthTick(double current, double maximum, int index) {
        if (!Double.isFinite(maximum) || maximum <= 0 || !Double.isFinite(current)) return 0;
        int count = healthCells(maximum) * 5;
        double capacity = maximum > 200 ? maximum : Math.max(maximum, healthCells(maximum) * 25);
        return Math.max(
                0,
                Math.min(1, Math.max(0, Math.min(maximum, current)) / (capacity / count) - index));
    }
}

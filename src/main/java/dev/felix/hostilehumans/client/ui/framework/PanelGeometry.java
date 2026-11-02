package dev.felix.hostilehumans.client.ui.framework;

/** Size classes and panel geometry, independent of Minecraft for boundary tests. */
public final class PanelGeometry {
    public static final int TITLE = 28, BAND = 24, MARGIN = 12, ACTION = 40;

    public enum Size {
        S(400, 420),
        S_PLUS(420, 520),
        M(600, 480),
        L(720, 540);
        final int width, height;

        Size(int w, int h) {
            width = w;
            height = h;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }
    }

    private PanelGeometry() {}

    public static UiRect frame(Size size, int screenWidth, int screenHeight) {
        int w = Math.max(1, Math.min(size.width, screenWidth - 24)),
                h = Math.max(1, Math.min(size.height, screenHeight - 24));
        return new UiRect(
                Math.max(0, (screenWidth - w) / 2), Math.max(0, (screenHeight - h) / 2), w, h);
    }

    /** Filter band directly under the title band. */
    public static UiRect band(UiRect frame) {
        int top = frame.y() + TITLE + 4;
        return new UiRect(frame.x() + MARGIN, top, Math.max(0, frame.width() - 2 * MARGIN), BAND);
    }

    /** Recessed content shelf; reserves the title band, the optional filter band and the action bar. */
    public static UiRect body(UiRect frame, boolean band) {
        int top = frame.y() + (band ? TITLE + 4 + BAND + 4 : TITLE + 5);
        int bottom = frame.bottom() - ACTION;
        return new UiRect(
                frame.x() + MARGIN,
                top,
                Math.max(0, frame.width() - MARGIN - 14),
                Math.max(0, bottom - top));
    }

    /** Left and right columns of a master/detail body; the divider is the left column's last pixel. */
    public static UiRect[] split(UiRect body, int leftWidth) {
        int split = body.width() <= 1 ? body.width() : Math.max(1, Math.min(body.width() - 1, leftWidth));
        return new UiRect[] {
            new UiRect(body.x(), body.y(), split, body.height()),
            new UiRect(body.x() + split, body.y(), Math.max(0, body.width() - split), body.height())
        };
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
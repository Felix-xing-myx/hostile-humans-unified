package dev.felix.hostilehumans.client.ui.framework;

/** Logical pixels; never stores a world or renderer reference. */
public record UiRect(int x, int y, int width, int height) {
    public UiRect {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }

    public boolean intersects(int top, int bottom) {
        return bottom > y && top < bottom();
    }

    public UiRect inset(int amount) {
        int a = Math.min(amount, Math.min(width, height) / 2);
        return new UiRect(x + a, y + a, width - 2 * a, height - 2 * a);
    }
}

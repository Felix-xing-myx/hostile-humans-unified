package dev.felix.hostilehumans.core;

/** Inventory utility only: this never changes nutrition, healing or birth weights. */
public final class FoodInventoryValue {
    private FoodInventoryValue() { }
    public static double quality(int nutrition, float saturation) {
        double value = 28.0 + 4.0 * nutrition + 10.0 * saturation;
        return Double.isFinite(value) ? Math.max(28.0, Math.min(90.0, value)) : 28.0;
    }
    public static double value(double quality, int count) {
        if (count <= 0 || !Double.isFinite(quality) || quality <= 0) return 0;
        int bounded = Math.min(64, count);
        return quality * (1.0 + 0.25 * (1.0 - 1.0 / Math.sqrt(bounded)));
    }
}

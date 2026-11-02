package dev.felix.hostilehumans.client.ui.framework;

/**
 * Integer colour arithmetic for the shared palette. No allocation, no GL state; every derived
 * colour is computed from theme data so faction differences stay in one place.
 */
public final class UiColor {
    private UiColor() {}

    /** Opaque RGB interpolation; the alpha channel of {@code from} is preserved. */
    public static int mix(int from, int to, float amount) {
        float t = Math.max(0F, Math.min(1F, amount));
        int a = from >>> 24;
        int r = Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
        int g = Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Replaces the alpha channel, keeping RGB. */
    public static int alpha(int color, float value) {
        int a = Math.round(Math.max(0F, Math.min(1F, value)) * 255F);
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int alpha(int color, int value) {
        return (Math.max(0, Math.min(255, value)) << 24) | (color & 0xFFFFFF);
    }

    /** Multiplies RGB toward black; alpha untouched. */
    public static int shade(int color, float factor) {
        float f = Math.max(0F, Math.min(1F, factor));
        int a = color >>> 24;
        int r = Math.round(((color >> 16) & 0xFF) * f);
        int g = Math.round(((color >> 8) & 0xFF) * f);
        int b = Math.round((color & 0xFF) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int rgb(int red, int green, int blue) {
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    /** Alpha-blends an already translucent overlay onto an opaque base. */
    public static int overlay(int base, int layer) {
        float a = (layer >>> 24) / 255F;
        if (a <= 0F) return base;
        return mix(base, layer | 0xFF000000, a);
    }
}
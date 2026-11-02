package dev.felix.hostilehumans.client.render;

/** Alpha-only mask in vanilla 64x64 skin UV space; independent of pose, camera and GPU state. */
final class SpectatorArmMask {
    // Shoulder -> elbow is six skin pixels; the hand-facing half stays fully opaque.
    private static final double FADE_START = 2;
    private static final double FADE_END = 6;
    private static final int[][] ARM_UV = {{40, 16}, {32, 48}, {40, 32}, {48, 48}};

    private SpectatorArmMask() {}

    static int pixel(int original, double u, double v) {
        for (int[] base : ARM_UV) {
            int x = base[0], y = base[1];
            // Only the shoulder cap disappears; the adjacent hand cap is untouched.
            if (u >= x + 4 && u < x + 8 && v >= y && v < y + 4)
                return original & 0x00ffffff;
            if (u >= x && u < x + 16 && v >= y + 4 && v < y + 16) {
                double t = Math.max(0, Math.min(1, (v - y - 4 - FADE_START) / (FADE_END - FADE_START)));
                double opacity = t * t * (3 - 2 * t);
                int alpha = (int) Math.round((original >>> 24) * opacity);
                return (original & 0x00ffffff) | (alpha << 24);
            }
        }
        return original;
    }
}

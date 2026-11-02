package dev.felix.hostilehumans.client.render;

/** Native-image-free skin UV checks; these do not verify live translucent rendering. */
public final class SpectatorArmMaskTest {
    public static void main(String[] args) {
        int color = 0xff563412;
        for (int[] base : new int[][]{{40, 16}, {32, 48}, {40, 32}, {48, 48}}) {
            int x = base[0], y = base[1];
            check(SpectatorArmMask.pixel(color, x + 5.5, y + 1.5) >>> 24 == 0, "shoulder cap hidden");
            check(SpectatorArmMask.pixel(color, x + 9.5, y + 1.5) == color, "hand cap untouched");
            int previous = -1;
            for (int row = 0; row < 12; row++) {
                int masked = SpectatorArmMask.pixel(color, x + .5, y + 4 + row + .5);
                int alpha = masked >>> 24;
                check(alpha >= previous, "fade follows shoulder to hand without reversing");
                if (row < 2) check(alpha == 0, "shoulder side hidden");
                if (row >= 6) check(masked == color, "entire forearm retains exact color and alpha");
                previous = alpha;
            }
            check(previous == 255, "hand end fully opaque");
        }
        for (int alpha : new int[]{0, 80, 255}) for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            int original = (color & 0x00ffffff) | (alpha << 24);
            int masked = SpectatorArmMask.pixel(original, x + .5, y + .5);
            check((masked & 0x00ffffff) == (original & 0x00ffffff), "RGB cannot change anywhere");
            check((masked >>> 24) <= alpha, "original transparency cannot become more opaque");
            // All arm faces are in the right half of the UV sheet. Head/body/legs stay untouched.
            if (x < 32) check(masked == original, "unrelated UV regions untouched");
        }
        for (int y = 0; y < 12; y++) {
            int skin = SpectatorArmMask.pixel(color, 40.5, 20.5 + y);
            int sleeve = SpectatorArmMask.pixel(color, 40.5, 36.5 + y);
            check(skin == sleeve, "skin and sleeve fade at the same arm position");
        }
        System.out.println("SpectatorArmMaskTest: caps, fade, all RGBA regions and sleeves passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

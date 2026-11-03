package dev.felix.hostilehumans.monitor.client;

/**
 * Stable centred controls with the weapon slab pinned to the bottom-right corner.
 *
 * <p>The slab has its own small native size, so it never has to be pose-scaled (which used to blur
 * its icons) and it no longer has to float above the command slab to avoid covering it. On windows
 * too narrow for both, it falls back to sitting directly above the command slab.
 */
public record MonitorHudLayout(int commandX, int commandY, int commandWidth, int weaponX, int weaponY, float weaponScale) {
    /** Native drawing size of the weapon slab; the panel is never scaled. */
    public static final int WEAPON_WIDTH = 128, WEAPON_HEIGHT = 52;
    public static final int COMMAND_HEIGHT = 62, COMMAND_BOTTOM = 72;
    /** Box the gun art is fitted into inside the slab. */
    public static final int ART_WIDTH = 54, ART_HEIGHT = 30;

    public static MonitorHudLayout of(int width, int height, boolean weapon) {
        int w = Math.max(80, Math.min(360, width - 16));
        int x = Math.max(4, (width - w) / 2), y = Math.max(33, height - COMMAND_BOTTOM);
        int wx = Math.max(4, width - WEAPON_WIDTH - 6), wy = Math.max(4, height - WEAPON_HEIGHT - 6);
        // Bottom-right by default; dodge upwards only when the two slabs would actually collide.
        if (wx < x + w + 4 && wy + WEAPON_HEIGHT > y - 4) wy = Math.max(4, y - 8 - WEAPON_HEIGHT);
        return new MonitorHudLayout(x, y, w, wx, wy, 1F);
    }
}
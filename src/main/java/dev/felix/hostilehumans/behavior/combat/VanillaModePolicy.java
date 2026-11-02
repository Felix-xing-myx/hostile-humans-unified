package dev.felix.hostilehumans.behavior.combat;

/** Base attributes change; weapon/effect modifiers remain owned by Minecraft. */
public final class VanillaModePolicy {
    private VanillaModePolicy() { }
    public static double attackBase(boolean vanilla, double configured) { return vanilla ? 1.0D : configured; }
    public static float clampedHealth(float previousHealth, double newMax) {
        return (float) Math.min(Math.max(0, previousHealth), Math.max(0, newMax));
    }
}

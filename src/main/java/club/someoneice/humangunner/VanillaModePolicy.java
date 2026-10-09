package club.someoneice.humangunner;

/** Base attributes change; weapon/effect modifiers remain owned by Minecraft. */
final class VanillaModePolicy {
    private VanillaModePolicy() { }
    static double attackBase(boolean vanilla, double configured) { return vanilla ? 1.0D : configured; }
    static float clampedHealth(float previousHealth, double newMax) {
        return (float) Math.min(Math.max(0, previousHealth), Math.max(0, newMax));
    }
}

package club.someoneice.humangunner;

/** Base attributes change; weapon/effect modifiers remain owned by Minecraft. */
final class VanillaModePolicy {
    private VanillaModePolicy() { }
    static double attackBase(boolean vanilla, double configured) { return vanilla ? 1.0D : configured; }
    static double restoredBase(boolean tierOwned, double savedBase, int savedRoll, double currentTierOriginal) {
        return !tierOwned ? savedBase : savedRoll > 0 ? savedRoll : currentTierOriginal;
    }
    static float transitionedHealth(float previousHealth, double previousMax, double newMax) {
        double ratio = Math.max(0.0D, Math.min(1.0D, previousHealth / Math.max(1.0D, previousMax)));
        return (float) (newMax * ratio);
    }
}

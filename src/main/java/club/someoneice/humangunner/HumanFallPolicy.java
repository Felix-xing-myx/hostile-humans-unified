package club.someoneice.humangunner;

/** Plan with a small current-health budget; never treat high maximum health as a jump license. */
public final class HumanFallPolicy {
    public static final int MAX_PLANNED_DROP = 12;
    private HumanFallPolicy() {}

    public static double damageBudget(double health) {
        if (!Double.isFinite(health) || health <= 0.0D) return 0.0D;
        return Math.max(0.0D, Math.min(6.0D, Math.min(health * 0.20D, health - 4.0D)));
    }

    public static boolean accepts(double health, double drop, double rawDamage) {
        return Double.isFinite(drop) && drop >= 0 && drop <= MAX_PLANNED_DROP
                && Double.isFinite(rawDamage) && rawDamage >= 0 && rawDamage <= damageBudget(health);
    }
}

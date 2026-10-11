package club.someoneice.humangunner;

/** Pure impact-time budget; a commitment is not a permanent immunity to avoidance. */
public final class CreeperFinishPolicy {
    private CreeperFinishPolicy() {}

    public static boolean canFinish(double healthAndAbsorption, double firstDamage, double subsequentDamage,
                                    int firstImpactTicks, int hitIntervalTicks, int fuseTicks) {
        return canFinish(healthAndAbsorption, firstDamage, subsequentDamage, firstImpactTicks,
                hitIntervalTicks, fuseTicks, false);
    }

    public static boolean canFinish(double healthAndAbsorption, double firstDamage, double subsequentDamage,
                                    int firstImpactTicks, int hitIntervalTicks, int fuseTicks, boolean bypassesCooldown) {
        if (!Double.isFinite(healthAndAbsorption) || !Double.isFinite(firstDamage)
                || !Double.isFinite(subsequentDamage)
                || healthAndAbsorption <= 0 || firstDamage <= 0 || subsequentDamage <= 0 || firstImpactTicks < 0
                || hitIntervalTicks <= 0 || fuseTicks <= 2) return false;
        double extraHits = Math.ceil(Math.max(0, healthAndAbsorption - firstDamage) / subsequentDamage);
        // Forecast the real attack interval; only vanilla cooldown-limited hits need the ten-tick floor.
        double killTick = firstImpactTicks + extraHits * (bypassesCooldown ? hitIntervalTicks : Math.max(10, hitIntervalTicks));
        return killTick + 2 < fuseTicks;
    }
}

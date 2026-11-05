package dev.felix.hostilehumans.behavior.combat.recovery;

/** Non-player potion charge and total regeneration cadence. */
public final class PotionRecoveryPolicy {
    private PotionRecoveryPolicy() {}

    public static boolean needsPotionCharge(boolean samePotion, int before, int after) {
        return samePotion && before > 0 && before == after;
    }

    static boolean regenerationActive(int duration) { return duration > 0 || duration == -1; }
    static boolean urgentInstantHealing(double actualHealthRatio) { return actualHealthRatio < 0.50D; }
    static int instantHealingUseDuration(int originalTicks) { return Math.min(20, Math.max(1, originalTicks)); }
    static final int REGENERATION_INTERVAL_TICKS = 10;
    static float instantHealingAmount(int amplifier, float maximumHealth, double exposure, double regenerationMultiplier) {
        if (!Double.isFinite(exposure) || exposure <= 0) return 0;
        double strength = Math.min(1, exposure);
        double base = Math.floor((amplifier >= 1 ? 12 : 6) * strength + .5);
        double bonus = Float.isFinite(maximumHealth) && maximumHealth > 0
                ? maximumHealth * (amplifier >= 1 ? .12 : .06) * strength : 0;
        return (float) (base * regenerationMultiplier + bonus);
    }
    static float regenerationStep(int amplifier) { return amplifier >= 1 ? 1.0F : 0.5F; }

    static final class Clock {
        private int next;
        Clock(int now, int interval) { next = now + Math.max(1, interval); }
        boolean pulse(int now, int interval) {
            if (now < next) return false;
            next = now + Math.max(1, interval);
            return true;
        }
    }
}

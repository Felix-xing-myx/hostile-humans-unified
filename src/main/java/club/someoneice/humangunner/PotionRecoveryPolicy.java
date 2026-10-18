package club.someoneice.humangunner;

/** Non-player potion charge and total regeneration cadence. */
final class PotionRecoveryPolicy {
    private PotionRecoveryPolicy() {}

    static boolean needsPotionCharge(boolean samePotion, int before, int after) {
        return samePotion && before > 0 && before == after;
    }

    static boolean regenerationActive(int duration) { return duration > 0 || duration == -1; }
    static boolean urgentInstantHealing(double actualHealthRatio) { return actualHealthRatio < 0.30D; }
    static int instantHealingUseDuration(int originalTicks) { return Math.min(20, Math.max(1, originalTicks)); }
    static final int REGENERATION_INTERVAL_TICKS = 10;
    static int instantHealingAmount(double exposure) {
        return Double.isFinite(exposure) ? (int) Math.floor(8.0D * Math.max(0.0D, Math.min(1.0D, exposure)) + 0.5D) : 0;
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

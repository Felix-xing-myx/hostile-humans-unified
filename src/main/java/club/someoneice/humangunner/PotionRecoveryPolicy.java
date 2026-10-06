package club.someoneice.humangunner;

/** Non-player potion charge and independent regeneration cadence. */
final class PotionRecoveryPolicy {
    private PotionRecoveryPolicy() {}

    static boolean needsPotionCharge(boolean samePotion, int before, int after) {
        return samePotion && before > 0 && before == after;
    }

    static boolean regenerationActive(int duration) { return duration > 0 || duration == -1; }
    static boolean urgentInstantHealing(double actualHealthRatio) { return actualHealthRatio < 0.30D; }
    static int instantHealingUseDuration(int originalTicks) { return Math.min(20, Math.max(1, originalTicks)); }
    static float regenerationStep(int amplifier) { return amplifier >= 1 ? 2.0F : 1.0F; }

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

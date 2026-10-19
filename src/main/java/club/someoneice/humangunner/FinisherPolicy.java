package club.someoneice.humangunner;

/** A bounded opportunity score, separate from defensive threat and command permissions. */
final class FinisherPolicy {
    // Central policy defaults; these are implementation constants, not JSON configuration keys.
    static final double MAX_HEALTH_FRACTION = .35;
    static final int INTERRUPTION_LIMIT = 3;
    static final int INTERRUPTION_WINDOW_TICKS = 200;
    static final int ABANDON_TICKS = 300;
    private FinisherPolicy() {}
    static double score(double maximumHealth, double health, double distance) {
        if (!(maximumHealth > 0) || !(health > 0) || !Double.isFinite(maximumHealth + health + distance)) return 0;
        double remaining = health / maximumHealth;
        if (remaining > MAX_HEALTH_FRACTION) return 0;
        // High maximum health is valuable, but one gigantic modded boss must not dominate forever.
        double value = Math.min(4, Math.sqrt(maximumHealth / 20));
        return value * (1 - remaining) / (1 + Math.max(0, distance) / 16);
    }
    static final class Attempts {
        private long window = Long.MIN_VALUE, blockedUntil = Long.MIN_VALUE, lastInterrupt = Long.MIN_VALUE;
        private int interruptions;
        void interrupt(long now) {
            if (lastInterrupt == now) return;
            lastInterrupt = now;
            if (window == Long.MIN_VALUE || now < window || now - window > INTERRUPTION_WINDOW_TICKS) { window = now; interruptions = 0; }
            if (++interruptions >= INTERRUPTION_LIMIT) { blockedUntil = now + ABANDON_TICKS; interruptions = 0; window = now; }
        }
        boolean blocked(long now) { return now < blockedUntil; }
    }
}

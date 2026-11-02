package dev.felix.hostilehumans.faction;

/**
 * Snapshot-before-event daily limits. A later political promotion never rewrites an earlier
 * receipt.
 */
public record StandingQuota(boolean dynamic, int hostile, int cautious, int friendly, int fixed) {
    public StandingQuota {
        for (int value : new int[] {hostile, cautious, friendly, fixed})
            if (value < 0 || value > 1_000_000)
                throw new IllegalArgumentException("standing quota");
    }

    public int cap(int trueStandingBeforeEvent) {
        return dynamic
                ? trueStandingBeforeEvent < -99
                        ? hostile
                        : trueStandingBeforeEvent < 150 ? cautious : friendly
                : fixed;
    }

    public static StandingQuota defaults() {
        return new StandingQuota(true, 150, 100, 60, 30);
    }
}

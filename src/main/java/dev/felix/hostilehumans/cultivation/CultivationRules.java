package dev.felix.hostilehumans.cultivation;

/** Central first-version test defaults; no material acquisition recipe or hidden facility bonus. */
public final class CultivationRules {
    public static final long UNIT = 1_000_000L, STEP = 100_000L, PERIOD_MILLIS = 600_000L;
    public static final double SUCCESS = .70, POTENTIAL_MEDIAN = .60, POTENTIAL_LOG_SIGMA = .60;
    public static final long MAX_POTENTIAL = 6 * UNIT, POTENTIAL_ITEM = 200_000L;
    public static final int SEATS = 3, SCHEMA = 1;
    public static final java.util.List<String> DIMENSIONS =
            java.util.List.of(
                    "health",
                    "armor",
                    "toughness",
                    "knockback_resistance",
                    "speed",
                    "detection",
                    "melee",
                    "gun",
                    "projectile",
                    "accuracy",
                    "regeneration",
                    "attack_speed",
                    "armor_penetration");

    private CultivationRules() {}

    public static long micro(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1_000_000)
            throw new IllegalArgumentException("Invalid cultivation number");
        return Math.round(value * UNIT);
    }

    public static long mix(long x) {
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }

    public static long seed(java.util.UUID entity, long allocationSeed) {
        return mix(
                entity.getMostSignificantBits()
                        ^ entity.getLeastSignificantBits()
                        ^ allocationSeed
                        ^ 0x43554c5449564154L);
    }

    public static long potential(long seed) {
        var random = new java.util.Random(mix(seed ^ 0x504f54454e544941L));
        for (int i = 0; i < 10000; i++) {
            double value =
                    Math.exp(
                            Math.log(POTENTIAL_MEDIAN)
                                    + POTENTIAL_LOG_SIGMA * random.nextGaussian());
            if (value > 0 && value <= 6) return Math.max(1, micro(value));
        }
        throw new IllegalStateException("Truncated growth draw failed");
    }
}

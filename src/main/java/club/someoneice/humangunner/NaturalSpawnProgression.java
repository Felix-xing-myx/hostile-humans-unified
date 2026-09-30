package club.someoneice.humangunner;

/** Persistent mod-day gates for automatic encounters, not player-requested summons. */
public record NaturalSpawnProgression(boolean enabled, int safeDays,
        int roamerFirstDay, int tier1FirstDay, int tier2FirstDay, int tier3FirstDay) {
    private static final long TICKS_PER_DAY = 24000L;

    public static long dayAt(long progressionTicks) {
        return Math.max(0L, progressionTicks) / TICKS_PER_DAY + 1L;
    }

    public boolean allowsTier(long progressionTicks, int tier) {
        int firstDay = switch (tier) {
            case 0 -> roamerFirstDay;
            case 1 -> tier1FirstDay;
            case 2 -> tier2FirstDay;
            case 3 -> tier3FirstDay;
            default -> Integer.MAX_VALUE;
        };
        if (tier < 0 || tier > 3) return false;
        return !enabled || dayAt(progressionTicks) >= Math.max((long) safeDays + 1L, firstDay);
    }

    /** The legacy battle event only contains tier I and tier II humans. */
    public boolean allowsBattle(long progressionTicks) {
        return allowsTier(progressionTicks, 1) || allowsTier(progressionTicks, 2);
    }
}

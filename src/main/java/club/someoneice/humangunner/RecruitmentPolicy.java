package club.someoneice.humangunner;

final class RecruitmentPolicy {
    private static final int[] COSTS = {8, 24, 72, 216};
    private static final int[] WAVE_SIZES = {5, 4, 3, 2};
    private RecruitmentPolicy() {}

    static int cost(int tier) { return COSTS[tier]; }
    static int limit(int tier) { return UnifiedConfig.get().recruitmentLimit(tier); }
    static int limit(int tier, UnifiedConfig config) { return config.recruitmentLimit(tier); }
    static int waveSize(int tier) { return WAVE_SIZES[tier]; }
    static boolean hasContractClearance(boolean creative, boolean neutral, boolean retaliating) {
        return creative || (neutral && !retaliating);
    }
    static boolean betrayed(float projectedHealth, float maxHealth) {
        return maxHealth > 0.0F && projectedHealth <= maxHealth * 0.25F;
    }
}

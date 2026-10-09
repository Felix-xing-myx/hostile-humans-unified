package club.someoneice.humangunner;

/** Deterministic progression arithmetic shared by server state and regression tests. */
final class GrowthProgression {
    record Result(int level, int experience, double stageProgress, double experienceProgress) { }
    private GrowthProgression() { }

    static Result advance(GrowthConfig config, int level, int experience, double stageProgress,
                          double experienceProgress, double reward) {
        level = Math.max(0, Math.min(5, level));
        int cap = (int) Math.round(config.experienceCapPercent());
        experience = Math.max(1, Math.min(cap, experience));
        stageProgress = rounded(finite(stageProgress) + finite(reward));
        experienceProgress = rounded(finite(experienceProgress) + finite(reward));
        while (level < 5 && stageProgress + 1E-8 >= config.stageCost(level)) {
            stageProgress = rounded(stageProgress - config.stageCost(level));
            level++;
        }
        int gains = (int) Math.min(cap - experience, Math.floor((experienceProgress + 1E-8) / config.experienceCost()));
        experience += gains;
        experienceProgress = rounded(experienceProgress - gains * config.experienceCost());
        return new Result(level, experience, level == 5 ? 0 : Math.max(0, stageProgress),
                experience == cap ? 0 : Math.max(0, experienceProgress));
    }

    static double reward(double damage, boolean crossedBonus, double damageUnit, double rewardUnit,
                         double baseCap, double bonusReward) {
        double base = Math.min(baseCap, Math.floor((damage + 1E-8) / damageUnit) * rewardUnit);
        return rounded(base + (crossedBonus ? bonusReward : 0));
    }

    static double rounded(double value) { return Math.rint(value * 1_000_000.0) / 1_000_000.0; }
    private static double finite(double value) { return Double.isFinite(value) ? Math.max(0, value) : 0; }
}

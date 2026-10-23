package club.someoneice.humangunner;

import com.google.gson.JsonObject;

/** Immutable birth policy. Replacing this policy never rewrites an allocation. */
public record SpecialtyConfig(boolean enabled, double budgetMean, double budgetSigma,
        double budgetMin, double budgetMax, double deviationSigma, double deviationCap,
        double capBudgetExponent, double multiplierMin, double multiplierMax,
        double penetrationSeed, double attackSpeedMin, double attackSpeedMax,
        java.util.Map<String, Double> tierBudgetMultipliers) {
    public SpecialtyConfig {
        tierBudgetMultipliers = java.util.Map.copyOf(tierBudgetMultipliers);
        tierBudgetMultipliers.forEach((tier, value) -> {
            if (!java.util.Set.of("roamer", "tier1", "tier2", "tier3").contains(tier)
                    || !Double.isFinite(value) || value <= 0 || value > 3)
                throw new IllegalArgumentException("invalid tier budget multiplier: " + tier);
        });
    }
    public SpecialtyConfig(boolean enabled, double budgetMean, double budgetSigma, double budgetMin, double budgetMax,
            double deviationSigma, double deviationCap, double capBudgetExponent, double multiplierMin, double multiplierMax,
            double penetrationSeed, double attackSpeedMin, double attackSpeedMax) {
        this(enabled, budgetMean, budgetSigma, budgetMin, budgetMax, deviationSigma, deviationCap,
                capBudgetExponent, multiplierMin, multiplierMax, penetrationSeed, attackSpeedMin, attackSpeedMax,
                defaultTierMultipliers());
    }
    private static java.util.Map<String, Double> defaultTierMultipliers() {
        return java.util.Map.of("roamer", 1.0, "tier1", 1.0, "tier2", 1.1, "tier3", 1.2);
    }
    public double tierBudgetMultiplier(String tier) { return tierBudgetMultipliers.getOrDefault(tier, 1.0); }
    private static java.util.Map<String, Double> tierMultipliers(JsonObject data) {
        var values = new java.util.HashMap<>(defaultTierMultipliers());
        if (data.has("budget_tier_multipliers")) {
            JsonObject overrides = data.getAsJsonObject("budget_tier_multipliers");
            for (var entry : overrides.entrySet()) {
                if (entry.getKey().startsWith("_")) continue;
                if (!values.containsKey(entry.getKey())) throw new IllegalArgumentException("unknown tier budget key");
                values.put(entry.getKey(), number(overrides, entry.getKey(), values.get(entry.getKey()), Double.MIN_NORMAL, 3));
            }
        }
        return values;
    }
    public SpecialtyConfig(boolean enabled, double budgetMean, double budgetSigma, double budgetMin, double budgetMax,
            double deviationSigma, double deviationCap, double capBudgetExponent, double multiplierMin, double multiplierMax) {
        this(enabled, budgetMean, budgetSigma, budgetMin, budgetMax, deviationSigma, deviationCap,
                capBudgetExponent, multiplierMin, multiplierMax, .01, multiplierMin, multiplierMax);
    }
    private static volatile SpecialtyConfig current = defaults();
    public static SpecialtyConfig defaults() {
        return new SpecialtyConfig(true, 1.10, .10, .80, 1.40, .20, .45, .60, .50, 1.80);
    }
    public static SpecialtyConfig get() { return current; }
    public static SpecialtyConfig prepare(JsonObject data) {
        SpecialtyConfig candidate = new SpecialtyConfig(
                !data.has("enabled") || data.get("enabled").getAsBoolean(),
                number(data, "budget_mean", 1.10, .5, 2), number(data, "budget_sigma", .10, 0, .5),
                number(data, "budget_min", .80, .3, 1), number(data, "budget_max", 1.40, 1, 3),
                number(data, "deviation_sigma", .20, 0, 1), number(data, "deviation_cap", .45, .05, 1),
                number(data, "cap_budget_exponent", .60, 0, 1),
                number(data, "multiplier_min", .50, .2, 1), number(data, "multiplier_max", 1.80, 1, 3),
                number(data, "penetration_seed", .01, Double.MIN_NORMAL, .45),
                number(data, "attack_speed_min", number(data, "multiplier_min", .50, .2, 1), Double.MIN_NORMAL, 1),
                number(data, "attack_speed_max", number(data, "multiplier_max", 1.80, 1, 3), 1, Double.MAX_VALUE),
                tierMultipliers(data));
        if (candidate.budgetMin > candidate.budgetMax || candidate.multiplierMin > candidate.multiplierMax || candidate.attackSpeedMin > candidate.attackSpeedMax
                || candidate.budgetMean < candidate.budgetMin || candidate.budgetMean > candidate.budgetMax)
            throw new IllegalArgumentException("inverted bounds");
        return candidate;
    }
    public static void publish(SpecialtyConfig candidate) { current = java.util.Objects.requireNonNull(candidate); }
    public static void configure(JsonObject data) {
        try { publish(prepare(data)); }
        catch (RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().warn("Invalid specialty configuration; retaining previous policy: {}", failure.getMessage());
        }
    }
    private static double number(JsonObject data, String key, double fallback, double min, double max) {
        if (!data.has(key)) return fallback;
        double value = data.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException(key + " must be finite in [" + min + "," + max + "]");
        return value;
    }
}

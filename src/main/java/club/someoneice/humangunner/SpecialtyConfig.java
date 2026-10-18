package club.someoneice.humangunner;

import com.google.gson.JsonObject;

/** Immutable birth policy. Replacing this policy never rewrites an allocation. */
public record SpecialtyConfig(boolean enabled, double budgetMean, double budgetSigma,
        double budgetMin, double budgetMax, double deviationSigma, double deviationCap,
        double capBudgetExponent, double multiplierMin, double multiplierMax) {
    private static volatile SpecialtyConfig current = defaults();
    public static SpecialtyConfig defaults() {
        return new SpecialtyConfig(true, 1, .10, .75, 1.25, .15, .30, .25, .50, 1.80);
    }
    public static SpecialtyConfig get() { return current; }
    public static SpecialtyConfig prepare(JsonObject data) {
        SpecialtyConfig candidate = new SpecialtyConfig(
                !data.has("enabled") || data.get("enabled").getAsBoolean(),
                number(data, "budget_mean", 1, .5, 2), number(data, "budget_sigma", .10, 0, .5),
                number(data, "budget_min", .75, .3, 1), number(data, "budget_max", 1.25, 1, 3),
                number(data, "deviation_sigma", .15, 0, 1), number(data, "deviation_cap", .30, .05, 1),
                number(data, "cap_budget_exponent", .25, 0, 1),
                number(data, "multiplier_min", .50, .2, 1), number(data, "multiplier_max", 1.80, 1, 3));
        if (candidate.budgetMin > candidate.budgetMax || candidate.multiplierMin > candidate.multiplierMax)
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

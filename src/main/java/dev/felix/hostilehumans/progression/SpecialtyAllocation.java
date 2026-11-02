package dev.felix.hostilehumans.progression;

/** Pure allocation arithmetic; all inputs and outputs use positive utility, including accuracy. */
public final class SpecialtyAllocation {
    private SpecialtyAllocation() { }
    public record Result(double[] multipliers, double[] lower, double[] upper, boolean feasible, double residual) { }

    public static double[] center(double[] raw, double[] costs) {
        if (raw.length != costs.length || raw.length == 0) throw new IllegalArgumentException("dimensions");
        double sum = 0, cost = baselineCost(costs);
        for (int i = 0; i < raw.length; i++) {
            positive(raw[i]); positive(costs[i]);
            sum += costs[i] * raw[i];
        }
        positive(sum); positive(cost);
        double[] result = raw.clone();
        for (int i = 0; i < result.length; i++) result[i] *= cost / sum;
        return result;
    }

    /** The appended penetration dimension is a zero-baseline ratio, not a unit multiplier. */
    public static double baselineCost(double[] costs) {
        double total = 0;
        for (int i = 0; i < costs.length; i++) if (costs.length != 13 || i != 12) total += costs[i];
        return total;
    }

    public static Result allocate(double budget, double[] preference, double[] costs, double[] caps,
                                  double[] tilts, double alpha, double absoluteMin, double absoluteMax) {
        return allocate(budget, preference, costs, caps, tilts, alpha,
                SpecialtyBounds.uniform(costs.length, absoluteMin, absoluteMax));
    }

    public static Result allocate(double budget, double[] preference, double[] costs, double[] caps,
                                  double[] tilts, double alpha, SpecialtyBounds.Limits limits) {
        positive(budget);
        if (!Double.isFinite(alpha) || alpha < 0 || alpha > 1)
            throw new IllegalArgumentException("alpha must be finite in [0,1]");
        int n = costs.length;
        if (n != caps.length || n != tilts.length) throw new IllegalArgumentException("dimensions");
        double[] center = center(preference, costs);
        SpecialtyBounds.Bounds bounds = SpecialtyBounds.at(budget, center, caps, alpha, limits);
        double[] raw = new double[n];
        double cost = baselineCost(costs), denominator = 0;
        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(caps[i]) || caps[i] < 0 || caps[i] > 1) throw new IllegalArgumentException("cap");
            positive(tilts[i]);
            raw[i] = center[i] * clamp(tilts[i], 1 - caps[i], 1 + caps[i]);
            denominator += raw[i] * costs[i];
        }
        positive(denominator);
        for (int i = 0; i < n; i++) raw[i] *= budget * cost / denominator;
        return solve(raw, costs, bounds.lower(), bounds.upper(), budget * cost);
    }

    /** Infeasible totals keep boundaries, return the nearest attainable total, and report residual. */
    public static Result solve(double[] raw, double[] costs, double[] lower, double[] upper, double target) {
        int n = raw.length;
        if (n == 0 || costs.length != n || lower.length != n || upper.length != n || !Double.isFinite(target))
            throw new IllegalArgumentException("dimensions/target");
        double min = 0, max = 0, right = 0;
        for (int i = 0; i < n; i++) {
            positive(raw[i]); positive(costs[i]);
            if (!Double.isFinite(lower[i]) || !Double.isFinite(upper[i]) || lower[i] < 0 || lower[i] > upper[i])
                throw new IllegalArgumentException("empty or invalid merged bounds at " + i);
            min += costs[i] * lower[i]; max += costs[i] * upper[i];
            right = Math.max(right, upper[i] / raw[i]);
        }
        boolean feasible = target >= min - 1e-12 && target <= max + 1e-12;
        double wanted = clamp(target, min, max), left = 0;
        double[] values = new double[n];
        for (int iteration = 0; iteration < 64; iteration++) {
            double scale = left + (right - left) / 2, total = 0;
            for (int i = 0; i < n; i++) {
                values[i] = clamp(scale * raw[i], lower[i], upper[i]);
                total += costs[i] * values[i];
            }
            if (Math.abs(total - wanted) <= 1e-10 * Math.max(1, Math.abs(wanted))) break;
            if (total < wanted) left = scale; else right = scale;
        }
        double total = 0;
        for (int i = 0; i < n; i++) total += costs[i] * values[i];
        return new Result(values, lower.clone(), upper.clone(), feasible, total - target);
    }
    static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    static void positive(double value) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("finite positive input required");
    }
}

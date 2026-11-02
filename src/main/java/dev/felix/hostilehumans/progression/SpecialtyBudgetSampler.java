package dev.felix.hostilehumans.progression;

/** Fixed-work inverse-CDF sampling, including intervals too narrow for ordinary rejection sampling. */
public final class SpecialtyBudgetSampler {
    private SpecialtyBudgetSampler() { }

    public static double sample(double uniform, double mean, double sigma, double minimum, double maximum) {
        if (!Double.isFinite(uniform) || uniform < 0 || uniform >= 1 || !Double.isFinite(mean)
                || !Double.isFinite(sigma) || sigma < 0 || !Double.isFinite(minimum)
                || !Double.isFinite(maximum) || minimum > maximum)
            throw new IllegalArgumentException("invalid truncated normal parameters");
        if (sigma == 0 || minimum == maximum) return Math.max(minimum, Math.min(maximum, mean));
        if (mean < minimum || mean > maximum) throw new IllegalArgumentException("mean outside truncation interval");
        // Beyond twelve standard deviations, tail probabilities are below the resolution of the
        // 53-bit uniform source. Limiting the inverse search also handles arbitrarily small sigma.
        double left = Math.max(-12, (minimum - mean) / sigma);
        double right = Math.min(12, (maximum - mean) / sigma);
        double low = centeredCdf(left), high = centeredCdf(right);
        // Centered CDF avoids subtracting two rounded values near 0.5 in very thin intervals.
        double quantile = low + uniform * (high - low);
        for (int step = 0; step < 64; step++) {
            double middle = left + (right - left) / 2;
            if (centeredCdf(middle) < quantile) left = middle; else right = middle;
        }
        return mean + sigma * (left + (right - left) / 2);
    }

    /** erf(z/sqrt(2)), evaluated with a positive, convergent incomplete-gamma series.
     * Unlike an approximation with a constant error term, it retains relative precision near zero. */
    private static double centeredCdf(double z) {
        double x = Math.abs(z) / Math.sqrt(2);
        if (x >= 8) return Math.copySign(1, z);
        double square = x * x, term = 1, sum = 1;
        for (int n = 1; n <= 256; n++) {
            term *= 2 * square / (2 * n + 1);
            sum += term;
            if (term <= sum * 1e-16) break;
        }
        double erf = 2 / Math.sqrt(Math.PI) * x * Math.exp(-square) * sum;
        return Math.copySign(Math.min(1, erf), z);
    }
}

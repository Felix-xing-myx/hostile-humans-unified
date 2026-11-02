package dev.felix.hostilehumans.economy;

import java.math.BigInteger;

/**
 * Exact conversion/price factor. Round only once at the settlement boundary, never once per coin.
 */
public record MoneyRate(long numerator, long denominator) {
    public MoneyRate {
        if (numerator <= 0
                || denominator <= 0
                || numerator > 1_000_000_000
                || denominator > 1_000_000_000) throw new IllegalArgumentException("money rate");
        long gcd = gcd(numerator, denominator);
        numerator /= gcd;
        denominator /= gcd;
    }

    public long floor(long amount) {
        return rounded(amount, false);
    }

    public long ceil(long amount) {
        return rounded(amount, true);
    }

    private long rounded(long amount, boolean ceil) {
        if (amount < 0) throw new IllegalArgumentException("negative money");
        BigInteger product = BigInteger.valueOf(amount).multiply(BigInteger.valueOf(numerator));
        var qr = product.divideAndRemainder(BigInteger.valueOf(denominator));
        return (ceil && qr[1].signum() > 0 ? qr[0].add(BigInteger.ONE) : qr[0]).longValueExact();
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long r = a % b;
            a = b;
            b = r;
        }
        return a;
    }
}

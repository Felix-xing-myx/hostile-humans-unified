package dev.felix.hostilehumans.economy;

import net.minecraft.nbt.CompoundTag;

import java.math.BigInteger;

/**
 * Compose existing MoneyRate values exactly; round once for the full quote, never per coin/item.
 */
public final class MoneyMath {
    private MoneyMath() {}

    private static long divide(BigInteger numerator, BigInteger denominator, boolean up) {
        var qr = numerator.divideAndRemainder(denominator);
        return (up && qr[1].signum() > 0 ? qr[0].add(BigInteger.ONE) : qr[0]).longValueExact();
    }

    public static long quote(
            long base,
            int count,
            MoneyRate coefficient,
            int factorNumerator,
            int factorDenominator,
            boolean buy) {
        if (base < 0 || count < 1 || count > 4096 || factorNumerator < 0 || factorDenominator < 1)
            throw new IllegalArgumentException("quote inputs");
        BigInteger numerator =
                BigInteger.valueOf(base)
                        .multiply(BigInteger.valueOf(count))
                        .multiply(BigInteger.valueOf(coefficient.denominator()))
                        .multiply(BigInteger.valueOf(factorNumerator));
        return divide(
                numerator,
                BigInteger.valueOf(coefficient.numerator())
                        .multiply(BigInteger.valueOf(factorDenominator)),
                buy);
    }

    public static long exchange(long amount, MoneyRate from, MoneyRate to) {
        if (amount < 1) throw new IllegalArgumentException("positive exchange amount required");
        return divide(
                BigInteger.valueOf(amount)
                        .multiply(BigInteger.valueOf(from.numerator()))
                        .multiply(BigInteger.valueOf(to.denominator()))
                        .multiply(BigInteger.valueOf(9)),
                BigInteger.valueOf(from.denominator())
                        .multiply(BigInteger.valueOf(to.numerator()))
                        .multiply(BigInteger.TEN),
                false);
    }

    /**
     * Exact common-base expenditure, including denominator remainder; splitting cannot lower the
     * daily charge.
     */
    public static void exchangeAllowance(
            CompoundTag account, long now, long amount, MoneyRate coefficient, long limit) {
        if (limit < 1) throw new IllegalArgumentException("exchange daily cap not configured");
        long previous = account.getLong("exchange_day");
        long day = Math.max(previous, Math.max(0, now) / 24000);
        BigInteger used = BigInteger.ZERO, denominator = BigInteger.ONE;
        if (day == previous && account.contains("exchange_used_n")) {
            String n = account.getString("exchange_used_n"),
                    d = account.getString("exchange_used_d");
            if (n.length() > 128 || d.length() > 128)
                throw new IllegalArgumentException("exchange counter bounds");
            used = new BigInteger(n);
            denominator = new BigInteger(d);
            if (used.signum() < 0 || denominator.signum() <= 0)
                throw new IllegalArgumentException("invalid retained exchange counter");
        }
        BigInteger rateDen = BigInteger.valueOf(coefficient.denominator());
        BigInteger next =
                used.multiply(rateDen)
                        .add(
                                BigInteger.valueOf(amount)
                                        .multiply(BigInteger.valueOf(coefficient.numerator()))
                                        .multiply(denominator));
        BigInteger common = denominator.multiply(rateDen), gcd = next.gcd(common);
        next = next.divide(gcd);
        common = common.divide(gcd);
        if (next.compareTo(BigInteger.valueOf(limit).multiply(common)) > 0)
            throw new IllegalArgumentException("daily exchange cap exhausted");
        account.putLong("exchange_day", day);
        account.putString("exchange_used_n", next.toString());
        account.putString("exchange_used_d", common.toString());
    }

    public static String remainingAllowance(CompoundTag account, long now, long limit) {
        try {
            long previous = account.getLong("exchange_day");
            if (Math.max(0, now) / 24000 > previous || !account.contains("exchange_used_n"))
                return limit + " Azure copper";
            String rawN = account.getString("exchange_used_n"),
                    rawD = account.getString("exchange_used_d");
            if (rawN.length() > 128 || rawD.length() > 128) throw new IllegalArgumentException();
            var n = new BigInteger(rawN);
            var d = new BigInteger(rawD);
            if (n.signum() < 0 || d.signum() <= 0) throw new IllegalArgumentException();
            var remaining = BigInteger.valueOf(limit).multiply(d).subtract(n).max(BigInteger.ZERO);
            var gcd = remaining.gcd(d);
            remaining = remaining.divide(gcd);
            d = d.divide(gcd);
            return remaining + (d.equals(BigInteger.ONE) ? "" : "/" + d) + " Azure copper";
        } catch (RuntimeException bad) {
            return "unknown retained exchange counter";
        }
    }

    public static int buyFactor(int standing) {
        return standing < 50 ? 15 : standing < 150 ? 13 : standing < 250 ? 11 : 10;
    }

    public static int sellFactor(int standing) {
        return standing < 50
                ? 5
                : standing < 150 ? 7 : standing < 250 ? 8 : standing < 400 ? 9 : 10;
    }
}

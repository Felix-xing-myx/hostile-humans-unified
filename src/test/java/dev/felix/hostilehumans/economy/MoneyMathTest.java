package dev.felix.hostilehumans.economy;

import net.minecraft.nbt.CompoundTag;

public final class MoneyMathTest {
    private static int checks;

    private static void require(boolean v, String reason) {
        checks++;
        if (!v) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        MoneyRate[] rates = {
            new MoneyRate(3, 4), new MoneyRate(1, 1), new MoneyRate(7, 5), new MoneyRate(9, 5)
        };
        require(MoneyMath.quote(100, 3, rates[3], 15, 10, true) == 250, "one ceil for full batch");
        require(
                MoneyMath.quote(100, 1, rates[3], 15, 10, true) * 3 == 252,
                "per-item quote must not be multiplied by client");
        for (var from : rates)
            for (var to : rates)
                for (long amount :
                        new long[] {1, 2, 3, 9, 10, 99, 100, 9999, 10000, 1_000_000_000}) {
                    long output = MoneyMath.exchange(amount, from, to);
                    if (output > 0)
                        require(
                                MoneyMath.exchange(output, to, from) <= amount,
                                "no two-way arbitrage");
                    if (amount > 1)
                        require(
                                MoneyMath.exchange(amount / 2, from, to)
                                                + MoneyMath.exchange(amount - amount / 2, from, to)
                                        <= output,
                                "splitting cannot improve output");
                }
        for (var rate : rates)
            for (int score : new int[] {-99, 49, 50, 149, 150, 249, 250, 399, 400, 500})
                for (int count : new int[] {1, 3, 64, 4096})
                    require(
                            MoneyMath.quote(123, count, rate, MoneyMath.buyFactor(score), 10, true)
                                    >= MoneyMath.quote(
                                            123,
                                            count,
                                            rate,
                                            MoneyMath.sellFactor(score),
                                            10,
                                            false),
                            "buy/sell no profitable roundtrip");
        var split = new CompoundTag();
        for (int i = 0; i < 4; i++) MoneyMath.exchangeAllowance(split, 10, 1, rates[0], 3);
        require(
                split.getString("exchange_used_n").equals("3")
                        && split.getString("exchange_used_d").equals("1"),
                "exact fractional quota");
        var before = split.copy();
        try {
            MoneyMath.exchangeAllowance(split, 10, 1, rates[0], 3);
            throw new AssertionError("cap exceeded");
        } catch (IllegalArgumentException expected) {
        }
        require(split.equals(before), "rejected quota leaves account unchanged");
        var whole = new CompoundTag();
        MoneyMath.exchangeAllowance(whole, 10, 4, rates[0], 3);
        require(whole.equals(split), "split and whole consume identical daily quota");
        MoneyMath.exchangeAllowance(split, 24000, 1, rates[0], 3);
        require(split.getLong("exchange_day") == 1, "day reset uses gameTime");
        MoneyMath.exchangeAllowance(split, 0, 1, rates[0], 3);
        require(
                split.getLong("exchange_day") == 1
                        && split.getString("exchange_used_n").equals("3")
                        && split.getString("exchange_used_d").equals("2"),
                "time rollback does not replenish daily quota");
        require(
                MoneyMath.remainingAllowance(split, 0, 3).equals("3/2 Azure copper"),
                "server exact remaining quote uses rational residue");
        System.out.println(
                "MoneyMathTest: " + checks + " exact pricing/exchange/quota checks passed");
    }
}

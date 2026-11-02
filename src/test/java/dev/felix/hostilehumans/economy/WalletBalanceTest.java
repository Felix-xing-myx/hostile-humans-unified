package dev.felix.hostilehumans.economy;

import java.util.Map;

public final class WalletBalanceTest {
    public static void main(String[] args) {
        var wallet = WalletBalance.empty().with(Currency.CRIMSON, 12_345);
        var faces = wallet.faces(Currency.CRIMSON);
        require(faces[0] == 45 && faces[1] == 23 && faces[2] == 1, "automatic carry uses 100:1");
        require(wallet.amount(Currency.AZURE) == 0, "currencies are isolated");
        wallet = wallet.with(Currency.AZURE, WalletBalance.CAPACITY);
        faces = wallet.faces(Currency.AZURE);
        require(
                faces[0] == 9999 && faces[1] == 9999 && faces[2] == 9999,
                "all three face limits remain representable");
        try {
            new WalletBalance(Map.of(Currency.ASHEN, WalletBalance.CAPACITY + 1), true);
            throw new AssertionError("overflow accepted");
        } catch (IllegalArgumentException expected) {
        }
        try {
            wallet.copper().clear();
            throw new AssertionError("mutable balance");
        } catch (UnsupportedOperationException expected) {
        }
        var conversion = new MoneyRate(75 * 9, 100 * 10);
        require(conversion.floor(100) == 67, "exchange floors only at the output boundary");
        require(
                conversion.floor(1) * 100 <= conversion.floor(100),
                "splitting cannot increase exchange receipts");
        require(
                new MoneyRate(3, 2).ceil(1) == 2 && new MoneyRate(3, 2).floor(1) == 1,
                "buy up and sell down");
        require(
                new MoneyRate(2, 2).floor(Long.MAX_VALUE) == Long.MAX_VALUE,
                "large exact calculation avoids multiply overflow");
        System.out.println(
                "WalletBalanceTest: carry, capacity, currency isolation and exact rounding passed");
    }

    private static void require(boolean v, String text) {
        if (!v) throw new AssertionError(text);
    }
}

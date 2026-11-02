package dev.felix.hostilehumans.economy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Value-preserving wallet arithmetic; amounts are copper integers, never floating point. */
public record WalletBalance(Map<Currency, Long> copper, boolean autoPickup) {
    public static final long FACE_LIMIT = 9_999;
    public static final long CAPACITY = FACE_LIMIT * (1 + 100 + 10_000);

    public WalletBalance {
        var copy = new EnumMap<Currency, Long>(Currency.class);
        copper.forEach(
                (currency, amount) -> {
                    if (currency == null || amount == null || amount < 0 || amount > CAPACITY)
                        throw new IllegalArgumentException("wallet balance");
                    if (amount > 0) copy.put(currency, amount);
                });
        copper = Collections.unmodifiableMap(copy);
    }

    public static WalletBalance empty() {
        return new WalletBalance(Map.of(), true);
    }

    public long amount(Currency currency) {
        return copper.getOrDefault(currency, 0L);
    }

    public WalletBalance with(Currency currency, long amount) {
        var values = new EnumMap<Currency, Long>(Currency.class);
        values.putAll(copper);
        values.put(currency, amount);
        return new WalletBalance(values, autoPickup);
    }

    public WalletBalance autoPickup(boolean enabled) {
        return new WalletBalance(copper, enabled);
    }

    public long room(Currency currency) {
        return CAPACITY - amount(currency);
    }

    /** Carry greedily until the higher face is full; no denomination may exceed 9999. */
    public long[] faces(Currency currency) {
        long remaining = amount(currency);
        long gold = Math.min(FACE_LIMIT, remaining / 10_000);
        remaining -= gold * 10_000;
        long silver = Math.min(FACE_LIMIT, remaining / 100);
        remaining -= silver * 100;
        if (remaining > FACE_LIMIT) throw new IllegalStateException("wallet capacity invariant");
        return new long[] {remaining, silver, gold};
    }
}

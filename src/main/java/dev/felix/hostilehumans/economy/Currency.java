package dev.felix.hostilehumans.economy;

import java.util.Optional;

/**
 * Fixed registry currencies. Data-driven factions may reference these, but cannot register items
 * during reload.
 */
public enum Currency {
    CRIMSON("crimson"),
    AZURE("azure"),
    VERDANT("verdant"),
    ASHEN("ashen");
    private final String key;

    Currency(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String faction() {
        return "hostile_humans_unified:" + key;
    }

    public static Optional<Currency> faction(String value) {
        for (Currency currency : values())
            if (currency.faction().equals(value) || currency.key.equals(value))
                return Optional.of(currency);
        return Optional.empty();
    }

    public enum Denomination {
        COPPER(1),
        SILVER(100),
        GOLD(10_000);
        private final long copper;

        Denomination(long copper) {
            this.copper = copper;
        }

        public long copper() {
            return copper;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}

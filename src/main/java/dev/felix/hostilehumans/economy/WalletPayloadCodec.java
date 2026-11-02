package dev.felix.hostilehumans.economy;

import net.minecraft.nbt.*;

import java.util.*;

/**
 * Item-independent real NBT codec; malformed/unknown payloads are never normalized into spendable
 * balances.
 */
public final class WalletPayloadCodec {
    private WalletPayloadCodec() {}

    public record Data(UUID id, int revision, WalletBalance balance) {
        public Data {
            if (id == null || revision < 0 || balance == null)
                throw new IllegalArgumentException("wallet identity/revision");
        }
    }

    public static Optional<Data> read(CompoundTag tag) {
        if (tag.getInt("schema") != 1
                || !tag.hasUUID("id")
                || tag.getUUID("id").equals(new UUID(0, 0))
                || tag.getInt("revision") < 0
                || !tag.contains("balances", Tag.TAG_COMPOUND)) return Optional.empty();
        try {
            var amounts = new EnumMap<Currency, Long>(Currency.class);
            var balances = tag.getCompound("balances");
            if (balances.size() > 4) return Optional.empty();
            for (String key : balances.getAllKeys()) {
                Currency currency = Currency.faction(key).orElseThrow();
                if (!balances.contains(key, Tag.TAG_LONG) || amounts.containsKey(currency))
                    return Optional.empty();
                amounts.put(currency, balances.getLong(key));
            }
            return Optional.of(
                    new Data(
                            tag.getUUID("id"),
                            tag.getInt("revision"),
                            new WalletBalance(
                                    amounts,
                                    !tag.contains("auto_pickup")
                                            || tag.getBoolean("auto_pickup"))));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    public static CompoundTag write(CompoundTag previous, Data value) {
        if (!previous.isEmpty() && read(previous).isEmpty())
            throw new IllegalArgumentException("unknown wallet payload retained");
        var tag = previous.copy();
        tag.putInt("schema", 1);
        tag.putUUID("id", value.id().equals(new UUID(0, 0)) ? UUID.randomUUID() : value.id());
        tag.putInt("revision", value.revision());
        tag.putBoolean("auto_pickup", value.balance().autoPickup());
        var amounts = new CompoundTag();
        for (Currency c : Currency.values()) amounts.putLong(c.key(), value.balance().amount(c));
        tag.put("balances", amounts);
        return tag;
    }
}

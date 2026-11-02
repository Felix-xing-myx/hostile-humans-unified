package dev.felix.hostilehumans.economy;

import net.minecraft.nbt.*;

/**
 * NBT authority shared by the live service and tests. Test provenance is a separate namespace,
 * never inferred from IDs.
 */
public final class TaskLedger {
    private TaskLedger() {}

    private static CompoundTag compound(CompoundTag root, String name) {
        if (root.contains(name) && !root.contains(name, Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("unknown task ledger shape retained");
        if (!root.contains(name)) root.put(name, new CompoundTag());
        return root.getCompound(name);
    }

    public static CompoundTag instances(CompoundTag account, boolean test) {
        return compound(account, test ? "test_tasks" : "tasks");
    }

    public static CompoundTag history(CompoundTag account, Currency currency, boolean test) {
        return compound(account, (test ? "test_history_" : "history_") + currency.key());
    }

    public static String key(Currency currency, String id) {
        return currency.key() + "|" + id;
    }

    public static CompoundTag read(
            CompoundTag account, Currency currency, String id, boolean test) {
        var root = instances(account, test);
        String key = key(currency, id);
        if (!root.contains(key)) return null;
        if (!root.contains(key, Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("unknown task instance retained");
        var row = root.getCompound(key);
        if (row.getInt("schema") != 1
                || !row.getString("source").equals(test ? "creative_test" : "formal"))
            throw new IllegalArgumentException("unknown or different-source task retained");
        return row;
    }
}

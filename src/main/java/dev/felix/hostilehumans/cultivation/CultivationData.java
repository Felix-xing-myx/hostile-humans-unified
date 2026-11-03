package dev.felix.hostilehumans.cultivation;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** One world journal owns clock, investor credit, reservations and pending entity applications. */
final class CultivationData extends SavedData {
    static final class Account {
        long available, reserved;
        final Map<UUID, Long> stock = new HashMap<>();

        TrainingCredit at(UUID facility) {
            return new TrainingCredit(stock.getOrDefault(facility, 0L), available, reserved);
        }

        void put(UUID facility, TrainingCredit c) {
            if (c.stock() == 0) stock.remove(facility);
            else stock.put(facility, c.stock());
            available = c.available();
            reserved = c.reserved();
        }
    }

    long runtimeMillis, revision;
    final Map<UUID, CultivationRecord> records = new LinkedHashMap<>();
    int settlementCursor; // Runtime rotation only; saved record order is unchanged.
    final Map<UUID, Account> accounts = new HashMap<>();
    final LinkedHashMap<UUID, UUID> transactions = new LinkedHashMap<>();
    final Set<UUID> retired = new HashSet<>();
    private final ListTag quarantined = new ListTag();
    private CompoundTag future;

    static CultivationData get(MinecraftServer s) {
        return s.overworld()
                .getDataStorage()
                .computeIfAbsent(
                        CultivationData::load,
                        CultivationData::new,
                        "hostile_humans_unified_cultivation");
    }

    int quarantinedCount() {
        return quarantined.size();
    }

    Account account(UUID owner) {
        writable();
        return accounts.computeIfAbsent(owner, k -> new Account());
    }

    boolean readOnly() {
        return future != null;
    }

    void writable() {
        if (readOnly())
            throw new IllegalStateException(
                    "Unknown cultivation journal schema retained; writing disabled");
    }

    void changed() {
        writable();
        revision++;
        setDirty();
    }

    boolean seen(UUID tx, UUID owner) {
        return owner.equals(transactions.get(tx));
    }

    void remember(UUID tx, UUID owner) {
        transactions.put(tx, owner);
        while (transactions.size() > 1024)
            transactions.remove(transactions.keySet().iterator().next());
        changed();
    }

    private static CultivationData load(CompoundTag t) {
        var d = new CultivationData();
        if (t.getInt("schema") != 1) {
            d.future = t.copy();
            return d;
        }
        d.runtimeMillis = Math.max(0, t.getLong("runtime"));
        d.revision = Math.max(0, t.getLong("revision"));
        for (Tag raw : t.getList("records", Tag.TAG_COMPOUND)) {
            CompoundTag r = (CompoundTag) raw;
            try {
                var value = CultivationNbt.record(r);
                if (d.records.putIfAbsent(value.soldier, value) != null)
                    d.quarantined.add(r.copy());
            } catch (RuntimeException invalid) {
                d.quarantined.add(r.copy());
            }
        }
        for (Tag raw : t.getList("quarantined", Tag.TAG_COMPOUND)) d.quarantined.add(raw.copy());
        for (Tag raw : t.getList("accounts", Tag.TAG_COMPOUND)) {
            var a = (CompoundTag) raw;
            if (!a.hasUUID("owner")) continue;
            var account = new Account();
            account.available = a.getLong("available");
            account.reserved = a.getLong("reserved");
            if (account.available < 0 || account.reserved < 0) {
                d.quarantined.add(a.copy());
                continue;
            }
            for (Tag s : a.getList("stock", Tag.TAG_COMPOUND)) {
                var v = (CompoundTag) s;
                if (v.hasUUID("facility") && v.getLong("count") >= 0)
                    account.stock.put(v.getUUID("facility"), v.getLong("count"));
            }
            d.accounts.put(a.getUUID("owner"), account);
        }
        for (Tag raw : t.getList("transactions", Tag.TAG_COMPOUND)) {
            var x = (CompoundTag) raw;
            if (x.hasUUID("id") && x.hasUUID("owner"))
                d.transactions.put(x.getUUID("id"), x.getUUID("owner"));
        }
        for (Tag raw : t.getList("retired", Tag.TAG_COMPOUND)) {
            var x = (CompoundTag) raw;
            if (x.hasUUID("id")) d.retired.add(x.getUUID("id"));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        if (future != null) return future.copy();
        t.putInt("schema", 1);
        t.putLong("runtime", runtimeMillis);
        t.putLong("revision", revision);
        var recordsTag = new ListTag();
        for (var r : records.values()) recordsTag.add(CultivationNbt.record(r));
        t.put("records", recordsTag);
        t.put("quarantined", quarantined.copy());
        var accountsTag = new ListTag();
        accounts.forEach(
                (owner, a) -> {
                    var v = new CompoundTag();
                    v.putUUID("owner", owner);
                    v.putLong("available", a.available);
                    v.putLong("reserved", a.reserved);
                    var stock = new ListTag();
                    a.stock.forEach(
                            (id, count) -> {
                                var x = new CompoundTag();
                                x.putUUID("facility", id);
                                x.putLong("count", count);
                                stock.add(x);
                            });
                    v.put("stock", stock);
                    accountsTag.add(v);
                });
        t.put("accounts", accountsTag);
        var transactionsTag = new ListTag();
        transactions.forEach(
                (id, owner) -> {
                    var x = new CompoundTag();
                    x.putUUID("id", id);
                    x.putUUID("owner", owner);
                    transactionsTag.add(x);
                });
        t.put("transactions", transactionsTag);
        var retiredTag = new ListTag();
        for (UUID id : retired) {
            var x = new CompoundTag();
            x.putUUID("id", id);
            retiredTag.add(x);
        }
        t.put("retired", retiredTag);
        return t;
    }
}

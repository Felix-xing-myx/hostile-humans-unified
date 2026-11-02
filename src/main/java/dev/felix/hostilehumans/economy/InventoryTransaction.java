package dev.felix.hostilehumans.economy;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.MessageDigest;
import java.util.*;

/**
 * Plan on copies, validate all inputs, then publish once on the server thread. Failed plans never
 * touch live stacks.
 */
public final class InventoryTransaction {
    private static final class State {
        final WalletInventory.Slot slot;
        final ItemStack before;
        ItemStack after;

        State(WalletInventory.Slot slot) {
            this.slot = slot;
            before = slot.read().get().copy();
            after = before.copy();
        }
    }

    private final List<State> states;
    private final ServerPlayer player;
    private boolean committed;

    public InventoryTransaction(ServerPlayer player) {
        this.player = player;
        states = new ArrayList<>(WalletInventory.slots(player).stream().map(State::new).toList());
    }

    public static final String PICKUP_STAMP = "hostile_humans_unified:wallet_pickup_transaction";

    public void pickup(
            net.minecraft.world.entity.item.ItemEntity entity, UUID transaction, int count) {
        ItemStack before = entity.getItem().copy();
        if (count < 1 || count > before.getCount())
            throw new IllegalArgumentException("pickup count changed");
        UUID old =
                entity.getPersistentData().hasUUID(PICKUP_STAMP)
                        ? entity.getPersistentData().getUUID(PICKUP_STAMP)
                        : null;
        var slot =
                new WalletInventory.Slot(
                        "external:" + entity.getUUID(),
                        false,
                        () -> entity.isRemoved() ? ItemStack.EMPTY : entity.getItem(),
                        stack -> {
                            entity.setItem(stack.copy());
                            if (ItemStack.matches(stack, before)) {
                                if (old == null) entity.getPersistentData().remove(PICKUP_STAMP);
                                else entity.getPersistentData().putUUID(PICKUP_STAMP, old);
                            } else entity.getPersistentData().putUUID(PICKUP_STAMP, transaction);
                        });
        var state = new State(slot);
        state.after.shrink(count);
        states.add(state);
    }

    public static InventoryTransaction fromJournal(ServerPlayer player, CompoundTag journal) {
        var result = new InventoryTransaction(player);
        for (Tag tag : journal.getList("slots", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) tag;
            String key = row.getString("slot");
            if (!key.startsWith("external:")) continue;
            var entity = player.serverLevel().getEntity(UUID.fromString(key.substring(9)));
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity item)
                result.states.add(
                        new State(
                                new WalletInventory.Slot(
                                        key, false, item::getItem, item::setItem)));
        }
        return result;
    }

    public String beforeFingerprint() {
        return fingerprint(false);
    }

    public String afterFingerprint() {
        return fingerprint(true);
    }

    private String fingerprint(boolean after) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            for (var state : states) {
                out.writeUTF(state.slot.key());
                NbtIo.write((after ? state.after : state.before).save(new CompoundTag()), out);
            }
            out.close();
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (Exception invalid) {
            throw new IllegalStateException("inventory fingerprint failed", invalid);
        }
    }

    public List<EconomyView.WalletRow> wallets() {
        var result = new ArrayList<EconomyView.WalletRow>();
        for (var state : states)
            if (state.after.getItem() instanceof WalletItem) {
                var d = WalletCodec.read(state.after);
                result.add(
                        new EconomyView.WalletRow(
                                d.map(WalletCodec.Data::id).orElse(EconomyView.NONE),
                                state.slot.key(),
                                d.map(WalletCodec.Data::revision).orElse(0),
                                d.isPresent(),
                                d.map(v -> v.balance().autoPickup()).orElse(false),
                                Arrays.stream(Currency.values())
                                        .map(c -> d.map(v -> v.balance().amount(c)).orElse(0L))
                                        .toList()));
            }
        if (result.size() > 64) throw new IllegalStateException("wallet count limit");
        return List.copyOf(result);
    }

    public long walletTotal(Currency currency) {
        long result = 0;
        for (var state : states) {
            var d = WalletCodec.read(state.after);
            if (d.isPresent()) result = Math.addExact(result, d.get().balance().amount(currency));
        }
        return result;
    }

    public long looseTotal(Currency currency) {
        long result = 0;
        for (var state : states)
            if (state.after.getItem() instanceof CurrencyItem coin && coin.currency() == currency)
                result =
                        Math.addExact(
                                result,
                                Math.multiplyExact(
                                        coin.denomination().copper(), state.after.getCount()));
        return result;
    }

    public long total(Currency currency) {
        return Math.addExact(walletTotal(currency), looseTotal(currency));
    }

    private State wallet(UUID id) {
        State found = null;
        for (var s : states) {
            var value = WalletCodec.read(s.after);
            if (value.isPresent() && value.get().id().equals(id)) {
                if (found != null)
                    throw new IllegalArgumentException("ambiguous duplicate wallet identity");
                found = s;
            }
        }
        if (found == null) throw new IllegalArgumentException("wallet is not currently carried");
        return found;
    }

    public void autoPickup(UUID id, boolean enabled) {
        var s = wallet(id);
        var value = WalletCodec.read(s.after).orElseThrow();
        WalletCodec.balance(s.after, value.balance().autoPickup(enabled));
    }

    public void deposit(UUID id, Currency currency, Currency.Denomination face, int count) {
        if (count < 1) throw new IllegalArgumentException("positive coin count required");
        long amount = Math.multiplyExact(face.copper(), count);
        State target = wallet(id);
        var data = WalletCodec.read(target.after).orElseThrow();
        if (data.balance().room(currency) < amount)
            throw new IllegalArgumentException("wallet capacity exceeded");
        int left = count;
        for (var s : states)
            if (s.after.getItem() instanceof CurrencyItem coin
                    && coin.currency() == currency
                    && coin.denomination() == face) {
                int take = Math.min(left, s.after.getCount());
                s.after.shrink(take);
                left -= take;
                if (left == 0) break;
            }
        if (left != 0) throw new IllegalArgumentException("requested physical coins unavailable");
        WalletCodec.balance(
                target.after,
                data.balance()
                        .with(currency, Math.addExact(data.balance().amount(currency), amount)));
    }

    public void withdraw(UUID id, Currency currency, Currency.Denomination face, int count) {
        if (count < 1) throw new IllegalArgumentException("positive coin count required");
        long amount = Math.multiplyExact(face.copper(), count);
        State target = wallet(id);
        var data = WalletCodec.read(target.after).orElseThrow();
        if (data.balance().amount(currency) < amount)
            throw new IllegalArgumentException("wallet balance insufficient");
        WalletCodec.balance(
                target.after,
                data.balance().with(currency, data.balance().amount(currency) - amount));
        insertCoins(currency, face, count);
    }

    public void debit(Currency currency, long amount) {
        if (amount < 0 || total(currency) < amount)
            throw new IllegalArgumentException("insufficient funds");
        long left = amount;
        for (var s : states) {
            var d = WalletCodec.read(s.after);
            if (d.isEmpty()) continue;
            long take = Math.min(left, d.get().balance().amount(currency));
            if (take > 0) {
                WalletCodec.balance(
                        s.after,
                        d.get()
                                .balance()
                                .with(currency, d.get().balance().amount(currency) - take));
                left -= take;
            }
            if (left == 0) return;
        }
        for (var face : Currency.Denomination.values())
            for (var s : states)
                if (s.after.getItem() instanceof CurrencyItem coin
                        && coin.currency() == currency
                        && coin.denomination() == face) {
                    long wanted = (left + face.copper() - 1) / face.copper();
                    int take = (int) Math.min(wanted, s.after.getCount());
                    long paid = Math.multiplyExact(take, face.copper());
                    s.after.shrink(take);
                    if (paid >= left) {
                        long change = paid - left;
                        if (change > 0) credit(currency, change);
                        return;
                    }
                    left -= paid;
                }
        if (left > 0) throw new IllegalArgumentException("funds changed");
    }

    public void credit(Currency currency, long amount) {
        if (amount < 0) throw new IllegalArgumentException("negative credit");
        long left = amount;
        for (var s : states) {
            var d = WalletCodec.read(s.after);
            if (d.isEmpty()) continue;
            long take = Math.min(left, d.get().balance().room(currency));
            if (take > 0) {
                WalletCodec.balance(
                        s.after,
                        d.get()
                                .balance()
                                .with(
                                        currency,
                                        Math.addExact(d.get().balance().amount(currency), take)));
                left -= take;
            }
            if (left == 0) return;
        }
        for (int i = Currency.Denomination.values().length - 1; i >= 0; i--) {
            var face = Currency.Denomination.values()[i];
            long count = left / face.copper();
            if (count > 0) {
                insertCoins(currency, face, count);
                left %= face.copper();
            }
        }
    }

    /**
     * Auto-pickup stores only what fits enabled wallets; remainder stays in the actual ItemEntity.
     */
    public int absorb(Currency currency, Currency.Denomination face, int available) {
        long remaining = Math.multiplyExact(face.copper(), available);
        int absorbed = 0;
        for (var s : states) {
            var d = WalletCodec.read(s.after);
            if (d.isEmpty() || !d.get().balance().autoPickup()) continue;
            long fit =
                    Math.min(
                            remaining / face.copper(),
                            d.get().balance().room(currency) / face.copper());
            if (fit <= 0) continue;
            long amount = fit * face.copper();
            WalletCodec.balance(
                    s.after,
                    d.get().balance().with(currency, d.get().balance().amount(currency) + amount));
            remaining -= amount;
            absorbed = Math.addExact(absorbed, Math.toIntExact(fit));
        }
        return absorbed;
    }

    private void insertCoins(Currency currency, Currency.Denomination face, long count) {
        if (count > 4096)
            throw new IllegalArgumentException("too many physical coin stacks requested");
        while (count > 0) {
            int part = (int) Math.min(64, count);
            insert(new ItemStack(EconomyContent.coin(currency, face), part));
            count -= part;
        }
    }

    public void insert(ItemStack incoming) {
        ItemStack remaining = incoming.copy();
        if (remaining.isEmpty()) return;
        for (var s : states)
            if (s.slot.mainInventory()
                    && !s.after.isEmpty()
                    && ItemStack.isSameItemSameTags(s.after, remaining)) {
                int add =
                        Math.min(
                                remaining.getCount(),
                                Math.max(0, s.after.getMaxStackSize() - s.after.getCount()));
                s.after.grow(add);
                remaining.shrink(add);
                if (remaining.isEmpty()) return;
            }
        for (var s : states)
            if (s.slot.mainInventory() && s.after.isEmpty()) {
                int add = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                s.after = remaining.copy();
                s.after.setCount(add);
                remaining.shrink(add);
                if (remaining.isEmpty()) return;
            }
        throw new IllegalArgumentException("no inventory capacity; transaction unchanged");
    }

    public ItemStack item(int index) {
        return states.stream()
                .filter(s -> s.slot.key().equals("inventory:" + index))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("inventory slot unavailable"))
                .after
                .copy();
    }

    public void consumeSlot(int index, net.minecraft.world.item.Item item, int count) {
        var s =
                states.stream()
                        .filter(t -> t.slot.key().equals("inventory:" + index))
                        .findFirst()
                        .orElseThrow();
        if (count < 1 || s.after.getItem() != item || s.after.getCount() < count)
            throw new IllegalArgumentException("submitted item/count changed");
        s.after.shrink(count);
    }

    public void consume(net.minecraft.world.item.Item item, int count) {
        if (count < 1) throw new IllegalArgumentException("positive item count required");
        int left = count;
        for (var s : states)
            if (s.slot.mainInventory() && s.after.getItem() == item) {
                int take = Math.min(left, s.after.getCount());
                s.after.shrink(take);
                left -= take;
                if (left == 0) return;
            }
        throw new IllegalArgumentException("required delivery items unavailable");
    }

    public boolean matches() {
        return states.stream().allMatch(s -> ItemStack.matches(s.before, s.slot.read().get()));
    }

    public CompoundTag journal() {
        var result = new CompoundTag();
        result.putString("before_hash", beforeFingerprint());
        result.putString("after_hash", afterFingerprint());
        var changed = new ListTag();
        for (var s : states)
            if (!ItemStack.matches(s.before, s.after)) {
                var row = new CompoundTag();
                row.putString("slot", s.slot.key());
                row.put("before", s.before.save(new CompoundTag()));
                row.put("after", s.after.save(new CompoundTag()));
                changed.add(row);
            }
        result.put("slots", changed);
        return result;
    }

    public boolean publish() {
        if (!player.getServer().isSameThread() || !matches()) return false;
        committed = true;
        try {
            for (var s : states)
                if (!ItemStack.matches(s.before, s.after)) s.slot.write().accept(s.after.copy());
            if (!states.stream().allMatch(s -> ItemStack.matches(s.after, s.slot.read().get())))
                throw new IllegalStateException(
                        "slot setter did not publish exact planned inventory");
            committed = true;
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            return true;
        } catch (RuntimeException failed) {
            rollback();
            throw failed;
        }
    }

    public void rollback() {
        for (var s : states)
            if (!ItemStack.matches(s.before, s.after)) s.slot.write().accept(s.before.copy());
        committed = false;
        player.getInventory().setChanged();
    }

    public boolean committed() {
        return committed;
    }

    public static boolean replay(ServerPlayer player, CompoundTag journal) {
        var transaction = fromJournal(player, journal);
        if (!transaction.beforeFingerprint().equals(journal.getString("before_hash"))) return false;
        var rows = journal.getList("slots", Tag.TAG_COMPOUND);
        if (rows.size() > 105) return false;
        for (Tag tag : rows) {
            var row = (CompoundTag) tag;
            var state =
                    transaction.states.stream()
                            .filter(s -> s.slot.key().equals(row.getString("slot")))
                            .findFirst()
                            .orElse(null);
            if (state == null
                    || !ItemStack.matches(state.before, ItemStack.of(row.getCompound("before"))))
                return false;
            state.after = ItemStack.of(row.getCompound("after"));
        }
        return transaction.afterFingerprint().equals(journal.getString("after_hash"))
                && transaction.publish();
    }
}

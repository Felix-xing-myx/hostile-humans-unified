package dev.felix.hostilehumans.faction;

import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.economy.InventoryTransaction;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Transaction drafts reuse the existing player's political row; they are temporary copies, never
 * another authority.
 */
public final class FactionEconomyLedger {
    public static final String STAMP = "hostile_humans_unified:economy_transaction";

    public record Receipt(boolean changed, String reason, int revision) {}

    private FactionEconomyLedger() {}

    public static int revision(ServerPlayer player) {
        var tag =
                FactionStandingData.get(player.getServer())
                        .player(player.getUUID())
                        .getCompound("economy");
        return tag.getInt("revision");
    }

    public static Draft draft(ServerPlayer player) {
        return new Draft(player.getServer(), player.getUUID());
    }

    public static Draft draft(MinecraftServer server, UUID player) {
        return new Draft(server, player);
    }

    public static final class Draft {
        private final MinecraftServer server;
        private final UUID player;
        private final FactionStandingData data;
        private final CompoundTag before, after, account;
        private final long now;

        Draft(MinecraftServer server, UUID player) {
            if (!server.isSameThread())
                throw new IllegalStateException("politics transaction requires server thread");
            this.server = server;
            this.player = player;
            data = FactionStandingData.get(server);
            now = server.overworld().getGameTime();
            before = data.player(player);
            after = before.copy();
            if (after.contains("economy", Tag.TAG_COMPOUND)) {
                account = after.getCompound("economy");
                if (account.getInt("schema") != 1)
                    throw new IllegalArgumentException("unknown economy schema retained");
            } else {
                account = new CompoundTag();
                account.putInt("schema", 1);
                after.put("economy", account);
            }
            if (account.getBoolean("recovery_blocked"))
                throw new IllegalStateException("economic recovery requires management repair");
        }

        public int revision() {
            return account.getInt("revision");
        }

        public long now() {
            return now;
        }

        public int remaining(String faction) {
            var f = faction(faction);
            long day = Math.max(f.getLong("reward_day"), Math.max(0, now) / 24000);
            int used = day > f.getLong("reward_day") ? 0 : f.getInt("earned");
            return Math.max(
                    0, UnifiedConfig.get().standingQuota().cap(f.getInt("standing")) - used);
        }

        public CompoundTag account() {
            return account;
        }

        private CompoundTag faction(String raw) {
            var definition = FactionDefinitions.find(raw);
            if (definition == null) throw new IllegalArgumentException("unknown faction");
            String id = definition.id().toString();
            var rows = after.getList("factions", Tag.TAG_COMPOUND);
            if (!after.contains("factions")) after.put("factions", rows);
            for (int i = 0; i < rows.size(); i++)
                if (rows.getCompound(i).getString("id").equals(id)) return rows.getCompound(i);
            var f = new CompoundTag();
            f.putString("id", id);
            f.putInt("standing", definition.startingStanding());
            f.putLong("reward_day", -1);
            f.putInt("earned", 0);
            rows.add(f);
            return f;
        }

        public int standing(String faction) {
            return faction(faction).getInt("standing");
        }

        public int gain(String faction, int amount) {
            var f = faction(faction);
            int old = f.getInt("standing");
            long day = Math.max(f.getLong("reward_day"), Math.max(0, now) / 24000);
            int used = day > f.getLong("reward_day") ? 0 : f.getInt("earned");
            int limit = UnifiedConfig.get().standingQuota().cap(old);
            int applied =
                    amount > 0
                            ? StandingReward.positiveDelta(old, amount, Math.max(0, limit - used))
                            : amount;
            int next = (int) Math.max(-500L, Math.min(500L, (long) old + applied));
            int actual = next - old;
            f.putInt("standing", next);
            f.putLong("reward_day", day);
            f.putInt("earned", used + Math.max(0, actual));
            return actual;
        }

        public int redemption(String faction, int step, int reward) {
            var f = faction(faction);
            int old = f.getInt("standing");
            int next =
                    step == 1
                            ? Math.max(old, -99)
                            : old >= 50
                                    ? old
                                    : (int) Math.min(50L, (long) old + Math.max(0, reward));
            f.putInt("standing", next);
            return next - old;
        }

        public PassagePermit permit(String faction) {
            var f = faction(faction);
            return PassageCodec.read(f.getCompound("passage"))
                    .orElseThrow(
                            () -> new IllegalArgumentException("unknown permit schema retained"));
        }

        public void permit(String faction, PassagePermit permit) {
            var f = faction(faction);
            if (PassageCodec.read(f.getCompound("passage")).isEmpty())
                throw new IllegalArgumentException("unknown permit schema retained");
            var raw = f.getCompound("passage").copy();
            raw.merge(PassageCodec.write(permit));
            f.put("passage", raw);
        }

        public Receipt commit(
                ServerPlayer live,
                UUID transaction,
                InventoryTransaction inventory,
                boolean political) {
            if (!live.getUUID().equals(player) || live.getServer() != server)
                throw new IllegalArgumentException("transaction actor mismatch");
            if (political && !UnifiedConfig.get().featureEnabled("standing", true))
                return new Receipt(false, "standing disabled; state preserved", revision());
            if (data.journaled(player, transaction))
                return new Receipt(false, "transaction already recorded", revision());
            if (!data.player(player).equals(before) || !inventory.matches())
                return new Receipt(false, "player state changed; refresh before retry", revision());
            account.putInt("revision", Math.addExact(account.getInt("revision"), 1));
            var journal = inventory.journal();
            journal.putInt("schema", 1);
            journal.putUUID("player", player);
            journal.putUUID("transaction", transaction);
            journal.putString("state", "PREPARED");
            journal.putLong("sequence", data.nextSequence());
            journal.put("politics_before", before.copy());
            journal.put("politics_after", after.copy());
            if (live.getPersistentData().hasUUID(STAMP))
                journal.putUUID("previous_stamp", live.getPersistentData().getUUID(STAMP));
            data.journal(player, transaction, journal);
            try {
                if (!inventory.publish())
                    return abort(transaction, journal, "inventory changed before commit");
                if (!data.player(player).equals(before)) {
                    inventory.rollback();
                    return abort(
                            transaction,
                            journal,
                            "political state changed during inventory publish");
                }
                data.player(player, after);
                live.getPersistentData().putUUID(STAMP, transaction);
                journal.putString("state", "COMMITTED");
                data.journal(player, transaction, journal);
                return new Receipt(true, "committed", revision());
            } catch (RuntimeException failed) {
                try {
                    if (inventory.committed()) inventory.rollback();
                    journal.putString("state", "ABORTED");
                    data.journal(player, transaction, journal);
                } catch (RuntimeException unresolved) {
                    journal.putString("state", "REPAIR_REQUIRED");
                    data.journal(player, transaction, journal);
                }
                throw failed;
            }
        }

        private Receipt abort(UUID transaction, CompoundTag journal, String reason) {
            journal.putString("state", "ABORTED");
            data.journal(player, transaction, journal);
            return new Receipt(false, reason, revision());
        }

        /**
         * No inventory side effects: used only for violation/progress/reconciliation, never cash
         * rewards.
         */
        public void commitState() {
            if (!data.player(player).equals(before))
                throw new IllegalStateException("political state changed");
            account.putInt("revision", Math.addExact(revision(), 1));
            data.player(player, after);
        }
    }

    public static boolean recover(ServerPlayer player) {
        var data = FactionStandingData.get(player.getServer());
        var receipt = data.latestJournal(player.getUUID());
        boolean hasStamp = player.getPersistentData().hasUUID(STAMP);
        UUID stamp = hasStamp ? player.getPersistentData().getUUID(STAMP) : null;
        var stamped = hasStamp ? data.journal(player.getUUID(), stamp) : null;
        boolean recognized =
                !hasStamp
                        || stamped != null
                                && (stamped.getString("state").equals("COMMITTED")
                                        || stamped.getString("state").equals("PREPARED"));
        if (!recognized) {
            block(player, data, "player references transaction absent or invalid in world journal");
            return false;
        }
        if (receipt == null) return true;
        String state = receipt.getString("state");
        UUID id = receipt.getUUID("transaction");
        var current = InventoryTransaction.fromJournal(player, receipt);
        String hash = current.beforeFingerprint();
        boolean politics =
                data.player(player.getUUID()).equals(receipt.getCompound("politics_before"))
                        || data.player(player.getUUID())
                                .equals(receipt.getCompound("politics_after"));
        var decision =
                dev.felix.hostilehumans.economy.EconomyRecovery.decide(
                        state,
                        hasStamp,
                        recognized,
                        hasStamp && stamp.equals(id),
                        hasStamp
                                && receipt.hasUUID("previous_stamp")
                                && stamp.equals(receipt.getUUID("previous_stamp")),
                        receipt.hasUUID("previous_stamp"),
                        hash.equals(receipt.getString("before_hash")),
                        hash.equals(receipt.getString("after_hash")),
                        politics,
                        data.player(player.getUUID())
                                .equals(receipt.getCompound("politics_before")));
        switch (decision) {
            case ACCEPT -> {
                return true;
            }
            case ABORT_PREPARED -> {
                receipt.putString("state", "ABORTED");
                data.journal(player.getUUID(), id, receipt);
                return true;
            }
            case COMPLETE_PREPARED -> {
                data.player(player.getUUID(), receipt.getCompound("politics_after"));
                player.getPersistentData().putUUID(STAMP, id);
                receipt.putString("state", "COMMITTED");
                data.journal(player.getUUID(), id, receipt);
                return true;
            }
            case REPLAY_COMMITTED -> {
                if (InventoryTransaction.replay(player, receipt)) {
                    player.getPersistentData().putUUID(STAMP, id);
                    return true;
                }
            }
            default -> {}
        }
        block(player, data, "journal/inventory mismatch; no assets overwritten");
        return false;
    }

    /**
     * Reconcile a loaded source before pickup; its own stamp tolerates legitimate later stack
     * merges.
     */
    public static int external(net.minecraft.world.entity.item.ItemEntity entity) {
        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) return 0;
        var receipt = FactionStandingData.get(level.getServer()).external(entity.getUUID());
        if (receipt == null) return 0;
        UUID id = receipt.getUUID("transaction");
        String state = receipt.getString("state");
        if (state.equals("ABORTED")
                || entity.getPersistentData().hasUUID(InventoryTransaction.PICKUP_STAMP)
                        && entity.getPersistentData()
                                .getUUID(InventoryTransaction.PICKUP_STAMP)
                                .equals(id)) return 0;
        if (!state.equals("COMMITTED")) return -1;
        for (Tag tag : receipt.getList("slots", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) tag;
            if (!row.getString("slot").equals("external:" + entity.getUUID())) continue;
            var before = net.minecraft.world.item.ItemStack.of(row.getCompound("before"));
            var after = net.minecraft.world.item.ItemStack.of(row.getCompound("after"));
            if (net.minecraft.world.item.ItemStack.matches(entity.getItem(), before)) {
                entity.setItem(after.copy());
                entity.getPersistentData().putUUID(InventoryTransaction.PICKUP_STAMP, id);
                if (after.isEmpty()) entity.discard();
                return 1;
            }
            if (net.minecraft.world.item.ItemStack.matches(entity.getItem(), after)) {
                entity.getPersistentData().putUUID(InventoryTransaction.PICKUP_STAMP, id);
                return 0;
            }
        }
        return -1;
    }

    private static void block(ServerPlayer player, FactionStandingData data, String reason) {
        var raw = data.player(player.getUUID());
        var account = raw.getCompound("economy");
        if (!raw.contains("economy")) {
            account.putInt("schema", 1);
            raw.put("economy", account);
        }
        if (account.getInt("schema") == 1) {
            account.putBoolean("recovery_blocked", true);
            account.putString("recovery_reason", reason);
            data.player(player.getUUID(), raw);
        }
    }
}

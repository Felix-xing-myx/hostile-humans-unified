package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Durable, owner-bound delivery for loaded and unloaded soldiers. Never loads chunks. */
final class GrowthRewards extends SavedData {
    private static final String ID = HumanGunner.MOD_ID + "_growth_rewards";
    private record Reward(UUID owner, long sequence, double amount) { }
    private final Map<UUID, Reward> pending = new LinkedHashMap<>();
    private long sequence;
    private long epoch;
    private boolean enabled = true;
    private static long disabledRevision;
    private long observedDisabledRevision;

    /** Called synchronously by the server config loader, including transitions between ticks. */
    static void configurationReloaded(boolean enabled) { if (!enabled) disabledRevision++; }

    static GrowthRewards get(MinecraftServer server) {
        GrowthRewards store = server.overworld().getDataStorage().computeIfAbsent(
                GrowthRewards::load, GrowthRewards::new, ID);
        boolean enabled = UnifiedConfig.get().growth().enabled();
        if (store.observedDisabledRevision != disabledRevision) {
            store.observedDisabledRevision = disabledRevision;
            store.epoch++;
            store.setDirty();
        }
        if (store.enabled != enabled) {
            store.enabled = enabled;
            if (!enabled) store.epoch++;
            store.setDirty();
        }
        return store;
    }

    long epoch() { return epoch; }

    void enqueue(UUID soldier, UUID owner, double amount) {
        if (!enabled || !Double.isFinite(amount) || amount <= 0) return;
        Reward previous = pending.get(soldier);
        double total = amount + (previous != null && previous.owner.equals(owner) ? previous.amount : 0);
        pending.put(soldier, new Reward(owner, ++sequence, HumanGrowth.rounded(total)));
        setDirty();
    }

    static boolean claim(Human human) {
        MinecraftServer server = human.getServer();
        if (server == null) return false;
        GrowthRewards store = get(server);
        if (!store.enabled) return false; // Already earned rewards survive a temporary disable.
        Reward reward = store.pending.get(human.getUUID());
        if (reward == null) return false;
        if (!human.isAlive() || !human.hasOwner() || !reward.owner.equals(human.getOwnerUUID())
                || !RecruitmentLedger.get(server).isOwnedRecord(human.getUUID(), reward.owner)) {
            store.pending.remove(human.getUUID());
            store.setDirty();
            return false;
        }
        CompoundTag state = HumanGrowth.state(human);
        if (state.getLong("reward_sequence") < reward.sequence) {
            HumanGrowth.award(human, reward.amount);
            state.putLong("reward_sequence", reward.sequence);
        }
        store.pending.remove(human.getUUID());
        store.setDirty();
        return true;
    }

    static GrowthRewards load(CompoundTag root) {
        GrowthRewards store = new GrowthRewards();
        store.sequence = Math.max(0, root.getLong("sequence"));
        store.epoch = Math.max(0, root.getLong("epoch"));
        store.enabled = !root.contains("enabled") || root.getBoolean("enabled");
        for (Tag value : root.getList("pending", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) value;
            double amount = tag.getDouble("amount");
            if (tag.hasUUID("soldier") && tag.hasUUID("owner") && Double.isFinite(amount) && amount > 0) {
                long sequence = Math.max(0, tag.getLong("sequence"));
                store.sequence = Math.max(store.sequence, sequence);
                store.pending.put(tag.getUUID("soldier"), new Reward(tag.getUUID("owner"), sequence, amount));
            }
        }
        return store;
    }

    @Override public CompoundTag save(CompoundTag root) {
        root.putLong("sequence", sequence);
        root.putLong("epoch", epoch);
        root.putBoolean("enabled", enabled);
        ListTag list = new ListTag();
        pending.forEach((soldier, reward) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("soldier", soldier);
            tag.putUUID("owner", reward.owner);
            tag.putLong("sequence", reward.sequence);
            tag.putDouble("amount", reward.amount);
            list.add(tag);
        });
        root.put("pending", list);
        return root;
    }
}

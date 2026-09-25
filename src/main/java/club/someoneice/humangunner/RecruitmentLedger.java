package club.someoneice.humangunner;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class RecruitmentLedger extends SavedData {
    private static final String ID = "humangunner_recruits";
    private final Map<UUID, Entry> entries = new HashMap<>();

    static RecruitmentLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                RecruitmentLedger::load, RecruitmentLedger::new, ID);
    }

    private static RecruitmentLedger load(CompoundTag root) {
        RecruitmentLedger ledger = new RecruitmentLedger();
        for (Tag value : root.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) value;
            if (tag.hasUUID("human") && tag.hasUUID("owner"))
                ledger.entries.put(tag.getUUID("human"), new Entry(tag.getUUID("owner"), tag.getInt("tier")));
        }
        return ledger;
    }

    void hire(UUID human, UUID owner, int tier) {
        entries.put(human, new Entry(owner, tier));
        setDirty();
    }

    void dismiss(UUID human) {
        if (entries.remove(human) != null) setDirty();
    }

    int count(UUID owner, int tier) {
        return (int) entries.values().stream().filter(e -> e.owner.equals(owner) && e.tier == tier).count();
    }

    /** Repair stale external owner indexes from loaded, authoritative entities. */
    void refreshLoadedIndex(MinecraftServer server, UUID owner) {
        com.craftix.hostile_humans.entity.data.HumanServerData data =
                com.craftix.hostile_humans.entity.data.HumanServerData.get();
        if (data == null) return;
        for (Map.Entry<UUID, Entry> entry : entries.entrySet()) {
            if (!entry.getValue().owner.equals(owner)) continue;
            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                if (!(level.getEntity(entry.getKey())
                        instanceof com.craftix.hostile_humans.entity.entities.Human human)) continue;
                com.craftix.hostile_humans.entity.data.HumanData stored = data.getHumanMob(entry.getKey());
                if (stored == null || !owner.equals(stored.getOwnerUUID())) {
                    data.updateOrRegisterHumanMob(human);
                }
                break;
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        entries.forEach((human, entry) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("human", human);
            tag.putUUID("owner", entry.owner);
            tag.putInt("tier", entry.tier);
            list.add(tag);
        });
        root.put("entries", list);
        return root;
    }

    private record Entry(UUID owner, int tier) {}
}

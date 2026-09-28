package club.someoneice.humangunner;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RecruitmentLedger extends SavedData {
    private static final String ID = "humangunner_recruits";
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, UUID> pendingDismissals = new HashMap<>();
    private long nextSortOrder;
    private long peacefulGeneration;
    private boolean peacefulActive;

    static RecruitmentLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                RecruitmentLedger::load, RecruitmentLedger::new, ID);
    }

    static RecruitmentLedger load(CompoundTag root) {
        RecruitmentLedger ledger = new RecruitmentLedger();
        List<CompoundTag> savedEntries = new ArrayList<>();
        for (Tag value : root.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) value;
            if (tag.hasUUID("human") && tag.hasUUID("owner")) {
                savedEntries.add(tag);
            }
        }
        // Normalize duplicate/corrupt ranks while retaining the saved order.
        // Pre-rank saves displayed soldiers by tier and then UUID.
        savedEntries.sort(Comparator.comparingInt((CompoundTag tag) ->
                        tag.contains("sort_order", Tag.TAG_LONG) ? 0 : 1)
                .thenComparingLong(tag -> tag.contains("sort_order", Tag.TAG_LONG)
                        ? tag.getLong("sort_order") : Long.MAX_VALUE)
                .thenComparingInt(tag -> tag.getInt("tier"))
                .thenComparing(tag -> tag.getUUID("human")));
        for (CompoundTag tag : savedEntries) {
            ledger.entries.put(tag.getUUID("human"),
                    new Entry(tag.getUUID("owner"), tag.getInt("tier"), ledger.nextSortOrder++));
        }
        for (Tag value : root.getList("pending_dismissals", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) value;
            if (tag.hasUUID("human") && tag.hasUUID("owner")) {
                ledger.pendingDismissals.put(tag.getUUID("human"), tag.getUUID("owner"));
            }
        }
        ledger.peacefulGeneration = Math.max(0L, root.getLong("peaceful_generation"));
        ledger.peacefulActive = root.getBoolean("peaceful_active");
        return ledger;
    }

    void hire(UUID human, UUID owner, int tier) {
        Entry previous = entries.get(human);
        long sortOrder = previous != null && previous.owner.equals(owner) && previous.tier == tier
                ? previous.sortOrder : nextSortOrder++;
        Entry replacement = new Entry(owner, tier, sortOrder);
        entries.put(human, replacement);
        boolean removedDismissal = pendingDismissals.remove(human) != null;
        if (!replacement.equals(previous) || removedDismissal) setDirty();
    }

    void dismiss(UUID human) {
        if (entries.remove(human) != null) setDirty();
    }

    int count(UUID owner, int tier) {
        return (int) entries.values().stream().filter(e -> e.owner.equals(owner) && e.tier == tier).count();
    }

    record Soldier(UUID id, int tier) {}

    List<Soldier> soldiers(UUID owner) {
        return entries.entrySet().stream()
                .filter(entry -> entry.getValue().owner.equals(owner))
                .map(entry -> new Soldier(entry.getKey(), entry.getValue().tier))
                .sorted(Comparator.comparingLong((Soldier soldier) -> entries.get(soldier.id()).sortOrder)
                        .thenComparing(Soldier::id))
                .toList();
    }

    /** Move one visible step; a tier filter only reorders soldiers in that tier. */
    boolean move(UUID human, UUID owner, int direction, int filter) {
        if (direction != -1 && direction != 1) return false;
        Entry current = entries.get(human);
        if (current == null || !current.owner.equals(owner)) return false;
        if (filter >= 0 && current.tier != filter) return false;
        List<Soldier> visibleSoldiers = soldiers(owner).stream()
                .filter(soldier -> filter < 0 || soldier.tier() == filter).toList();
        int index = -1;
        for (int i = 0; i < visibleSoldiers.size(); i++) {
            if (visibleSoldiers.get(i).id().equals(human)) {
                index = i;
                break;
            }
        }
        int next = index + direction;
        if (index < 0 || next < 0 || next >= visibleSoldiers.size()) return false;
        UUID otherId = visibleSoldiers.get(next).id();
        Entry other = entries.get(otherId);
        entries.put(human, new Entry(current.owner, current.tier, other.sortOrder));
        entries.put(otherId, new Entry(other.owner, other.tier, current.sortOrder));
        setDirty();
        return true;
    }

    List<UUID> soldierIds() {
        return List.copyOf(entries.keySet());
    }

    boolean isOwnedRecord(UUID human, UUID owner) {
        Entry entry = entries.get(human);
        return entry != null && entry.owner.equals(owner);
    }

    int tier(UUID human) {
        Entry entry = entries.get(human);
        return entry == null ? -1 : entry.tier;
    }

    boolean requestDismissal(UUID human, UUID owner) {
        if (!isOwnedRecord(human, owner)) return false;
        entries.remove(human);
        // Persist the dismissal before releasing capacity. An unloaded entity
        // with stale owner NBT is neutralized when it next joins the world.
        pendingDismissals.put(human, owner);
        setDirty();
        return true;
    }

    UUID pendingDismissalOwner(UUID human) {
        return pendingDismissals.get(human);
    }

    void finishDismissal(UUID human) {
        if (pendingDismissals.remove(human) != null) setDirty();
    }

    long peacefulGeneration() {
        return peacefulGeneration;
    }

    /** A Peaceful transition invalidates old wild Humans, not hired records. */
    boolean enterPeaceful() {
        if (peacefulActive) return false;
        peacefulActive = true;
        peacefulGeneration++;
        setDirty();
        return true;
    }

    void leavePeaceful() {
        if (peacefulActive) {
            peacefulActive = false;
            setDirty();
        }
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
            tag.putLong("sort_order", entry.sortOrder);
            list.add(tag);
        });
        root.put("entries", list);
        ListTag dismissals = new ListTag();
        pendingDismissals.forEach((human, owner) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("human", human);
            tag.putUUID("owner", owner);
            dismissals.add(tag);
        });
        root.put("pending_dismissals", dismissals);
        root.putLong("peaceful_generation", peacefulGeneration);
        root.putBoolean("peaceful_active", peacefulActive);
        return root;
    }

    private record Entry(UUID owner, int tier, long sortOrder) {}
}

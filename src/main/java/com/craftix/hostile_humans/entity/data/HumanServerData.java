package com.craftix.hostile_humans.entity.data;

import club.someoneice.humangunner.HumanServerDataCleanup;
import com.craftix.hostile_humans.HostileHumans;
import com.craftix.hostile_humans.entity.HumanEntity;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Per-save aggregate root. All mutations occur on the logical server thread.
 * Registry names, file name and HumanMobs NBT remain compatible with existing saves.
 * No static world/entity/player references survive a server shutdown.
 */
public final class HumanServerData extends SavedData implements HumanServerDataCleanup {
    public static final String HUMAN_MOBS_TAG = "HumanMobs";
    private final Map<UUID, HumanData> humans = new LinkedHashMap<>();
    private final ListTag unreadableRecords = new ListTag();
    private final dev.felix.hostilehumans.core.OwnerIndex ownership = new dev.felix.hostilehumans.core.OwnerIndex();

    public static String getFileId() { return "hostile_humans"; }
    public static void prepare(MinecraftServer server) { if (server != null) forServer(server); }
    private static HumanServerData forServer(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(HumanServerData::load, HumanServerData::new, getFileId());
    }
    public static HumanServerData get() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        // Async world generation must defer initialization to the entity's first server tick.
        return server == null || !server.isSameThread() ? null : forServer(server);
    }
    public static HumanServerData load(CompoundTag tag) {
        HumanServerData data = new HumanServerData();
        ListTag list = tag.getList(HUMAN_MOBS_TAG, 10);
        for (int i = 0; i < list.size(); i++) {
            try {
                HumanData human = new HumanData(list.getCompound(i));
                if (human.getUUID() == null) throw new IllegalArgumentException("missing UUID");
                data.put(human);
            } catch (RuntimeException invalidRecord) {
                // DimensionDataStorage may replace a failed loader with empty data.
                // Preserve malformed/unsupported records verbatim instead of losing the whole file.
                data.unreadableRecords.add(list.getCompound(i).copy());
                HostileHumans.LOGGER.warn("Preserved unreadable HumanMobs record {} for recovery ({})",
                        i, invalidRecord.getClass().getSimpleName());
            }
        }
        return data;
    }
    private void put(HumanData human) {
        UUID id = Objects.requireNonNull(human.getUUID());
        humans.put(id, human);
        ownership.assign(id, human.getOwnerUUID());
    }
    public HumanData getHumanMob(UUID id) { return id == null ? null : humans.get(id); }
    public Entity getHumanMobEntity(UUID id, ServerLevel level) {
        return level == null || getHumanMob(id) == null ? null : level.getEntity(id);
    }
    public Set<HumanData> getHumanMobs(UUID owner) {
        Set<HumanData> result = new LinkedHashSet<>();
        for (UUID id : ownership.members(owner)) {
            HumanData human = humans.get(id);
            if (human != null) result.add(human);
        }
        return Collections.unmodifiableSet(result);
    }
    public Set<Entity> getHumanMobsEntity(UUID owner, ServerLevel level) {
        Set<Entity> result = new HashSet<>();
        for (HumanData human : getHumanMobs(owner)) {
            Entity entity = getHumanMobEntity(human.getUUID(), level);
            if (entity != null) result.add(entity);
        }
        return result;
    }
    public void updateOrRegisterHumanMob(HumanEntity entity) { updateHumanMob(entity); }
    public HumanData updateHumanMob(HumanEntity entity) {
        HumanData human = humans.get(entity.getUUID());
        if (human == null) return registerHumanMob(entity);
        UUID previous = ownership.owner(entity.getUUID());
        human.load(entity);
        put(human);
        setDirty();
        if (previous != null && !Objects.equals(previous, human.getOwnerUUID())) syncHumanData(previous);
        syncHumanData(human);
        return human;
    }
    public void updateHumanData(HumanEntity entity) { updateHumanMob(entity); }
    public HumanData registerHumanMob(HumanEntity entity) { return registerHumanMob(entity, false); }
    public HumanData registerHumanMob(HumanEntity entity, boolean requireOwner) {
        HumanData existing = humans.get(entity.getUUID());
        if (existing != null) return existing;
        if (requireOwner && !entity.hasOwner()) return null;
        HumanData human = new HumanData(entity);
        put(human);
        setDirty();
        syncHumanData(human);
        return human;
    }
    public void syncHumanData(UUID owner) {
        if (owner != null) HumansServerDataClientSync.syncHumanData(owner, getHumanMobs(owner));
    }
    public void syncHumanData(HumanData human) { HumansServerDataClientSync.syncHumanData(human); }
    @Override public boolean humanGunner$removeHuman(UUID id) {
        if (id == null || humans.remove(id) == null) return false;
        UUID owner = ownership.assign(id, null);
        setDirty();
        if (owner != null) syncHumanData(owner);
        return true;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (HumanData human : humans.values()) {
            CompoundTag entry = new CompoundTag();
            human.save(entry);
            list.add(entry);
        }
        for (var entry : unreadableRecords) list.add(entry.copy());
        tag.put(HUMAN_MOBS_TAG, list);
        tag.putInt("UnifiedDataVersion", 1);
        return tag;
    }
}


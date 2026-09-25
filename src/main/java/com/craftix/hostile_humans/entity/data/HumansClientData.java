package com.craftix.hostile_humans.entity.data;

import com.craftix.hostile_humans.entity.data.HumanData;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public class HumansClientData {
    private static final ConcurrentHashMap<UUID, HumanData> humanMobEntitiesMap = new ConcurrentHashMap();

    public static void clear() { humanMobEntitiesMap.clear(); }

    public static void load(CompoundTag compoundTag) {
        if (compoundTag == null) return;
        if (compoundTag.contains("HumanMobs")) {
            clear();
            ListTag humanMobListTag = compoundTag.getList("HumanMobs", 10);
            for (int i = 0; i < humanMobListTag.size(); ++i) {
                HumansClientData.loadData(humanMobListTag.getCompound(i));
            }
        } else if (compoundTag.contains("UUID")) {
            HumansClientData.loadData(compoundTag);
        }
    }

    public static void loadData(CompoundTag compoundTag) {
        if (compoundTag != null && compoundTag.hasUUID("UUID")) {
            UUID humanMobUUID = compoundTag.getUUID("UUID");
            HumanData humanMobEntityData = humanMobEntitiesMap.get(humanMobUUID);
            if (humanMobEntityData != null) {
                humanMobEntityData.load(compoundTag);
            } else {
                HumanData humanMobEntity = new HumanData(compoundTag);
                HumansClientData.loadData(humanMobEntity);
            }
        }
    }

    public static void loadData(HumanData humanMobEntity) {
        if (humanMobEntity != null) {
            humanMobEntitiesMap.put(humanMobEntity.getUUID(), humanMobEntity);
        }
    }
}


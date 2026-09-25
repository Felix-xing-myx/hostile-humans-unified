package com.craftix.hostile_humans.entity.data;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.network.NetworkHandler;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class HumansServerDataClientSync {
    protected HumansServerDataClientSync() {
    }

    public static void syncHumanData(HumanData humanMobEntityData) {
        if (humanMobEntityData == null || !humanMobEntityData.hasOwner()) {
            return;
        }
        CompoundTag data = HumansServerDataClientSync.exportHHFollowerData(humanMobEntityData);
        UUID humanMobEntityUUID = humanMobEntityData.getUUID();
        UUID ownerUUID = humanMobEntityData.getOwnerUUID();
        NetworkHandler.updateHHFollowerData(humanMobEntityUUID, ownerUUID, data);
    }

    public static void syncHumanData(UUID ownerUUID, Set<HumanData> humanDataSet) {
        if (ownerUUID == null || humanDataSet == null) {
            return;
        }
        CompoundTag data = HumansServerDataClientSync.export(humanDataSet);
        NetworkHandler.updateHHFollowersData(ownerUUID, data);
    }

    public static CompoundTag exportHHFollowerData(HumanData humanMobEntityData) {
        if (humanMobEntityData == null || humanMobEntityData.getUUID() == null || humanMobEntityData.getOwnerUUID() == null) {
            return null;
        }
        CompoundTag compoundTag = new CompoundTag();
        humanMobEntityData.saveMetaData(compoundTag);
        return compoundTag;
    }

    public static CompoundTag export(Set<HumanData> humanDataSet) {
        if (humanDataSet == null) {
            return null;
        }
        CompoundTag compoundTag = new CompoundTag();
        ListTag humanMobListTag = new ListTag();
        for (HumanData humanMobEntity : humanDataSet) {
            if (humanMobEntity == null) continue;
            CompoundTag humanMobEntityCompoundTag = new CompoundTag();
            humanMobEntity.saveMetaData(humanMobEntityCompoundTag);
            humanMobListTag.add(humanMobEntityCompoundTag);
        }
        compoundTag.put("HumanMobs", (Tag)humanMobListTag);
        return compoundTag;
    }
}


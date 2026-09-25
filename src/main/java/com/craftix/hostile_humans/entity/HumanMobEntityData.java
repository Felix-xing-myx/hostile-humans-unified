package com.craftix.hostile_humans.entity;

import com.craftix.hostile_humans.entity.AggressionMode;
import com.craftix.hostile_humans.entity.HumanEntity;
import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.data.HumansDataSync;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class HumanMobEntityData
extends TamableAnimal
implements HumansDataSync {
    public static final EntityDataAccessor<BlockPos> DATA_SIT_POS = SynchedEntityData.defineId(HumanMobEntityData.class, (EntityDataSerializer)EntityDataSerializers.BLOCK_POS);
    public static final EntityDataAccessor<BlockPos> DATA_HOME_POS = SynchedEntityData.defineId(HumanMobEntityData.class, (EntityDataSerializer)EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> DATA_IS_SLEEPING_THIS_NIGHT = SynchedEntityData.defineId(HumanMobEntityData.class, (EntityDataSerializer)EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HAS_DECIDED_ON_SLEEP = SynchedEntityData.defineId(HumanMobEntityData.class, (EntityDataSerializer)EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING = SynchedEntityData.defineId(HumanMobEntityData.class, (EntityDataSerializer)EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_NAME = SynchedEntityData.defineId(HumanMobEntityData.class, (EntityDataSerializer)EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_VARIANT = SynchedEntityData.defineId(HumanMobEntityData.class, (EntityDataSerializer)EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(HumanMobEntityData.class, (EntityDataSerializer)EntityDataSerializers.INT);
    private static final int DATA_SYNC_TICK = 10;
    public String team = "";
    protected UUID persistentAngerTarget;
    private BlockPos orderedToPosition = null;
    private AggressionMode aggressionLevel = AggressionMode.PASSIVE;
    private boolean isDataSyncNeeded = false;
    private HumanEntity humanEntity;
    private int dataSyncTicker = 0;

    protected HumanMobEntityData(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public HumanEntity getSyncReference() {
        return this.humanEntity;
    }

    @Override
    public com.craftix.hostile_humans.entity.data.HumanServerData getServerData() {
        return level().isClientSide ? null : com.craftix.hostile_humans.entity.data.HumanServerData.get();
    }

    public void setSyncReference(HumanEntity humanEntity) {
        this.humanEntity = humanEntity;
    }

    public boolean isTamable() {
        return false;
    }

    public Player getNearestPlayer(TargetingConditions targetingConditions) {
        return this.level().getNearestPlayer(targetingConditions, (LivingEntity)this);
    }

    public boolean isChargingCrossbow() {
        return (Boolean)this.entityData.get(DATA_IS_CHARGING);
    }

    public void setCharging(boolean charging) {
        this.entityData.set(DATA_IS_CHARGING, charging);
    }

    public boolean isSleepingThisNight() {
        return (Boolean)this.entityData.get(DATA_IS_SLEEPING_THIS_NIGHT);
    }

    public void setSleepingThisNight(boolean val) {
        this.entityData.set(DATA_IS_SLEEPING_THIS_NIGHT, val);
    }

    public boolean hasDecidedToSleepTonight() {
        return (Boolean)this.entityData.get(DATA_HAS_DECIDED_ON_SLEEP);
    }

    public void setHasDecidedToSleepTonight(boolean val) {
        this.entityData.set(DATA_HAS_DECIDED_ON_SLEEP, val);
    }

    public void setDataSyncNeeded() {
        if (!this.level().isClientSide && this.hasOwner()) {
            this.isDataSyncNeeded = true;
        }
    }

    @Override
    public boolean getDataSyncNeeded() {
        return this.isDataSyncNeeded;
    }

    @Override
    public void setDataSyncNeeded(boolean dirty) {
        if (!this.level().isClientSide) {
            this.isDataSyncNeeded = dirty;
        }
    }

    public String getVariant() {
        String variant = (String)this.entityData.get(DATA_VARIANT);
        if (variant.isEmpty()) {
            variant = "skin1";
        }
        return variant;
    }

    public void setVariant(String variant) {
        this.entityData.set(DATA_VARIANT, variant);
    }

    public HumanTier getTier() {
        return HumanTier.byId((Integer)this.entityData.get(DATA_TIER));
    }

    public void setTier(HumanTier variant) {
        this.entityData.set(DATA_TIER, variant.id);
    }

    public String getCustomHumanMobName() {
        return (String)this.entityData.get(DATA_NAME);
    }

    public Component getCustomHumanMobNameComponent() {
        return Component.literal((String)this.getCustomHumanMobName());
    }

    public boolean hasOwner() {
        return this.getOwnerUUID() != null;
    }

    public boolean hasOwnerAndIsAlive() {
        return this.getOwnerUUID() != null && this.isAlive();
    }

    public void setOrderedToPosition(BlockPos blockPos) {
        if (this.orderedToPosition == blockPos) {
            return;
        }
        this.orderedToPosition = blockPos;
        this.setDataSyncNeeded();
    }

    public AggressionMode getAggressionLevel() {
        return this.aggressionLevel;
    }

    public void setAggressionLevel(AggressionMode aggressionLevel) {
        if (this.aggressionLevel == aggressionLevel) {
            return;
        }
        this.aggressionLevel = aggressionLevel;
        this.setDataSyncNeeded();
    }

    public void toggleAggressionLevel() {
        AggressionMode nextAggressionLevel = this.aggressionLevel.getNext();
        int loopProtection = 0;
        int maxLoopSize = AggressionMode.values().length;
        while (!this.isSupportedAggressionLevel(nextAggressionLevel) && loopProtection++ < maxLoopSize) {
            nextAggressionLevel = nextAggressionLevel.getNext();
        }
        if (nextAggressionLevel != this.aggressionLevel && this.isSupportedAggressionLevel(this.aggressionLevel)) {
            this.setAggressionLevel(nextAggressionLevel);
        }
    }

    public boolean isSupportedAggressionLevel(AggressionMode aggressionLevel) {
        return aggressionLevel != null;
    }

    @Nullable
    public HumanData getData() {
        return this.getData(this.getUUID());
    }

    public void setItemSlot(EquipmentSlot equipmentSlot, ItemStack itemStack) {
        super.setItemSlot(equipmentSlot, itemStack);
        HumanData data = this.getData();
        if (data != null) {
            if (equipmentSlot.getType() == EquipmentSlot.Type.ARMOR) {
                data.setArmorItem(equipmentSlot.getIndex(), itemStack.copy());
            } else if (equipmentSlot == EquipmentSlot.MAINHAND) {
                data.setHandItem(0, itemStack.copy());
            } else if (equipmentSlot == EquipmentSlot.OFFHAND) {
                data.setHandItem(1, itemStack.copy());
            }
        }
        this.setDataSyncNeeded();
    }

    public void setItemInHand(InteractionHand hand, ItemStack stack) {
        super.setItemInHand(hand, stack);
        HumanData data = this.getData();
        if (data != null) {
            data.setHandItem(hand == InteractionHand.MAIN_HAND ? 0 : 1, stack.copy());
        }
        this.setDataSyncNeeded();
    }

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_NAME, "");
        this.entityData.define(DATA_IS_CHARGING, false);
        this.entityData.define(DATA_IS_SLEEPING_THIS_NIGHT, false);
        this.entityData.define(DATA_HAS_DECIDED_ON_SLEEP, false);
        this.entityData.define(DATA_VARIANT, "skin1");
        this.entityData.define(DATA_TIER, 1);
        this.entityData.define(DATA_SIT_POS, new BlockPos(0, 0, 0));
        this.entityData.define(DATA_HOME_POS, new BlockPos(0, 0, 0));
    }

    public void addAdditionalSaveData(CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        compoundTag.putString("Variant", this.getVariant());
        compoundTag.putInt("SitPosX", this.getSitPos().getX());
        compoundTag.putInt("SitPosY", this.getSitPos().getY());
        compoundTag.putInt("SitPosZ", this.getSitPos().getZ());
        if (this.getHomePos() != null) {
            compoundTag.putInt("HomePosX", this.getHomePos().getX());
            compoundTag.putInt("HomePosY", this.getHomePos().getY());
            compoundTag.putInt("HomePosZ", this.getHomePos().getZ());
        }
        compoundTag.putString("HumanTeam", this.team);
    }

    public BlockPos getSitPos() {
        return (BlockPos)this.getEntityData().get(DATA_SIT_POS);
    }

    @Nullable
    public BlockPos getHomePos() {
        BlockPos pos = (BlockPos)this.getEntityData().get(DATA_HOME_POS);
        if (pos.getY() == 0 && pos.getX() == 0 && pos.getZ() == 0) {
            return null;
        }
        return pos;
    }

    public void setHomePos(BlockPos pos) {
        this.getEntityData().set(DATA_HOME_POS, pos);
    }

    public void readAdditionalSaveData(CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        int i = compoundTag.getInt("SitPosX");
        int j = compoundTag.getInt("SitPosY");
        int k = compoundTag.getInt("SitPosZ");
        this.team = compoundTag.getString("HumanTeam");
        if (this.team.isEmpty()) {
            this.team = "human";
        }
        this.entityData.set(DATA_SIT_POS, new BlockPos(i, j, k));
        int i1 = compoundTag.getInt("HomePosX");
        int j1 = compoundTag.getInt("HomePosY");
        int k1 = compoundTag.getInt("HomePosZ");
        this.entityData.set(DATA_HOME_POS, new BlockPos(i1, j1, k1));
        this.setVariant(compoundTag.getString("Variant"));
    }

    public HumanEntity getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
        return null;
    }

    public void tick() {
        super.tick();
        if (++this.dataSyncTicker >= DATA_SYNC_TICK) {
            this.dataSyncTicker = 0;
            this.syncDataIfNeeded();
        }
    }
}


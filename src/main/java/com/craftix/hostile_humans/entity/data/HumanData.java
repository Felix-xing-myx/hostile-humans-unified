package com.craftix.hostile_humans.entity.data;

import com.craftix.hostile_humans.entity.AggressionMode;
import com.craftix.hostile_humans.entity.HumanEntity;
import com.craftix.hostile_humans.entity.data.HumanHelper;
import com.craftix.hostile_humans.entity.data.HumanServerData;
import com.craftix.hostile_humans.patch.HostileHumansEquipmentPatch;
import java.util.UUID;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

public class HumanData {
    public static final String UUID_TAG = "UUID";
    private static final String ENTITY_AGGRESSION_LEVEL = "EntityAggressionLevel";
    private static final String ENTITY_DATA_TAG = "EntityData";
    private static final String ENTITY_ID_TAG = "EntityId";
    private static final String ENTITY_SITTING_TAG = "EntitySitting";
    private static final String ENTITY_TYPE_TAG = "EntityType";
    private static final String LEVEL_TAG = "Level";
    private static final String NAME_TAG = "Name";
    private static final String OWNER_NAME_TAG = "OwnerName";
    private static final String OWNER_TAG = "Owner";
    private static final String POSITION_TAG = "Position";
    private AggressionMode entityAggressionLevel = AggressionMode.PASSIVE;
    private BlockPos blockPos;

    private CompoundTag entityData;
    private EntityType<?> entityType;
    private NonNullList<ItemStack> armorItems = NonNullList.withSize((int)4, ItemStack.EMPTY);
    private NonNullList<ItemStack> handItems = NonNullList.withSize((int)2, ItemStack.EMPTY);
    private NonNullList<ItemStack> inventoryItems = NonNullList.withSize((int)30, ItemStack.EMPTY);
    private java.lang.ref.WeakReference<HumanEntity> humanRef = new java.lang.ref.WeakReference<>(null);
    private ResourceKey<Level> level;

    private String levelName = "";
    private String name = "";
    private String ownerName = "";
    private UUID humanMobUUID = null;
    private UUID ownerUUID = null;
    private boolean entitySitting = false;
    private boolean hasOwner = false;
    private int entityId;

    public HumanData(HumanEntity humanMob) {
        this.load(humanMob);
    }

    public HumanData(CompoundTag compoundTag) {
        this.load(compoundTag);
    }

    public boolean hasOwner() {
        return this.ownerUUID != null;
    }

    public UUID getUUID() {
        return this.humanMobUUID;
    }

    public String getName() {
        return this.name;
    }

    public UUID getOwnerUUID() {
        if (this.ownerUUID == null) {
            return null;
        }
        return this.ownerUUID;
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    public ResourceKey<Level> getLevelKey() {
        return this.level;
    }

    public HumanEntity getHHFollowerEntity() {
        HumanEntity cached = humanRef.get();
        if (cached != null && !cached.isRemoved() && humanMobUUID.equals(cached.getUUID())) return cached;
        return null;
    }

    public NonNullList<ItemStack> getArmorItems() {
        return this.armorItems;
    }

    public void setArmorItems(NonNullList<ItemStack> armor) {
        this.armorItems = armor;
        this.setDirty();
    }

    public void setArmorItem(int index, ItemStack itemStack) {
        this.armorItems.set(index, itemStack);
        this.setDirty();
    }

    public NonNullList<ItemStack> getHandItems() {
        return this.handItems;
    }

    public void setHandItems(NonNullList<ItemStack> hand) {
        this.handItems = hand;
        this.setDirty();
    }

    public void setHandItem(int index, ItemStack itemStack) {
        this.handItems.set(index, itemStack);
        this.setDirty();
    }

    public NonNullList<ItemStack> getInventoryItems() {
        return this.inventoryItems;
    }

    public void setInventoryItem(int index, ItemStack itemStack) {
        this.inventoryItems.set(index, itemStack);
        this.setDirty();
    }

    public void markInventoryDirty() {
        this.setDirty();
    }

    @Nonnull
    public ItemStack getInventoryItem(int index) {
        return (ItemStack)this.inventoryItems.get(index);
    }

    public int getInventoryItemsSize() {
        return this.inventoryItems.size();
    }

    public boolean storeInventoryItem(ItemStack itemStack) {
        if (!club.someoneice.humangunner.HumanGunAcceptance.accepts(itemStack)) return false;
        return HostileHumansEquipmentPatch.storePickupTransactionally(this, itemStack);
    }

    public void load(HumanEntity humanMob) {
        this.humanRef = new java.lang.ref.WeakReference<>(humanMob);
        this.humanMobUUID = humanMob.getUUID();
        this.name = humanMob.getCustomHumanMobName();
        this.ownerUUID = null;
        this.ownerName = "";
        this.hasOwner = humanMob.hasOwner();
        if (this.hasOwner) {
            this.ownerUUID = humanMob.getOwnerUUID();
            if (humanMob.getOwner() != null) {
                this.ownerName = humanMob.getOwner().getName().getString();
            }
        }
        this.blockPos = humanMob.blockPosition();
        this.level = humanMob.level().dimension();
        this.levelName = String.valueOf(this.level.registry()) + "/" + String.valueOf(this.level.location());
        this.entityId = humanMob.getId();
        this.entityAggressionLevel = humanMob.getAggressionLevel();
        this.entityType = humanMob.getType();
        this.entitySitting = humanMob.isOrderedToSit();
        this.entityData = humanMob.serializeNBT();
        this.setArmorItems((NonNullList<ItemStack>)((NonNullList)humanMob.getArmorSlots()));
        this.setHandItems((NonNullList<ItemStack>)((NonNullList)humanMob.getHandSlots()));
    }

    public void load(CompoundTag compoundTag) {
        this.humanMobUUID = compoundTag.getUUID(UUID_TAG);
        this.name = compoundTag.getString(NAME_TAG);
        this.ownerUUID = null;
        this.ownerName = "";
        this.hasOwner = compoundTag.hasUUID(OWNER_TAG);
        if (this.hasOwner) {
            this.ownerUUID = compoundTag.getUUID(OWNER_TAG);
            if (compoundTag.contains(OWNER_NAME_TAG)) {
                this.ownerName = compoundTag.getString(OWNER_NAME_TAG);
            }
        }
        this.blockPos = NbtUtils.readBlockPos((CompoundTag)compoundTag.getCompound(POSITION_TAG));
        if (compoundTag.contains(LEVEL_TAG)) {
            this.levelName = compoundTag.getString(LEVEL_TAG);
            if (this.levelName.contains("/")) {
                String[] levelNameParts = this.levelName.split("/", 2);
                ResourceLocation registryName = new ResourceLocation(levelNameParts[0]);
                ResourceLocation locationName = new ResourceLocation(levelNameParts[1]);
                this.level = ResourceKey.create((ResourceKey)ResourceKey.createRegistryKey((ResourceLocation)registryName), (ResourceLocation)locationName);
            }
        }
        if (compoundTag.contains(ENTITY_ID_TAG)) {
            this.entityId = compoundTag.getInt(ENTITY_ID_TAG);
        }
        if (compoundTag.contains(ENTITY_TYPE_TAG)) {
            this.entityType = (EntityType)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(compoundTag.getString(ENTITY_TYPE_TAG)));
        }
        this.entityData = compoundTag.getCompound(ENTITY_DATA_TAG);
        this.entitySitting = compoundTag.getBoolean(ENTITY_SITTING_TAG);
        HumanHelper.loadArmorItems(compoundTag, this.armorItems);
        HumanHelper.loadHandItems(compoundTag, this.handItems);
        HumanHelper.loadInventoryItems(compoundTag, this.inventoryItems);
        if (compoundTag.contains(ENTITY_AGGRESSION_LEVEL)) {
            this.entityAggressionLevel = AggressionMode.get(compoundTag.getString(ENTITY_AGGRESSION_LEVEL));
        }
    }

    public CompoundTag save(CompoundTag compoundTag) {
        return this.save(compoundTag, true);
    }

    public CompoundTag saveMetaData(CompoundTag compoundTag) {
        return this.save(compoundTag, false);
    }

    public CompoundTag save(CompoundTag compoundTag, boolean includeData) {
        HumanEntity humanEntity;
        compoundTag.putUUID(UUID_TAG, this.humanMobUUID);
        compoundTag.putString(NAME_TAG, this.name);
        compoundTag.put(POSITION_TAG, (Tag)NbtUtils.writeBlockPos((BlockPos)this.blockPos));
        if (!this.levelName.isEmpty()) {
            compoundTag.putString(LEVEL_TAG, this.levelName);
        }
        compoundTag.putInt(ENTITY_ID_TAG, this.entityId);
        ResourceLocation entityTypeId = ForgeRegistries.ENTITY_TYPES.getKey(this.entityType);
        if (entityTypeId != null) {
            compoundTag.putString(ENTITY_TYPE_TAG, entityTypeId.toString());
        }
        humanEntity = this.getHHFollowerEntity();
        if (includeData && humanEntity != null && humanEntity.isAlive()) {
            this.entityData = humanEntity.serializeNBT();
        }
        if (includeData && this.entityData != null) {
            compoundTag.put(ENTITY_DATA_TAG, (Tag)this.entityData);
        }
        if (humanEntity != null && humanEntity.isAlive()) {
            compoundTag.putBoolean(ENTITY_SITTING_TAG, humanEntity.isOrderedToSit());
            compoundTag.putString(ENTITY_AGGRESSION_LEVEL, humanEntity.getAggressionLevel().name());
            if (humanEntity.getOwner() != null) {
                compoundTag.putUUID(OWNER_TAG, humanEntity.getOwner().getUUID());
                compoundTag.putString(OWNER_NAME_TAG, humanEntity.getOwner().getName().getString());
            }
            this.setArmorItems((NonNullList<ItemStack>)((NonNullList)humanEntity.getArmorSlots()));
            this.setHandItems((NonNullList<ItemStack>)((NonNullList)humanEntity.getHandSlots()));
        } else {
            compoundTag.putBoolean(ENTITY_SITTING_TAG, this.entitySitting);
            compoundTag.putString(ENTITY_AGGRESSION_LEVEL, this.entityAggressionLevel.name());
            if (this.ownerUUID != null) {
                compoundTag.putUUID(OWNER_TAG, this.ownerUUID);
                compoundTag.putString(OWNER_NAME_TAG, this.ownerName);
            }
        }
        HumanHelper.saveArmorItems(compoundTag, this.armorItems);
        HumanHelper.saveHandItems(compoundTag, this.handItems);
        HumanHelper.saveInventoryItems(compoundTag, this.inventoryItems);
        return compoundTag;
    }

    private void setDirty() {
        HumanEntity entity = humanRef.get();
        if (entity != null && entity.level().isClientSide) return;
        HumanServerData serverData = HumanServerData.get();
        if (serverData != null) {
            serverData.setDirty();
        }
    }

    public boolean is(HumanData humanMobEntity) {
        return humanMobEntity != null && java.util.Objects.equals(this.humanMobUUID, humanMobEntity.humanMobUUID);
    }

    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (!(object instanceof HumanData)) {
            return false;
        }
        HumanData humanMobEntity = (HumanData)object;
        return humanMobEntity.getUUID().equals(this.humanMobUUID);
    }

    public int hashCode() {
        return this.humanMobUUID.hashCode();
    }
}


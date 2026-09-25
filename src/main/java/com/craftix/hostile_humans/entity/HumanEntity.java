package com.craftix.hostile_humans.entity;

import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.AggressionMode;
import com.craftix.hostile_humans.entity.HumanCommand;
import com.craftix.hostile_humans.entity.HumanMobEntityData;
import com.craftix.hostile_humans.entity.ai.control.HumanEntityWalkControl;
import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.data.HumanServerData;
import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.type.human.PickUpLoot;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="hostile_humans")
public class HumanEntity
extends HumanMobEntityData {
    public void advanceUseTime(int ticks) {
        if (isUsingItem()) useItemRemaining = Math.max(0, useItemRemaining - Math.max(0, ticks));
    }
    public void addCombatGoal(int priority, net.minecraft.world.entity.ai.goal.Goal goal) {
        goalSelector.addGoal(priority, goal);
    }
    public void addTargetGoal(int priority, net.minecraft.world.entity.ai.goal.Goal goal) {
        targetSelector.addGoal(priority, goal);
    }
    public static final MobCategory CATEGORY = MobCategory.MONSTER;
    protected PickUpLoot pick;

    public HumanEntity(EntityType<? extends HumanEntity> entityType, Level level) {
        super(entityType, level);
        this.setSyncReference(this);
        this.navigation.setCanFloat(true);
        this.setDataSyncNeeded();
        this.moveControl = new HumanEntityWalkControl((Mob)this);
        this.setAggressionLevel(AggressionMode.AGGRESSIVE_MONSTER);
        this.pick = new PickUpLoot(this, level);
    }

    public Item getTameItem() {
        return null;
    }

    public Ingredient getFoodItems() {
        ArrayList all = Lists.newArrayList((Object[])HumanUtil.EDIBLE_ITEMS);
        all.addAll(Lists.newArrayList((Object[])HumanUtil.EDIBLE_ITEMS_2));
        all.addAll(Lists.newArrayList((Object[])Human.EXTRA_EDIBLE_ITEMS));
        return Ingredient.of(all.stream());
    }

    protected void pet() {
        if (this.getHealth() < this.getMaxHealth()) {
            this.setHealth((float)((double)this.getHealth() + 0.1));
        }
    }

    public void follow() {
        this.setOrderedToSit(false);
        this.navigation.recomputePath();
    }

    public boolean eat(ItemStack itemStack, Player player) {
        if (!this.canEat(itemStack)) {
            return false;
        }
        this.gameEvent(GameEvent.ENTITY_INTERACT, (Entity)player);
        Item item = itemStack.getItem();
        this.heal(item.getFoodProperties() != null ? (float)item.getFoodProperties().getNutrition() : 0.5f);
        if (item.getFoodProperties() != null) {
            for (Pair pair : item.getFoodProperties().getEffects()) {
                if (this.level().isClientSide || pair.getFirst() == null || !(this.getRandom().nextFloat() < ((Float)pair.getSecond()).floatValue())) continue;
                this.addEffect(new MobEffectInstance((MobEffectInstance)pair.getFirst()));
            }
        }
        if (player != null && !player.getAbilities().instabuild) {
            itemStack.shrink(1);
        }
        this.gameEvent(GameEvent.EAT);
        return true;
    }

    public boolean canEat() {
        return this.getHealth() < this.getMaxHealth() && ((Human)this).needsFood();
    }

    public boolean canEat(ItemStack itemStack) {
        return this.isFood(itemStack) && this.getHealth() < this.getMaxHealth() && ((Human)this).needsFood();
    }

    protected void sit() {
        this.setOrderedToSit(true);
        this.navigation.stop();
        this.entityData.set(DATA_SIT_POS, this.blockPosition());
        super.setTarget(null);
    }

    @Override
    public void tick() {
        super.tick();
        // Also release entities already saved as boat passengers before this
        // restriction was added; otherwise the new startRiding guard is too late.
        if (this.getVehicle() instanceof Boat) {
            this.stopRiding();
        }
        this.pick.tick();
    }

    public void handleCommand(HumanCommand command) {
        switch (command) {
            case SIT: {
                this.sit();
                break;
            }
            case FOLLOW: {
                this.follow();
                break;
            }
            case SIT_FOLLOW_TOGGLE: {
                if (this.isOrderedToSit()) {
                    this.follow();
                    break;
                }
                this.sit();
                break;
            }
            case AGGRESSION_LEVEL_TOGGLE: {
                this.toggleAggressionLevel();
                break;
            }
            case PET: {
                this.pet();
            }
        }
    }

    public void finalizeSpawn() {
        if (!this.hasCustomName()) {
            // empty if block
        }
        this.registerData();
    }

    public void sendOwnerMessage(Component component) {
        LivingEntity owner = this.getOwner();
        if (component != null && owner instanceof Player) {
            Player player = (Player)owner;
            player.displayClientMessage(component, false);
        }
    }

    public int getAmbientSoundInterval() {
        return 400;
    }

    public float getSoundVolume() {
        return 1.0f;
    }

    public void setTarget(@Nullable LivingEntity livingEntity) {
        if (this.getTarget() == livingEntity) {
            return;
        }
        if (livingEntity == null || !livingEntity.isAlive()) {
            super.setTarget(null);
            this.setDataSyncNeeded();
            return;
        }
        super.setTarget(livingEntity);
        this.setDataSyncNeeded();
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        if (vehicle instanceof Boat) {
            return false;
        }
        return super.startRiding(vehicle, force);
    }

    public void tame(Player player) {
        super.tame(player);
        if (player instanceof ServerPlayer) {
            this.registerData();
        }
    }

    @Override
    public HumanEntity getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
        return null;
    }

    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    public boolean isFood(ItemStack itemStack) {
        if (this.getFoodItems() != null) {
            return this.getFoodItems().test(itemStack);
        }
        return false;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        this.setDataSyncNeeded();
    }

    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor serverLevelAccessor, DifficultyInstance difficulty, MobSpawnType mobSpawnType, @Nullable SpawnGroupData spawnGroupData, @Nullable CompoundTag compoundTag) {
        spawnGroupData = super.finalizeSpawn(serverLevelAccessor, difficulty, mobSpawnType, spawnGroupData, compoundTag);
        this.finalizeSpawn();
        return spawnGroupData;
    }

    protected void dropEquipment() {
        if (!this.level().isClientSide) {
            HumanData humanMobEntityData = HumanServerData.get().getHumanMob(this.getUUID());
            if (humanMobEntityData == null) {
                return;
            }
            float dropChance = 0.2f;
            NonNullList<ItemStack> inventory = humanMobEntityData.getInventoryItems();
            if (inventory != null) {
                for (ItemStack itemstack : inventory) {
                    if (itemstack.isEmpty() || EnchantmentHelper.hasVanishingCurse((ItemStack)itemstack)) continue;
                    if (this.getMainHandItem() == itemstack) {
                        if (!(this.random.nextFloat() < dropChance)) continue;
                        this.spawnAtLocation(itemstack);
                        continue;
                    }
                    this.spawnAtLocation(itemstack);
                }
            }
        }
    }

    public void die(DamageSource damageSource) {
        super.die(damageSource);
        this.clearFire();
        this.dropLeash(true, true);
        this.removeAllEffects();
    }

    public void setOrderedToSit(boolean sit) {
        if (this.isOrderedToSit() != sit) {
            super.setOrderedToSit(sit);
            this.setDataSyncNeeded();
        }
    }
}


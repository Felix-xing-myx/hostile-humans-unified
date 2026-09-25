package com.craftix.hostile_humans.entity.entities;

import club.someoneice.humangunner.ConditionalGoal;
import club.someoneice.humangunner.HumanGunner;
import net.minecraft.world.entity.ai.goal.Goal;
import club.someoneice.humangunner.RangedHybridMeleeGoal;
import club.someoneice.humangunner.RangedWeaponCustody;
import club.someoneice.humangunner.SpartanRangedCompat;
import club.someoneice.humangunner.StaticCombatGoalHost;
import club.someoneice.humangunner.TargetPreservingConditionalGoal;
import club.someoneice.humangunner.TridentHybridGoal;
import com.craftix.hostile_humans.entity.data.HumanServerData;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.sounds.SoundSource;
import com.craftix.hostile_humans.Config;
import com.craftix.hostile_humans.HostileHumans;
import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.HumanEntity;
import com.craftix.hostile_humans.entity.PotionRangedAttackMob;
import com.craftix.hostile_humans.entity.ai.goal.AvoidCreeperGoal;
import com.craftix.hostile_humans.entity.ai.goal.AvoidTNTGoal;
import com.craftix.hostile_humans.entity.ai.goal.BowAttack;
import com.craftix.hostile_humans.entity.ai.goal.CrossbowGoal;
import com.craftix.hostile_humans.entity.ai.goal.FindWaterOnFireGoal;
import com.craftix.hostile_humans.entity.ai.goal.HumanFloatGoal;
import com.craftix.hostile_humans.entity.ai.goal.HumanLookAtPlayerGoal;
import com.craftix.hostile_humans.entity.ai.goal.InvestigateSoundGoal;
import com.craftix.hostile_humans.entity.ai.goal.LadderClimbGoal;
import com.craftix.hostile_humans.entity.ai.goal.LookForChestGoal;
import com.craftix.hostile_humans.entity.ai.goal.MeleeAttackGoal;
import com.craftix.hostile_humans.entity.ai.goal.NearestAttackableTargetGoalCustom;
import com.craftix.hostile_humans.entity.ai.goal.NearestAttackableTargetGoalWithHumanLimiter;
import com.craftix.hostile_humans.entity.ai.goal.OpenDoorsGoal;
import com.craftix.hostile_humans.entity.ai.goal.OpenFenceGoal;
import com.craftix.hostile_humans.entity.ai.goal.OpenTrapdoorGoal;
import com.craftix.hostile_humans.entity.ai.goal.PotionRangedAttackGoal;
import com.craftix.hostile_humans.entity.ai.goal.RandomStrollGoalFar;
import com.craftix.hostile_humans.entity.ai.goal.RandomStrollGoalWithHome;
import com.craftix.hostile_humans.entity.ai.goal.RunFromTarget;
import com.craftix.hostile_humans.entity.ai.goal.TridentAttackGoal;
import com.craftix.hostile_humans.entity.entities.HumanFood;
import com.craftix.hostile_humans.entity.entities.HumanInventoryGenerator;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import com.craftix.hostile_humans.entity.entities.ModEntityType;
import com.craftix.hostile_humans.patch.HostileHumansEquipmentPatch;
import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.NotNull;

public class Human
extends HumanEntity
implements RangedAttackMob,
CrossbowAttackMob,
PotionRangedAttackMob, StaticCombatGoalHost {
    private boolean humanGunner$staticCombatGoalsInstalled;
    public final club.someoneice.humangunner.SoldierCombatMemory soldierCombatMemory =
            new club.someoneice.humangunner.SoldierCombatMemory();

    public void forgetSoldierTarget() {
        if (getTarget() != null) soldierCombatMemory.forget(getTarget().getUUID(), level().getGameTime());
        persistentAngerTarget = null;
        setLastHurtByMob(null);
        setLastHurtMob(null);
        setTarget(null);
        getNavigation().stop();
        stopUsingItem();
    }

    private Goal humanGunner$gunnerGoal;
    private boolean humanGunner$movementShieldAllowance;

    private BowAttack<Human> humanGunner$enhancedBowGoal;

    private CrossbowGoal<Human> humanGunner$enhancedCrossbowGoal;

    private TridentHybridGoal humanGunner$tridentHybridGoal;

    private RangedHybridMeleeGoal humanGunner$rangedHybridMeleeGoal;

    @Override public void humanGunner$installStaticCombatGoals() {
        if (humanGunner$staticCombatGoalsInstalled) {
            return;
        }
        Human human = this;
        GoalSelector goals = this.goalSelector;
        humanGunner$gunnerGoal = club.someoneice.humangunner.GunSupport.get().goal(human);
        // The Human bow goal advances its use timer faster than real time and
        // consumes this cooldown in two places per tick. Raising the interval
        // to 17 keeps that compatibility behavior while lowering the resulting
        // shot cadence by about 30% overall (not merely increasing the counter
        // by 30%).
        humanGunner$enhancedBowGoal = new BowAttack<>(human, 1.0D, 17, 36.0F);
        humanGunner$enhancedCrossbowGoal = new CrossbowGoal<>(human, 1.0D, 48.0F);
        humanGunner$tridentHybridGoal = new TridentHybridGoal(human);
        humanGunner$rangedHybridMeleeGoal = new RangedHybridMeleeGoal(human);

        // Remove the one weapon-specific goal installed by Hostile Humans, then
        // register every mode exactly once. ConditionalGoal switches modes via
        // canUse/canContinueToUse without ever editing GoalSelector again.
        goals.addGoal(2, humanGunner$gunnerGoal);
        goals.addGoal(2, humanGunner$tridentHybridGoal);
        goals.addGoal(2, humanGunner$rangedHybridMeleeGoal);
        goals.addGoal(2, new ConditionalGoal(humanGunner$enhancedCrossbowGoal,
                () -> humanGunner$isCrossbowMode(human)));
        goals.addGoal(2, new ConditionalGoal(humanGunner$enhancedBowGoal,
                () -> humanGunner$isBowMode(human)));
        goals.addGoal(2, new TargetPreservingConditionalGoal(
                human,
                meleeAttackGoal,
                () -> humanGunner$isMeleeMode(human),
                () -> RangedWeaponCustody.isActive(human)
        ));
        humanGunner$staticCombatGoalsInstalled = true;
    
    }
    private static boolean humanGunner$isCrossbowMode(Human human) {
        return !RangedWeaponCustody.hasGunPriority(human)
                && !(human.getMainHandItem().getItem() instanceof TridentItem)
                && (RangedWeaponCustody.isCrossbowWeapon(human.getMainHandItem())
                || RangedWeaponCustody.isCrossbowWeapon(human.getOffhandItem()));
    }

    private static boolean humanGunner$isBowMode(Human human) {
        return !RangedWeaponCustody.hasGunPriority(human)
                && !(human.getMainHandItem().getItem() instanceof TridentItem)
                && !RangedWeaponCustody.isCrossbowWeapon(human.getMainHandItem())
                && (RangedWeaponCustody.isBowWeapon(human.getMainHandItem())
                || RangedWeaponCustody.isBowWeapon(human.getOffhandItem()));
    }

    private static boolean humanGunner$isMeleeMode(Human human) {
        ItemStack mainHand = human.getMainHandItem();
        ItemStack offHand = human.getOffhandItem();
        return !club.someoneice.humangunner.GunSupport.get().isGun(mainHand)
                && !RangedWeaponCustody.isActive(human)
                && !(mainHand.getItem() instanceof TridentItem)
                && !(mainHand.getItem() instanceof CrossbowItem)
                && !(offHand.getItem() instanceof CrossbowItem)
                && !(mainHand.getItem() instanceof BowItem)
                && !(offHand.getItem() instanceof BowItem);
    }

    private void bootstrapMissingHumanData() {
        Human human = this;
        if (human.level().isClientSide || (human.getData() != null
                && !human.getPersistentData().getBoolean("hostile_humans:pending_loadout"))) {
            return;
        }
        HumanServerData serverData = HumanServerData.get();
        if (serverData == null || serverData.registerHumanMob(human) == null) {
            return;
        }

        // Respect explicitly supplied summon equipment. Plain /summon and
        // EntityType#create entities still need the normal tier loadout.
        boolean hasExplicitEquipment = false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!human.getItemBySlot(slot).isEmpty()) {
                hasExplicitEquipment = true;
                break;
            }
        }
        boolean pending = human.getPersistentData().getBoolean("hostile_humans:pending_loadout");
        if (!hasExplicitEquipment || pending) {
            HumanInventoryGenerator.generateInventory(human,
                    human.getPersistentData().getBoolean("hostile_humans:pending_ranged"));
            human.getPersistentData().remove("hostile_humans:pending_loadout");
            human.getPersistentData().remove("hostile_humans:pending_ranged");
        }
    
    }
    private static final UUID FLURRY_ID = UUID.fromString("82624020-1000-4000-8000-000000000204");
    private static final AttributeModifier FLURRY_DAMAGE = new AttributeModifier(
            FLURRY_ID, "Human flurry damage", -0.6, AttributeModifier.Operation.MULTIPLY_TOTAL);
    private boolean attributesMigrated;
    private static void humanGunner$removeLoyalty(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        java.util.Map<Enchantment, Integer> enchantments =
                new java.util.HashMap<>(EnchantmentHelper.getEnchantments(stack));
        if (enchantments.remove(Enchantments.LOYALTY) != null) {
            EnchantmentHelper.setEnchantments(enchantments, stack);
        }
    
    }
    @Override public void broadcastBreakEvent(EquipmentSlot slot) {
        if (!level().isClientSide) club.someoneice.humangunner.EquipmentBreakSounds.playNow(this, slot);
        super.broadcastBreakEvent(slot);
    }
    @Override public void playSound(SoundEvent sound, float volume, float pitch) {
        ItemStack active = getUseItem();
        UseAnim animation = active.getUseAnimation();
        boolean action = isUsingItem() && !active.isEmpty()
                && ((animation == UseAnim.EAT && sound.equals(active.getEatingSound()))
                || (animation == UseAnim.DRINK && sound.equals(active.getDrinkingSound())));
        if (!action) { super.playSound(sound, volume, pitch); return; }
        if (!level().isClientSide && !isSilent())
            level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.PLAYERS,
                    volume * 1.5F, pitch);
    }
    public boolean needCheckAmmo() { return false; }
    public boolean consumesAmmoOrNot() { return true; }

    public static final ItemStack[] EXTRA_EDIBLE_ITEMS = new ItemStack[]{Items.GOLDEN_APPLE.getDefaultInstance(), PotionUtils.setPotion((ItemStack)Items.POTION.getDefaultInstance(), (Potion)Potions.REGENERATION), PotionUtils.setPotion((ItemStack)Items.POTION.getDefaultInstance(), (Potion)Potions.HEALING), PotionUtils.setPotion((ItemStack)Items.POTION.getDefaultInstance(), (Potion)Potions.STRENGTH)};
    public static final ItemStack[] PRE_ATTACK_BUFF_ITEMS = new ItemStack[]{Items.GOLDEN_APPLE.getDefaultInstance(), PotionUtils.setPotion((ItemStack)Items.POTION.getDefaultInstance(), (Potion)Potions.STRENGTH), PotionUtils.setPotion((ItemStack)Items.POTION.getDefaultInstance(), (Potion)Potions.REGENERATION), PotionUtils.setPotion((ItemStack)Items.POTION.getDefaultInstance(), (Potion)Potions.SWIFTNESS)};
    public static final ItemStack[] MID_FIGHT_BUFF_ITEMS = new ItemStack[]{Items.GOLDEN_APPLE.getDefaultInstance(), PotionUtils.setPotion((ItemStack)Items.POTION.getDefaultInstance(), (Potion)Potions.STRONG_REGENERATION)};
    private static final UUID MODIFIER_UUID = UUID.fromString("7a0811af-4025-4691-ba75-2d638d4ab3f4");
    // Eating/shield movement multipliers are configurable through the unified AI settings.
    private static final Map<String, ResourceLocation> TEXTURE_BY_VARIANT = (Map)Util.make(Maps.newHashMap(), hashMap -> {
        for (int i = 1; i <= 37; ++i) {
            String name = "skin" + i;
            hashMap.put(name, new ResourceLocation("hostile_humans", "textures/entity/human/" + name + ".png"));
        }
    });
    private final MeleeAttackGoal meleeAttackGoal = new MeleeAttackGoal(this, 1.05, true);
    public int shieldCoolDown;
    public int shieldUpTicks;
    public int ticksEyesOutOfWater;
    public int switchingWeaponCoolDown;
    public int meleeFlurryHitsRemaining;
    public int meleeFlurryDamageTicks;
    public int onPlayerJumpCoolDown;
    private boolean resolvedFleeThisCombat;
    private boolean shouldFleeThisCombat;
    public boolean isFleeing;
    public long lastCombatTime;
    @Nullable
    public LivingEntity toAvoid;
    public BlockPos investigateSound = BlockPos.ZERO;
    public int lookForChestCooldown;
    public HumanFood food = new HumanFood();
    public int healCooldown;
    public int ticksOutOfCombat;
    public boolean isAlert;
    public static final EntityDimensions STANDING_DIMENSIONS = EntityDimensions.scalable((float)0.6f, (float)1.8f);
    public static final EntityDimensions SWIMMING_DIMENSIONS = EntityDimensions.scalable((float)0.6f, (float)0.6f);
    public boolean shouldCatchBreath;
    public int breathRecoveryTicks;
    private double lastDetectedExitY = Double.NaN;
    private int swimHoldTicks;
    private boolean latchedWaterMovement;
    private int waterMovementLockTicks;
    private int waterMovementLatchTick = -1;
    protected final WaterBoundPathNavigation waterNavigation;
    protected final GroundPathNavigation groundNavigation;

    public BlockPos investigateSound() {
        return this.investigateSound;
    }

    public void setInvestigateSound(BlockPos investigateSound) {
        this.investigateSound = investigateSound;
    }

    public void addExhaustion(float p_38704_) {
        this.food.exhaustionLevel = Math.min(this.food.exhaustionLevel + p_38704_, 40.0f);
    }

    public boolean needsFood() {
        return this.food.foodLevel < 20.0f;
    }

    public void eat(int food, float sat) {
        this.food.foodLevel = Math.min((float)food + this.food.foodLevel, 20.0f);
        this.food.saturationLevel = Math.min(this.food.saturationLevel + (float)food * sat * 2.0f, this.food.foodLevel);
    }

    public Human(EntityType<? extends HumanEntity> entityType, Level level, HumanTier type) {
        super(entityType, level);
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0f);
        // Make normal path selection strongly prefer routes around lava, while
        // keeping lava nodes traversable so the emergency escape goal can path out.
        this.setPathfindingMalus(BlockPathTypes.LAVA, 16.0f);
        this.setCanPickUpLoot(true);
        this.setTier(type);
        this.initTeam(type);
        this.moveControl = new HumanMoveControl(this);
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0f);
        this.waterNavigation = new WaterBoundPathNavigation((Mob)this, level);
        this.groundNavigation = new com.craftix.hostile_humans.entity.ai.HumanNavigation((Mob)this, level);
        // HumanEntity enables floating on the navigation created by the
        // superclass, but this constructor replaces that navigator. Apply the
        // setting to the actual ground and water navigators as well.
        this.waterNavigation.setCanFloat(true);
        this.groundNavigation.setCanFloat(true);
        this.groundNavigation.setCanOpenDoors(true);
        this.groundNavigation.setCanPassDoors(true);
        this.groundNavigation.setMaxVisitedNodesMultiplier(2.0f);
        this.navigation = this.groundNavigation;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()
                && this.hasOwner() && player.getUUID().equals(this.getOwnerUUID())) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                HumanGunner.openHiredHumanUi(serverPlayer, this);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.105D).add(Attributes.MAX_HEALTH, 60.0).add(Attributes.ATTACK_DAMAGE, 1.0).add((Attribute)ForgeMod.ENTITY_REACH.get(), 3.0).add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    public void setSpeed(float speed) {
        super.setSpeed(speed);
        if (!this.shouldUseWaterMovement()) {
            // Mob#setSpeed also copies speed into forward input, which makes
            // ground velocity proportional to MOVEMENT_SPEED squared. Players
            // instead use full forward input with their 0.1 movement attribute.
            // Keep the path speed multiplier in `speed` and the input at 1.
            this.setZza(speed > 0.0F ? 1.0F : 0.0F);
        }
    }

    public void applySpawnedWeaponEnchantments(RandomSource random, float enchantChance) {
        this.enchantSpawnedWeapon(random, enchantChance);
    }

    public void applySpawnedArmorEnchantments(RandomSource random, float enchantChance, EquipmentSlot equipmentSlot) {
        this.enchantSpawnedArmor(random, enchantChance, equipmentSlot);
    }

    protected PathNavigation createNavigation(Level p_33802_) {
        return new com.craftix.hostile_humans.entity.ai.HumanNavigation((Mob)this, p_33802_);
    }

    private void initTeam(HumanTier type) {
        if (this.team.isEmpty()) {
            this.team = type == HumanTier.ROAMER ? "roamer" + this.getRandom().nextInt(1, 100000) : "human";
        }
    }

    public boolean hurt(@NotNull DamageSource damageSource, float amount) {
        this.lastCombatTime = this.tickCount;
        Entity equipmentSlotArray = damageSource.getEntity();
        LivingEntity attacker = equipmentSlotArray instanceof LivingEntity living && living != this
                ? living : null;
        // Wild humans retain the original rare whole-piece break. Hired gear
        // instead wears down through hurtArmor, like equipment worn by a player.
        if (amount > 1.0f && !this.hasOwner()) {
            EquipmentSlot[] slots;
            for (EquipmentSlot equipmentslot : slots = EquipmentSlot.values()) {
                ItemStack item;
                if (equipmentslot.getType() != EquipmentSlot.Type.ARMOR) continue;
                double d = this.random.nextFloat();
                double d2 = this.getTier() == HumanTier.LEVEL1 ? 0.0025 : 0.00125;
                if (!(d < d2) || (item = this.getItemBySlot(equipmentslot)).isEmpty()) continue;
                // Route through the shared break-sound gate: the equipment
                // change listener can observe the same removal synchronously.
                club.someoneice.humangunner.EquipmentBreakSounds.playNow(this, equipmentslot);
                this.setItemSlot(equipmentslot, Items.AIR.getDefaultInstance());
            }
        }
        boolean damaged = super.hurt(damageSource, amount);
        if (damaged && this.isAlive() && attacker != null
                && club.someoneice.humangunner.SoldierCombatMode.authorizeSelfDefense(this, attacker)
                && this.canAttack(attacker)) {
            this.setTarget(attacker);
        }
        return damaged;
    }

    @Override
    protected void hurtArmor(DamageSource source, float amount) {
        super.hurtArmor(source, amount);
        if (!this.hasOwner() || amount <= 0.0F) {
            return;
        }
        int durabilityCost = Math.max(1, (int)(amount / 4.0F));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) {
                continue;
            }
            ItemStack armor = this.getItemBySlot(slot);
            if (armor.getItem() instanceof ArmorItem
                    && !(source.is(DamageTypeTags.IS_FIRE) && armor.getItem().isFireResistant())) {
                armor.hurtAndBreak(durabilityCost, this, broken -> this.broadcastBreakEvent(slot));
            }
        }
    }

    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    public ResourceLocation getResourceLocation() {
        return TEXTURE_BY_VARIANT.get(this.getVariant());
    }

    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(-10, (Goal)new HumanFloatGoal(this));
        this.goalSelector.addGoal(-10, (Goal)new AvoidCreeperGoal((PathfinderMob)this, 10.0f, 1.0, 1.2));
        this.goalSelector.addGoal(-5, (Goal)new OpenDoorsGoal((Mob)this, true));
        this.goalSelector.addGoal(-5, (Goal)new OpenFenceGoal((Mob)this, true));
        this.goalSelector.addGoal(-5, (Goal)new OpenTrapdoorGoal((Mob)this, true));
        this.goalSelector.addGoal(-5, (Goal)new LadderClimbGoal((Mob)this));
        this.goalSelector.addGoal(0, (Goal)new FindWaterOnFireGoal((PathfinderMob)this, 1.2));
        this.goalSelector.addGoal(0, (Goal)new RunFromTarget(this, 6.0f, 1.0, 1.2));
        this.goalSelector.addGoal(0, (Goal)new AvoidTNTGoal((PathfinderMob)this, 6.0f, 1.0, 1.2));
        this.goalSelector.addGoal(0, (Goal)new InvestigateSoundGoal((Mob)this, 1.0));
        this.goalSelector.addGoal(1, (Goal)new PotionRangedAttackGoal(this, 1.0, 10, 12.0f));
        this.goalSelector.addGoal(-30, (Goal)new LookForChestGoal(this, 1.0));
        if (this.getType() == ModEntityType.ROAMER.get()) {
            this.goalSelector.addGoal(8, (Goal)new RandomStrollGoalFar((PathfinderMob)this, 0.65, 15, false));
        } else {
            this.goalSelector.addGoal(8, (Goal)new RandomStrollGoalWithHome(this, 0.65, 120, true));
        }
        this.goalSelector.addGoal(10, (Goal)new RandomLookAroundGoal((Mob)this));
        this.goalSelector.addGoal(11, (Goal)new HumanLookAtPlayerGoal((Mob)this, Player.class, 64.0f));
        this.targetSelector.addGoal(0, (Goal)new HurtByTargetGoal((PathfinderMob)this, new Class[0]).setAlertOthers(new Class[0]));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoalCustom<LivingEntity>((Mob)this, LivingEntity.class, 13, true, false, this::isAngryAt));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoalWithHumanLimiter<Player>(this, Player.class, true));
        this.targetSelector.addGoal(4, (Goal)new NearestAttackableTargetGoalCustom<Mob>((Mob)this, Mob.class, 5, false, false, target -> {
            if (target instanceof EnderMan) {
                return false;
            }
            return club.someoneice.humangunner.HumanTargeting.isAutonomousPlayerEnemy(this, target);
        }));
    }

    public void setCombatTask() {
        // Goals are installed once after entity construction by the unified lifecycle.
        // Their predicates select weapons without mutating a running GoalSelector.
    }

    public boolean humanGunner$tryImmediateMeleeCounterattack(LivingEntity target) {
        return this.meleeAttackGoal.humanGunner$tryImmediateCounterattack(target);
    }

    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    public boolean doHurtTarget(Entity entityIn) {
        this.resetFallDistance();
        AttributeInstance attack = getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) return super.doHurtTarget(entityIn);
        attack.removeModifier(FLURRY_ID);
        if (meleeFlurryDamageTicks > 0) attack.addTransientModifier(FLURRY_DAMAGE);
        try {
            boolean hit = super.doHurtTarget(entityIn);
            swing(InteractionHand.MAIN_HAND);
            return hit;
        } finally {
            attack.removeModifier(FLURRY_ID);
        }
    }

    protected void blockUsingShield(LivingEntity entityIn) {
        super.blockUsingShield(entityIn);
        if (entityIn.getMainHandItem().canDisableShield(this.useItem, (LivingEntity)this, entityIn)) {
            this.disableShield(true);
        }
    }

    public void disableShield(boolean increase) {
        float chance = 0.25f + (float)EnchantmentHelper.getBlockEfficiency((LivingEntity)this) * 0.05f;
        if (increase) {
            chance = (float)((double)chance + 0.75);
        }
        if (this.random.nextFloat() < chance) {
            this.shieldCoolDown = 100;
            this.stopUsingItem();
            this.level().broadcastEntityEvent((Entity)this, (byte)30);
        }
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor serverLevelAccessor, DifficultyInstance difficulty, MobSpawnType mobSpawnType, @Nullable SpawnGroupData spawnGroupData, @Nullable CompoundTag compoundTag) {
        spawnGroupData = super.finalizeSpawn(serverLevelAccessor, difficulty, mobSpawnType, spawnGroupData, compoundTag);
        ArrayList<String> variants = new ArrayList<String>(TEXTURE_BY_VARIANT.keySet());
        this.setRandomVariant(variants);
        this.setCanPickUpLoot(true);
        return spawnGroupData;
    }

    private void setRandomVariant(List<String> variants) {
        this.setVariant(variants.get(this.random.nextInt(variants.size())));
    }

    @Override
    public void setItemSlot(EquipmentSlot slotIn, ItemStack stack) {
        super.setItemSlot(slotIn, stack);
        if (!this.level().isClientSide && !stack.isEmpty()) {
            this.setCombatTask();
        }
    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        if (this.isBlocking()) {
            return SoundEvents.SHIELD_BLOCK;
        }
        return super.getHurtSound(damageSourceIn);
    }

    protected void hurtCurrentlyUsedShield(float f) {
        HostileHumansEquipmentPatch.damageShield(this, f);
    }

    public void startUsingItem(@NotNull InteractionHand hand) {
        Human human = this;
        ItemStack requested = human.getItemInHand(hand);
        boolean holdingGun = club.someoneice.humangunner.GunSupport.get().isGun(human.getMainHandItem())
                || club.someoneice.humangunner.GunSupport.get().isGun(human.getOffhandItem());
        if (holdingGun && club.someoneice.humangunner.SpartanEquipmentCompat.isShield(requested)
                && !humanGunner$movementShieldAllowance) {
            return;
        }
    
        super.startUsingItem(hand);
        ItemStack itemstack = this.getItemInHand(hand);
        boolean shield = itemstack.canPerformAction(ToolActions.SHIELD_BLOCK);
        boolean food = itemstack.getUseAnimation() == UseAnim.EAT;
        if (this.isUsingItem() && this.getUsedItemHand() == hand && (shield || food)) {
            AttributeInstance modifiableattributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (modifiableattributeinstance != null) {
                modifiableattributeinstance.removeModifier(MODIFIER_UUID);
                double multiplier;
                if (shield) {
                    double configuredShieldMultiplier =
                            club.someoneice.humangunner.CombatAiConfig.get().shieldUseSpeedMultiplier();
                    multiplier = humanGunner$movementShieldAllowance
                            ? Math.max(0.4D, configuredShieldMultiplier)
                            : configuredShieldMultiplier;
                } else {
                    multiplier = club.someoneice.humangunner.CombatAiConfig.get().foodUseSpeedMultiplier();
                }
                if (multiplier < 1.0D) {
                    modifiableattributeinstance.addTransientModifier(new AttributeModifier(MODIFIER_UUID,
                            shield ? "Shield use speed penalty" : "Food use speed penalty",
                            multiplier - 1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
            }
        }
    }

    /** Start a mobile defensive block without the ordinary shield speed penalty. */
    public void humanGunner$startMovementShield(InteractionHand hand) {
        if (!club.someoneice.humangunner.SpartanEquipmentCompat.isShield(getItemInHand(hand))) {
            return;
        }
        humanGunner$movementShieldAllowance = true;
        try {
            startUsingItem(hand);
        } finally {
            humanGunner$movementShieldAllowance = false;
        }
    }

    /** Start a predicted-projectile block while preserving mobile shield movement. */
    public void humanGunner$startProjectileShield(InteractionHand hand) {
        humanGunner$startMovementShield(hand);
    }

    public void stopUsingItem() {
        super.stopUsingItem();
        clearUseSpeedPenalty();
    }

    private void clearUseSpeedPenalty() {
        AttributeInstance movement = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null && movement.getModifier(MODIFIER_UUID) != null) {
            movement.removeModifier(MODIFIER_UUID);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setCombatTask();
    }

    public boolean canAttack(LivingEntity entity) {
        if (entity == null) return false;
        if (!club.someoneice.humangunner.SoldierOrder.allowsTarget(this, entity)) return false;
        if (!club.someoneice.humangunner.HumanGunner.canControlledHumanAttack(this, entity)) return false;
        Human otherHuman;
        if (entity instanceof Human && (otherHuman = (Human)entity).isAlive()) {
            return !this.team.equals(otherHuman.team)
                    || (club.someoneice.humangunner.SoldierCombatMode.hasCombatController(this)
                    && club.someoneice.humangunner.SoldierCombatMode.hasCombatController(otherHuman))
                    || (club.someoneice.humangunner.SoldierCombatMode.hasCombatController(this)
                    && !club.someoneice.humangunner.SoldierCombatMode.hasCombatController(otherHuman)
                    && (club.someoneice.humangunner.SoldierCombatMode
                    .isExplicitlyAuthorizedAgainst(this, otherHuman)
                    || club.someoneice.humangunner.HumanTargeting
                    .isAutonomousWildEnemy(this, otherHuman)))
                    || club.someoneice.humangunner.HumanGunner
                    .canWildHumanRetaliateAgainst(this, otherHuman);
        }
        return super.canAttack(entity);
    }

    public boolean isAngryAt(LivingEntity entity) {
        Human otherHuman;
        if (entity instanceof Human && (otherHuman = (Human)entity).isAlive()) {
            if (!this.canAttack(otherHuman)) return false;
            if (club.someoneice.humangunner.SoldierCombatMode.hasCombatController(this)) {
                return club.someoneice.humangunner.SoldierCombatMode
                        .isExplicitlyAuthorizedAgainst(this, otherHuman)
                        || club.someoneice.humangunner.HumanTargeting
                        .isAutonomousWildEnemy(this, otherHuman);
            }
            return !this.team.equals(otherHuman.team);
        }
        if (!this.canAttack(entity)) {
            return false;
        }
        if (entity instanceof AbstractSchoolingFish) {
            return false;
        }
        if (entity instanceof Player) {
            if (club.someoneice.humangunner.HumanRelations.isForcedHostileTo(this, (Player)entity)) {
                return true;
            }
            return club.someoneice.humangunner.SoldierCombatMode.hasCombatController(this)
                    && club.someoneice.humangunner.SoldierCombatMode
                    .isExplicitlyAuthorizedAgainst(this, entity);
        }
        return entity.getUUID().equals(this.getPersistentAngerTarget());
    }

    @Override
    public void setTarget(@Nullable LivingEntity livingEntity) {
        if (livingEntity != null && !club.someoneice.humangunner.SoldierOrder.allowsTarget(this, livingEntity))
            livingEntity = null;
        super.setTarget(livingEntity);
    }

    @Override
    public void finalizeSpawn() {
        super.finalizeSpawn();
        HumanInventoryGenerator.generateInventory(this, false);
    }

    public void setBanner(ItemStack banner) {
        this.setItemSlot(EquipmentSlot.HEAD, banner);
    }

    public void putItemAway(ItemStack itemStack) {
        HostileHumansEquipmentPatch.stowOrDrop(this, itemStack);
    }

    public boolean equipWeapon(Predicate<ItemStack> predicate) {
        return this.equipWeapon(predicate, EquipmentSlot.MAINHAND);
    }

    public boolean equipWeapon(Predicate<ItemStack> predicate, EquipmentSlot equipmentSlot) {
        return HostileHumansEquipmentPatch.swapRequestedSlot(this, predicate, equipmentSlot);
    }

    protected void completeUsingItem() {
        boolean strongHealing = club.someoneice.humangunner.RecoverySupplies.isStrongInstantHealingPotion(getUseItem());
        try {
            super.completeUsingItem();
            if (strongHealing && !level().isClientSide) heal(12.0F);
        } finally {
            clearUseSpeedPenalty();
        }
    }

    @Override
    public void tick() {
        bootstrapMissingHumanData();
        if (!level().isClientSide && !attributesMigrated) {
            AttributeInstance attack = getAttribute(Attributes.ATTACK_DAMAGE);
            if (attack != null) { attack.removeModifier(FLURRY_ID); }
            club.someoneice.humangunner.TierAttributes.apply(this);
            attributesMigrated = true;
        }

        super.tick();
        if (this.lookForChestCooldown > 0) {
            --this.lookForChestCooldown;
        }
        if (this.getTarget() != null) {
            this.ticksOutOfCombat = 0;
        } else {
            ++this.ticksOutOfCombat;
            if (this.ticksOutOfCombat > 2400) {
                this.resolvedFleeThisCombat = false;
                this.shouldFleeThisCombat = false;
            }
        }
        this.ticksEyesOutOfWater = this.wasEyeInWater ? 0 : ++this.ticksEyesOutOfWater;
        if (this.level().isNight() && !this.hasDecidedToSleepTonight()) {
            this.setSleepingThisNight(this.random.nextFloat() < 0.3f);
            this.setHasDecidedToSleepTonight(true);
        } else if (!this.level().isNight() && this.hasDecidedToSleepTonight()) {
            this.setSleepingThisNight(false);
            this.setHasDecidedToSleepTonight(false);
        }
        if (this.isSleeping()) {
            if (!this.level().isClientSide && !this.level().isNight()) {
                this.stopSleeping();
            }
        } else if (this.shouldUseWaterMovement()) {
            this.setPose(Pose.SWIMMING);
        } else if (this.getPose() == Pose.SWIMMING) {
            this.setPose(Pose.STANDING);
        }
        if (this.food.exhaustionLevel > 4.0f) {
            this.food.exhaustionLevel -= 4.0f;
            if (this.food.saturationLevel > 0.0f) {
                this.food.saturationLevel = Math.max(this.food.saturationLevel - 1.0f, 0.0f);
            }
            this.food.foodLevel = Math.max(this.food.foodLevel - 1.0f, 0.0f);
        }
        if (this.food.saturationLevel > 0.0f && this.getHealth() > 0.0f && this.getHealth() < this.getMaxHealth() && this.food.foodLevel >= 20.0f) {
            ++this.healCooldown;
            if (this.healCooldown >= 10) {
                float f = Math.min(this.food.saturationLevel, 6.0f);
                this.heal(f / 6.0f);
                this.addExhaustion(f);
                this.healCooldown = 0;
            }
        } else if (this.food.foodLevel >= 18.0f && this.getHealth() > 0.0f && this.getHealth() < this.getMaxHealth()) {
            ++this.healCooldown;
            if (this.healCooldown >= 80) {
                this.heal(1.0f);
                this.addExhaustion(6.0f);
                this.healCooldown = 0;
            }
        } else {
            this.healCooldown = 0;
        }
        if (this.level().isClientSide || this.isSleeping()) {
            return;
        }
        if (this.getAirSupply() <= this.getMaxAirSupply() / 8 && !this.shouldCatchBreath) {
            this.shouldCatchBreath = true;
            this.breathRecoveryTicks = this.getRandom().nextInt(60, 101);
        }
        if (this.shouldCatchBreath && !this.isEyeInFluid(FluidTags.WATER) && this.breathRecoveryTicks > 0) {
            --this.breathRecoveryTicks;
        }
        if (this.shouldCatchBreath && this.breathRecoveryTicks <= 0 && !this.isEyeInFluid(FluidTags.WATER)) {
            this.shouldCatchBreath = false;
        }
        if (this.tickCount % 20 == 0) {
            if (this.hasCustomName() && this.getCustomName().getString().contains("give_random_gear")) {
                this.setNoAi(true);
                return;
            }
            // The hired-Human inventory is a live view over HumanData and the
            // equipment slots. Do not undo its edit lock here: combat goals
            // can otherwise swap the same weapon while the owner is dragging
            // it, corrupting custody state and leaving the entity stalled.
            if (!club.someoneice.humangunner.HumanGunner.isManualInventoryOpen(this)) {
                this.setNoAi(false);
            }
        }
        if (this.getData() == null) {
            HostileHumans.LOGGER.warn("Missing data during tick " + String.valueOf(this));
            this.discard();
            return;
        }
        if (this.toAvoid != null || this.getTarget() != null) {
            this.lastCombatTime = this.tickCount;
        }
        if (this.shieldUpTicks > 0) {
            --this.shieldUpTicks;
        }
        if (this.tickCount % 300 == 0) {
            this.setCombatTask();
        }
    }

    public boolean shouldStartFleeingThisCombat() {
        if (club.someoneice.humangunner.RetreatRecoveryPolicy.shouldForceRetreat(
                this.getHealth() / Math.max(1.0F, this.getMaxHealth()))) {
            this.resolvedFleeThisCombat = true;
            this.shouldFleeThisCombat = true;
            return true;
        }
        if (!this.resolvedFleeThisCombat) {
            this.resolvedFleeThisCombat = true;
            if (this.getTier() == HumanTier.LEVEL2 && (double)this.random.nextFloat() < (Double)Config.midBattleBuffInsteadOfRunChance.get()) {
                this.shouldFleeThisCombat = false;
            } else {
                this.shouldFleeThisCombat = (double)this.random.nextFloat() < (Double)Config.runAwayMiddleFightChance.get();
            }
        }
        return this.shouldFleeThisCombat;
    }

    public void aiStep() {
        super.aiStep();
        if (this.tickCount % 220 == 0 && this.getTarget() == null && !this.level().isClientSide) {
            if (this.getData() == null) {
                HostileHumans.LOGGER.warn("Missing data? " + String.valueOf(this));
                this.remove(Entity.RemovalReason.DISCARDED);
            }
        }
        if (this.shieldCoolDown > 0) {
            --this.shieldCoolDown;
        }
        if (this.switchingWeaponCoolDown > 0) {
            --this.switchingWeaponCoolDown;
        }
        if (this.onPlayerJumpCoolDown > 0) {
            --this.onPlayerJumpCoolDown;
        }
        if (this.meleeFlurryDamageTicks > 0) {
            --this.meleeFlurryDamageTicks;
        }
        this.updateSwingTime();
    }

    public ItemStack equipItemIfPossible(ItemStack itemStack) {
        return ItemStack.EMPTY;
    }

    protected void dropCustomDeathLoot(DamageSource p_21385_, int p_21386_, boolean p_21387_) {
        super.dropCustomDeathLoot(p_21385_, p_21386_, p_21387_);
        for (EquipmentSlot equipmentslot : EquipmentSlot.values()) {
            boolean flag;
            ItemStack itemstack = this.getItemBySlot(equipmentslot);
            itemstack.setDamageValue(itemstack.getMaxDamage() - this.random.nextInt(10));
            float f = this.getEquipmentDropChance(equipmentslot);
            boolean bl = flag = f > 1.0f;
            if (itemstack.isEmpty() || EnchantmentHelper.hasVanishingCurse((ItemStack)itemstack) || !p_21387_ && !flag || !(Math.max(this.random.nextFloat() - (float)p_21386_ * 0.01f, 0.0f) < f)) continue;
            if (!flag && itemstack.isDamageableItem()) {
                itemstack.setDamageValue(itemstack.getMaxDamage() - this.random.nextInt(1 + this.random.nextInt(Math.max(itemstack.getMaxDamage() - 3, 1))));
            }
            this.spawnAtLocation(itemstack);
            this.setItemSlot(equipmentslot, ItemStack.EMPTY);
        }
    }

    public Vec3 getLeashOffset() {
        return new Vec3(0.0, (double)(0.6f * this.getEyeHeight()), (double)(this.getBbWidth() * 0.4f));
    }

    @Override
    public Item getTameItem() {
        return Items.DIAMOND;
    }

    @Override
    public Ingredient getFoodItems() {
        return Ingredient.of((ItemStack[])HumanUtil.EDIBLE_ITEMS);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 600;
    }

    public float getVoicePitch() {
        return 1.0f;
    }

    public void setChargingCrossbow(boolean pIsCharging) {
        this.setCharging(pIsCharging);
    }

    public boolean canFireProjectileWeapon(Item item) {
        ProjectileWeaponItem weaponItem;
        return item instanceof ProjectileWeaponItem && this.canFireProjectileWeapon(weaponItem = (ProjectileWeaponItem)item);
    }

    public boolean canFireProjectileWeapon(ProjectileWeaponItem item) {
        return item instanceof BowItem || item instanceof CrossbowItem;
    }

    public void shootCrossbowProjectile(LivingEntity target, ItemStack crossbow, Projectile projectile, float angle) {
        this.shootCrossbowProjectile((LivingEntity)this, target, projectile, angle, 1.6f);
        // CrossbowItem creates and launches the projectile before adding it to
        // the level. Apply the authoritative velocity here so the spawn packet
        // carries the same trajectory that the server will simulate.
        if (projectile instanceof AbstractArrow arrow) {
            arrow.getPersistentData().putBoolean(HumanGunner.NPC_CROSSBOW_PROJECTILE, true);
            HumanGunner.applyFirstTickArrowBallistics(this, arrow, target);
        }
    }

    public ItemStack getProjectile(ItemStack p_21272_) {
        return new ItemStack((ItemLike)Items.ARROW);
    }

    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        if (SpartanRangedCompat.fire(this, target)) return;

        if (this.getMainHandItem().getItem() instanceof TridentItem) {
            this.performRangedAttackTrident(target, distanceFactor);
            return;
        }
        this.shieldCoolDown = 8;
        ItemStack weaponStack = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand((LivingEntity)this, this::canFireProjectileWeapon));
        if (weaponStack.getItem() instanceof CrossbowItem) {
            this.performCrossbowAttack((LivingEntity)this, 1.6f);
        } else {
            ItemStack itemstack = this.getProjectile(weaponStack);
            AbstractArrow mobArrow = ProjectileUtil.getMobArrow((LivingEntity)this, (ItemStack)itemstack, (float)distanceFactor);
            if (this.getMainHandItem().getItem() instanceof BowItem) {
                mobArrow = ((BowItem)this.getMainHandItem().getItem()).customArrow(mobArrow);
            }
            double d0 = target.getX() - this.getX();
            double d1 = target.getY(0.3333333333333333) - mobArrow.getY();
            double d2 = target.getZ() - this.getZ();
            double d3 = Math.sqrt(d0 * d0 + d2 * d2);
            mobArrow.shoot(d0, d1 + d3 * (double)0.2f, d2, 1.6f, (float)(14 - this.level().getDifficulty().getId() * 4));
            // Do this before addFreshEntity: changing velocity on the first
            // server tick is too late for the client spawn packet and creates
            // a visibly low phantom trajectory despite a correct server hit.
            HumanGunner.applyFirstTickArrowBallistics(this, mobArrow, target);
            this.playSound(SoundEvents.SKELETON_SHOOT, 1.0f, 1.0f / (this.getRandom().nextFloat() * 0.4f + 0.8f));
            this.level().addFreshEntity((Entity)mobArrow);
        }
    }

    public void performRangedAttackTrident(LivingEntity target, float distanceFactor) {

        Human human = this;
        ItemStack held = human.getMainHandItem();
        if (!(held.getItem() instanceof TridentItem)) {
            return;
        }

        // The base method empties MAINHAND, constructs a brand-new Loyalty I
        // projectile, then Human Gunner used to copy the original weapon back.
        // ThrownTrident caches Loyalty into synced entity data in its
        // constructor, so removing the enchantment on EntityJoin was already
        // too late: it still returned and duplicated. Mirror Drowned instead:
        // keep the real held weapon untouched and create non-pickup ammunition.
        humanGunner$removeLoyalty(held);
        ItemStack projectileStack = held.copy();
        humanGunner$removeLoyalty(projectileStack);

        if (!human.level().isClientSide) {
            ThrownTrident projectile = new ThrownTrident(human.level(), human, projectileStack);
            projectile.pickup = AbstractArrow.Pickup.DISALLOWED;
            projectile.getPersistentData().putBoolean(
                    "humangunner:drowned_style_trident", true
            );

            double dx = target.getX() - projectile.getX();
            double dy = target.getY() + target.getBbHeight() * 0.55D - projectile.getY();
            double dz = target.getZ() - human.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double travelTicks = Math.min(12.0D, horizontal / 1.6D);
            projectile.shoot(
                    dx, dy + 0.025D * travelTicks * travelTicks, dz, 1.6F,
                    14.0F - human.level().getDifficulty().getId() * 4.0F
            );
            projectile.setOwner(human);
            HumanGunner.applyTridentBallistics(human, projectile, target);
            human.level().addFreshEntity(projectile);
            human.level().playSound(
                    null, human.getX(), human.getY(), human.getZ(),
                    SoundEvents.DROWNED_SHOOT, SoundSource.HOSTILE, 1.0F,
                    0.8F + human.getRandom().nextFloat() * 0.4F
            );
        }

    
    }

    @Override
    public void performPotionRangedAttack(LivingEntity target, float var2) {
        if (!club.someoneice.humangunner.PotionThrowing.inRange(this, target)) {
            return;
        }
        ItemStack potionStack = null;
        if (this.getMainHandItem().getItem() instanceof SplashPotionItem) {
            potionStack = this.getMainHandItem();
        } else if (this.getOffhandItem().getItem() instanceof SplashPotionItem) {
            potionStack = this.getOffhandItem();
        }
        if (potionStack == null) {
            return;
        }
        ThrownPotion thrownPotion = new ThrownPotion(this.level(), (LivingEntity)this);
        thrownPotion.setItem(potionStack.copy());
        club.someoneice.humangunner.PotionThrowing.shoot(thrownPotion, target);
        potionStack.shrink(1);
        if (!this.isSilent()) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITCH_THROW, this.getSoundSource(), 1.0f, 0.8f + this.random.nextFloat() * 0.4f);
        }
        this.level().addFreshEntity((Entity)thrownPotion);
    }

    public boolean isVisuallySwimming() {
        return super.isVisuallySwimming() || this.shouldUseWaterMovement() && this.isEyeInFluid(FluidTags.WATER);
    }

    public EntityDimensions getDimensions(Pose p_36166_) {
        if (p_36166_ == Pose.SWIMMING) {
            return SWIMMING_DIMENSIONS;
        }
        return super.getDimensions(p_36166_);
    }

    public boolean prefersToFloat() {
        if (this.shouldCatchBreath || this.breathRecoveryTicks > 0) {
            return true;
        }
        return this.getTarget() == null && this.feetInWater() && !this.isInShallowWater();
    }

    public boolean wantsToSwim() {
        return this.shouldUseWaterMovement();
    }

    public boolean feetInWater() {
        return this.level().getFluidState(this.blockPosition()).is(FluidTags.WATER);
    }

    public BlockPos findWadeFloor() {
        BlockPos pos = this.blockPosition();
        for (int depth = 0; depth < 2; depth++) {
            pos = pos.below();
            if (!this.level().getFluidState(pos).is(FluidTags.WATER)) {
                return this.level().getBlockState(pos).isFaceSturdy(this.level(), pos, Direction.UP) ? pos : null;
            }
        }
        return null;
    }

    public boolean isInShallowWater() {
        return this.feetInWater() && this.findWadeFloor() != null;
    }

    public boolean hasSwimmingClearance() {
        BlockPos feetPos = this.blockPosition();
        BlockPos upperPos = feetPos.above();
        return this.level().getFluidState(feetPos).is(FluidTags.WATER) && this.level().getFluidState(upperPos).is(FluidTags.WATER) && this.level().getBlockState(upperPos).getCollisionShape((BlockGetter)this.level(), upperPos).isEmpty();
    }

    public boolean shouldUseWaterMovement() {
        return this.computeShouldUseWaterMovement();
    }

    private boolean computeShouldUseWaterMovement() {
        if (!this.isInWater()) {
            this.latchedWaterMovement = false;
            this.waterMovementLockTicks = 0;
            return false;
        }

        boolean emergencySurface = this.prefersToFloat()
                && this.getAirSupply() <= this.getMaxAirSupply() / 4
                && this.isEyeInFluid(FluidTags.WATER);
        if (emergencySurface) {
            this.latchedWaterMovement = true;
            this.waterMovementLockTicks = 20;
            return true;
        }

        LivingEntity target = this.getTarget();
        boolean pursuingUnderwaterTarget = target != null
                && !this.isFleeing
                // Keep the water navigator active for the full chase instead
                // of dropping to ground navigation as soon as air falls below
                // 75%. The breath-recovery flag takes over at the low-air
                // threshold and then makes surfacing the priority.
                && !this.shouldCatchBreath
                && this.getAirSupply() > this.getMaxAirSupply() / 8
                && !this.isInShallowWater()
                && target.getY() < this.getY() - 0.5D;
        if (pursuingUnderwaterTarget) {
            this.latchedWaterMovement = true;
            this.waterMovementLockTicks = 20;
            return true;
        }

        if (this.prefersToFloat() && this.isEyeInFluid(FluidTags.WATER)
                || !this.feetInWater() && this.swimHoldTicks <= 0) {
            this.latchedWaterMovement = false;
            this.waterMovementLockTicks = 0;
            return false;
        }

        if (this.tickCount != this.waterMovementLatchTick) {
            this.waterMovementLatchTick = this.tickCount;
            if (this.waterMovementLockTicks > 0) {
                this.waterMovementLockTicks--;
            } else {
                boolean wantsWaterMovement = this.isEyeInFluid(FluidTags.WATER)
                        && (this.swimHoldTicks <= 0 || this.feetInWater() && !this.isInShallowWater());
                if (wantsWaterMovement != this.latchedWaterMovement) {
                    this.latchedWaterMovement = wantsWaterMovement;
                    this.waterMovementLockTicks = 20;
                }
            }
        }
        return this.latchedWaterMovement;
    }

    public boolean shouldJumpOutOfWaterToward(double wantedX, double wantedY, double wantedZ) {
        if (!this.feetInWater() && !this.isInWater()) {
            this.lastDetectedExitY = Double.NaN;
            return false;
        }
        this.lastDetectedExitY = Double.NaN;
        LivingEntity target = this.getTarget();
        boolean targetLeavingWater = target != null && !target.isInWater()
                && target.getY() >= this.getY() - 0.5D;
        if (!targetLeavingWater && wantedY <= this.getY() + 0.6D) {
            return false;
        }
        double dx = wantedX - this.getX();
        double dz = wantedZ - this.getZ();
        double horizontalDistanceSqr = dx * dx + dz * dz;
        if (horizontalDistanceSqr < 0.04) {
            return false;
        }
        double horizontalDistance = Math.sqrt(horizontalDistanceSqr);
        double stepX = dx / horizontalDistance * 0.6;
        double stepZ = dz / horizontalDistance * 0.6;
        int baseY = Mth.floor(this.getY());
        for (int yOffset = -1; yOffset <= 2; yOffset++) {
            BlockPos frontPos = BlockPos.containing(this.getX() + stepX, baseY + yOffset, this.getZ() + stepZ);
            BlockPos climbPos = frontPos.above();
            BlockPos headPos = climbPos.above();
            BlockState frontState = this.level().getBlockState(frontPos);
            BlockState climbState = this.level().getBlockState(climbPos);
            BlockState headState = this.level().getBlockState(headPos);
            boolean canStepOnto = !frontState.getCollisionShape(this.level(), frontPos).isEmpty()
                    && climbState.getCollisionShape(this.level(), climbPos).isEmpty()
                    && headState.getCollisionShape(this.level(), headPos).isEmpty();
            if (canStepOnto) {
                this.lastDetectedExitY = frontPos.getY() + 1.0D;
                return true;
            }
        }
        return false;
    }

    public boolean hasDetectedExitY() {
        return !Double.isNaN(this.lastDetectedExitY);
    }

    public double getDetectedExitY() {
        return this.lastDetectedExitY;
    }

    public void travel(Vec3 p_32394_) {
        if (this.isEffectiveAi() && this.shouldUseWaterMovement()) {
            this.moveRelative(0.01f, p_32394_);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
            this.setPose(Pose.SWIMMING);
        } else {
            if (this.getPose() == Pose.SWIMMING) {
                this.setPose(Pose.STANDING);
            }
            super.travel(p_32394_);
        }
    }

    public void updateSwimming() {
        if (!this.level().isClientSide) {
            if (this.feetInWater() && this.hasSwimmingClearance()
                    && !this.prefersToFloat() && !this.isInShallowWater()) {
                this.swimHoldTicks = 15;
            } else if (this.swimHoldTicks > 0) {
                this.swimHoldTicks = Math.max(0, this.swimHoldTicks - (this.feetInWater() ? 1 : 3));
            }
            if (this.isEffectiveAi() && this.shouldUseWaterMovement()) {
                if (this.navigation != this.waterNavigation) {
                    this.navigation.stop();
                }
                this.navigation = this.waterNavigation;
                this.setSwimming(true);
            } else {
                if (this.navigation != this.groundNavigation) {
                    this.navigation.stop();
                }
                this.navigation = this.groundNavigation;
                this.setSwimming(false);
            }
        }
    }

    static class HumanMoveControl
    extends MoveControl {
        private final Human human;
        private int waterExitJumpCooldown;

        public HumanMoveControl(Human p_32433_) {
            super((Mob)p_32433_);
            this.human = p_32433_;
        }

        public void tick() {
            // Path goals may request 1.1-1.2 speed. Cap the control input at
            // normal walking pace; sprint and potion modifiers are applied
            // separately by vanilla MOVEMENT_SPEED, just as for a player.
            this.speedModifier = Math.min(this.speedModifier, 1.0D);
            if (this.waterExitJumpCooldown > 0) {
                this.waterExitJumpCooldown--;
            }
            LivingEntity livingentity = this.human.getTarget();
            if (this.human.shouldUseWaterMovement()) {
                if (this.operation == MoveControl.Operation.MOVE_TO
                        && !this.human.getNavigation().isDone()
                        && this.waterExitJumpCooldown == 0
                        && this.human.shouldJumpOutOfWaterToward(this.wantedX, this.wantedY, this.wantedZ)
                        && this.human.hasDetectedExitY()
                        && this.human.getY() < this.human.getDetectedExitY() + 0.25D) {
                    this.human.getJumpControl().jump();
                    this.human.setDeltaMovement(this.human.getDeltaMovement().add(0.0, 0.18, 0.0));
                    this.waterExitJumpCooldown = 6;
                }
                // Rise only until the eyes clear the surface. Continuing to
                // inject upward velocity during the whole recovery timer made
                // Humans bob at the surface and prevented horizontal combat
                // movement from settling.
                if (this.human.shouldCatchBreath
                        && this.human.isEyeInFluid(FluidTags.WATER)) {
                    this.human.setDeltaMovement(this.human.getDeltaMovement().add(0.0, 0.02, 0.0));
                } else if (livingentity != null
                        && this.human.isEyeInFluid(FluidTags.WATER)
                        && livingentity.getY() > this.human.getY()) {
                    this.human.setDeltaMovement(this.human.getDeltaMovement().add(0.0, 0.002, 0.0));
                }

                if (this.operation == MoveControl.Operation.STRAFE) {
                    applyWaterStrafe();
                    this.operation = MoveControl.Operation.WAIT;
                    return;
                }
                if (this.operation != MoveControl.Operation.MOVE_TO || this.human.getNavigation().isDone()) {
                    this.human.setSpeed(0.0f);
                    this.human.setZza(0.0F);
                    this.human.setXxa(0.0F);
                    if (this.operation == MoveControl.Operation.MOVE_TO) {
                        this.operation = MoveControl.Operation.WAIT;
                    }
                    return;
                }
                double d0 = this.wantedX - this.human.getX();
                double d1 = this.wantedY - this.human.getY();
                double d2 = this.wantedZ - this.human.getZ();
                double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                if (d3 < 0.05D) {
                    this.human.setSpeed(0.0F);
                    this.human.setZza(0.0F);
                    this.human.setXxa(0.0F);
                    this.operation = MoveControl.Operation.WAIT;
                    return;
                }
                double horizontalDistance = Math.sqrt(d0 * d0 + d2 * d2);
                if (horizontalDistance > 1.0E-4D) {
                    float yaw = (float)(Mth.atan2(d2, d0) * 57.2957763671875) - 90.0F;
                    this.human.setYRot(this.rotlerp(this.human.getYRot(), yaw, 90.0F));
                    this.human.yBodyRot = this.human.getYRot();
                }
                double verticalDirection = Mth.clamp(d1 / d3, -0.25D, 0.25D);
                this.applyWaterImpulse(d0 / Math.max(1.0E-4D, horizontalDistance),
                        verticalDirection, d2 / Math.max(1.0E-4D, horizontalDistance));
            } else {
                if (!this.human.onGround()) {
                    this.human.setDeltaMovement(this.human.getDeltaMovement().add(0.0, -0.008, 0.0));
                }
                super.tick();
            }
        }

        /**
         * Water navigation used to multiply acceleration by the remaining
         * waypoint distance. Since water paths are made of short node-to-node
         * steps, this produced almost no velocity; normalize the steering
         * vector and apply a stable water-speed impulse instead.
         */
        private void applyWaterStrafe() {
            float forward = this.strafeForwards;
            float right = this.strafeRight;
            float inputLength = Mth.sqrt(forward * forward + right * right);
            if (inputLength > 1.0F) {
                forward /= inputLength;
                right /= inputLength;
            }
            float yawRadians = this.human.getYRot() * ((float)Math.PI / 180.0F);
            float sin = Mth.sin(yawRadians);
            float cos = Mth.cos(yawRadians);
            double directionX = forward * cos - right * sin;
            double directionZ = right * cos + forward * sin;
            this.applyWaterImpulse(directionX, 0.0D, directionZ);
        }

        private void applyWaterImpulse(double directionX, double directionY, double directionZ) {
            float movementSpeed = (float)(this.speedModifier
                    * this.human.getAttributeValue(Attributes.MOVEMENT_SPEED));
            this.human.setSpeed(movementSpeed);
            // travel() still runs moveRelative while swimming; zero the inputs
            // because the normalized impulse below is the single movement
            // owner for this frame.
            this.human.setZza(0.0F);
            this.human.setXxa(0.0F);
            double horizontalAcceleration = movementSpeed * 0.06D;
            double verticalAcceleration = movementSpeed * 0.20D;
            this.human.setDeltaMovement(this.human.getDeltaMovement().add(
                    directionX * horizontalAcceleration,
                    directionY * verticalAcceleration,
                    directionZ * horizontalAcceleration
            ));
        }
    }
}


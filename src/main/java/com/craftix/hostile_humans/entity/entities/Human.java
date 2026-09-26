package com.craftix.hostile_humans.entity.entities;

import club.someoneice.humangunner.ConditionalGoal;
import club.someoneice.humangunner.HumanGunner;
import net.minecraft.world.entity.ai.goal.Goal;
import club.someoneice.humangunner.RangedHybridMeleeGoal;
import club.someoneice.humangunner.RangedWeaponCustody;
import club.someoneice.humangunner.ShoreSeekingPolicy;
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
import com.craftix.hostile_humans.entity.ai.goal.HumanFloatGoal;
import com.craftix.hostile_humans.entity.ai.goal.HumanLookAtPlayerGoal;
import com.craftix.hostile_humans.entity.ai.goal.InvestigateSoundGoal;
import com.craftix.hostile_humans.entity.ai.goal.LadderClimbGoal;
import com.craftix.hostile_humans.entity.ai.goal.LeaveWaterWhenIdleGoal;
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
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
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
                && !RangedWeaponCustody.isActive(human)
                && !HumanUtil.isMeleeWeapon(human.getMainHandItem())
                && !(human.getMainHandItem().getItem() instanceof TridentItem)
                && (RangedWeaponCustody.isCrossbowWeapon(human.getMainHandItem())
                || RangedWeaponCustody.isCrossbowWeapon(human.getOffhandItem()));
    }

    private static boolean humanGunner$isBowMode(Human human) {
        return !RangedWeaponCustody.hasGunPriority(human)
                && !RangedWeaponCustody.isActive(human)
                && !HumanUtil.isMeleeWeapon(human.getMainHandItem())
                && !(human.getMainHandItem().getItem() instanceof TridentItem)
                && !RangedWeaponCustody.isCrossbowWeapon(human.getMainHandItem())
                && (RangedWeaponCustody.isBowWeapon(human.getMainHandItem())
                || RangedWeaponCustody.isBowWeapon(human.getOffhandItem()));
    }

    private static boolean humanGunner$isMeleeMode(Human human) {
        ItemStack mainHand = human.getMainHandItem();
        ItemStack offHand = human.getOffhandItem();
        if (!club.someoneice.humangunner.GunSupport.get().isGun(mainHand)
                && !RangedWeaponCustody.isActive(human)
                && HumanUtil.isMeleeWeapon(mainHand)) {
            return true;
        }
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
    private boolean seekingShore;
    private boolean shorePathUsesWaterNavigation;
    @Nullable
    private BlockPos shoreFallbackTarget;
    private int shoreTransitionPendingTick = Integer.MIN_VALUE;
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
        // Make normal path selection strongly prefer routes around lava, while
        // keeping lava nodes traversable so the emergency escape goal can path out.
        this.setPathfindingMalus(BlockPathTypes.LAVA, 16.0f);
        this.setCanPickUpLoot(true);
        this.setTier(type);
        this.initTeam(type);
        this.moveControl = new HumanMoveControl(this);
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
    public float getPathfindingMalus(BlockPathTypes nodeType) {
        if (nodeType == BlockPathTypes.WATER || nodeType == BlockPathTypes.WATER_BORDER) {
            return ShoreSeekingPolicy.waterPathMalus(this.isInWater(), this.seekingShore);
        }
        return super.getPathfindingMalus(nodeType);
    }

    /** Find a reachable dry standing position, preferring the nearest shoreline. */
    @Nullable
    public Path findNearestShorePath() {
        return this.findNearestShorePath(null);
    }

    /** Find a reachable dry position, optionally avoiding the previous stalled destination. */
    @Nullable
    public Path findNearestShorePath(@Nullable BlockPos avoidDestination) {
        return this.findNearestShorePath(avoidDestination, null);
    }

    /**
     * Cheap fallback scan used to acquire the shore movement lease immediately
     * while expensive path searches are staggered across nearby Humans.
     */
    @Nullable
    public BlockPos findNearestDryShoreTarget() {
        BlockPos origin = this.blockPosition();
        double startAngle = this.getRandom().nextDouble() * Math.PI * 2.0D;
        int[] searchRadii = {4, 8, 12, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128};
        BlockPos nearest = null;
        double nearestDistanceSqr = Double.POSITIVE_INFINITY;
        for (int radius : searchRadii) {
            for (int direction = 0; direction < 16; direction++) {
                double angle = startAngle + direction * (Math.PI / 8.0D);
                int x = origin.getX() + (int)Math.round(Math.cos(angle) * radius);
                int z = origin.getZ() + (int)Math.round(Math.sin(angle) * radius);
                BlockPos column = new BlockPos(x, origin.getY(), z);
                if (!this.level().hasChunkAt(column)) {
                    continue;
                }
                BlockPos candidate = new BlockPos(x,
                        this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (!this.isDryStandingPosition(candidate)) {
                    continue;
                }
                double distanceSqr = origin.distSqr(candidate);
                if (distanceSqr < nearestDistanceSqr) {
                    nearest = candidate.immutable();
                    nearestDistanceSqr = distanceSqr;
                }
            }
        }
        this.shoreFallbackTarget = nearest;
        return nearest;
    }

    /**
     * Find a reachable dry position. Retreating Humans may accept a short
     * detour when it exits the water farther from the threat.
     */
    @Nullable
    public Path findNearestShorePath(@Nullable BlockPos avoidDestination,
            @Nullable LivingEntity retreatThreat) {
        this.shorePathUsesWaterNavigation = false;
        this.shoreFallbackTarget = null;
        BlockPos origin = this.blockPosition();
        double fallbackDistanceSqr = Double.POSITIVE_INFINITY;
        double startAngle = this.getRandom().nextDouble() * Math.PI * 2.0D;
        // Check nearby banks first, then expand in coarser rings. The former
        // 16-block limit often left swimmers trapped in wide rivers or lakes.
        int[] searchRadii = {4, 8, 12, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128};
        Path shortestPath = null;
        int shortestNodeCount = Integer.MAX_VALUE;
        boolean shortestUsesWaterNavigation = false;
        Path saferPath = null;
        int saferNodeCount = Integer.MAX_VALUE;
        boolean saferUsesWaterNavigation = false;
        double currentThreatDistance = retreatThreat != null && retreatThreat.isAlive()
                ? Math.sqrt(retreatThreat.distanceToSqr(this))
                : Double.NaN;
        for (int radius : searchRadii) {
            Path bestPath = null;
            int bestNodeCount = Integer.MAX_VALUE;
            boolean bestUsesWaterNavigation = false;
            for (int direction = 0; direction < 16; direction++) {
                double angle = startAngle + direction * (Math.PI / 8.0D);
                int x = origin.getX() + (int)Math.round(Math.cos(angle) * radius);
                int z = origin.getZ() + (int)Math.round(Math.sin(angle) * radius);
                BlockPos column = new BlockPos(x, origin.getY(), z);
                if (!this.level().hasChunkAt(column)) {
                    continue;
                }
                int y = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos candidate = new BlockPos(x, y, z);
                if (!this.isDryStandingPosition(candidate)) {
                    continue;
                }
                double fallbackCandidateDistanceSqr = origin.distSqr(candidate);
                if (fallbackCandidateDistanceSqr < fallbackDistanceSqr) {
                    this.shoreFallbackTarget = candidate.immutable();
                    fallbackDistanceSqr = fallbackCandidateDistanceSqr;
                }
                if (avoidDestination != null && candidate.equals(avoidDestination)) {
                    continue;
                }

                // Shore destinations are dry. Prefer the ground navigator so
                // its walk evaluator can finish the last shallow-water/shore
                // step; water navigation is only a fallback for routes the
                // ground navigator cannot reach.
                Path path = this.groundNavigation.createPath(candidate, 0);
                boolean usesWaterNavigation = false;
                if (path == null || !path.canReach()) {
                    path = this.waterNavigation.createPath(candidate, 0);
                    usesWaterNavigation = path != null && path.canReach();
                }
                if (path != null && path.canReach() && path.getNodeCount() < bestNodeCount) {
                    bestPath = path;
                    bestNodeCount = path.getNodeCount();
                    bestUsesWaterNavigation = usesWaterNavigation;
                }

                if (path != null && path.canReach()) {
                    if (path.getNodeCount() < shortestNodeCount) {
                        shortestPath = path;
                        shortestNodeCount = path.getNodeCount();
                        shortestUsesWaterNavigation = usesWaterNavigation;
                    }
                    if (retreatThreat != null && retreatThreat.isAlive()) {
                        BlockPos pathTarget = path.getTarget();
                        double exitThreatDistance = Math.sqrt(retreatThreat.distanceToSqr(
                                pathTarget.getX() + 0.5D,
                                pathTarget.getY(),
                                pathTarget.getZ() + 0.5D));
                        if (exitThreatDistance >= currentThreatDistance + 3.0D
                                && path.getNodeCount() < saferNodeCount) {
                            saferPath = path;
                            saferNodeCount = path.getNodeCount();
                            saferUsesWaterNavigation = usesWaterNavigation;
                        }
                    }
                }
            }
            if (bestPath != null && (retreatThreat == null || !retreatThreat.isAlive())) {
                this.shorePathUsesWaterNavigation = bestUsesWaterNavigation;
                return bestPath;
            }
        }

        if (ShoreSeekingPolicy.shouldPreferSaferShorePath(
                shortestNodeCount,
                saferNodeCount,
                saferPath != null)) {
            this.shorePathUsesWaterNavigation = saferUsesWaterNavigation;
            return saferPath;
        }
        this.shorePathUsesWaterNavigation = shortestUsesWaterNavigation;
        return shortestPath;
    }

    private boolean isDryStandingPosition(BlockPos feetPos) {
        BlockPos headPos = feetPos.above();
        BlockPos floorPos = feetPos.below();
        if (this.level().getFluidState(feetPos).is(FluidTags.WATER)
                || this.level().getFluidState(feetPos).is(FluidTags.LAVA)
                || this.level().getFluidState(headPos).is(FluidTags.WATER)
                || this.level().getFluidState(headPos).is(FluidTags.LAVA)
                || this.level().getFluidState(floorPos).is(FluidTags.WATER)
                || this.level().getFluidState(floorPos).is(FluidTags.LAVA)
                || !this.level().getBlockState(feetPos).getCollisionShape(this.level(), feetPos).isEmpty()
                || !this.level().getBlockState(headPos).getCollisionShape(this.level(), headPos).isEmpty()) {
            return false;
        }
        return this.level().getBlockState(floorPos).isFaceSturdy(this.level(), floorPos, Direction.UP);
    }

    public void startSeekingShore(Path path, double speed) {
        this.shoreTransitionPendingTick = Integer.MIN_VALUE;
        this.seekingShore = true;
        this.shoreFallbackTarget = path.getTarget().immutable();
        this.navigation.stop();
        this.selectShoreNavigation();
        this.setSwimming(!this.isNearWaterSurface());
        this.navigation.moveTo(path, speed);
    }

    public void startSeekingShoreWithoutPath() {
        this.shoreTransitionPendingTick = Integer.MIN_VALUE;
        this.seekingShore = true;
        this.shorePathUsesWaterNavigation = true;
        this.navigation.stop();
        this.selectShoreNavigation();
        this.setSwimming(!this.isNearWaterSurface());
        this.continueSeekingShoreWithoutPath(1.0D);
    }

    public void continueSeekingShore(Path path, double speed) {
        this.shoreFallbackTarget = path.getTarget().immutable();
        this.selectShoreNavigation();
        this.navigation.moveTo(path, speed);
    }

    /**
     * A complete path can be temporarily unavailable for unloaded chunks or
     * unusual underwater terrain. Keep moving toward a known dry bank rather
     * than leaving the swimmer stationary until the next path-search retry.
     */
    public void continueSeekingShoreWithoutPath(double speed) {
        if (!this.seekingShore || this.shoreFallbackTarget == null
                || !ShoreSeekingPolicy.shouldSteerTowardFallback(
                this.seekingShore, true, this.navigation.isDone())) {
            return;
        }
        BlockPos destination = this.shoreFallbackTarget;
        this.getMoveControl().setWantedPosition(
                destination.getX() + 0.5D,
                destination.getY(),
                destination.getZ() + 0.5D,
                speed
        );
    }

    public void pauseSeekingShore() {
        if (this.seekingShore) {
            this.navigation.stop();
        }
    }

    public void stopSeekingShore() {
        this.shoreTransitionPendingTick = Integer.MIN_VALUE;
        this.seekingShore = false;
        this.shoreFallbackTarget = null;
        this.navigation.stop();
    }

    public void requestShoreTransition() {
        this.shoreTransitionPendingTick = this.tickCount;
    }

    public boolean isShoreTransitionPending() {
        return this.shoreTransitionPendingTick == this.tickCount;
    }

    public boolean isSeekingShore() {
        return this.seekingShore;
    }

    private void selectShoreNavigation() {
        if (this.shorePathUsesWaterNavigation) {
            if (this.navigation != this.waterNavigation) {
                this.navigation.stop();
                this.navigation = this.waterNavigation;
            }
        } else if (this.navigation != this.groundNavigation) {
            this.navigation.stop();
            this.navigation = this.groundNavigation;
        }
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
        // Water exit is an environmental movement priority. It must also
        // pre-empt chest seeking and active combat/retreat movement.
        this.goalSelector.addGoal(ShoreSeekingPolicy.GOAL_PRIORITY, new LeaveWaterWhenIdleGoal(this));
        this.goalSelector.addGoal(-10, (Goal)new AvoidCreeperGoal((PathfinderMob)this, 10.0f, 1.0, 1.2));
        this.goalSelector.addGoal(-5, (Goal)new OpenDoorsGoal((Mob)this, true));
        this.goalSelector.addGoal(-5, (Goal)new OpenFenceGoal((Mob)this, true));
        this.goalSelector.addGoal(-5, (Goal)new OpenTrapdoorGoal((Mob)this, true));
        this.goalSelector.addGoal(-5, (Goal)new LadderClimbGoal((Mob)this));
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
            this.setPose(this.isNearWaterSurface() ? Pose.STANDING : Pose.SWIMMING);
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
        // Begin surfacing with a real safety margin. The old 1/8 threshold
        // often left less air than the time needed to rise from deep water.
        if (this.getAirSupply() <= this.getMaxAirSupply() / 2 && !this.shouldCatchBreath) {
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
        return !this.isNearWaterSurface()
                && (super.isVisuallySwimming() || this.shouldUseWaterMovement() && this.isEyeInFluid(FluidTags.WATER));
    }

    public EntityDimensions getDimensions(Pose p_36166_) {
        if (p_36166_ == Pose.SWIMMING) {
            return SWIMMING_DIMENSIONS;
        }
        return super.getDimensions(p_36166_);
    }

    public boolean wantsToSwim() {
        return this.shouldUseWaterMovement();
    }

    public boolean feetInWater() {
        return this.level().getFluidState(this.blockPosition()).is(FluidTags.WATER);
    }

    public boolean shouldUseWaterMovement() {
        return this.computeShouldUseWaterMovement();
    }

    private boolean computeShouldUseWaterMovement() {
        if (!this.isInWater()) {
            this.seekingShore = false;
            return false;
        }

        if (this.seekingShore) {
            return true;
        }

        // Keep aquatic movement latched through the surface boundary while
        // the body is still in water. Switching to ground movement the instant
        // the eyes clear the surface applies gravity again and can make the
        // Human bob below the surface instead of breathing steadily.
        return this.isEyeInFluid(FluidTags.WATER) || this.isNearWaterSurface();
    }

    private boolean isPursuingLowerWaterTarget() {
        LivingEntity target = this.getTarget();
        return ShoreSeekingPolicy.shouldPursueLowerWaterTarget(
                this.seekingShore,
                target != null && target.isAlive() && target.isInWater(),
                target != null && target.getY() < this.getY() - 0.5D,
                this.isFleeing,
                this.shouldCatchBreath);
    }

    private boolean isNearWaterSurface() {
        if (!this.isInWater()) {
            return false;
        }

        BlockPos origin = this.blockPosition();
        for (int yOffset = -1; yOffset <= 2; yOffset++) {
            BlockPos fluidPos = origin.offset(0, yOffset, 0);
            var fluid = this.level().getFluidState(fluidPos);
            if (!fluid.is(FluidTags.WATER)) {
                continue;
            }

            double surfaceY = fluidPos.getY() + fluid.getHeight(this.level(), fluidPos);
            double distanceToSurface = surfaceY - this.getY();
            if (distanceToSurface < -0.05D || distanceToSurface > 2.0D) {
                continue;
            }

            EntityDimensions standing = this.getDimensions(Pose.STANDING);
            AABB standingBox = new AABB(
                    this.getX() - standing.width / 2.0D, this.getY(), this.getZ() - standing.width / 2.0D,
                    this.getX() + standing.width / 2.0D, this.getY() + standing.height, this.getZ() + standing.width / 2.0D
            );
            return this.level().noCollision(this, standingBox);
        }
        return false;
    }

    private boolean isWaterSurfaceCloseForShorePop() {
        if (!this.isInWater()) {
            return false;
        }

        BlockPos origin = this.blockPosition();
        for (int yOffset = -1; yOffset <= 2; yOffset++) {
            BlockPos fluidPos = origin.offset(0, yOffset, 0);
            var fluid = this.level().getFluidState(fluidPos);
            if (!fluid.is(FluidTags.WATER)) {
                continue;
            }

            double surfaceY = fluidPos.getY() + fluid.getHeight(this.level(), fluidPos);
            double distanceToSurface = surfaceY - this.getY();
            // Unlike isNearWaterSurface(), this check intentionally ignores
            // standing-box collision with the bank: that collision is exactly
            // what the short shore-pop helps the Human clear.
            if (distanceToSurface >= -0.1D && distanceToSurface <= 1.25D) {
                return true;
            }
        }
        return false;
    }

    private boolean isFollowingHigherShoreWaypoint() {
        return this.seekingShore
                && this.moveControl instanceof HumanMoveControl humanMoveControl
                && humanMoveControl.wantsHigherWaypoint();
    }

    private boolean hasHigherDryLandingAhead(double directionX, double directionZ) {
        if (!this.seekingShore) {
            return false;
        }

        double directionLength = Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (directionLength < 1.0E-4D) {
            return false;
        }
        directionX /= directionLength;
        directionZ /= directionLength;

        int minY = Mth.floor(this.getY() + 0.1D);
        int maxY = Mth.floor(this.getY() + 2.05D);
        for (int forwardIndex = 0; forwardIndex < 3; forwardIndex++) {
            double forward = 0.4D + forwardIndex * 0.35D;
            for (int sideIndex = -1; sideIndex <= 1; sideIndex++) {
                double side = sideIndex * 0.3D;
                int x = Mth.floor(this.getX() + directionX * forward - directionZ * side);
                int z = Mth.floor(this.getZ() + directionZ * forward + directionX * side);
                for (int y = minY; y <= maxY; y++) {
                    if (this.isDryStandingPosition(new BlockPos(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void travel(Vec3 p_32394_) {
        if (this.isEffectiveAi() && this.shouldUseWaterMovement()) {
            this.moveRelative(0.01f, p_32394_);
            Vec3 motion = this.getDeltaMovement();
            if (this.isEyeInFluid(FluidTags.WATER) && !this.isPursuingLowerWaterTarget()) {
                boolean nearSurface = this.isNearWaterSurface();
                boolean climbingShore = this.isFollowingHigherShoreWaypoint();
                // Shore paths can contain lower waypoints. Do not let those
                // path instructions cancel the buoyancy needed to reach air.
                // A higher shore waypoint gets a controlled climb assist so
                // the standing model can step over the final shallow bank.
                double minimumRise = climbingShore
                        ? (this.shouldCatchBreath ? 0.12D : 0.08D)
                        : (this.shouldCatchBreath
                        ? (nearSurface ? 0.06D : 0.12D)
                        : (nearSurface ? 0.04D : 0.06D));
                double ascentLimit = this.getWaterAscentSpeedLimit(nearSurface);
                double verticalMotion = Math.max(motion.y, minimumRise);
                motion = new Vec3(motion.x, Math.min(ascentLimit, verticalMotion), motion.z);
            } else if (this.isNearWaterSurface()
                    && !this.isFollowingHigherShoreWaypoint()
                    && !this.isPursuingLowerWaterTarget()) {
                // Once the eyes are clear, hold a calm surface stance instead
                // of carrying swim momentum into a hop above the water.
                motion = new Vec3(motion.x, Mth.clamp(motion.y, -0.02D, 0.02D), motion.z);
            }
            this.setDeltaMovement(motion);
            this.move(MoverType.SELF, motion);
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
            this.setPose(this.isNearWaterSurface() ? Pose.STANDING : Pose.SWIMMING);
        } else {
            if (this.getPose() == Pose.SWIMMING) {
                this.setPose(Pose.STANDING);
            }
            super.travel(p_32394_);
        }
    }

    private double getWaterAscentSpeedLimit(boolean nearSurface) {
        if (this.moveControl instanceof HumanMoveControl humanMoveControl
                && humanMoveControl.isShorePopActive()) {
            return 0.20D;
        }
        if (this.isFollowingHigherShoreWaypoint()) {
            return this.shouldCatchBreath ? 0.18D : 0.14D;
        }
        // Smooth per-tick velocities, not jump impulses. Keep enough upward
        // force near the surface to finish surfacing without launching out.
        return this.shouldCatchBreath
                ? (nearSurface ? 0.08D : 0.16D)
                : (nearSurface ? 0.06D : 0.08D);
    }

    public void updateSwimming() {
        if (!this.level().isClientSide) {
            if (this.isEffectiveAi() && this.shouldUseWaterMovement()) {
                if (this.seekingShore && !this.shorePathUsesWaterNavigation) {
                    if (this.navigation != this.groundNavigation) {
                        this.navigation.stop();
                    }
                    this.navigation = this.groundNavigation;
                } else {
                    if (this.navigation != this.waterNavigation) {
                        this.navigation.stop();
                    }
                    this.navigation = this.waterNavigation;
                }
                this.setSwimming(!this.isNearWaterSurface());
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
        private static final int SHORE_POP_COOLDOWN_TICKS = 20;
        private static final int SHORE_POP_DURATION_TICKS = 2;
        private static final double SHORE_POP_SPEED = 0.20D;

        private final Human human;
        private int shorePopCooldownTicks;
        private int shorePopTicks;

        public HumanMoveControl(Human p_32433_) {
            super((Mob)p_32433_);
            this.human = p_32433_;
        }

        public void tick() {
            if (this.shorePopCooldownTicks > 0) {
                this.shorePopCooldownTicks--;
            }
            if (this.shorePopTicks > 0) {
                this.shorePopTicks--;
            }
            // Path goals may request 1.1-1.2 speed. Cap the control input at
            // normal walking pace; sprint and potion modifiers are applied
            // separately by vanilla MOVEMENT_SPEED, just as for a player.
            this.speedModifier = Math.min(this.speedModifier, 1.0D);
            if (this.human.shouldUseWaterMovement()) {
                if (this.operation == MoveControl.Operation.STRAFE) {
                    applyWaterStrafe();
                    this.operation = MoveControl.Operation.WAIT;
                    return;
                }
                boolean directShoreFallback = ShoreSeekingPolicy.shouldSteerTowardFallback(
                        this.human.seekingShore,
                        this.human.shoreFallbackTarget != null,
                        this.human.getNavigation().isDone());
                if (this.operation != MoveControl.Operation.MOVE_TO
                        || (this.human.getNavigation().isDone() && !directShoreFallback)) {
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
                this.tryShorePop(d0, d2);
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
            // Counter the swimming drag (0.9 in travel()) so a full steering
            // input produces at least normal walking speed instead of a slow
            // drift. This impulse remains normalized and independent of the
            // short water-navigation waypoint distance.
            double horizontalAcceleration = movementSpeed * 0.20D;
            double verticalAcceleration = movementSpeed * 0.30D;
            Vec3 motion = this.human.getDeltaMovement();
            double nextVertical = motion.y + directionY * verticalAcceleration;
            boolean surfacePriority = !this.human.isPursuingLowerWaterTarget();
            boolean nearSurface = this.human.isNearWaterSurface();
            boolean climbingShore = this.human.isFollowingHigherShoreWaypoint();
            boolean shorePopActive = this.isShorePopActive();
            if (climbingShore) {
                nextVertical = Math.max(nextVertical, this.human.shouldCatchBreath ? 0.12D : 0.08D);
            }
            if (shorePopActive) {
                nextVertical = Math.max(nextVertical, SHORE_POP_SPEED);
            }
            double verticalLimit = nearSurface && !this.human.isEyeInFluid(FluidTags.WATER)
                    && !climbingShore
                    ? 0.02D
                    : this.human.getWaterAscentSpeedLimit(nearSurface);
            if (shorePopActive) {
                verticalLimit = Math.max(verticalLimit, SHORE_POP_SPEED);
            }
            nextVertical = surfacePriority
                    ? Mth.clamp(nextVertical, 0.0D, verticalLimit)
                    : Mth.clamp(nextVertical, -0.05D, verticalLimit);
            double horizontalShoreKick = shorePopActive ? 0.02D : 0.0D;
            this.human.setDeltaMovement(
                    motion.x + directionX * (horizontalAcceleration + horizontalShoreKick),
                    nextVertical,
                    motion.z + directionZ * (horizontalAcceleration + horizontalShoreKick)
            );
        }

        private void tryShorePop(double directionX, double directionZ) {
            if (this.shorePopCooldownTicks > 0
                    || this.shorePopTicks > 0
                    || !this.human.seekingShore
                    || !this.human.isInWater()
                    || !this.human.isWaterSurfaceCloseForShorePop()
                    || this.human.getDeltaMovement().y > 0.08D
                    || !this.human.hasHigherDryLandingAhead(directionX, directionZ)) {
                return;
            }

            // A brief, low impulse only when a dry higher landing is within a
            // step of the current shore path. Never launch swimmers in open water.
            this.shorePopTicks = SHORE_POP_DURATION_TICKS;
            this.shorePopCooldownTicks = SHORE_POP_COOLDOWN_TICKS;
        }

        private boolean isShorePopActive() {
            return this.shorePopTicks > 0;
        }

        private boolean wantsHigherWaypoint() {
            if (this.operation != MoveControl.Operation.MOVE_TO
                    || this.human.getNavigation().isDone()) {
                return false;
            }
            if (this.wantedY > this.human.getY() + 0.1D) {
                return true;
            }

            double dx = this.wantedX - this.human.getX();
            double dz = this.wantedZ - this.human.getZ();
            return this.human.hasHigherDryLandingAhead(dx, dz);
        }
    }
}


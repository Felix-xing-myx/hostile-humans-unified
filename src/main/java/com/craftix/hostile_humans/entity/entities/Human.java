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
    private int rangedFacingTick = Integer.MIN_VALUE;
    private LivingEntity rangedFacingTarget;

    /** The firing goal requests a final facing update after navigation has moved. */
    public void markRangedFacing(LivingEntity target) {
        this.rangedFacingTick = this.tickCount;
        this.rangedFacingTarget = target;
    }

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
        // The bow goal advances draw time and consumes its idle cooldown in
        // two places per tick. 32 yields roughly 70% of the previous full
        // draw-and-wait shot cadence (which used an interval of 17).
        humanGunner$enhancedBowGoal = new BowAttack<>(human, 1.0D, 32, 36.0F);
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
    private static final UUID WATER_KNOCKBACK_ID = UUID.fromString("327564c3-f609-4dd9-a9e1-49de9157bd30");
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
    public boolean shouldCatchBreath;
    public int breathRecoveryTicks;
    private boolean seekingShore;
    private boolean shorePathUsesWaterNavigation;
    @Nullable
    private BlockPos shoreFallbackTarget;
    private int shoreTransitionPendingTick = Integer.MIN_VALUE;
    private int directWaterCombatSteeringTick = Integer.MIN_VALUE;
    @Nullable private UUID waterCombatTarget;
    private int waterCombatProgressTick;
    private int waterCombatDirectUntilTick;
    private double waterCombatDistance;
    private int directWaterLootSteeringTick = Integer.MIN_VALUE;
    private boolean pursuingWaterLoot;
    private boolean activelyCollectingLoot;
    private boolean combatFiringShore;
    private UUID combatFiringShoreTarget;
    private int combatFiringShoreUntilTick;
    private boolean waterKnockbackModifierApplied;
    private int surfaceCheckTick = Integer.MIN_VALUE;
    private double surfaceCheckX;
    private double surfaceCheckY;
    private double surfaceCheckZ;
    private boolean cachedNearWaterSurface;
    private double cachedDistanceToWaterSurface;
    private int dryLandingCheckTick = Integer.MIN_VALUE;
    private double dryLandingCheckX;
    private double dryLandingCheckY;
    private double dryLandingCheckZ;
    private double dryLandingDirectionX;
    private double dryLandingDirectionZ;
    private boolean cachedDryLandingAhead;
    private double cachedDryLandingFeetY;
    protected final WaterBoundPathNavigation waterNavigation;
    protected final GroundPathNavigation groundNavigation;
    @Nullable
    private PathNavigation watchedNavigation;
    @Nullable
    private BlockPos watchedPathGoal;
    @Nullable
    private BlockPos watchedWaypoint;
    private double bestWaypointDistance = Double.MAX_VALUE;
    private int lastWaypointProgressTick;
    @Nullable
    private BlockPos abandonedNavigationGoal;
    private int abandonedNavigationGoalUntil;

    public boolean wasNavigationGoalAbandoned(BlockPos goal) {
        return goal != null && goal.equals(abandonedNavigationGoal)
                && tickCount < abandonedNavigationGoalUntil;
    }

    private void recoverStalledNavigation() {
        Path path = navigation.getPath();
        if (path == null || path.isDone()) {
            // Keep the observation across a short vanilla stuck-stop/retry.
            // Otherwise a goal that recreates the same path every few ticks
            // could reset this watchdog forever.
            if (tickCount - lastWaypointProgressTick > 200) {
                watchedNavigation = null;
                watchedPathGoal = null;
                watchedWaypoint = null;
            }
            return;
        }
        BlockPos goal = path.getTarget();
        BlockPos waypoint = path.getNextNodePos();
        double distance = position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(waypoint));
        if (navigation != watchedNavigation || !goal.equals(watchedPathGoal)
                || !waypoint.equals(watchedWaypoint)) {
            watchedNavigation = navigation;
            watchedPathGoal = goal;
            watchedWaypoint = waypoint;
            bestWaypointDistance = distance;
            lastWaypointProgressTick = tickCount;
            return;
        }
        if (isUsingItem()) {
            // Drawing a bow/crossbow, eating and blocking can deliberately
            // pause movement. Do not blacklist a valid waypoint because the
            // combat or recovery goal temporarily chose to stand still.
            lastWaypointProgressTick = tickCount;
            return;
        }
        if (distance < bestWaypointDistance - 0.5D) {
            bestWaypointDistance = distance;
            lastWaypointProgressTick = tickCount;
            return;
        }
        // A path that makes no progress for four seconds is worse than
        // abandoning one waypoint and letting the owning goal choose again.
        if (tickCount - lastWaypointProgressTick < 80) return;
        abandonedNavigationGoal = goal.immutable();
        abandonedNavigationGoalUntil = tickCount + 100;
        if (navigation instanceof com.craftix.hostile_humans.entity.ai.HumanNavigation ground) {
            ground.avoidWaypoint(waypoint, level().getGameTime() + 100L);
        }
        navigation.stop();
        watchedNavigation = null;
        watchedPathGoal = null;
        watchedWaypoint = null;
    }

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
            LivingEntity target = this.getTarget();
            boolean pursuingCombatTarget = target != null && target.isAlive() && !this.isFleeing;
            return ShoreSeekingPolicy.waterPathMalus(
                    this.isInWater(), this.seekingShore,
                    pursuingCombatTarget, this.pursuingWaterLoot);
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
        return this.findNearestDryShoreTarget(ShoreSeekingPolicy.MAX_SHORE_BLOCK_PROBES_PER_SEARCH);
    }

    @Nullable
    private BlockPos findNearestDryShoreTarget(int maxColumns) {
        BlockPos origin = this.blockPosition();
        double startAngle = this.getRandom().nextDouble() * Math.PI * 2.0D;
        int[] searchRadii = {4, 8, 12, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128};
        BlockPos nearest = null;
        double nearestDistanceSqr = Double.POSITIVE_INFINITY;
        int inspectedColumns = 0;
        search:
        for (int radius : searchRadii) {
            for (int direction = 0; direction < 16; direction += 4) {
                if (inspectedColumns >= maxColumns) {
                    break search;
                }
                inspectedColumns++;
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
            // The fallback only needs the nearest ring with any dry footing.
            // Searching every larger ring on every entry into water made this
            // scan needlessly expensive on the integrated server thread.
            if (nearest != null) {
                break;
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
        int pathProbeCount = 0;
        int inspectedColumns = 0;
        double currentThreatDistance = retreatThreat != null && retreatThreat.isAlive()
                ? Math.sqrt(retreatThreat.distanceToSqr(this))
                : Double.NaN;
        search:
        for (int radius : searchRadii) {
            Path bestPath = null;
            int bestNodeCount = Integer.MAX_VALUE;
            boolean bestUsesWaterNavigation = false;
            for (int direction = 0; direction < 16; direction += 4) {
                if (inspectedColumns >= ShoreSeekingPolicy.MAX_SHORE_BLOCK_PROBES_PER_SEARCH) {
                    break search;
                }
                inspectedColumns++;
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
                if (fallbackCandidateDistanceSqr
                        > ShoreSeekingPolicy.MAX_SHORE_PATH_DISTANCE
                        * ShoreSeekingPolicy.MAX_SHORE_PATH_DISTANCE) {
                    // Keep the far bank as a direct-steering fallback. Asking
                    // navigation for a 128-block route from every wet Human
                    // is expensive and exceeds its useful local search range.
                    continue;
                }
                if (pathProbeCount >= ShoreSeekingPolicy.MAX_SHORE_PATH_PROBES_PER_SEARCH) {
                    break search;
                }
                pathProbeCount++;

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
        this.setSwimming(false);
        this.navigation.moveTo(path, speed);
    }

    public void startSeekingShoreWithoutPath() {
        this.shoreTransitionPendingTick = Integer.MIN_VALUE;
        this.seekingShore = true;
        this.shorePathUsesWaterNavigation = true;
        if (this.shoreFallbackTarget == null) {
            // The full 128-block search is reserved for the globally budgeted
            // path slot. A wave of Humans entering water should not all scan
            // distant heightmaps synchronously just to acquire movement.
            this.findNearestDryShoreTarget(16);
        }
        this.navigation.stop();
        this.selectShoreNavigation();
        this.setSwimming(false);
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
        boolean wasSeekingShore = this.seekingShore;
        this.shoreTransitionPendingTick = Integer.MIN_VALUE;
        this.seekingShore = false;
        this.shoreFallbackTarget = null;
        this.combatFiringShore = false;
        this.combatFiringShoreTarget = null;
        // The pickup goal may already have released shore seeking and started
        // its own route before the old shore goal receives stop(). A second
        // stop must not erase that newly owned navigation path.
        if (wasSeekingShore) {
            this.navigation.stop();
        }
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

    public boolean isCombatFiringShore() {
        return this.combatFiringShore && this.seekingShore;
    }

    /** Last-resort ranged movement when no reachable water firing lane was found. */
    public void beginCombatFiringShore(LivingEntity target, double speed) {
        if (!this.isInWater() || this.isFleeing || this.combatFiringShore
                || target == null || !target.isAlive()) return;
        Path path = this.findNearestShorePath(null);
        if (path != null) {
            this.startSeekingShore(path, speed);
        } else {
            this.startSeekingShoreWithoutPath();
        }
        this.combatFiringShore = true;
        this.combatFiringShoreTarget = target.getUUID();
        this.combatFiringShoreUntilTick = this.tickCount + 100;
    }

    public void updateCombatFiringShore(LivingEntity target, double speed) {
        if (!this.combatFiringShore) return;
        if (!this.isInWater() || this.isFleeing || target == null || !target.isAlive()
                || !target.getUUID().equals(this.combatFiringShoreTarget)
                || this.getSensing().hasLineOfSight(target)
                || this.tickCount >= this.combatFiringShoreUntilTick) {
            this.stopSeekingShore();
        } else if (this.navigation.isDone()) {
            this.continueSeekingShoreWithoutPath(speed);
        }
    }

    /** Fall back to direct water steering when a live path makes no progress toward combat range. */
    public void approachCombatTargetInWater(LivingEntity target, double maximumRange, double speed) {
        if (!this.level().isClientSide && this.shouldUseWaterMovement()
                && target != null && target.isAlive() && !this.isFleeing
                && !this.seekingShore
                && this.distanceToSqr(target) > maximumRange * maximumRange) {
            double distance = this.distanceTo(target);
            if (!target.getUUID().equals(this.waterCombatTarget)
                    || this.tickCount - this.waterCombatProgressTick > 30) {
                this.waterCombatTarget = target.getUUID();
                this.waterCombatDistance = distance;
                this.waterCombatProgressTick = this.tickCount;
                this.waterCombatDirectUntilTick = 0;
            } else if (this.tickCount - this.waterCombatProgressTick >= 12) {
                if (distance >= this.waterCombatDistance - 0.5D) {
                    this.waterCombatDirectUntilTick = this.tickCount + 16;
                }
                this.waterCombatDistance = distance;
                this.waterCombatProgressTick = this.tickCount;
            }
            if (this.getNavigation().isDone() || this.getNavigation().isStuck()
                    || this.tickCount < this.waterCombatDirectUntilTick) {
                this.directWaterCombatSteeringTick = this.tickCount;
                this.getMoveControl().setWantedPosition(
                        target.getX(), target.getY(), target.getZ(), speed);
            }
        }
    }

    public void setPursuingWaterLoot(boolean pursuingWaterLoot) {
        if (pursuingWaterLoot && this.seekingShore) {
            this.stopSeekingShore();
        }
        this.pursuingWaterLoot = pursuingWaterLoot;
    }

    public boolean isPursuingWaterLoot() {
        return this.pursuingWaterLoot;
    }

    public void setActivelyCollectingLoot(boolean collecting) {
        this.activelyCollectingLoot = collecting;
    }

    public boolean isActivelyCollectingLoot() {
        return this.activelyCollectingLoot;
    }

    public void approachWaterLoot(net.minecraft.world.entity.item.ItemEntity item, double speed) {
        if (this.level().isClientSide || !this.pursuingWaterLoot || this.seekingShore
                || item == null || !item.isAlive() || !item.isInWater()
                || !this.getNavigation().isDone() || this.distanceToSqr(item) <= 2.25D) {
            return;
        }
        if (this.shouldUseWaterMovement()) {
            this.directWaterLootSteeringTick = this.tickCount;
            this.getMoveControl().setWantedPosition(item.getX(), item.getY(), item.getZ(), speed);
        } else if (Math.abs(this.getY() - item.getY()) <= 2.0D
                && this.getSensing().hasLineOfSight(item)) {
            // Ground navigation sometimes refuses a water-item endpoint. A
            // visible, shallow item can still be approached from its bank.
            this.getMoveControl().setWantedPosition(item.getX(), item.getY(), item.getZ(), speed);
        }
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

    /** Drop the lateral command from a ranged goal before melee takes over. */
    public void clearRangedStrafeMotion() {
        if (this.moveControl instanceof HumanMoveControl control) {
            control.clearCombatStrafe();
        }
        this.setXxa(0.0F);
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
        // Shore exit is an idle movement fallback. Combat and retreat goals
        // keep ownership of movement whenever a threat is active.
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
        if (!this.level().isClientSide) {
            this.updateWaterKnockbackResistance();
        }
        if (!level().isClientSide && !attributesMigrated) {
            AttributeInstance attack = getAttribute(Attributes.ATTACK_DAMAGE);
            if (attack != null) { attack.removeModifier(FLURRY_ID); }
            club.someoneice.humangunner.TierAttributes.apply(this);
            attributesMigrated = true;
        }

        super.tick();
        if (!level().isClientSide && rangedFacingTick == tickCount
                && rangedFacingTarget != null
                && rangedFacingTarget == getTarget() && rangedFacingTarget.isAlive()) {
            // MoveControl can turn the body toward its next path node after the
            // firing goal aims. Restore target-facing yaw only for an active
            // shot, without replacing the movement path or strafe input.
            double dx = rangedFacingTarget.getX() - getX();
            double dz = rangedFacingTarget.getZ() - getZ();
            if (dx * dx + dz * dz > 1.0E-6D) {
                float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
                setYRot(yaw);
                setYHeadRot(yaw);
                yBodyRot = yaw;
            }
        }
        rangedFacingTarget = null;
        if (!level().isClientSide && !isNoAi()) {
            recoverStalledNavigation();
        }
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
            // Death/removal legitimately clears the external index before a
            // final entity tick. Logging and discarding it again only floods
            // mass-combat logs and allocates a full entity description.
            if (!this.isRemoved()) {
                HostileHumans.LOGGER.warn("Missing data during tick " + this);
                this.discard();
            }
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

    private void updateWaterKnockbackResistance() {
        boolean inWater = this.isInWater();
        if (inWater == this.waterKnockbackModifierApplied) {
            return;
        }
        AttributeInstance resistance = this.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (resistance == null) {
            return;
        }
        if (inWater) {
            resistance.addTransientModifier(new AttributeModifier(WATER_KNOCKBACK_ID,
                    "hostile_humans.water_knockback_resistance", 1.0D,
                    AttributeModifier.Operation.ADDITION));
        } else {
            resistance.removeModifier(WATER_KNOCKBACK_ID);
        }
        this.waterKnockbackModifierApplied = inWater;
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
        return false;
    }

    public EntityDimensions getDimensions(Pose p_36166_) {
        if (p_36166_ == Pose.SWIMMING) {
            return STANDING_DIMENSIONS;
        }
        return super.getDimensions(p_36166_);
    }

    public boolean wantsToSwim() {
        return false;
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
        LivingEntity combatTarget = this.getTarget();
        if (this.seekingShore && combatTarget != null && combatTarget.isAlive()
                && (!this.combatFiringShore || this.isFleeing)) {
            // The idle shore goal has yielded to combat. Release its route
            // immediately, even before GoalSelector runs its next stop pass.
            this.stopSeekingShore();
        }
        // A shoreline collision can make isNearWaterSurface() false while the
        // eyes are already above water. Switching navigators at that boundary
        // stops the current path and makes combat goals calculate it again.
        // Keep one movement owner until the feet have actually left the water.
        return true;
    }

    private boolean isNearWaterSurface() {
        if (!this.isInWater()) {
            return false;
        }
        this.refreshWaterSurfaceCheck();
        return this.cachedNearWaterSurface;
    }

    private double distanceToActualWaterSurface() {
        if (!this.isInWater()) {
            return Double.POSITIVE_INFINITY;
        }
        this.refreshWaterSurfaceCheck();
        return this.cachedDistanceToWaterSurface;
    }

    private void refreshWaterSurfaceCheck() {
        // Movement, rendering and the navigator can ask this several times in
        // one tick. Collision checks are costly; reuse only at the exact same
        // position, then recompute immediately after movement.
        if (this.surfaceCheckTick == this.tickCount
                && this.surfaceCheckX == this.getX()
                && this.surfaceCheckY == this.getY()
                && this.surfaceCheckZ == this.getZ()) {
            return;
        }
        this.surfaceCheckTick = this.tickCount;
        this.surfaceCheckX = this.getX();
        this.surfaceCheckY = this.getY();
        this.surfaceCheckZ = this.getZ();
        this.cachedDistanceToWaterSurface = this.computeDistanceToActualWaterSurface();
        this.cachedNearWaterSurface = this.cachedDistanceToWaterSurface >= -0.1D
                && this.cachedDistanceToWaterSurface <= 2.0D
                && this.hasStandingRoomAtCurrentPosition();
    }

    private double computeDistanceToActualWaterSurface() {
        BlockPos origin = this.blockPosition();
        for (int yOffset = -1; yOffset <= 3; yOffset++) {
            BlockPos fluidPos = origin.offset(0, yOffset, 0);
            var fluid = this.level().getFluidState(fluidPos);
            if (!fluid.is(FluidTags.WATER)
                    || this.level().getFluidState(fluidPos.above()).is(FluidTags.WATER)) {
                continue;
            }

            double surfaceY = fluidPos.getY() + fluid.getHeight(this.level(), fluidPos);
            double distanceToSurface = surfaceY - this.getY();
            if (distanceToSurface >= -1.0D) {
                return distanceToSurface;
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    private boolean hasStandingRoomAtCurrentPosition() {
        EntityDimensions standing = this.getDimensions(Pose.STANDING);
        AABB standingBox = new AABB(
                this.getX() - standing.width / 2.0D, this.getY(), this.getZ() - standing.width / 2.0D,
                this.getX() + standing.width / 2.0D, this.getY() + standing.height, this.getZ() + standing.width / 2.0D
        );
        return this.level().noCollision(this, standingBox);
    }

    private boolean isWaterSurfaceCloseForShorePop() {
        // Ignore the standing-box collision at the bank; it is precisely what
        // this small step assist must clear. Internal water blocks are never
        // mistaken for the top of the water column.
        double distance = this.distanceToActualWaterSurface();
        return ShoreSeekingPolicy.isNearRealSurfaceForShorePop(distance);
    }

    private boolean isFollowingHigherShoreWaypoint() {
        return this.seekingShore
                && this.moveControl instanceof HumanMoveControl humanMoveControl
                && humanMoveControl.wantsHigherWaypoint();
    }

    private boolean hasHigherDryLandingAhead(double directionX, double directionZ) {
        double directionLength = Math.sqrt(directionX * directionX + directionZ * directionZ);
        if (directionLength < 1.0E-4D) {
            return false;
        }
        directionX /= directionLength;
        directionZ /= directionLength;

        if (this.dryLandingCheckTick == this.tickCount
                && this.dryLandingCheckX == this.getX()
                && this.dryLandingCheckY == this.getY()
                && this.dryLandingCheckZ == this.getZ()
                && this.dryLandingDirectionX == directionX
                && this.dryLandingDirectionZ == directionZ) {
            return this.cachedDryLandingAhead;
        }
        this.dryLandingCheckTick = this.tickCount;
        this.dryLandingCheckX = this.getX();
        this.dryLandingCheckY = this.getY();
        this.dryLandingCheckZ = this.getZ();
        this.dryLandingDirectionX = directionX;
        this.dryLandingDirectionZ = directionZ;
        this.cachedDryLandingAhead = false;
        this.cachedDryLandingFeetY = Double.NEGATIVE_INFINITY;

        int minY = Mth.floor(this.getY() + 0.1D);
        int maxY = Mth.floor(this.getY() + 2.65D);
        double surfaceDistance = this.distanceToActualWaterSurface();
        double oneBlockLandingLimit = Double.isFinite(surfaceDistance)
                ? ShoreSeekingPolicy.oneBlockShoreLandingLimit(this.getY() + surfaceDistance)
                : Double.POSITIVE_INFINITY;
        for (int forwardIndex = 0; forwardIndex < 3; forwardIndex++) {
            double forward = 0.4D + forwardIndex * 0.35D;
            for (int sideIndex = -1; sideIndex <= 1; sideIndex++) {
                double side = sideIndex * 0.3D;
                int x = Mth.floor(this.getX() + directionX * forward - directionZ * side);
                int z = Mth.floor(this.getZ() + directionZ * forward + directionX * side);
                for (int y = minY; y <= maxY; y++) {
                    if (y > this.getY() + 0.35D
                            && y <= this.getY() + 2.55D
                            && y <= oneBlockLandingLimit
                            && this.isDryStandingPosition(new BlockPos(x, y, z))) {
                        this.cachedDryLandingAhead = true;
                        this.cachedDryLandingFeetY = y;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Direction findAdjacentDryLandingDirection() {
        double surfaceDistance = this.distanceToActualWaterSurface();
        if (!Double.isFinite(surfaceDistance)) return null;
        double oneBlockLandingLimit = ShoreSeekingPolicy.oneBlockShoreLandingLimit(
                this.getY() + surfaceDistance);
        int minY = Mth.floor(this.getY() + 0.1D);
        int maxY = Mth.floor(this.getY() + 2.65D);
        for (Direction direction : new Direction[]{
                Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            int x = Mth.floor(this.getX() + direction.getStepX() * 0.85D);
            int z = Mth.floor(this.getZ() + direction.getStepZ() * 0.85D);
            for (int y = minY; y <= maxY; y++) {
                if (y > this.getY() + 0.35D && y <= this.getY() + 2.55D
                        && y <= oneBlockLandingLimit
                        && this.isDryStandingPosition(new BlockPos(x, y, z))) {
                    this.cachedDryLandingFeetY = y;
                    return direction;
                }
            }
        }
        return null;
    }

    public void travel(Vec3 p_32394_) {
        if (this.isEffectiveAi() && this.shouldUseWaterMovement()) {
            this.moveRelative(0.01f, p_32394_);
            Vec3 motion = this.getDeltaMovement();
            double surfaceDistance = this.distanceToActualWaterSurface();
            if (ShoreSeekingPolicy.shouldRiseTowardSurface(
                    this.isEyeInFluid(FluidTags.WATER), surfaceDistance)) {
                boolean nearSurface = this.isNearWaterSurface();
                boolean climbingShore = this.isFollowingHigherShoreWaypoint();
                // Keep rising until the feet, not merely the eyes, approach
                // the real water surface. Internal full water blocks do not
                // count as a surface.
                // A higher shore waypoint gets a controlled climb assist so
                // the standing model can step over the final shallow bank.
                double minimumRise = climbingShore
                        ? (this.shouldCatchBreath ? 0.12D : 0.08D)
                        : (this.shouldCatchBreath
                        ? (nearSurface ? 0.06D : 0.12D)
                        : (nearSurface ? 0.04D : 0.06D));
                double ascentLimit = this.getWaterAscentSpeedLimit(nearSurface);
                if (!climbingShore && !(this.moveControl instanceof HumanMoveControl humanMoveControl
                        && humanMoveControl.isShorePopActive())) {
                    ascentLimit = ShoreSeekingPolicy.surfaceAscentLimit(surfaceDistance, ascentLimit);
                }
                double verticalMotion = Math.max(motion.y, minimumRise);
                motion = new Vec3(motion.x, Math.min(ascentLimit, verticalMotion), motion.z);
            } else if (surfaceDistance <= ShoreSeekingPolicy.SURFACE_STANCE_DEPTH
                    && !this.isFollowingHigherShoreWaypoint()
                    && !(this.moveControl instanceof HumanMoveControl humanMoveControl
                    && humanMoveControl.isShorePopActive())) {
                // Settle a little deeper than before, without changing the
                // separate bank-step and shore-pop clearance requirements.
                double settle = surfaceDistance < ShoreSeekingPolicy.SURFACE_STANCE_DEPTH - 0.05D
                        ? -0.015D : 0.0D;
                motion = new Vec3(motion.x, Mth.clamp(motion.y, -0.02D, settle), motion.z);
            }
            this.setDeltaMovement(motion);
            this.move(MoverType.SELF, motion);
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
            this.setPose(Pose.STANDING);
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
                this.setSwimming(false);
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
        private static final int MAX_SHORE_POP_DURATION_TICKS = 16;
        private static final double SHORE_POP_SPEED = 0.20D;

        private final Human human;
        private int shorePopCooldownTicks;
        private int nextShorePopProbeTick;
        private int shorePopTicks;
        private double shorePopTargetY;
        private double shorePopDirectionX;
        private double shorePopDirectionZ;
        private int combatStrafeTick = Integer.MIN_VALUE;
        private float combatStrafeForward;
        private float combatStrafeRight;

        public HumanMoveControl(Human p_32433_) {
            super((Mob)p_32433_);
            this.human = p_32433_;
        }

        @Override
        public void strafe(float forward, float right) {
            super.strafe(forward, right);
            this.combatStrafeTick = this.human.tickCount;
            this.combatStrafeForward = forward;
            this.combatStrafeRight = right;
        }

        private void clearCombatStrafe() {
            this.combatStrafeTick = Integer.MIN_VALUE;
            this.combatStrafeForward = 0.0F;
            this.combatStrafeRight = 0.0F;
            this.strafeForwards = 0.0F;
            this.strafeRight = 0.0F;
            if (this.operation == MoveControl.Operation.STRAFE) {
                this.operation = MoveControl.Operation.WAIT;
            }
        }

        public void tick() {
            if (this.shorePopCooldownTicks > 0) {
                this.shorePopCooldownTicks--;
            }
            if (this.shorePopTicks > 0
                    && this.human.onGround()
                    && this.human.getY() >= this.shorePopTargetY - 0.05D) {
                this.shorePopTicks = 0;
                Vec3 motion = this.human.getDeltaMovement();
                if (motion.y > 0.05D) {
                    this.human.setDeltaMovement(motion.x, 0.05D, motion.z);
                }
            } else if (this.shorePopTicks > 0) {
                this.shorePopTicks--;
            }
            // Path goals may request 1.1-1.2 speed. Cap the control input at
            // normal walking pace; sprint and potion modifiers are applied
            // separately by vanilla MOVEMENT_SPEED, just as for a player.
            this.speedModifier = Math.min(this.speedModifier, 1.0D);
            // Navigation runs after goals and can replace their STRAFE operation
            // with MOVE_TO. Keep the combat strafe requested this tick independent
            // of that operation, but never override a retreat or shore route.
            if (HumanUtil.isMeleeWeapon(this.human.getMainHandItem())
                    && this.combatStrafeRight != 0.0F) {
                clearCombatStrafe();
            }
            if (this.combatStrafeTick == this.human.tickCount
                    && (this.combatStrafeForward != 0.0F || this.combatStrafeRight != 0.0F)
                    && !this.human.isFleeing && !this.human.seekingShore) {
                if (this.human.shouldUseWaterMovement()) {
                    this.strafeForwards = this.combatStrafeForward;
                    this.strafeRight = this.combatStrafeRight;
                    // MoveControl.strafe defaults to quarter speed; that is
                    // inappropriate for an explicit combat movement input.
                    this.speedModifier = 1.0D;
                    applyWaterStrafe();
                } else {
                    float length = Mth.sqrt(this.combatStrafeForward * this.combatStrafeForward
                            + this.combatStrafeRight * this.combatStrafeRight);
                    float normalizer = Math.max(1.0F, length);
                    this.human.setSpeed((float) this.human.getAttributeValue(Attributes.MOVEMENT_SPEED));
                    this.human.setZza(this.combatStrafeForward / normalizer);
                    this.human.setXxa(this.combatStrafeRight / normalizer);
                }
                this.operation = MoveControl.Operation.WAIT;
                return;
            }
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
                boolean directCombatFallback = this.human.directWaterCombatSteeringTick
                        == this.human.tickCount && this.human.getTarget() != null
                        && this.human.getTarget().isAlive() && !this.human.isFleeing;
                if (directCombatFallback) {
                    LivingEntity target = this.human.getTarget();
                    this.wantedX = target.getX();
                    this.wantedY = target.getY();
                    this.wantedZ = target.getZ();
                    this.speedModifier = 1.0D;
                    this.operation = MoveControl.Operation.MOVE_TO;
                }
                boolean directLootFallback = this.human.directWaterLootSteeringTick
                        == this.human.tickCount && this.human.pursuingWaterLoot
                        && this.human.getTarget() == null;
                if (this.operation != MoveControl.Operation.MOVE_TO
                        || (this.human.getNavigation().isDone()
                        && !directShoreFallback && !directCombatFallback
                        && !directLootFallback)) {
                    if (this.human.horizontalCollision) {
                        BlockPos waterPos = this.human.blockPosition();
                        Vec3 flow = this.human.level().getFluidState(waterPos)
                                .getFlow(this.human.level(), waterPos);
                        Vec3 motion = this.human.getDeltaMovement();
                        double directionX = flow.horizontalDistanceSqr() > 0.0001D
                                ? flow.x : motion.x;
                        double directionZ = flow.horizontalDistanceSqr() > 0.0001D
                                ? flow.z : motion.z;
                        this.tryShorePop(directionX, directionZ);
                    }
                    if (this.isShorePopActive()) this.applyShorePopImpulse();
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
                    if (this.isShorePopActive()) this.applyShorePopImpulse();
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
                if (this.isShorePopActive() && !this.human.onGround()) {
                    // Crossing the waterline is not the same as reaching the
                    // dry block. Finish the bounded bank step in air too.
                    this.applyShorePopImpulse();
                } else if (!this.human.onGround()) {
                    this.human.setDeltaMovement(this.human.getDeltaMovement().add(0.0, -0.008, 0.0));
                }
                // MOVE_TO and WAIT do not clear the side input that the last
                // ranged STRAFE wrote. Clear it before normal path movement.
                this.human.setXxa(0.0F);
                super.tick();
            }
        }

        private void applyShorePopImpulse() {
            Vec3 motion = this.human.getDeltaMovement();
            this.human.setDeltaMovement(
                    motion.x + this.shorePopDirectionX * 0.035D,
                    Math.min(SHORE_POP_SPEED,
                            Math.max(0.0D, this.shorePopTargetY - this.human.getY())),
                    motion.z + this.shorePopDirectionZ * 0.035D);
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
            // At yaw zero Minecraft faces +Z, so forward must steer +Z and
            // right must steer +X (the old transform was rotated 90 degrees).
            double directionX = right * cos - forward * sin;
            double directionZ = forward * cos + right * sin;
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
            boolean nearSurface = this.human.isNearWaterSurface();
            boolean climbingShore = this.human.isFollowingHigherShoreWaypoint();
            boolean shorePopActive = this.isShorePopActive();
            if (climbingShore) {
                nextVertical = Math.max(nextVertical, this.human.shouldCatchBreath ? 0.12D : 0.08D);
            }
            if (shorePopActive) {
                nextVertical = Math.max(nextVertical,
                        Math.min(SHORE_POP_SPEED,
                                Math.max(0.0D, this.shorePopTargetY - this.human.getY())));
            }
            double verticalLimit = this.human.getWaterAscentSpeedLimit(nearSurface);
            if (!climbingShore && !shorePopActive) {
                verticalLimit = ShoreSeekingPolicy.surfaceAscentLimit(
                        this.human.distanceToActualWaterSurface(), verticalLimit);
            }
            if (shorePopActive) {
                verticalLimit = Math.max(verticalLimit, SHORE_POP_SPEED);
            }
            nextVertical = Mth.clamp(nextVertical, 0.0D, verticalLimit);
            if (shorePopActive) {
                nextVertical = Math.min(nextVertical,
                        Math.max(0.0D, this.shorePopTargetY - this.human.getY()));
            }
            this.human.setDeltaMovement(
                    motion.x + directionX * horizontalAcceleration
                            + (shorePopActive ? this.shorePopDirectionX * 0.035D : 0.0D),
                    nextVertical,
                    motion.z + directionZ * horizontalAcceleration
                            + (shorePopActive ? this.shorePopDirectionZ * 0.035D : 0.0D)
            );
        }

        private void tryShorePop(double directionX, double directionZ) {
            if (this.shorePopCooldownTicks > 0
                    || this.shorePopTicks > 0
                    || this.human.tickCount < this.nextShorePopProbeTick
                    || !this.human.horizontalCollision
                    || !this.human.isInWater()
                    || !this.human.isWaterSurfaceCloseForShorePop()
                    || this.human.getDeltaMovement().y > 0.18D) {
                return;
            }

            double landingDirectionX = directionX;
            double landingDirectionZ = directionZ;
            if (!this.human.hasHigherDryLandingAhead(landingDirectionX, landingDirectionZ)) {
                BlockPos waterPos = this.human.blockPosition();
                Vec3 flow = this.human.level().getFluidState(waterPos)
                        .getFlow(this.human.level(), waterPos);
                landingDirectionX = flow.x;
                landingDirectionZ = flow.z;
                if (!this.human.hasHigherDryLandingAhead(landingDirectionX, landingDirectionZ)) {
                    Direction adjacent = this.human.findAdjacentDryLandingDirection();
                    if (adjacent == null) {
                        this.nextShorePopProbeTick = this.human.tickCount + 5;
                        return;
                    }
                    landingDirectionX = adjacent.getStepX();
                    landingDirectionZ = adjacent.getStepZ();
                }
            }

            // A bounded step only at a collided dry bank, whether navigation
            // or a flowing current brought the Human there. Never pop in open water.
            this.shorePopTicks = Math.min(MAX_SHORE_POP_DURATION_TICKS,
                    Mth.ceil((this.human.cachedDryLandingFeetY - this.human.getY())
                            / SHORE_POP_SPEED) + 2);
            this.shorePopCooldownTicks = SHORE_POP_COOLDOWN_TICKS;
            this.shorePopTargetY = this.human.cachedDryLandingFeetY;
            double directionLength = Math.sqrt(landingDirectionX * landingDirectionX
                    + landingDirectionZ * landingDirectionZ);
            this.shorePopDirectionX = landingDirectionX / directionLength;
            this.shorePopDirectionZ = landingDirectionZ / directionLength;
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


package club.someoneice.humangunner;

import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * High-priority, interruptible survival layer above Hostile Humans' native
 * weapon goals. It only owns movement while a defensive action is necessary;
 * once the burst/retreat is complete, the original melee, bow, crossbow,
 * trident or TaCZ goal resumes instead of being replaced wholesale.
 */
public final class AdaptiveCombatGoal extends Goal {
    private enum Phase {
        DEFEND,
        RETREAT,
        RECOVER,
        FLANK,
        PRESSURE,
        UNSTUCK
    }

    private record PlannedPath(Path path, Vec3 position, double score) {
    }

    private record RememberedThreat(LivingEntity entity, int rememberedAtTick) {
    }

    private record IncomingHit(LivingEntity attacker, int hitAtTick) {
    }

    private record IncomingArrow(AbstractArrow projectile, LivingEntity shooter, double ticksToImpact) {
    }

    private static final int THREAT_MEMORY_TICKS = 20 * 30;
    private static final int MAX_REMEMBERED_THREATS = 4;
    private static final double MAX_REMEMBERED_THREAT_DISTANCE_SQR = 64.0D * 64.0D;
    private static final int FOCUS_PRESSURE_WINDOW_TICKS = 60;
    private static final int FOCUS_LOCK_TICKS = 80;

    private final Human human;
    private final CombatAiConfig config;
    private final GunSupport.Control gunOperator;
    private Phase pendingPhase;
    private Phase phase;
    private LivingEntity threat;
    private Path activePath;
    private Vec3 lastObservedPosition;
    private UUID combatTarget;
    private int phaseTicks;
    private int repathTicks;
    private int nextRecoveryUseTick;
    private RecoverySupplies.UseSession recoverySession;
    private int nextDefenseTick;
    private int defenseStartTick;
    private int defensePressureTick;
    private int defenseObservedHurtTimestamp = Integer.MIN_VALUE;
    private int defenseObservedGunfireUntil;
    private LivingEntity defenseMovementTarget;
    private int projectileDefenseUntil;
    private int nextProjectileGuardTick;
    private boolean defendingProjectile;
    private int lastShieldTriggerHurtTimestamp = Integer.MIN_VALUE;
    private int lastShieldTriggerGunfireUntil;
    private int nextMeleePredictionTick;
    private int nextTacticalTick;
    private int nextCrowdScanTick;
    private int crowdPressureUntil;
    private int burstDamageRetreatUntil;
    private int nextTerrainJumpTick;
    private int retreatAimTicks;
    private int retreatStartedTick;
    private int retreatStagnantTicks;
    private int nextRetreatMeleeTick;
    private int nextRetreatTridentTick;
    private int stationaryChecks;
    private int lastProcessedHurtTimestamp = Integer.MIN_VALUE;
    private int lastThreatTick = Integer.MIN_VALUE / 2;
    private LivingEntity focusedThreat;
    private int focusLockUntil;
    private boolean ownsFleeFlag;
    private Vec3 lastRetreatPosition;
    private final ArrayDeque<RememberedThreat> threatMemory = new ArrayDeque<>();
    private final ArrayDeque<IncomingHit> incomingHits = new ArrayDeque<>();
    // The same arrow remains visible for several ticks. Never reroll a miss.
    private final Map<UUID, Boolean> arrowDefenseDecisions = new LinkedHashMap<>();

    public AdaptiveCombatGoal(Human human) {
        this.human = human;
        this.config = CombatAiConfig.get();
        this.gunOperator = GunSupport.get().control(human);
        this.lastObservedPosition = human.position();
        this.lastRetreatPosition = human.position();
        // Spread nearby-entity queries across six ticks so a large battle does
        // not make every Human scan its surroundings on the same server tick.
        this.nextCrowdScanTick = human.tickCount + Math.floorMod(human.getId(), 6);
        human.getPersistentData().remove("humangunner:ai_visible_threats");
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!config.enabled() || human.level().isClientSide || !human.isAlive()) {
            return false;
        }
        IncomingArrow incomingArrow = findIncomingArrow();
        // Hold position forbids ordinary tactical movement, but not a
        // life-saving retreat or blocking a projectile headed into its post.
        if (SoldierOrder.isHoldingPosition(human)
                && !RetreatRecoveryPolicy.shouldForceRetreat(healthRatio())
                && incomingArrow == null) {
            return false;
        }

        LivingEntity currentThreat = resolveThreat();
        if (currentThreat == null && incomingArrow != null && isValidThreat(incomingArrow.shooter())) {
            currentThreat = incomingArrow.shooter();
        }
        if (currentThreat == null) {
            resetCombatSession();
            return false;
        }
        threat = currentThreat;
        updateCombatSession(currentThreat);

        updateBurstDamageRetreat();
        if (shouldUseBurstDamageRetreat() || shouldPrioritizeCrowdRetreat()) {
            pendingPhase = Phase.RETREAT;
            return true;
        }

        double health = healthRatio();
        if (RetreatRecoveryPolicy.shouldForceRetreat(health)
                || (HumanUtil.isLowHp(human) && human.shouldStartFleeingThisCombat())
                || health <= config.criticalHealthRatio()
                || (health < config.retreatHealthRatio()
                && projectedHealthRatio() < config.resumeHealthRatio()
                && config.itemRecoveryEnabled()
                && RecoverySupplies.hasCombatRecoverySupply(human))) {
            pendingPhase = Phase.RETREAT;
            return true;
        }

        if ((canBlockWithShield() && human.tickCount >= nextDefenseTick)
                || incomingArrow != null) {
            if (incomingArrow != null) {
                projectileDefenseUntil = human.tickCount
                        + ProjectileShieldPolicy.guardWindowTicks(
                        isDedicatedRangedCombatant(), incomingArrow.ticksToImpact());
                pendingPhase = Phase.DEFEND;
                return true;
            }
            int gunfireUntil = HumanGunner.recentGunfireUntil(human);
            int hurtTimestamp = human.getLastHurtByMobTimestamp();
            boolean freshGunfire = CombatPressurePolicy.isRecentGunfire(human.tickCount, gunfireUntil)
                    && gunfireUntil != lastShieldTriggerGunfireUntil;
            boolean freshHit = isValidThreat(human.getLastHurtByMob())
                    && CombatPressurePolicy.isRecentHit(human.tickCount, hurtTimestamp)
                    && hurtTimestamp != lastShieldTriggerHurtTimestamp;
            if (RecoverySupplies.consumeShieldReblockRequest(human)
                    && (freshHit || freshGunfire || predictsIncomingMelee(currentThreat))) {
                pendingPhase = Phase.DEFEND;
                return true;
            }
            if (freshGunfire) {
                lastShieldTriggerGunfireUntil = gunfireUntil;
            }
            if (freshHit) {
                lastShieldTriggerHurtTimestamp = hurtTimestamp;
            }
            if (freshGunfire
                    || (freshHit
                    && healthRatio() >= config.retreatHealthRatio()
                    && (human.distanceToSqr(currentThreat) > 12.25D
                    || human.getRandom().nextDouble() < config.shieldBlockChanceAfterHit()))) {
                pendingPhase = Phase.DEFEND;
                return true;
            }
            if (human.tickCount >= nextMeleePredictionTick
                    && predictsIncomingMelee(currentThreat)) {
                boolean committedSwing = currentThreat.swinging;
                nextMeleePredictionTick = human.tickCount + (committedSwing ? 12 : 18);
                if (committedSwing || human.getRandom().nextDouble() < 0.40D) {
                    pendingPhase = Phase.DEFEND;
                    return true;
                }
            }
        }

        updateStationaryState();
        // A gunner holding its configured firing band is expected to pause
        // between lateral relocations. Do not let this higher-priority MOVE
        // goal interpret that deliberate firing stance as a navigation stall;
        // GunnerGoal owns both range control and its new flank paths.
        if (!isUsingTaczGun()
                && !RangedWeaponCustody.controlsMainHand(human)
                && stationaryChecks >= 6 && human.distanceToSqr(currentThreat) > 9.0D) {
            pendingPhase = Phase.UNSTUCK;
            return true;
        }

        // TaCZ gunners already perform range control and strafing in GunnerGoal.
        // Letting this higher-priority goal start FLANK for them repeatedly
        // cancels their aim, turns sprint back on and prevents TaCZ from firing.
        if (isRangedCombat() && !isUsingTaczGun() && human.tickCount >= nextTacticalTick) {
            double distance = human.distanceTo(currentThreat);
            boolean crowded = countNearbyAllies(config.allySpacingRadius()) >= 2;
            boolean rangedDuel = isRangedThreat(currentThreat)
                    && human.getRandom().nextDouble() < 0.55D;
            nextTacticalTick = human.tickCount + config.tacticalRepositionInterval();
            if (distance < preferredMinimumRange()
                    || !human.getSensing().hasLineOfSight(currentThreat)
                    || crowded
                    || rangedDuel) {
                pendingPhase = Phase.FLANK;
                return true;
            }
        } else if (!isRangedCombat() && !RangedWeaponCustody.controlsMainHand(human)
                && human.tickCount >= nextTacticalTick) {
            double distance = human.distanceTo(currentThreat);
            nextTacticalTick = human.tickCount + config.tacticalRepositionInterval();
            if (distance > 4.0D
                    && distance < 24.0D
                    && (!human.getSensing().hasLineOfSight(currentThreat)
                    || human.getNavigation().isStuck())) {
                pendingPhase = Phase.PRESSURE;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (threat == null || !threat.isAlive() || !human.isAlive()) {
            return false;
        }
        if ((human.distanceToSqr(threat) > 4096.0D
                && !(phase == Phase.DEFEND && human.tickCount < projectileDefenseUntil))
                || phaseTicks <= 0) {
            return false;
        }
        return switch (phase) {
            case RETREAT, RECOVER -> !retreatSafe()
                    || recoverySession != null
                    || CombatFoodRecoveryGoal.isActive(human);
            case PRESSURE, UNSTUCK -> phaseTicks > 0
                    && !RangedWeaponCustody.controlsMainHand(human);
            case DEFEND, FLANK -> phaseTicks > 0;
        };
    }

    @Override
    public void start() {
        phase = pendingPhase;
        pendingPhase = null;
        repathTicks = 0;
        activePath = null;
        switch (phase) {
            case DEFEND -> {
                defenseMovementTarget = null;
                defendingProjectile = human.tickCount < projectileDefenseUntil;
                int projectileWindow = projectileDefenseUntil - human.tickCount;
                phaseTicks = defendingProjectile && isDedicatedRangedCombatant()
                        ? Math.max(1, projectileWindow)
                        : Math.max(rollShieldBlockDuration(), projectileWindow);
                defenseStartTick = human.tickCount;
                defensePressureTick = human.tickCount;
                defenseObservedHurtTimestamp = human.getLastHurtByMobTimestamp();
                defenseObservedGunfireUntil = HumanGunner.recentGunfireUntil(human);
                lastShieldTriggerHurtTimestamp = defenseObservedHurtTimestamp;
                lastShieldTriggerGunfireUntil = defenseObservedGunfireUntil;
                startShieldBlockIfAvailable(shouldAdvanceToMeleeThreat());
            }
            case RETREAT -> {
                phaseTicks = 200;
                retreatAimTicks = 0;
                retreatStartedTick = human.tickCount;
                retreatStagnantTicks = 0;
                nextRetreatTridentTick = human.tickCount + 12;
                lastRetreatPosition = human.position();
                prepareCrowdRetreatGun();
                setFleeing(true);
                planRetreat();
            }
            case RECOVER -> {
                phaseTicks = 200;
                setFleeing(true);
            }
            case FLANK -> {
                phaseTicks = 14;
                nextTacticalTick = human.tickCount + config.tacticalRepositionInterval();
                planTacticalMove();
            }
            case PRESSURE -> {
                phaseTicks = 10;
                nextTacticalTick = human.tickCount + config.tacticalRepositionInterval();
                planPressureMove();
            }
            case UNSTUCK -> {
                phaseTicks = 12;
                stationaryChecks = 0;
                planUnstuckMove();
            }
        }
        if (phase == Phase.RETREAT || phase == Phase.RECOVER) {
            MovementSpeedController.retreat(human, true);
        } else {
            MovementSpeedController.combat(human, phase != Phase.DEFEND && !isUsingTaczGun());
        }
        human.getPersistentData().putString("humangunner:ai_phase", phase.name().toLowerCase());
    }

    @Override
    public void stop() {
        boolean shoreTransitionPending = human.isShoreTransitionPending();
        boolean endedRetreat = phase == Phase.RETREAT || phase == Phase.RECOVER;
        if (phase == Phase.DEFEND) {
            int reblockDelay = config.shieldBlockCooldownTicks();
            if (RangedWeaponCustody.isCrossbowWeapon(human.getMainHandItem())
                    || RangedWeaponCustody.isCrossbowWeapon(human.getOffhandItem())) {
                // A five-tick reblock loop can cancel every crossbow charge
                // under automatic fire. Reserve one complete charge-and-shot
                // window after blocking before another shield cycle may begin.
                reblockDelay = Math.max(reblockDelay, 24);
            }
            nextDefenseTick = human.tickCount + reblockDelay;
            if (defendingProjectile && isDedicatedRangedCombatant()) {
                nextProjectileGuardTick = human.tickCount + (isCrossbowCombatant() ? 32 : 24);
            }
            defendingProjectile = false;
            projectileDefenseUntil = 0;
        }
        cancelRecoveryUse();
        restoreRetreatGun();
        setRetreatBackpedaling(false);
        gunOperator.aim(false);
        retreatAimTicks = 0;
        activePath = null;
        defenseMovementTarget = null;
        if (human.getTarget() == null) {
            MovementSpeedController.normal(human);
        } else {
            MovementSpeedController.combat(human, false);
        }
        if (ShoreSeekingPolicy.shouldClearFleeingAfterCombatStop(
                ownsFleeFlag, shoreTransitionPending)) {
            human.isFleeing = false;
        }
        if (ownsFleeFlag) {
            ownsFleeFlag = false;
        }
        if (human.hasOwner() && ShoreSeekingPolicy.shouldReturnToOwnerAfterCombatStop(
                endedRetreat, shoreTransitionPending)) {
            SoldierOrder.beginReturnFromRetreat(human);
        }
        if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) {
            human.stopUsingItem();
        }
        human.getPersistentData().putString("humangunner:ai_phase", "native_combat");
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity reevaluated = resolveThreat();
        if (reevaluated != null) {
            threat = reevaluated;
        }
        if (threat == null) {
            return;
        }
        updateBurstDamageRetreat();
        if ((shouldUseBurstDamageRetreat() || shouldPrioritizeCrowdRetreat())
                && phase != Phase.RETREAT && phase != Phase.RECOVER) {
            if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) {
                human.stopUsingItem();
            }
            phaseTicks = 0;
            human.getPersistentData().putString(
                    "humangunner:ai_phase",
                    crowdPressureActive() ? "crowd_retreat_pending" : "burst_damage_retreat_pending"
            );
            return;
        }
        lastThreatTick = human.tickCount;
        if (phase != Phase.RETREAT && phase != Phase.RECOVER) phaseTicks--;
        repathTicks--;
        human.getLookControl().setLookAt(threat, 45.0F, 35.0F);

        switch (phase) {
            case DEFEND -> tickDefend();
            case RETREAT -> tickRetreat();
            case RECOVER -> tickRecover();
            case FLANK -> tickFlank();
            case PRESSURE -> tickPressure();
            case UNSTUCK -> tickUnstuck();
        }
    }

    private void tickDefend() {
        MovementSpeedController.combat(human, false);
        if (!canBlockWithShield()
                && !(defendingProjectile && canPreemptivelyBlockProjectile())) {
            if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) {
                human.stopUsingItem();
            }
            phaseTicks = 0;
            return;
        }

        if (human.tickCount < projectileDefenseUntil
                && (isCloseMeleeEngagement() || shouldDeferRangedProjectileGuard())) {
            projectileDefenseUntil = 0;
            if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) {
                human.stopUsingItem();
            }
            phaseTicks = 0;
            return;
        }

        updateDefensePressure();
        IncomingArrow incomingArrow = findIncomingArrow();
        if (incomingArrow != null) {
            defendingProjectile = true;
            projectileDefenseUntil = human.tickCount
                    + ProjectileShieldPolicy.guardWindowTicks(
                    isDedicatedRangedCombatant(), incomingArrow.ticksToImpact());
            human.getLookControl().setLookAt(incomingArrow.projectile(), 90.0F, 90.0F);
        }
        boolean awaitingProjectile = human.tickCount < projectileDefenseUntil;
        if (awaitingProjectile) {
            phaseTicks = Math.max(phaseTicks, projectileDefenseUntil - human.tickCount);
            defensePressureTick = human.tickCount;
        }
        int heldTicks = human.tickCount - defenseStartTick;
        int quietTicks = human.tickCount - defensePressureTick;
        boolean canCounterFromCurrentRange = canCounterFromCurrentRange();
        boolean advancingMelee = shouldAdvanceToMeleeThreat();
        if (defendingProjectile && isDedicatedRangedCombatant()
                && incomingArrow == null && heldTicks >= 6) {
            if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) {
                human.stopUsingItem();
            }
            phaseTicks = 0;
            return;
        }
        // Keep the shield through the incoming shot, but give a combatant that
        // can actually answer at this range a firing window between volleys.
        if ((!awaitingProjectile || (incomingArrow == null && canCounterFromCurrentRange))
                && CombatPressurePolicy.shouldOpenCounterWindow(
                heldTicks, quietTicks, canCounterFromCurrentRange
        )) {
            if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) {
                human.stopUsingItem();
            }
            phaseTicks = 0;
            boolean attacked = performShieldCounterattack();
            human.getPersistentData().putString(
                    "humangunner:ai_phase",
                    attacked ? "shield_counterattack" : "shield_counter_window"
            );
            return;
        }

        if (!awaitingProjectile && CombatPressurePolicy.shouldReleaseForPursuit(
                heldTicks, quietTicks, !isRangedThreat(threat),
                human.distanceToSqr(threat), radialApproachSpeed(threat)
        )) {
            if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) {
                human.stopUsingItem();
            }
            phaseTicks = 0;
            human.getPersistentData().putString("humangunner:ai_phase", "shield_pursuit_release");
            return;
        }

        if (defendingProjectile && isDedicatedRangedCombatant()
                && !SoldierOrder.isHoldingPosition(human)) {
            // Keep a gunner or archer's existing withdrawal path; this brief
            // shield use must not issue a new path toward the target.
        } else if (SoldierOrder.isHoldingPosition(human)
                || (RangedWeaponCustody.controlsMainHand(human)
                && HumanUtil.isRangedWeapon(human.getMainHandItem()))) {
            // A shield may interrupt the shot, but it must not replace the
            // archer's retreat path with a path straight toward the attacker.
            human.getNavigation().stop();
        } else if (advancingMelee) {
            // A recent hit may raise the shield, but it must not pin a melee
            // fighter just outside its reach. Keep closing while guarded and
            // let the normal counter window interrupt the block once in range.
            if (defenseMovementTarget != threat
                    || human.getNavigation().isDone() || human.getNavigation().isStuck()
                    || human.tickCount % 8 == 0) {
                human.getNavigation().moveTo(threat, 1.0D);
            }
            defenseMovementTarget = threat;
            MovementSpeedController.combat(human, true);
        } else if (awaitingProjectile) {
            // Melee units keep pressing the active threat while briefly
            // blocking a predicted projectile instead of freezing in place.
            if (human.getNavigation().isDone() || human.getNavigation().isStuck()) {
                human.getNavigation().moveTo(threat, 1.0D);
            }
        } else if (human.distanceToSqr(threat) > 12.25D) {
            human.getNavigation().moveTo(threat, 1.0D);
        } else {
            human.getNavigation().stop();
        }
        if (!human.isUsingItem()) {
            startShieldBlockIfAvailable(advancingMelee);
        }
    }

    private void updateDefensePressure() {
        int hurtTimestamp = human.getLastHurtByMobTimestamp();
        if (hurtTimestamp != defenseObservedHurtTimestamp) {
            defenseObservedHurtTimestamp = hurtTimestamp;
            lastShieldTriggerHurtTimestamp = hurtTimestamp;
            defensePressureTick = human.tickCount;
        }
        int gunfireUntil = HumanGunner.recentGunfireUntil(human);
        if (gunfireUntil >= human.tickCount && gunfireUntil != defenseObservedGunfireUntil) {
            defenseObservedGunfireUntil = gunfireUntil;
            lastShieldTriggerGunfireUntil = gunfireUntil;
            defensePressureTick = human.tickCount;
        }
    }

    /** Predict an arrow crossing the defender's future collision box, not merely a nearby shot. */
    private IncomingArrow findIncomingArrow() {
        if (!canPreemptivelyBlockProjectile() || isCloseMeleeEngagement()
                || (isDedicatedRangedCombatant() && human.tickCount < nextProjectileGuardTick)) {
            return null;
        }
        IncomingArrow closest = null;
        double earliestImpact = Double.MAX_VALUE;
        AABB search = human.getBoundingBox().inflate(20.0D, 8.0D, 20.0D);
        for (AbstractArrow arrow : human.level().getEntitiesOfClass(AbstractArrow.class, search)) {
            if (!arrow.isAlive() || arrow.getOwner() == human
                    || (arrow.getOwner() instanceof LivingEntity shooter
                    && HumanRelations.allied(human, shooter))) {
                continue;
            }
            Vec3 velocity = arrow.getDeltaMovement();
            double speedSqr = velocity.lengthSqr();
            if (speedSqr < 0.04D) {
                continue;
            }
            double ticksToImpact = human.getBoundingBox().getCenter()
                    .subtract(arrow.position()).dot(velocity) / speedSqr;
            if (ticksToImpact < 0.0D || ticksToImpact > 12.0D
                    || ticksToImpact >= earliestImpact) {
                continue;
            }
            if (isDedicatedRangedCombatant()
                    && !ProjectileShieldPolicy.shouldGuardRangedUser(ticksToImpact)) {
                continue;
            }
            if (shouldDeferRangedProjectileGuard()) {
                continue;
            }
            // Approximate vanilla arrow gravity and the defender's present motion.
            // The enlarged box tolerates trajectory variance without reacting to
            // arrows that are merely passing through the same nearby area.
            Vec3 impact = arrow.position().add(velocity.scale(ticksToImpact))
                    .add(0.0D, -0.025D * ticksToImpact * ticksToImpact, 0.0D);
            AABB futureBox = human.getBoundingBox()
                    .move(human.getDeltaMovement().scale(ticksToImpact))
                    .inflate(0.8D, 0.6D, 0.8D);
            if (!futureBox.contains(impact)
                    || human.level().clip(new ClipContext(arrow.position(), impact,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, human)).getType()
                    != HitResult.Type.MISS) {
                continue;
            }
            UUID projectileId = arrow.getUUID();
            Boolean shouldBlock = arrowDefenseDecisions.get(projectileId);
            if (shouldBlock == null) {
                shouldBlock = human.getRandom().nextFloat() < projectileDefenseChance();
                arrowDefenseDecisions.put(projectileId, shouldBlock);
                if (arrowDefenseDecisions.size() > 128) {
                    arrowDefenseDecisions.remove(arrowDefenseDecisions.keySet().iterator().next());
                }
            }
            if (!shouldBlock) {
                continue;
            }
            closest = new IncomingArrow(arrow,
                    arrow.getOwner() instanceof LivingEntity shooter ? shooter : null,
                    ticksToImpact);
            earliestImpact = ticksToImpact;
        }
        return closest;
    }

    private float projectileDefenseChance() {
        if (TierThreeHuman.isTierThree(human)) {
            return 0.80F;
        }
        return switch (human.getTier()) {
            case ROAMER -> 0.40F;
            case LEVEL1 -> 0.55F;
            case LEVEL2 -> 0.70F;
        };
    }

    private boolean isCloseMeleeEngagement() {
        if (!HumanUtil.isMeleeWeapon(human.getMainHandItem())) {
            return false;
        }
        LivingEntity current = human.getTarget();
        LivingEntity attacker = human.getLastHurtByMob();
        return (isValidThreat(current) && human.distanceToSqr(current) <= 36.0D)
                || (isValidThreat(attacker) && human.distanceToSqr(attacker) <= 36.0D);
    }

    private boolean isDedicatedRangedCombatant() {
        return GunCustody.hasOwnedGun(human)
                || RangedWeaponCustody.controlsMainHand(human)
                || isRangedCombat()
                || human.getMainHandItem().getItem() instanceof TridentItem
                || human.getOffhandItem().getItem() instanceof TridentItem;
    }

    private boolean isCrossbowCombatant() {
        return RangedWeaponCustody.isCrossbowWeapon(human.getMainHandItem())
                || RangedWeaponCustody.isCrossbowWeapon(human.getOffhandItem());
    }

    private boolean canPreemptivelyBlockProjectile() {
        if (!hasShieldInHands()) {
            return false;
        }
        return !isHoldingTaczGun()
                || SpartanEquipmentCompat.isShield(human.getOffhandItem());
    }

    private boolean shouldDeferRangedProjectileGuard() {
        if (!isDedicatedRangedCombatant()) {
            return false;
        }
        if (human.isFleeing
                || (GunCustody.hasOwnedGun(human) && GunSupport.get().isReloading(human))
                || (human.isUsingItem() && !SpartanEquipmentCompat.isShield(human.getUseItem()))) {
            return true;
        }
        LivingEntity target = human.getTarget();
        double dangerRange = isCrossbowCombatant() ? 16.0D : 12.0D;
        if (GunSupport.get().isGun(human.getMainHandItem())) {
            dangerRange = Math.min(12.0D, GunRangePolicy.forType(
                    GunSupport.get().gunType(human.getMainHandItem())).minimum());
        }
        double dangerRangeSqr = dangerRange * dangerRange;
        return isValidThreat(target) && human.distanceToSqr(target) <= dangerRangeSqr;
    }

    private boolean predictsIncomingMelee(LivingEntity attacker) {
        if (!isValidThreat(attacker)
                || isRangedThreat(attacker)
                || !human.getSensing().hasLineOfSight(attacker)) {
            return false;
        }
        double distanceSqr = human.distanceToSqr(attacker);
        if (distanceSqr > 12.25D) {
            return false;
        }
        Vec3 towardHuman = human.position().subtract(attacker.position()).multiply(1.0D, 0.0D, 1.0D);
        Vec3 direction = towardHuman.lengthSqr() < 0.01D
                ? attacker.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize()
                : towardHuman.normalize();
        Vec3 look = attacker.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        Vec3 movement = attacker.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
        boolean facingHuman = look.lengthSqr() > 0.01D && look.normalize().dot(direction) >= 0.60D;
        return CombatPressurePolicy.predictsIncomingMelee(
                distanceSqr, attacker.swinging, hasMeleeIntent(attacker) || attacker.swinging,
                facingHuman, movement.dot(direction)
        );
    }

    private double radialApproachSpeed(LivingEntity attacker) {
        Vec3 towardHuman = human.position().subtract(attacker.position()).multiply(1.0D, 0.0D, 1.0D);
        if (towardHuman.lengthSqr() < 0.01D) {
            return 0.0D;
        }
        return attacker.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D)
                .dot(towardHuman.normalize());
    }

    private boolean hasMeleeIntent(LivingEntity attacker) {
        if (attacker instanceof Mob mob) {
            return mob.getTarget() == human;
        }
        return attacker.getLastHurtMob() == human
                && attacker.tickCount - attacker.getLastHurtMobTimestamp() <= 40;
    }

    private void tickRetreat() {
        MovementSpeedController.retreat(human, true);
        double distanceSqr = human.distanceToSqr(threat);
        boolean recentlyHit = human.getLastHurtByMob() != null
                && human.tickCount - human.getLastHurtByMobTimestamp() <= 6;
        boolean mayUseSupply = RetreatRecoveryPolicy.canStart(human, threat);
        tickMovingRecovery(mayUseSupply, recentlyHit);
        if (retreatSafe() && recoverySession == null
                && !CombatFoodRecoveryGoal.isActive(human)) {
            burstDamageRetreatUntil = 0;
            phaseTicks = 0;
            human.getPersistentData().putString("humangunner:ai_phase", "retreat_safe_to_return");
            return;
        }
        boolean counterfiring = tickRetreatCounterattack();
        updateRetreatProgress();
        boolean pathFailed = activePath == null
                || human.getNavigation().isDone()
                || human.getNavigation().isStuck()
                || retreatStagnantTicks >= 10;
        if (pathFailed && repathTicks <= 0
                && (!counterfiring || retreatStagnantTicks >= 10)) {
            planRetreat();
        }
    }

    private boolean retreatSafe() {
        if (threat == null) return true;
        int quietTicks = human.getLastHurtByMob() == null
                ? Integer.MAX_VALUE
                : Math.max(0, human.tickCount - human.getLastHurtByMobTimestamp());
        if (HumanGunner.recentGunfireUntil(human) >= human.tickCount) {
            quietTicks = 0;
        }
        return RetreatRecoveryPolicy.safeToReturn(human.distanceToSqr(threat),
                quietTicks, human.tickCount - retreatStartedTick);
    }

    private boolean tickRetreatCounterattack() {
        if (threat == null
                || !threat.isAlive()
                || !human.getSensing().hasLineOfSight(threat)) {
            setRetreatBackpedaling(false);
            gunOperator.aim(false);
            retreatAimTicks = 0;
            return false;
        }
        double distanceSqr = human.distanceToSqr(threat);
        if (human.isUsingItem()) {
            boolean closeShieldBlock = SpartanEquipmentCompat.isShield(human.getUseItem())
                    && distanceSqr <= 16.0D;
            boolean staleTridentUse = human.getUseItem().getItem() instanceof TridentItem;
            if (closeShieldBlock || staleTridentUse) {
                human.stopUsingItem();
            } else {
                return false;
            }
        }
        boolean crowdCounterfire = CombatPressurePolicy.shouldUseCrowdCounterfire(
                GunCustody.hasOwnedGun(human), crowdPressureActive(),
                human.getSensing().hasLineOfSight(threat), distanceSqr
        );
        if (crowdCounterfire) {
            prepareCrowdRetreatGun();
            ItemStack crowdWeapon = human.getMainHandItem();
            if (club.someoneice.humangunner.GunSupport.get().isGun(crowdWeapon)) {
                return tickRetreatGunfire(crowdWeapon);
            }
        }
        if (distanceSqr >= 16.0D) {
            restoreRetreatGun();
        }

        if (distanceSqr <= 4.0D) {
            setRetreatBackpedaling(false);
            gunOperator.aim(false);
            retreatAimTicks = 0;
            if (club.someoneice.humangunner.GunSupport.get().isGun(human.getMainHandItem())) {
                switchToRetreatMelee();
                ItemStack fallback = human.getMainHandItem();
                if (club.someoneice.humangunner.GunSupport.get().isGun(fallback)) {
                    // Some loadouts have no melee fallback; do not turn a
                    // point-blank pursuer into a reason to stop returning fire.
                    return tickRetreatGunfire(fallback);
                }
            }
            performRetreatMeleeCounter();
            return false;
        }

        ItemStack weapon = human.getMainHandItem();
        int retreatTicks = human.tickCount - retreatStartedTick;
        if (weapon.getItem() instanceof TridentItem
                && human.tickCount >= nextRetreatTridentTick
                && CombatPressurePolicy.shouldUseRetreatCounterfire(
                distanceSqr, retreatTicks, 2.0D, 36.0D, 12
        )) {
            float distanceFactor = Mth.clamp(
                    (float) (Math.sqrt(distanceSqr) / 36.0D), 0.1F, 1.0F
            );
            human.performRangedAttackTrident(threat, distanceFactor);
            nextRetreatTridentTick = human.tickCount + 14;
            return true;
        }

        if (club.someoneice.humangunner.GunSupport.get().isGun(weapon)
                && CombatPressurePolicy.shouldUseRetreatCounterfire(
                distanceSqr, retreatTicks, 2.0D, 64.0D, 12
        )) {
            // Keep firing while the attacker closes instead of leaving the
            // two-to-eight-block gap as a silent, uninterrupted retreat.
            return tickRetreatGunfire(weapon);
        }

        setRetreatBackpedaling(false);
        gunOperator.aim(false);
        retreatAimTicks = 0;
        return false;
    }

    private boolean tickRetreatGunfire(ItemStack weapon) {
        GunAttackMovementState.markAttackActive(human);
        boolean safeBackpedal = hasSafeBackpedalStep();
        if (safeBackpedal) {
            setRetreatBackpedaling(true);
            // Keep a valid escape path while shooting. Only use a manual
            // backpedal when pathfinding has no route to follow.
            if (human.getNavigation().isDone()) {
                human.getMoveControl().strafe(-0.65F, 0.0F);
            }
        } else {
            setRetreatBackpedaling(false);
        }
        MovementSpeedController.retreat(human, false);
        gunOperator.clearSprintLock();
        if (retreatAimTicks == 0) {
            gunOperator.draw();
        }
        if (human.distanceToSqr(threat) <= 16.0D) {
            gunOperator.prepareUrgentShot();
        }
        gunOperator.aim(true);
        Vec3 origin = human.getEyePosition();
        double leadTicks = Mth.clamp(origin.distanceTo(threat.getEyePosition()) / 5.0D, 0.0D, 5.0D);
        Vec3 aimPoint = threat.getEyePosition().add(threat.getDeltaMovement().scale(leadTicks));
        Vec3 delta = aimPoint.subtract(origin);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float exactYaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float exactPitch = (float) -(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        double spread = RangedAccuracy.gunSpreadDegrees(human, weapon);
        double limit = spread * 2.5D;
        float pitch = exactPitch + (float) Mth.clamp(human.getRandom().nextGaussian() * spread, -limit, limit);
        float yaw = exactYaw + (float) Mth.clamp(human.getRandom().nextGaussian() * spread, -limit, limit);
        human.setXRot(exactPitch);
        human.setYRot(exactYaw);
        human.setYBodyRot(exactYaw);
        human.setYHeadRot(exactYaw);
        if (++retreatAimTicks < 2) {
            return true;
        }
        gunOperator.shoot(weapon, pitch, yaw);
        return true;
    }

    private boolean hasSafeBackpedalStep() {
        Vec3 away = human.position().subtract(threat.position()).multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() < 0.01D) {
            return false;
        }
        Vec3 destination = human.position().add(away.normalize().scale(2.0D));
        Path path = human.getNavigation().createPath(BlockPos.containing(destination), 0);
        return path != null && path.canReach();
    }

    private void setRetreatBackpedaling(boolean backpedaling) {
        human.getPersistentData().putBoolean("humangunner:retreat_backpedaling", backpedaling);
        if (!backpedaling) {
            return;
        }
        human.setSprinting(false);
    }

    private void performRetreatMeleeCounter() {
        if (!(HumanUtil.isMeleeWeapon(human.getMainHandItem())
                || human.getMainHandItem().getItem() instanceof TridentItem)
                || human.tickCount < nextRetreatMeleeTick) {
            return;
        }
        human.swing(InteractionHand.MAIN_HAND);
        human.doHurtTarget(threat);
        nextRetreatMeleeTick = human.tickCount + MeleeAttackTiming.nextCooldown(human);
        human.getPersistentData().putString("humangunner:ai_phase", "retreat_melee_counterattack");
    }

    private void switchToRetreatMelee() {
        GunCustody.beginCloseMeleeCounter(human);
    }

    private void restoreRetreatGun() {
        // A short shield/recovery phase may pre-empt the dedicated gunner
        // close-defense goal. Preserve its 2-to-4-block hysteresis instead of
        // forcing the gun back for one tick and immediately swapping again.
        LivingEntity currentTarget = human.getTarget();
        if (GunCustody.isCloseDefenseActive(human)
                && currentTarget != null
                && currentTarget.isAlive()
                && human.distanceToSqr(currentTarget) < 16.0D) {
            return;
        }
        GunCustody.restorePrimaryGun(human, "adaptive_combat_exit");
    }

    private void tickRecover() {
        // Migrate an already-running legacy recovery phase into moving retreat.
        // This also prevents the post-potion stationary bug.
        phase = Phase.RETREAT;
        phaseTicks = Math.max(phaseTicks, 120);
        MovementSpeedController.retreat(human, true);
        human.getPersistentData().putString("humangunner:ai_phase", "retreat_recovering");
        if (activePath == null || human.getNavigation().isDone()) {
            planRetreat();
        }
        tickRetreat();
    }

    private void tickMovingRecovery(boolean mayStart, boolean recentlyHit) {
        if (CombatFoodRecoveryGoal.isActive(human)) {
            return;
        }
        if (recoverySession != null) {
            if (human.isUsingItem()) {
                boolean unsafeProximity = RetreatRecoveryPolicy.shouldBreakOffUse(
                        human.distanceToSqr(threat), human.getSensing().hasLineOfSight(threat),
                        human.getUseItemRemainingTicks()
                );
                if (unsafeProximity || (!RecoverySupplies.shouldCommitToUse(human, recoverySession, config)
                        && recentlyHit)) {
                    cancelRecoveryUse();
                    nextRecoveryUseTick = human.tickCount + 12;
                }
                return;
            }
            boolean completed = RecoverySupplies.completed(human, recoverySession);
            RecoverySupplies.finishUse(human, recoverySession, completed);
            recoverySession = null;
            nextRecoveryUseTick = human.tickCount + (completed ? 6 : 16);
            human.getPersistentData().putString(
                    "humangunner:ai_phase",
                    completed ? "retreat_recovery_chain" : "retreat_interrupted_use"
            );
            if (healthRatio() > config.criticalHealthRatio()
                    && projectedHealthRatio() >= config.resumeHealthRatio()
                    && retreatSafe()) {
                phaseTicks = 0;
            }
        }
        if (recoverySession == null
                && mayStart
                && human.tickCount >= nextRecoveryUseTick
                && projectedHealthRatio() < config.resumeHealthRatio()
                && RecoverySupplies.hasCombatRecoverySupply(human)) {
            recoverySession = RecoverySupplies.beginCombatRecoveryUse(human);
            if (recoverySession != null) {
                human.getPersistentData().putString("humangunner:ai_phase", "retreat_using_recovery_item");
            }
        }
    }

    private void tickFlank() {
        if (repathTicks <= 0 && (activePath == null || human.getNavigation().isDone())) {
            planTacticalMove();
        }
        jumpIfBlocked();
    }

    private void tickPressure() {
        if (human.distanceToSqr(threat) <= 12.25D) {
            phaseTicks = 0;
            return;
        }
        if (repathTicks <= 0 || activePath == null || human.getNavigation().isDone()) {
            planPressureMove();
        }
        jumpIfBlocked();
    }

    private void tickUnstuck() {
        if (human.onGround() && (human.horizontalCollision || human.getNavigation().isStuck())) {
            human.getJumpControl().jump();
        }
        if (repathTicks <= 0 || human.getNavigation().isDone()) {
            planUnstuckMove();
        }
    }

    private LivingEntity resolveThreat() {
        LivingEntity current = human.getTarget();
        LivingEntity attacker = human.getLastHurtByMob();
        int hurtTimestamp = human.getLastHurtByMobTimestamp();

        // Remember every real hit, but do not endlessly rotate toward the most
        // recent projectile owner. Repeated pressure from multiple attackers
        // starts a short focus window on the nearest remembered opponent.
        if (isValidThreat(attacker) && hurtTimestamp != lastProcessedHurtTimestamp) {
            lastProcessedHurtTimestamp = hurtTimestamp;
            recordIncomingHit(attacker);
            if (isValidThreat(current) && current != attacker) {
                rememberThreat(current);
            }
            if (isValidThreat(threat) && threat != attacker && threat != current) {
                rememberThreat(threat);
            }
            rememberThreat(attacker);

            LivingEntity locked = activeFocusedThreat();
            if (locked != null) {
                keepTarget(locked);
                return locked;
            }
            if (shouldStartFocusLock()) {
                LivingEntity nearest = nearestFocusCandidate(current, threat, attacker);
                if (nearest != null) {
                    beginFocusLock(nearest);
                    return nearest;
                }
            }
            if (current != attacker) {
                human.setTarget(attacker);
            }
            return applySurroundingThreatPriority(attacker);
        }

        LivingEntity locked = activeFocusedThreat();
        if (locked != null) {
            keepTarget(locked);
            return locked;
        }

        if (isValidThreat(current)) {
            if (isValidThreat(threat) && threat != current) {
                rememberThreat(threat);
            }
            rememberThreat(current);
            return applySurroundingThreatPriority(current);
        }

        // The legacy escape goal clears Mob#getTarget while retaining toAvoid.
        // Recover that threat so this goal can take over the retreat and use
        // supplies only after reaching its shared safe-distance gate.
        if (isValidThreat(human.toAvoid)) {
            human.setTarget(human.toAvoid);
            return applySurroundingThreatPriority(human.toAvoid);
        }

        LivingEntity remembered = recallThreat();
        if (remembered != null) {
            human.setTarget(remembered);
        }
        return applySurroundingThreatPriority(remembered);
    }

    /**
     * Preserves nearest-target behaviour when at least three threats are within
     * three blocks. Armed gunners also remember this as a short crowd-pressure
     * window so their higher-priority survival layer can restore the gun and
     * retreat instead of settling into shield-only close defense.
     */
    private LivingEntity applySurroundingThreatPriority(LivingEntity fallback) {
        LivingEntity locked = activeFocusedThreat();
        if (locked != null) {
            keepTarget(locked);
            return locked;
        }
        if (human.tickCount < nextCrowdScanTick) {
            return fallback;
        }
        nextCrowdScanTick = human.tickCount + 6;
        List<LivingEntity> closeEnemies = human.level().getEntitiesOfClass(
                LivingEntity.class,
                human.getBoundingBox().inflate(3.0D),
                this::isCrowdEnemy
        );
        if (closeEnemies.size() < 3) {
            return fallback;
        }
        if (CombatPressurePolicy.shouldPrioritizeGunnerRetreat(
                GunCustody.hasOwnedGun(human), closeEnemies.size()
        )) {
            crowdPressureUntil = Math.max(
                    crowdPressureUntil,
                    human.tickCount + CombatPressurePolicy.CROWD_PRESSURE_MEMORY_TICKS
            );
        }
        LivingEntity nearest = closeEnemies.stream()
                .min(java.util.Comparator.comparingDouble(human::distanceToSqr))
                .orElse(fallback);
        if (nearest != null && nearest != human.getTarget()) {
            if (isValidThreat(human.getTarget())) {
                rememberThreat(human.getTarget());
            }
            human.setTarget(nearest);
        }
        return nearest;
    }

    private boolean isCrowdEnemy(LivingEntity entity) {
        if (!isValidThreat(entity)) {
            return false;
        }
        if (entity == human.getTarget() || entity == human.getLastHurtByMob()) {
            return true;
        }
        if (entity instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        if (entity instanceof Human) {
            return true;
        }
        if (entity instanceof Mob mob && mob.getTarget() == human) {
            return true;
        }
        return entity.getLastHurtMob() == human
                && entity.tickCount - entity.getLastHurtMobTimestamp() <= 80;
    }

    private void recordIncomingHit(LivingEntity attacker) {
        incomingHits.addLast(new IncomingHit(attacker, human.tickCount));
        pruneIncomingHits();
    }

    private void pruneIncomingHits() {
        while (!incomingHits.isEmpty()
                && human.tickCount - incomingHits.peekFirst().hitAtTick() > FOCUS_PRESSURE_WINDOW_TICKS) {
            incomingHits.removeFirst();
        }
        incomingHits.removeIf(hit -> !isValidThreat(hit.attacker())
                || hit.attacker().level() != human.level());
    }

    private boolean shouldStartFocusLock() {
        pruneIncomingHits();
        if (incomingHits.size() < 3) {
            return false;
        }
        UUID first = null;
        for (IncomingHit hit : incomingHits) {
            UUID id = hit.attacker().getUUID();
            if (first == null) {
                first = id;
            } else if (!first.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private LivingEntity nearestFocusCandidate(LivingEntity... candidates) {
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            if (!isValidThreat(candidate)) {
                continue;
            }
            double distance = human.distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        for (IncomingHit hit : incomingHits) {
            LivingEntity candidate = hit.attacker();
            if (!isValidThreat(candidate)) {
                continue;
            }
            double distance = human.distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void beginFocusLock(LivingEntity target) {
        focusedThreat = target;
        focusLockUntil = human.tickCount + FOCUS_LOCK_TICKS;
        keepTarget(target);
        human.getPersistentData().putInt("humangunner:focus_target_until", focusLockUntil);
        human.getPersistentData().putUUID("humangunner:focus_target", target.getUUID());
    }

    private LivingEntity activeFocusedThreat() {
        if (focusedThreat == null) {
            return null;
        }
        if (human.tickCount >= focusLockUntil
                || !isValidThreat(focusedThreat)
                || focusedThreat.level() != human.level()
                || human.distanceToSqr(focusedThreat) > MAX_REMEMBERED_THREAT_DISTANCE_SQR) {
            clearFocusLock();
            return null;
        }
        return focusedThreat;
    }

    private void keepTarget(LivingEntity target) {
        if (human.getTarget() != target) {
            if (isValidThreat(human.getTarget())) {
                rememberThreat(human.getTarget());
            }
            human.setTarget(target);
        }
    }

    private void clearFocusLock() {
        focusedThreat = null;
        focusLockUntil = 0;
        human.getPersistentData().remove("humangunner:focus_target_until");
        human.getPersistentData().remove("humangunner:focus_target");
    }

    private void updateBurstDamageRetreat() {
        if (RecentDamageTracker.consumeRetreatTrigger(human)) {
            burstDamageRetreatUntil = Math.max(burstDamageRetreatUntil, human.tickCount + 100);
        }
    }

    private boolean burstDamageRetreatActive() {
        return human.tickCount < burstDamageRetreatUntil;
    }

    private boolean shouldUseBurstDamageRetreat() {
        // Bow/crossbow goals can now retreat while continuing their own draw
        // or charge state. Pre-empting them for the generic damage retreat
        // repeatedly cancelled crossbow charging under automatic gunfire.
        // Critical-health recovery still uses this goal via the health checks.
        return burstDamageRetreatActive() && !RangedWeaponCustody.controlsMainHand(human);
    }

    private boolean crowdPressureActive() {
        return human.tickCount < crowdPressureUntil;
    }

    private boolean shouldPrioritizeCrowdRetreat() {
        return crowdPressureActive() && GunCustody.hasOwnedGun(human);
    }

    private void prepareCrowdRetreatGun() {
        if (!shouldPrioritizeCrowdRetreat()) {
            return;
        }
        if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) {
            human.stopUsingItem();
        }
        GunCustody.restorePrimaryGun(human, "crowd_retreat");
        human.getPersistentData().putString("humangunner:ai_phase", "crowd_retreat");
    }

    private boolean canCounterFromCurrentRange() {
        if (threat == null || !human.getSensing().hasLineOfSight(threat)) {
            return false;
        }
        if (GunSupport.get().isGun(human.getMainHandItem())
                || HumanUtil.isRangedWeapon(human.getMainHandItem())
                || HumanUtil.isTrident(human.getMainHandItem())) {
            return human.distanceToSqr(threat) <= 784.0D;
        }
        return human.distanceToSqr(threat) <= meleeAttackReachSqr(threat);
    }

    private double meleeAttackReachSqr(LivingEntity target) {
        double reach = human.getBbWidth() * 3.0D;
        return reach * reach + target.getBbWidth();
    }

    private boolean shouldAdvanceToMeleeThreat() {
        return threat != null
                && threat.isAlive()
                && !GunCustody.hasOwnedGun(human)
                && !GunSupport.get().isGun(human.getMainHandItem())
                && !HumanUtil.isRangedWeapon(human.getMainHandItem())
                && !HumanUtil.isTrident(human.getMainHandItem())
                && !SoldierOrder.isHoldingPosition(human)
                && human.distanceToSqr(threat) > meleeAttackReachSqr(threat);
    }

    private boolean performShieldCounterattack() {
        if (threat == null || !threat.isAlive()) {
            return false;
        }
        ItemStack weapon = human.getMainHandItem();
        if (HumanUtil.isMeleeWeapon(weapon) || weapon.getItem() instanceof TridentItem) {
            return human.humanGunner$tryImmediateMeleeCounterattack(threat);
        }
        return false;
    }

    private void rememberThreat(LivingEntity entity) {
        if (!isValidThreat(entity)) {
            return;
        }
        UUID id = entity.getUUID();
        threatMemory.removeIf(entry -> entry.entity().getUUID().equals(id)
                || isThreatMemoryExpired(entry));
        threatMemory.addLast(new RememberedThreat(entity, human.tickCount));
        while (threatMemory.size() > MAX_REMEMBERED_THREATS) {
            threatMemory.removeFirst();
        }
    }

    private LivingEntity recallThreat() {
        threatMemory.removeIf(this::isThreatMemoryExpired);
        var iterator = threatMemory.descendingIterator();
        while (iterator.hasNext()) {
            RememberedThreat entry = iterator.next();
            LivingEntity candidate = entry.entity();
            if (isValidThreat(candidate)
                    && candidate.level() == human.level()
                    && human.distanceToSqr(candidate) <= MAX_REMEMBERED_THREAT_DISTANCE_SQR) {
                return candidate;
            }
            iterator.remove();
        }
        return null;
    }

    private boolean isThreatMemoryExpired(RememberedThreat entry) {
        return human.tickCount - entry.rememberedAtTick() > THREAT_MEMORY_TICKS
                || !isValidThreat(entry.entity())
                || entry.entity().level() != human.level();
    }

    private boolean isValidThreat(LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity == human) {
            return false;
        }
        if (HumanRelations.allied(human, entity)) {
            return false;
        }
        // This is also used by crowd scans and remembered threats, not only by
        // Mob#getTarget. Keep every tactical branch behind the same hired-mode
        // authorization gate as the ordinary target selectors.
        return human.canAttack(entity);
    }

    private void updateCombatSession(LivingEntity currentThreat) {
        UUID current = currentThreat.getUUID();
        if (combatTarget == null || human.tickCount - lastThreatTick > 200) {
            combatTarget = current;
            stationaryChecks = 0;
            nextTacticalTick = human.tickCount + human.getRandom().nextInt(
                    Math.max(1, config.tacticalRepositionInterval())
            );
        } else {
            combatTarget = current;
        }
        lastThreatTick = human.tickCount;
    }

    private void resetCombatSession() {
        if (human.tickCount - lastThreatTick > 200) {
            combatTarget = null;
            stationaryChecks = 0;
            clearFocusLock();
            incomingHits.clear();
        }
    }

    private void updateStationaryState() {
        Vec3 position = human.position();
        if (!human.getNavigation().isDone() && position.distanceToSqr(lastObservedPosition) < 0.0225D) {
            stationaryChecks++;
        } else {
            stationaryChecks = 0;
        }
        lastObservedPosition = position;
    }

    private void planRetreat() {
        human.getNavigation().stop();
        PlannedPath plan = chooseRetreatPath();
        if (plan != null) {
            startPath(plan, 1.0D);
        } else {
            int searchRadius = retreatSearchRadius();
            double currentDistanceSqr = human.distanceToSqr(threat);
            for (int attempt = 0; attempt < 8; attempt++) {
                Vec3 fallback = DefaultRandomPos.getPosAway(human, searchRadius, 7, threat.position());
                if (fallback != null && RetreatRecoveryPolicy.isUsefulRetreatDestination(
                        currentDistanceSqr, threat.distanceToSqr(fallback))
                        && moveToCandidate(fallback, 1.0D)) {
                    break;
                }
            }
        }
        retreatStagnantTicks = 0;
        lastRetreatPosition = human.position();
        repathTicks = 14;
    }

    private PlannedPath chooseRetreatPath() {
        PlannedPath best = null;
        double currentDistance = human.distanceToSqr(threat);
        Vec3 desiredAway = NavigationSupport.horizontalDirection(threat.position(), human.position());
        int searchRadius = retreatSearchRadius();
        for (int i = 0; i < config.coverSearchAttempts(); i++) {
            Vec3 candidate = DefaultRandomPos.getPosAway(
                    human,
                    searchRadius,
                    7,
                    threat.position()
            );
            if (candidate == null || !RetreatRecoveryPolicy.isUsefulRetreatDestination(
                    currentDistance, threat.distanceToSqr(candidate))) {
                continue;
            }
            Path path = human.getNavigation().createPath(BlockPos.containing(candidate), 0);
            if (path == null || !path.canReach()
                    || !NavigationSupport.pathStartsGenerallyToward(path, human.position(), desiredAway)) {
                continue;
            }
            Vec3 reachableEnd = Vec3.atCenterOf(path.getTarget());
            double distance = threat.position().distanceTo(reachableEnd);
            Vec3 candidateDirection = NavigationSupport.horizontalDirection(human.position(), reachableEnd);
            double directionalCommitment = Math.max(-1.0D, candidateDirection.dot(desiredAway));
            boolean sightBroken = !hasClearRay(
                    threat.getEyePosition(),
                    reachableEnd.add(0.0D, human.getEyeHeight(), 0.0D)
            );
            double score = distance * 4.0D
                    + directionalCommitment * 35.0D
                    + (sightBroken ? 24.0D : 0.0D)
                    - path.getNodeCount() * 0.65D
                    - Math.abs(reachableEnd.y - human.getY()) * 2.0D;
            if (best == null || score > best.score()) {
                best = new PlannedPath(path, reachableEnd, score);
            }
        }
        return best;
    }

    private int retreatSearchRadius() {
        return RetreatRecoveryPolicy.shouldForceRetreat(healthRatio())
                ? Math.max(24, config.coverSearchRadius()) : config.coverSearchRadius();
    }

    private void planTacticalMove() {
        PlannedPath plan = chooseTacticalPath();
        if (plan != null) {
            startPath(plan, 1.0D);
        } else if (human.distanceTo(threat) < preferredMinimumRange()) {
            Vec3 fallback = DefaultRandomPos.getPosAway(human, 12, 5, threat.position());
            if (fallback != null) {
                moveToCandidate(fallback, 1.0D);
            }
        } else {
            human.getNavigation().moveTo(threat, 1.0D);
        }
        repathTicks = 7;
    }

    private void planUnstuckMove() {
        human.getNavigation().stop();
        Vec3 candidate = DefaultRandomPos.getPos(human, 10, 5);
        if (candidate != null) {
            moveToCandidate(candidate, 1.0D);
        } else {
            Vec3 away = human.position().subtract(threat.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
            human.getMoveControl().setWantedPosition(
                    human.getX() + away.x * 5.0D,
                    human.getY() + 1.0D,
                    human.getZ() + away.z * 5.0D,
                    1.0D
            );
        }
        if (human.onGround()) {
            human.getJumpControl().jump();
        }
        repathTicks = 5;
    }

    private void planPressureMove() {
        Path direct = human.getNavigation().createPath(threat, 1);
        if (direct != null && direct.canReach()) {
            activePath = direct;
            human.getNavigation().moveTo(direct, 1.0D);
        } else {
            Vec3 candidate = DefaultRandomPos.getPosTowards(human, 8, 4, threat.position(), 0.55D);
            if (candidate == null || !moveToCandidate(candidate, 1.0D)) {
                human.getNavigation().moveTo(threat, 1.0D);
            }
        }
        repathTicks = 12;
    }

    private PlannedPath chooseTacticalPath() {
        PlannedPath best = null;
        PathNavigation navigation = human.getNavigation();
        for (int i = 0; i < config.coverSearchAttempts(); i++) {
            Vec3 candidate = DefaultRandomPos.getPos(human, Math.min(14, config.coverSearchRadius()), 6);
            if (candidate == null) {
                continue;
            }
            Path path = navigation.createPath(BlockPos.containing(candidate), 0);
            if (path == null) {
                continue;
            }
            double candidateThreatDistance = threat.distanceToSqr(candidate);
            boolean firingLane = hasClearRay(candidate.add(0.0D, human.getEyeHeight(), 0.0D), threat.getEyePosition());
            double score = candidateThreatDistance * 0.015D;
            score += firingLane ? 45.0D : 0.0D;
            score -= Math.abs(candidate.y - human.getY()) * 2.0D;
            score -= countAlliesNear(candidate, config.allySpacingRadius()) * 18.0D;
            if (best == null || score > best.score()) {
                best = new PlannedPath(path, candidate, score);
            }
        }
        return best;
    }

    private void startPath(PlannedPath plan, double speed) {
        activePath = plan.path();
        human.getNavigation().moveTo(activePath, speed);
    }

    private boolean moveToCandidate(Vec3 candidate, double speed) {
        Path path = human.getNavigation().createPath(BlockPos.containing(candidate), 0);
        if (path == null) {
            return false;
        }
        activePath = path;
        return human.getNavigation().moveTo(path, speed);
    }

    private boolean hasClearRay(Vec3 from, Vec3 to) {
        return human.level().clip(new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                human
        )).getType() == HitResult.Type.MISS;
    }

    private int countNearbyAllies(double radius) {
        return countAlliesNear(human.position(), radius);
    }

    private int countAlliesNear(Vec3 position, double radius) {
        if (radius <= 0.0D) {
            return 0;
        }
        AABB box = new AABB(position, position).inflate(radius);
        List<Human> nearby = human.level().getEntitiesOfClass(
                Human.class,
                box,
                other -> other != human && HumanRelations.allied(human, other)
        );
        return nearby.size();
    }

    private boolean jumpIfBlocked() {
        Path path = human.getNavigation().getPath();
        if (human.onGround()
                && human.tickCount >= nextTerrainJumpTick
                && path != null
                && !path.isDone()
                && path.getNextNodePos().getY() > Mth.floor(human.getY() + 0.1D)
                && human.distanceToSqr(Vec3.atCenterOf(path.getNextNodePos())) <= 12.25D) {
            human.getJumpControl().jump();
            nextTerrainJumpTick = human.tickCount + 7;
            return true;
        }
        return false;
    }

    private void updateRetreatProgress() {
        double movedSqr = human.position().distanceToSqr(lastRetreatPosition);
        if (!human.getNavigation().isDone() && movedSqr < 0.0064D) {
            retreatStagnantTicks++;
        } else if (movedSqr >= 0.0064D) {
            retreatStagnantTicks = 0;
        }
        lastRetreatPosition = human.position();
        if (human.horizontalCollision || retreatStagnantTicks >= 4) {
            NavigationSupport.openBlockingPassage(human);
        }
    }

    private void startShieldBlockIfAvailable(boolean movingToMelee) {
        if (human.isUsingItem()) return;
        InteractionHand shieldHand = SpartanEquipmentCompat.isShield(human.getOffhandItem())
                ? InteractionHand.OFF_HAND
                : SpartanEquipmentCompat.isShield(human.getMainHandItem())
                ? InteractionHand.MAIN_HAND : null;
        if (shieldHand == null) return;
        if (defendingProjectile && human.tickCount < projectileDefenseUntil) {
            human.humanGunner$startProjectileShield(shieldHand);
        } else if (movingToMelee) {
            human.humanGunner$startMovementShield(shieldHand);
        } else {
            human.startUsingItem(shieldHand);
        }
    }

    private boolean hasShieldInHands() {
        return SpartanEquipmentCompat.isShield(human.getOffhandItem())
                || SpartanEquipmentCompat.isShield(human.getMainHandItem());
    }

    private boolean canBlockWithShield() {
        return hasShieldInHands() && !isHoldingTaczGun();
    }

    private int rollShieldBlockDuration() {
        int minimum = config.shieldBlockDurationMinTicks();
        int span = Math.max(1, config.shieldBlockDurationMaxTicks() - minimum + 1);
        return minimum + human.getRandom().nextInt(span);
    }

    private void cancelRecoveryUse() {
        if (recoverySession == null) {
            return;
        }
        boolean completed = RecoverySupplies.completed(human, recoverySession);
        RecoverySupplies.finishUse(human, recoverySession, completed);
        recoverySession = null;
    }

    private void setFleeing(boolean fleeing) {
        human.isFleeing = fleeing;
        ownsFleeFlag = fleeing;
    }

    private double healthRatio() {
        return human.getHealth() / Math.max(1.0F, human.getMaxHealth());
    }

    private double projectedHealthRatio() {
        return RecoverySupplies.projectedHealth(human) / Math.max(1.0F, human.getMaxHealth());
    }

    private boolean isRangedCombat() {
        if (club.someoneice.humangunner.GunSupport.get().isGun(human.getMainHandItem())) {
            return true;
        }
        // Tridents and bow/crossbow users now own their distance transitions.
        // Do not stack this higher-priority random FLANK layer on top: the
        // native bow goal already performs measured backward strafing, while
        // custody switches to melee at five blocks and back at six.
        return (human.getMainHandItem().getItem() instanceof ProjectileWeaponItem
                && !RangedWeaponCustody.isBowOrCrossbow(human.getMainHandItem()))
                || (human.getOffhandItem().getItem() instanceof ProjectileWeaponItem
                && !RangedWeaponCustody.isBowOrCrossbow(human.getOffhandItem()));
    }

    private boolean isUsingTaczGun() {
        return club.someoneice.humangunner.GunSupport.get().isGun(human.getMainHandItem());
    }

    private boolean isHoldingTaczGun() {
        return club.someoneice.humangunner.GunSupport.get().isGun(human.getMainHandItem())
                || club.someoneice.humangunner.GunSupport.get().isGun(human.getOffhandItem());
    }

    private boolean isGunThreat(LivingEntity target) {
        return club.someoneice.humangunner.GunSupport.get().isGun(target.getMainHandItem())
                || club.someoneice.humangunner.GunSupport.get().isGun(target.getOffhandItem());
    }

    private boolean isRangedThreat(LivingEntity target) {
        // A stowed ranged weapon in the off-hand does not make a sword user a
        // ranged attacker while the active main hand is clearly melee.
        if (HumanUtil.isMeleeWeapon(target.getMainHandItem())) {
            return false;
        }
        if (isGunThreat(target)) {
            return true;
        }
        return target.getMainHandItem().getItem() instanceof ProjectileWeaponItem
                || target.getOffhandItem().getItem() instanceof ProjectileWeaponItem
                || target.getMainHandItem().getItem() instanceof TridentItem
                || target.getOffhandItem().getItem() instanceof TridentItem;
    }

    private double preferredMinimumRange() {
        if (club.someoneice.humangunner.GunSupport.get().isGun(human.getMainHandItem())) {
            return 8.0D;
        }
        return human.getMainHandItem().getItem() instanceof TridentItem ? 6.0D : 5.0D;
    }
}

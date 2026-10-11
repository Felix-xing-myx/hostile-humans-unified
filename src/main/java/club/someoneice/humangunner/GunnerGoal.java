package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.HumanUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public final class GunnerGoal<T extends PathfinderMob> extends Goal {
    private static final double RETREAT_RANGE = 4.0D;
    private static final double CLOSE_MELEE_ENTER_RANGE = 2.0D;
    private static final double CLOSE_MELEE_EXIT_RANGE = 4.0D;
    private static final double OBSCURED_ADVANCE_DISTANCE = 10.0D;
    private static final int OBSCURED_ADVANCE_AFTER_TICKS = 30;
    private static final int MIN_AIM_TICKS = 2;
    private static final double MAX_LEAD_TICKS = 8.0D;
    private final T mob;
    private final GunSupport.Control operator;
    private int aimTicks;
    private int unseenTargetTicks;
    private int nextCloseMeleeAttackTick;
    private int nextCloseRetreatPathTick;
    private int nextRangeRetreatPathTick;
    private int nextFiringPositionTick;
    private int nextApproachPathTick;
    private int nextStrafeRepositionTick;
    private int manualStrafeUntilTick;
    private int strafeLegStartTick;
    private double strafeLegStartX;
    private double strafeLegStartZ;
    private double strafeAnchorX;
    private double strafeAnchorZ;
    private double strafeAnchorTargetX;
    private double strafeAnchorTargetZ;
    private boolean strafeAnchorSet;
    private float strafeDirection = 1.0F;
    private int retreatFailureTicks;
    private boolean retreatSearchQueued;
    private double lastThreatDistance = Double.POSITIVE_INFINITY;
    private boolean retreatingForSpace;
    private boolean approachingTarget;
    private boolean strafing;
    private boolean strafePathActive;
    private boolean seekingFiringPosition;
    private LivingEntity movementTarget;

    public GunnerGoal(T mob) {
        this.mob = mob;
        this.operator = mob instanceof Human human ? GunSupport.get().control(human) : GunSupport.Control.NONE;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mob.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return false;
        boolean shoreSeeking = isShoreSeeking();
        LivingEntity target = mob.getTarget();
        if (mob instanceof Human human
                && target instanceof Player player
                && HumanRelations.allied(human, player)) {
            mob.setTarget(null);
            return false;
        }
        if (mob instanceof Human attacker
                && target instanceof Human teammate
                && HumanRelations.allied(attacker, teammate)) {
            mob.setTarget(null);
            return false;
        }
        boolean closeDefense = mob instanceof Human human
                && GunCustody.isCloseDefenseActive(human);
        if (mob instanceof Human human
                && !AmmoWeaponSelection.usableGun(human, human.getMainHandItem())
                && (!closeDefense || !AmmoWeaponSelection.hasUsableGun(human))) return false;
        if (target == null || !target.isAlive()
                || (!isGun(mob.getMainHandItem()) && !closeDefense)) {
            return false;
        }
        if (mob instanceof Human human && human.isFleeing && !shoreSeeking) {
            return false;
        }
        double distanceSqr = mob.distanceToSqr(target);
        double fireRangeSqr = firingRangeSqr(preferredRange(mob.getMainHandItem()));
        return distanceSqr <= fireRangeSqr
                || (mob instanceof Human human && target instanceof Player player
                && HumanRelationshipPolicy.shouldApproachBeaconPlayerWithoutFiring(
                        HumanRelations.isForcedHostileTo(human, player), distanceSqr,
                        fireRangeSqr));
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        aimTicks = 0;
        unseenTargetTicks = 0;
        retreatFailureTicks = 0;
        retreatSearchQueued = false;
        lastThreatDistance = Double.POSITIVE_INFINITY;
        retreatingForSpace = false;
        approachingTarget = false;
        strafing = false;
        strafePathActive = false;
        manualStrafeUntilTick = 0;
        strafeAnchorSet = false;
        seekingFiringPosition = false;
        strafeDirection = mob.getRandom().nextBoolean() ? 1.0F : -1.0F;
        nextStrafeRepositionTick = mob.tickCount;
        strafeLegStartTick = mob.tickCount;
        strafeLegStartX = mob.getX();
        strafeLegStartZ = mob.getZ();
        nextFiringPositionTick = mob.tickCount;
        nextApproachPathTick = mob.tickCount;
        movementTarget = mob.getTarget();
        if (isGun(mob.getMainHandItem())) {
            operator.ensureDrawn();
        }
    }

    @Override
    public void stop() {
        if (mob instanceof Human human) {
            BasicNavigationBudget.cancel(human);
            RetreatRouteContinuity.cancelSearch(human);
        }
        retreatSearchQueued = false;
        if (mob instanceof Human human) RangedFiringPosition.cancelVisibleSearch(human);
        if (mob instanceof Human human && human.isCombatFiringShore()) {
            human.stopSeekingShore();
        }
        if (isShoreSeeking()) {
            clearLateralInputPreservingNavigation();
        } else {
            stopLateralMovement();
        }
        aimTicks = 0;
        unseenTargetTicks = 0;
        retreatingForSpace = false;
        approachingTarget = false;
        seekingFiringPosition = false;
        strafePathActive = false;
        manualStrafeUntilTick = 0;
        strafeAnchorSet = false;
        movementTarget = null;
        setAiming(false);
        if (mob instanceof Human human) {
            MovementSpeedController.combat(human, false);
            GunAttackMovementState.clear(human);
        }
        operator.clearSprintLock();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public EnumSet<Flag> getFlags() {
        if (isShoreSeeking() && mob instanceof Human human && !human.isCombatFiringShore()) {
            return EnumSet.of(Flag.LOOK);
        }
        return EnumSet.of(Flag.MOVE, Flag.LOOK);
    }

    @Override
    public void tick() {
        if (mob instanceof Human human) {
            // PlayerLikeMovementGoal runs at a lower goal priority and reads
            // this short-lived hint to suppress combat hops while aiming.
            GunAttackMovementState.clear(human);
        }
        LivingEntity target = mob.getTarget();
        ItemStack weapon = mob.getMainHandItem();
        boolean closeDefense = mob instanceof Human human
                && GunCustody.isCloseDefenseActive(human);
        if (target != null && mob instanceof Human human && !human.canAttack(target)) {
            human.setTarget(null);
            setAiming(false);
            return;
        }
        if (target == null || !target.isAlive()
                || (!isGun(weapon) && !closeDefense)) {
            return;
        }
        if (mob instanceof Human human) {
            human.updateCombatFiringShore(target, 1.0D);
        }
        if (movementTarget != target) {
            if (!ShoreSeekingPolicy.shouldMoveTowardCombatTarget(isShoreSeeking())) {
                clearLateralInputPreservingNavigation();
            } else {
                stopLateralMovement();
                mob.getNavigation().stop();
            }
            movementTarget = target;
            approachingTarget = false;
            seekingFiringPosition = false;
            retreatingForSpace = false;
            strafeAnchorSet = false;
            aimTicks = 0;
            unseenTargetTicks = 0;
            nextFiringPositionTick = mob.tickCount;
            nextApproachPathTick = mob.tickCount;
        }

        double distance = mob.distanceTo(target);
        GunRangePolicy.Band range = preferredRange(weapon);
        boolean holdingPosition = mob instanceof Human human
                && SoldierOrder.isHoldingPosition(human);
        if (mob instanceof Human human) {
            if (closeDefense) {
                if (distance < CLOSE_MELEE_EXIT_RANGE) {
                    tickCloseMeleeDefense(human, target, distance);
                    return;
                }
                GunCustody.restorePrimaryGun(human, "close_defense_distance");
                weapon = mob.getMainHandItem();
                aimTicks = 0;
                if (!isGun(weapon)) {
                    return;
                }
                operator.ensureDrawn();
                human.getPersistentData().putString(
                        "hostile_humans_unified:ai_phase", "gun_close_defense_return"
                );
                range = preferredRange(weapon);
            } else {
                updateRetreatPressure(distance, range);
            }
            if (!closeDefense
                    && distance <= closeMeleeRange(human, target)
                    && retreatFailureTicks >= 12
                    && GunCustody.beginCloseMeleeCounter(human)) {
                tickCloseMeleeDefense(human, target, distance);
                return;
            }
        }

        if (mob instanceof Human human) {
            // Clear sprint and TaCZ's independent sprint timer before combat
            // processing. Pursuit branches may re-enable sprint only when
            // they return without aiming or firing.
            operator.clearSprintLock();
            if (!isShoreSeeking()) {
                MovementSpeedController.combat(human, false);
            }
        }

        if (operator.ensureDrawn()) aimTicks = 0;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double fireRangeSqr = firingRangeSqr(range);
        if (mob instanceof Human human
                && target instanceof Player player
                && HumanRelationshipPolicy.shouldApproachBeaconPlayerWithoutFiring(
                        HumanRelations.isForcedHostileTo(human, player), distance * distance,
                        fireRangeSqr)) {
            // Keep moving toward the marked player after a side fight, but do
            // not turn the beacon's pursuit range into extra weapon reach.
            aimTicks = 0;
            unseenTargetTicks = 0;
            setAiming(false);
            if (!ShoreSeekingPolicy.shouldMoveTowardCombatTarget(isShoreSeeking())) {
                // The environmental MOVE lease must remain pointed at dry
                // ground; this special beacon pursuit used to replace it with
                // a path straight toward the player, often deeper into water.
                clearLateralInputPreservingNavigation();
                return;
            }
            stopLateralMovement();
            approachTarget(target, range);
            enablePursuitSprint(human, target);
            return;
        }
        if (!mob.getSensing().hasLineOfSight(target)) {
            unseenTargetTicks = Math.min(OBSCURED_ADVANCE_AFTER_TICKS + 1,
                    unseenTargetTicks + 1);
            if (unseenTargetTicks == OBSCURED_ADVANCE_AFTER_TICKS) {
                // A failed preferred-band search must not postpone the first
                // closer-angle attempt for the rest of its retry interval.
                nextFiringPositionTick = mob.tickCount;
            }
            aimTicks = 0;
            setAiming(false);
            if (isShoreSeeking()) {
                clearLateralInputPreservingNavigation();
                return;
            }
            if (holdingPosition) {
                if (!SoldierOrder.isReturningToHoldPosition((Human)mob)) {
                    mob.getNavigation().stop();
                }
                return;
            }
            moveForFiringPosition(target, distance, range, true);
            if (mob instanceof Human human) {
                enablePursuitSprint(human, target);
            }
            return;
        }
        unseenTargetTicks = 0;

        if (isShoreSeeking()) {
            // LeaveWaterWhenIdleGoal owns navigation until the Human is dry.
            // Keep aiming and shooting below active without replacing its path.
            clearLateralInputPreservingNavigation();
        } else if (holdingPosition) {
            stopLateralMovement();
            if (!SoldierOrder.isReturningToHoldPosition((Human)mob)) {
                mob.getNavigation().stop();
            }
        } else {
            moveForFiringPosition(target, distance, range, false);
        }

        setAiming(true);
        if (mob instanceof Human human) {
            GunAttackMovementState.markAttackActive(human);
        }
        if (distance <= RETREAT_RANGE) {
            // Close pressure must not turn the gun into a silent prop. TaCZ
            // retains draw/melee/sprint locks independently from the held
            // ItemStack, so clear only those transient blockers before the
            // urgent counter-shot while continuing to retreat.
            operator.prepareUrgentShot();
        }
        AimRotation aim = calculateAim(target, weapon);
        mob.setXRot(aim.pitch());
        mob.setYRot(aim.yaw());
        mob.setYHeadRot(aim.yaw());
        mob.setYBodyRot(aim.yaw());
        if (mob instanceof Human human) {
            human.markRangedFacing(target, aim.yaw());
        }
        // The synchronized aiming-progress value is presentation state, not a
        // server-side ballistic requirement. It can remain at zero while the
        // Human is swimming, which used to suppress every underwater shot.
        if (++aimTicks < MIN_AIM_TICKS) {
            return;
        }

        // TaCZ expects pitch followed by the shooter's head yaw. Using the
        // body's Y rotation makes bullets leave at a different angle from the
        // direction selected by LookControl whenever the head is tracking a
        // target independently. Supply the already calculated ballistic aim so
        // the projectile, animation and AI target all agree.
        AimRotation shotAim = applyShotSpread(aim, weapon);
        operator.shoot(weapon, shotAim.pitch(), shotAim.yaw());
    }

    private void tickCloseMeleeDefense(Human human, LivingEntity target, double distance) {
        setAiming(false);
        aimTicks = 0;
        unseenTargetTicks = 0;
        boolean shoreSeeking = isShoreSeeking();
        if (shoreSeeking) {
            clearLateralInputPreservingNavigation();
        } else {
            stopLateralMovement();
        }
        operator.clearSprintLock();
        human.getLookControl().setLookAt(target, 45.0F, 35.0F);
        human.getPersistentData().putString(
                "hostile_humans_unified:ai_phase", "gun_close_melee_defense"
        );

        if (shoreSeeking) {
            // Preserve the shore route while still allowing an in-range melee response.
        } else if (SoldierOrder.isHoldingPosition(human)) {
            if (!SoldierOrder.isReturningToHoldPosition(human)) {
                human.getNavigation().stop();
            }
        } else if (human.tickCount >= nextCloseRetreatPathTick) {
            retreatFrom(target);
            nextCloseRetreatPathTick = human.tickCount + 8;
        }
        if (distance > closeMeleeRange(human, target)
                || !human.humanGunner$canMeleeAttack()
                || human.isUsingItem()
                || human.tickCount < nextCloseMeleeAttackTick
                || !(HumanUtil.isMeleeWeapon(human.getMainHandItem())
                || HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem()))) {
            return;
        }
        human.swing(InteractionHand.MAIN_HAND);
        human.doHurtTarget(target);
        nextCloseMeleeAttackTick = human.tickCount + MeleeAttackTiming.nextCooldown(human);
    }

    private static double closeMeleeRange(Human human, LivingEntity target) {
        return MeleeCombatRange.reach(human, target);
    }

    private void setAiming(boolean aiming) {
        operator.aim(aiming);
    }

    private AimRotation calculateAim(LivingEntity target, ItemStack weapon) {
        Vec3 origin = mob.getEyePosition();
        Vec3 aimPoint = GunAimPoint.forTarget(target);

        double blocksPerTick = GunSupport.get().projectileSpeed(weapon);
        if (blocksPerTick > 0) {
            double travelTicks = Mth.clamp(origin.distanceTo(aimPoint) / blocksPerTick, 0, MAX_LEAD_TICKS);
            aimPoint = aimPoint.add(target.getDeltaMovement().scale(travelTicks));
            aimPoint = aimPoint.add(0, 0.5D * GunSupport.get().projectileGravity(weapon) * travelTicks * travelTicks, 0);
        }

        Vec3 delta = aimPoint.subtract(origin);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        return new AimRotation(pitch, yaw);
    }

    private AimRotation applyShotSpread(AimRotation exactAim, ItemStack weapon) {
        if (GunSupport.get().ownsShotSpread(weapon)) return exactAim;
        double standardDeviation = mob instanceof Human human
                ? RangedAccuracy.gunSpreadDegrees(human, weapon)
                : 0.6D;
        if (standardDeviation <= 0.0D) {
            return exactAim;
        }

        // Add independent per-shot angular error around the corrected ballistic
        // aim. A 2.5-sigma cap keeps the occasional Gaussian outlier from
        // producing an implausibly wild shot while distance still naturally
        // makes the same angular error less forgiving.
        double limit = standardDeviation * 2.5D;
        float pitchOffset = (float) Mth.clamp(
                mob.getRandom().nextGaussian() * standardDeviation,
                -limit,
                limit
        );
        float yawOffset = (float) Mth.clamp(
                mob.getRandom().nextGaussian() * standardDeviation,
                -limit,
                limit
        );
        return new AimRotation(exactAim.pitch() + pitchOffset, exactAim.yaw() + yawOffset);
    }

    private void retreatFrom(LivingEntity target) {
        if (mob instanceof Human human) {
            MovementSpeedController.retreat(human, false);
            boolean moved = false;
            retreatSearchQueued = RetreatRouteContinuity.claimSearch(human, 1) == 0;
            if (!retreatSearchQueued) {
                Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, target.position());
                Path path = away == null ? null : mob.getNavigation().createPath(
                        net.minecraft.core.BlockPos.containing(away), 0);
                moved = RetreatPathSafety.isSafe(human, target, path, true)
                        && RetreatPathSafety.tryMove(human, path, 1.0D);
                RetreatRouteContinuity.cancelSearch(human);
            }
            // A non-null sample is not movement success. Preserve a live route,
            // and use collision/terrain checked backsteps only when no route runs.
            if (!moved) RetreatPathSafety.tryLocalStep(human, target, 1.0D);
        } else {
            Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, target.position());
            Path path = away == null ? null : mob.getNavigation().createPath(
                    net.minecraft.core.BlockPos.containing(away), 0);
            if (path != null && path.canReach()) mob.getNavigation().moveTo(path, 1.0D);
        }
    }

    private void moveForFiringPosition(LivingEntity target, double distance,
                                       GunRangePolicy.Band range, boolean obscured) {
        if (mob instanceof Human human) {
            if (CombatTerrainNavigation.hasWork(human, target)) stopLateralMovement();
            var terrain = CombatTerrainNavigation.update(human, target, 1.0D,
                    !obscured && distance * distance <= firingRangeSqr(range)
                            && HumanAmmo.canUseGun(human, mob.getMainHandItem()));
            if (terrain.ownsMovement()) {
                clearLateralInputPreservingNavigation();
                approachingTarget = terrain == CombatTerrainNavigation.Decision.ROUTE;
                seekingFiringPosition = false;
                return;
            }
        }
        if (obscured) {
            stopLateralMovement();
            boolean seekCloserAngle = unseenTargetTicks >= OBSCURED_ADVANCE_AFTER_TICKS
                    && distance > OBSCURED_ADVANCE_DISTANCE;
            if (distance > range.maximum() + 12.0D
                    || (mob instanceof Human human && human.shouldUseWaterMovement()
                    && distance > range.maximum())) {
                // The firing-lane search is local. Close most of the gap first
                // instead of repeatedly searching a 12-block neighborhood
                // that cannot possibly reach the configured firing band.
                if (seekingFiringPosition) {
                    mob.getNavigation().stop();
                    approachingTarget = false;
                }
                seekingFiringPosition = false;
                approachTarget(target, seekCloserAngle
                        ? OBSCURED_ADVANCE_DISTANCE : range.maximum());
                return;
            }
            if (!seekingFiringPosition) {
                // Cancel a stale straight-to-target path once cover blocks the
                // shot. After sustained occlusion, keep the deliberate closer
                // approach instead of cancelling it on every following tick.
                if ((!seekCloserAngle && approachingTarget)
                        || (!approachingTarget && !mob.getNavigation().isDone())) {
                    mob.getNavigation().stop();
                    approachingTarget = false;
                }
            }
            if (seekCloserAngle && !seekingFiringPosition && approachingTarget
                    && !mob.getNavigation().isDone() && !mob.getNavigation().isStuck()) {
                approachTarget(target, OBSCURED_ADVANCE_DISTANCE);
                return;
            }
            if ((mob.getNavigation().isDone() || mob.getNavigation().isStuck())
                    && mob.tickCount >= nextFiringPositionTick
                    && mob instanceof Human human) {
                Path firingPath = RangedFiringPosition.findVisiblePath(
                        human,
                        target,
                        seekCloserAngle
                                ? Math.min(OBSCURED_ADVANCE_DISTANCE, range.minimum())
                                : range.minimum(),
                        seekCloserAngle
                                ? Math.max(range.maximum(), distance)
                                : range.maximum(),
                        12,
                        16
                );
                // After sustained occlusion, accept a nearer firing lane.
                // Keeping one bounded search avoids doubling path probes.
                if (firingPath != null) {
                    if (RetreatPathSafety.tryMove(human, firingPath, 1.0D)) {
                        SoldierDialogue.event(human, "reposition");
                        approachingTarget = true;
                        seekingFiringPosition = true;
                    }
                } else if (RangedFiringPosition.hasPendingVisibleSearch(human)) {
                    // Pending is not failure: do not abandon the lane search
                    // for a different movement/shore policy after its first batch.
                } else if (seekCloserAngle) {
                    CombatTerrainNavigation.failed(human, target);
                    seekingFiringPosition = false;
                    approachTarget(target, OBSCURED_ADVANCE_DISTANCE);
                    if (!approachingTarget && human.isInWater()) {
                        human.beginCombatFiringShore(target, 1.0D);
                    }
                } else {
                    CombatTerrainNavigation.failed(human, target);
                    if (human.isInWater()) {
                        human.beginCombatFiringShore(target, 1.0D);
                    } else {
                        mob.getNavigation().stop();
                    }
                    approachingTarget = false;
                    seekingFiringPosition = false;
                }
                nextFiringPositionTick = mob.tickCount + RangedFiringPosition.retryDelay(
                        human, firingPath, seekCloserAngle ? 20 : 40);
            }
            return;
        }
        seekingFiringPosition = false;

        if (distance < range.minimum()) {
            retreatingForSpace = true;
        } else if (distance >= range.retreatResume()) {
            retreatingForSpace = false;
        }
        if (retreatingForSpace) {
            stopLateralMovement();
            approachingTarget = false;
            if (mob.tickCount >= nextRangeRetreatPathTick) {
                retreatFrom(target);
                nextRangeRetreatPathTick = mob.tickCount + (retreatSearchQueued ? 1 : 8);
            }
            return;
        }
        if (distance > range.maximum()) {
            stopLateralMovement();
            approachTarget(target, range);
            return;
        }
        if (approachingTarget) {
            // A direct chase path must not keep running after reaching gun range.
            mob.getNavigation().stop();
            approachingTarget = false;
        }
        strafeAround(target, range);
    }

    private void approachTarget(LivingEntity target, GunRangePolicy.Band range) {
        approachTarget(target, range.maximum());
    }

    private void approachTarget(LivingEntity target, double desiredDistance) {
        // A water navigator cannot always path to a target standing on the far
        // bank. Do not retry A-star every tick or abandon the weapon's range.
        if (mob instanceof Human pursuingHuman) {
            approachingTarget = PursuitNavigation.update(pursuingHuman, target, 1.0D);
        } else if (mob.tickCount >= nextApproachPathTick) {
            approachingTarget = mob.getNavigation().moveTo(target, 1.0D);
            nextApproachPathTick = mob.tickCount + 12;
        }
        if (mob instanceof Human human) {
            human.approachCombatTargetInWater(target, desiredDistance, 1.0D);
        }
    }

    /** Sprint on a gunner's non-firing approach path, never during a shot attempt. */
    private void enablePursuitSprint(Human human, LivingEntity target) {
        if (human.isFleeing
                || human.isUsingItem()
                || (human.hasOwner() && SoldierOrder.isHoldingPosition(human))
                || !approachingTarget) {
            return;
        }
        Path path = human.getNavigation().getPath();
        if (path == null || path.isDone() || human.getNavigation().isDone()) {
            return;
        }
        Vec3 pathDirection = NavigationSupport.horizontalDirection(
                human.position(), Vec3.atCenterOf(path.getNextNodePos())
        );
        Vec3 targetDirection = NavigationSupport.horizontalDirection(human.position(), target.position());
        if (pathDirection.lengthSqr() > 0.01D
                && targetDirection.lengthSqr() > 0.01D
                && pathDirection.dot(targetDirection) >= 0.55D) {
            MovementSpeedController.combat(human, true);
        }
    }

    private void updateRetreatPressure(double distance, GunRangePolicy.Band range) {
        if (distance >= range.minimum()) {
            retreatFailureTicks = 0;
            retreatSearchQueued = false;
            lastThreatDistance = distance;
            return;
        }
        if (retreatSearchQueued) {
            lastThreatDistance = distance;
            return;
        }
        boolean notGainingGround = Double.isFinite(lastThreatDistance)
                && distance <= lastThreatDistance + 0.05D;
        boolean pathFailed = mob.getNavigation().isDone() || mob.getNavigation().isStuck();
        if (notGainingGround || pathFailed) {
            retreatFailureTicks = Math.min(40, retreatFailureTicks + 1);
        } else {
            retreatFailureTicks = Math.max(0, retreatFailureTicks - 2);
        }
        lastThreatDistance = distance;
        if (mob instanceof Human human) {
            human.getPersistentData().putInt(
                    "hostile_humans_unified:gun_retreat_failure_ticks", retreatFailureTicks
            );
        }
    }

    private void strafeAround(LivingEntity target, GunRangePolicy.Band range) {
        mob.lookAt(target, 30.0F, 30.0F);
        double anchorRadius = Math.max(6.0D,
                Math.min(9.0D, (range.maximum() - range.minimum()) * 0.9D));
        if (!strafeAnchorSet
                || target.position().distanceToSqr(strafeAnchorTargetX, target.getY(),
                        strafeAnchorTargetZ) > 36.0D
                || strafeAnchorDistanceSqr() > anchorRadius * anchorRadius * 4.0D) {
            if (strafePathActive) mob.getNavigation().stop();
            strafePathActive = false;
            manualStrafeUntilTick = 0;
            strafeAnchorX = mob.getX();
            strafeAnchorZ = mob.getZ();
            strafeAnchorTargetX = target.getX();
            strafeAnchorTargetZ = target.getZ();
            strafeAnchorSet = true;
            nextStrafeRepositionTick = mob.tickCount;
        }

        if (strafePathActive) {
            if (!mob.getNavigation().isDone() && !mob.getNavigation().isStuck()) {
                return;
            }
            strafePathActive = false;
            strafing = false;
            // A completed flank should lead to a return leg, not another
            // path farther around the same side of the target.
            strafeDirection = -strafeDirection;
            nextStrafeRepositionTick = mob.tickCount + 16;
            mob.getMoveControl().strafe(0.0F, 0.0F);
            mob.setZza(0.0F);
            mob.setXxa(0.0F);
            return;
        }

        if (manualStrafeUntilTick > 0) {
            if (mob.tickCount < manualStrafeUntilTick) {
                if (mob.tickCount - strafeLegStartTick >= 8
                        && (strafeAnchorDistanceSqr() >= anchorRadius * anchorRadius
                        || mob.horizontalCollision
                        || strafeLegDisplacementSqr() < 0.16D)) {
                    strafeDirection = -strafeDirection;
                    strafeLegStartTick = mob.tickCount;
                    strafeLegStartX = mob.getX();
                    strafeLegStartZ = mob.getZ();
                }
                mob.getMoveControl().strafe(radialStrafeInput(target, range), strafeDirection);
                strafing = true;
                return;
            }
            manualStrafeUntilTick = 0;
            strafing = false;
            strafeDirection = -strafeDirection;
            nextStrafeRepositionTick = mob.tickCount + 12;
            mob.getMoveControl().strafe(0.0F, 0.0F);
            mob.setZza(0.0F);
            mob.setXxa(0.0F);
            return;
        }

        if (mob.tickCount < nextStrafeRepositionTick) {
            return;
        }

        if (mob instanceof Human human && !human.shouldUseWaterMovement()
                && !BasicNavigationBudget.tryAcquire(human, 3)) {
            nextStrafeRepositionTick = mob.tickCount + 1;
            return;
        }

        strafeLegStartTick = mob.tickCount;
        strafeLegStartX = mob.getX();
        strafeLegStartZ = mob.getZ();
        // Water paths are for crossing/closing distance; use a committed
        // manual lateral leg while afloat so aiming does not stall the flank.
        Path lateralPath = mob instanceof Human human && !human.shouldUseWaterMovement()
                ? RangedFiringPosition.findLateralPath(
                        human, target, range.minimum(), range.maximum(), (int) strafeDirection,
                        strafeAnchorX, strafeAnchorZ, anchorRadius)
                : null;
        if (lateralPath != null && (mob instanceof Human human
                ? RetreatPathSafety.tryMove(human, lateralPath, 1.15D)
                : mob.getNavigation().moveTo(lateralPath, 1.15D))) {
            strafePathActive = true;
            strafing = true;
            return;
        }

        // Commit to a visible flank rather than a twelve-tick left/right wobble.
        mob.getNavigation().stop();
        mob.getMoveControl().strafe(radialStrafeInput(target, range), strafeDirection);
        strafing = true;
        manualStrafeUntilTick = mob.tickCount + 32;
        nextStrafeRepositionTick = manualStrafeUntilTick;
    }

    private double strafeLegDisplacementSqr() {
        double dx = mob.getX() - strafeLegStartX;
        double dz = mob.getZ() - strafeLegStartZ;
        return dx * dx + dz * dz;
    }

    private double strafeAnchorDistanceSqr() {
        double dx = mob.getX() - strafeAnchorX;
        double dz = mob.getZ() - strafeAnchorZ;
        return dx * dx + dz * dz;
    }

    private float radialStrafeInput(LivingEntity target, GunRangePolicy.Band range) {
        double ideal = (range.minimum() + range.maximum()) * 0.5D;
        double distance = mob.distanceTo(target);
        if (distance > ideal + 1.0D) return 0.4F;
        if (distance < ideal - 1.0D) return -0.4F;
        return 0.0F;
    }

    private void stopLateralMovement() {
        strafeAnchorSet = false;
        if (!strafing && !strafePathActive && manualStrafeUntilTick == 0) {
            return;
        }
        boolean terrainRoute = mob instanceof Human human && CombatTerrainNavigation.ownsRoute(human);
        if (strafePathActive && !terrainRoute) {
            mob.getNavigation().stop();
        }
        if (!terrainRoute) mob.getMoveControl().strafe(0.0F, 0.0F);
        else if (mob instanceof Human human) human.clearRangedStrafeMotion();
        mob.setZza(0.0F);
        mob.setXxa(0.0F);
        strafing = false;
        strafePathActive = false;
        manualStrafeUntilTick = 0;
    }

    private void clearLateralInputPreservingNavigation() {
        // Do not issue even a zero-valued STRAFE command here. HumanMoveControl
        // treats STRAFE as an exclusive water-movement operation, which can
        // consume the same tick the shore navigator needs to advance its path.
        strafeAnchorSet = false;
        strafing = false;
        strafePathActive = false;
        manualStrafeUntilTick = 0;
    }

    private boolean isShoreSeeking() {
        return mob instanceof Human human
                && ShoreSeekingPolicy.isShoreTransitionActive(
                        human.isSeekingShore(), human.isShoreTransitionPending());
    }

    private static boolean isGun(ItemStack stack) {
        return GunSupport.get().isGun(stack);
    }

    private GunRangePolicy.Band preferredRange(ItemStack weapon) {
        return GunRangePolicy.forType(GunSupport.get().gunType(weapon));
    }

    private double firingRangeSqr(GunRangePolicy.Band range) {
        // A sniper can use the full configured discovery range, including the
        // beacon's temporary follow-range extension, once it has a target.
        double reach = GunRangePolicy.firingReach(range, mob.getAttributeValue(Attributes.FOLLOW_RANGE));
        return reach * reach;
    }

    private record AimRotation(float pitch, float yaw) {
    }

}

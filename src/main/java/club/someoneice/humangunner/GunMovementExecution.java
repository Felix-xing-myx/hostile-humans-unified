package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Executes positioning inside the calling Goal's admitted movement scope; never calculates ballistic aim or fires. */
final class GunMovementExecution<T extends PathfinderMob> {
    private static final double OBSCURED_ADVANCE_DISTANCE = 10.0D;
    private static final int OBSCURED_ADVANCE_AFTER_TICKS = 30;
    private final T mob;
    private int unseenTargetTicks;
    private int nextFiringPositionTick;
    private int nextApproachPathTick;
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
    private boolean approachingTarget;
    private boolean strafing;
    private boolean seekingFiringPosition;
    private LivingEntity movementTarget;

    GunMovementExecution(T mob) { this.mob = mob; }

    public void start() {
        unseenTargetTicks = 0;
        approachingTarget = false;
        strafing = false;
        manualStrafeUntilTick = 0;
        strafeAnchorSet = false;
        seekingFiringPosition = false;
        strafeDirection = mob.getRandom().nextBoolean() ? 1.0F : -1.0F;
        strafeLegStartTick = mob.tickCount;
        strafeLegStartX = mob.getX();
        strafeLegStartZ = mob.getZ();
        nextFiringPositionTick = mob.tickCount;
        nextApproachPathTick = mob.tickCount;
        movementTarget = mob.getTarget();
    }

    public void stop() {
        if (mob instanceof Human human) {
            BasicNavigationBudget.cancel(human);
            RetreatRouteContinuity.cancelSearch(human);
        }
        if (mob instanceof Human human) RangedFiringPosition.cancelVisibleSearch(human);
        if (mob instanceof Human human && human.isCombatFiringShore()) {
            human.stopSeekingShore();
        }
        if (isShoreSeeking()) {
            clearLateralInputPreservingNavigation();
        } else {
            stopLateralMovement();
        }
        unseenTargetTicks = 0;
        approachingTarget = false;
        seekingFiringPosition = false;
        manualStrafeUntilTick = 0;
        strafeAnchorSet = false;
        movementTarget = null;
        if (mob instanceof Human human) {
            MovementSpeedController.combat(human, false);
            MovementSpeedController.limit(human,MovementSpeedController.Limit.RANGED,1,false);
        }
    }

    boolean targetChanged(LivingEntity target) {
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
            strafeAnchorSet = false;
            unseenTargetTicks = 0;
            nextFiringPositionTick = mob.tickCount;
            nextApproachPathTick = mob.tickCount;
        }
        else return false;
        return true;
    }

    void visible() { unseenTargetTicks = 0; }
    void occluded() {
        unseenTargetTicks = Math.min(OBSCURED_ADVANCE_AFTER_TICKS + 1, unseenTargetTicks + 1);
        if (unseenTargetTicks == OBSCURED_ADVANCE_AFTER_TICKS) nextFiringPositionTick = mob.tickCount;
    }

    void moveForFiringPosition(LivingEntity target, double distance,
                                       GunRangePolicy.Band range, GunPositioning.Plan plan) {
        boolean obscured = plan.mode() == GunPositioning.Mode.SEARCH_WINDOW;
        if (mob instanceof Human human) {
            RangedFiringMemory.observe(human,target,!obscured && distance*distance<=firingRangeSqr(range)
                    && HumanAmmo.canUseGun(human,mob.getMainHandItem()),firingRangeSqr(range));
            if(RangedFiringMemory.recover(human,target,1.0D)) {
                clearLateralInputPreservingNavigation();
                approachingTarget=false;seekingFiringPosition=true;
                return;
            }
            if (CombatTerrainNavigation.hasWork(human, target)) stopLateralMovement();
            var terrain = CombatTerrainNavigation.update(human, target, 1.0D,
                    GunRangePolicy.directFiringMovement(obscured,distance*distance,firingRangeSqr(range))
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
            boolean keepDistance = mob instanceof Human human
                    && HumanCombatDirector.keepsRangedDistance(human, human.getMainHandItem());
            boolean seekCloserAngle = !keepDistance && unseenTargetTicks >= OBSCURED_ADVANCE_AFTER_TICKS
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
                Path firingPath = RangedFiringPosition.findAttackWindowPath(
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
                        RangedFiringMemory.selected(human,target,firingPath,firingRangeSqr(range));
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

        if (plan.mode() == GunPositioning.Mode.APPROACH) {
            stopLateralMovement();
            approachTarget(target, plan.desiredDistance());
            return;
        }
        if (plan.mode() == GunPositioning.Mode.HOLD) {
            stopLateralMovement();
            mob.getNavigation().stop();
            approachingTarget = false;
            return;
        }
        if (approachingTarget) {
            // A direct chase path must not keep running after reaching gun range.
            mob.getNavigation().stop();
            approachingTarget = false;
        }
        strafeAround(target, range);
    }

    void approachTarget(LivingEntity target, GunRangePolicy.Band range) {
        approachTarget(target, range.maximum());
    }

    void approachTarget(LivingEntity target, double desiredDistance) {
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
    void enablePursuitSprint(Human human, LivingEntity target) {
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

    private void strafeAround(LivingEntity target, GunRangePolicy.Band range) {
        double anchorRadius = Math.max(6.0D,
                Math.min(9.0D, (range.maximum() - range.minimum()) * 0.9D));
        if (!strafeAnchorSet
                || target.position().distanceToSqr(strafeAnchorTargetX, target.getY(),
                        strafeAnchorTargetZ) > 36.0D
                || strafeAnchorDistanceSqr() > anchorRadius * anchorRadius * 4.0D) {
            manualStrafeUntilTick = 0;
            strafeAnchorX = mob.getX();
            strafeAnchorZ = mob.getZ();
            strafeAnchorTargetX = target.getX();
            strafeAnchorTargetZ = target.getZ();
            strafeAnchorSet = true;
        }

        // Only a clear shot within effective firing reach reaches here. A lateral A-star route
        // would make travel yaw own the body and reject a roughly 90-degree shot.
        // Out-of-range pursuit, terrain, shore and survival movement return before this branch.
        mob.getNavigation().stop();
        clearDirectStrafe(); // Also replace a stale MoveControl MOVE_TO, even if no safe step exists.
        if (mob instanceof Human human) {
            LongRangeNavigation.cancel(human,LongRangeNavigation.Purpose.COMBAT);
            RangedFiringPosition.cancelVisibleSearch(human);
            RetreatRouteContinuity.cancelSearch(human);
            MovementContinuity.cancelStep(human);
            HumanAttention.movement(human,human.getX(),human.getZ(),true);
        }
        if (manualStrafeUntilTick > 0 && mob.tickCount >= manualStrafeUntilTick) {
            manualStrafeUntilTick = 0;
            strafeDirection = -strafeDirection;
        }
        if (manualStrafeUntilTick == 0) {
            strafeLegStartTick = mob.tickCount;
            strafeLegStartX = mob.getX();
            strafeLegStartZ = mob.getZ();
            manualStrafeUntilTick = mob.tickCount + 32;
        }
        if (mob.tickCount - strafeLegStartTick >= 8
                && (strafeAnchorDistanceSqr() >= anchorRadius * anchorRadius
                || mob.horizontalCollision || strafeLegDisplacementSqr() < 0.16D)) {
            strafeDirection = -strafeDirection;
            strafeLegStartTick = mob.tickCount;
            strafeLegStartX = mob.getX();
            strafeLegStartZ = mob.getZ();
        }
        if (mob instanceof Human human) {
            MovementSpeedController.limit(human,MovementSpeedController.Limit.RANGED,
                    GunCombatProfile.of(human,human.getMainHandItem()).movement(),true);
            strafing = RangedTerrainCombat.strafeFacingTarget(human,target,radialStrafeInput(target,range),strafeDirection);
            if(!strafing) {
                MovementSpeedController.limit(human,MovementSpeedController.Limit.RANGED,1,false);
                // A rejected local step is not permission to chase the target.
                // The hub chose STRAFE; retain the firing lane and retry next
                // tick. Only APPROACH/SEARCH_WINDOW may install a chase route.
            }
        } else {
            mob.lookAt(target, 30.0F, 30.0F);
            mob.getMoveControl().strafe(radialStrafeInput(target, range), strafeDirection);
            strafing = true;
        }
    }

    private void clearDirectStrafe() {
        if (mob instanceof Human human) human.clearRangedStrafeMotion();
        mob.getMoveControl().strafe(0.0F, 0.0F);
        mob.setZza(0.0F);
        mob.setXxa(0.0F);
        strafing = false;
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

    void stopLateralMovement() {
        strafeAnchorSet = false;
        if (!strafing && manualStrafeUntilTick == 0) {
            return;
        }
        boolean terrainRoute = mob instanceof Human human && CombatTerrainNavigation.ownsRoute(human);
        if (!terrainRoute) mob.getMoveControl().strafe(0.0F, 0.0F);
        else if (mob instanceof Human human) human.clearRangedStrafeMotion();
        mob.setZza(0.0F);
        mob.setXxa(0.0F);
        strafing = false;
        manualStrafeUntilTick = 0;
    }

    void clearLateralInputPreservingNavigation() {
        // Do not issue even a zero-valued STRAFE command here. HumanMoveControl
        // treats STRAFE as an exclusive water-movement operation, which can
        // consume the same tick the shore navigator needs to advance its path.
        strafeAnchorSet = false;
        strafing = false;
        manualStrafeUntilTick = 0;
    }

    private boolean isShoreSeeking() {
        return mob instanceof Human human
                && ShoreSeekingPolicy.isShoreTransitionActive(
                        human.isSeekingShore(), human.isShoreTransitionPending());
    }

    private double firingRangeSqr(GunRangePolicy.Band range) {
        // A sniper can use the full configured discovery range, including the
        // beacon's temporary follow-range extension, once it has a target.
        double reach = GunRangePolicy.firingReach(range, mob.getAttributeValue(Attributes.FOLLOW_RANGE));
        return reach * reach;
    }
}

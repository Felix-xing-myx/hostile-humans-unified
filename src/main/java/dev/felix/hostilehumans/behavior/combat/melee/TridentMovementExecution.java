package dev.felix.hostilehumans.behavior.combat.melee;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.ranged.RangedFiringPosition;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedStrafeSpeed;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedTerrainCombat;
import dev.felix.hostilehumans.navigation.CombatTerrainNavigation;
import dev.felix.hostilehumans.navigation.PursuitNavigation;
import dev.felix.hostilehumans.soldier.SoldierDialogue;
import dev.felix.hostilehumans.soldier.SoldierOrder;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.pathfinder.Path;

/** Trident movement under the original Goal token; never performs a melee strike or throw. */
public final class TridentMovementExecution {
    private static final double MAX_THROW_DISTANCE_SQR = 16.0D * 16.0D;
    private final Human human;
    private int failedSeparationTicks;
    private double separationDistanceSqr;
    private int nextFiringPositionTick;
    TridentMovementExecution(Human human) { this.human = human; }
    void start(LivingEntity target) {
        resetSeparation(target == null ? 0 : human.distanceToSqr(target));
        nextFiringPositionTick = human.tickCount;
    }
    void resetSeparation(double distanceSqr) { failedSeparationTicks = 0; separationDistanceSqr = distanceSqr; }
    void stop() {
        RangedStrafeSpeed.setTrident(human, false);
        human.clearRangedStrafeMotion();
        RangedFiringPosition.cancelVisibleSearch(human);
        if (human.isCombatFiringShore()) human.stopSeekingShore();
        if (!CombatTerrainNavigation.ownsRoute(human)) human.getNavigation().stop();
    }
    boolean separate(LivingEntity target, double distanceSqr) {
        human.getNavigation().stop();
        boolean retreat = RangedTerrainCombat.strafeFacingTarget(human, target, -.8F,
                ((human.tickCount / 40) & 1) == 0 ? .45F : -.45F);
        if (distanceSqr > separationDistanceSqr + .25) {
            failedSeparationTicks = 0; separationDistanceSqr = distanceSqr;
        } else failedSeparationTicks++;
        return !retreat || failedSeparationTicks >= 10;
    }

    boolean ranged(LivingEntity target, double distanceSqr, boolean visible, boolean shoreSeeking) {
        if (shoreSeeking) {
            return true;
        }
        if (SoldierOrder.isHoldingPosition(human)) {
            if (!SoldierOrder.isReturningToHoldPosition(human)) {
                human.getNavigation().stop();
            }
            return true;
        }
        boolean waterApproach = human.shouldUseWaterMovement()
                && distanceSqr > MAX_THROW_DISTANCE_SQR;
        if (CombatTerrainNavigation.update(human, target, 1.0D,
                visible && distanceSqr <= MAX_THROW_DISTANCE_SQR).ownsMovement()) {
            return true;
        }
        if (!visible && human.isInWater() && distanceSqr <= MAX_THROW_DISTANCE_SQR) {
            if ((human.getNavigation().isDone() || human.getNavigation().isStuck())
                    && human.tickCount >= nextFiringPositionTick) {
                Path firingPath = RangedFiringPosition.findVisiblePath(
                        human, target, 6.0D, 16.0D, 10, 12);
                if (firingPath != null) {
                    if (human.getNavigation().moveTo(firingPath, 1.0D))
                        SoldierDialogue.event(human, "reposition");
                } else if (!RangedFiringPosition.hasPendingVisibleSearch(human)) {
                    human.beginCombatFiringShore(target, 1.0D);
                }
                nextFiringPositionTick = human.tickCount + RangedFiringPosition.retryDelay(human, firingPath, 40);
            }
            return false;
        }
        if (!visible || distanceSqr > MAX_THROW_DISTANCE_SQR || waterApproach) {
            PursuitNavigation.update(human, target, 1.0D);
            human.approachCombatTargetInWater(target, waterApproach ? 16.0D : 6.0D, 1.0D);
            return true;
        }

        human.getNavigation().stop();
        RangedTerrainCombat.strafeFacingTarget(human, target, 0,
                ((human.tickCount / 40) & 1) == 0 ? .8F : -.8F);
        return true;
    }

}

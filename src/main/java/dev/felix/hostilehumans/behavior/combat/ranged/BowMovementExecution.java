package dev.felix.hostilehumans.behavior.combat.ranged;

import dev.felix.hostilehumans.entity.HumanEntity;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedMovementDecision;

import dev.felix.hostilehumans.behavior.combat.ranged.BowRangePolicy;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedMovementDecision.Mode;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedFiringPosition;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedStrafeSpeed;
import dev.felix.hostilehumans.soldier.SoldierOrder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Bow positioning executes under its original Goal; attack use/cadence remains outside. */
final class BowMovementExecution<T extends HumanEntity> {
    record Request(double attackRadiusSqr, double minimumBand, double maximumBand, RangedMovementDecision decision) { }
    private final T mob;
    private final double speedModifier;
    private boolean strafingClockwise;
    private int strafingTime = -1;
    private int updatePathDelay;
    private int nextFiringPositionTick;
    private int nextWaterApproachPathTick;

    BowMovementExecution(T mob, double speedModifier) { this.mob = mob; this.speedModifier = speedModifier; }
    void start() { nextFiringPositionTick = nextWaterApproachPathTick = mob.tickCount; strafingTime = -1; }
    void reset() { strafingTime = -1; }
    boolean tick(LivingEntity livingentity, double d0, boolean flag, Request request) {
            Mode mode = request.decision().mode();
            if (mode == Mode.UNAVAILABLE) { setOrbitSpeed(false); clearStrafeInput(); return false; }
            boolean holdingPosition = mode == Mode.HOLD;
            if (mob instanceof Human human && mode == Mode.ADVANCE) {
                setOrbitSpeed(false);
                human.clearRangedStrafeMotion();
                dev.felix.hostilehumans.navigation.PursuitNavigation.update(human, livingentity, speedModifier);
                mob.getLookControl().setLookAt(livingentity, 45, 35);
                return flag;
            }
            if(mob instanceof Human human) {
                RangedEngagement.observe(human, livingentity, flag && d0 <= request.attackRadiusSqr());
                dev.felix.hostilehumans.behavior.combat.ranged.RangedFiringMemory.observe(human,livingentity,
                        flag && d0<=request.attackRadiusSqr(),request.attackRadiusSqr());
                if(dev.felix.hostilehumans.behavior.combat.ranged.RangedFiringMemory.recover(human,livingentity,speedModifier)) {
                    setOrbitSpeed(false);
                    clearStrafeInput();this.strafingTime=-1;
                    mob.getLookControl().setLookAt(livingentity,45.0F,35.0F);
                    return flag && d0<=request.attackRadiusSqr();
                }
            }
            if (mob instanceof Human human) {
                var terrain = dev.felix.hostilehumans.navigation.CombatTerrainNavigation.update(human, livingentity, speedModifier, flag && d0 <= request.attackRadiusSqr());
                if (terrain == dev.felix.hostilehumans.navigation.CombatTerrainNavigation.Decision.EXHAUSTED)
                    RangedEngagement.failure(human, livingentity, RangedEngagement.Failure.TERRAIN_EXHAUSTED);
                if (terrain.ownsMovement()) {
                    setOrbitSpeed(false); clearStrafeInput(); this.strafingTime = -1;
                    mob.getLookControl().setLookAt(livingentity, 45.0F, 35.0F);
                    return flag && d0 <= request.attackRadiusSqr();
                }
            }
            boolean waterApproach = request.decision().waterApproach();
            boolean pursuing = mode == Mode.APPROACH;
            boolean retreating = mode == Mode.RETREAT;
            boolean blockedWhileEngaging = mode == Mode.SEARCH_WINDOW;
            setOrbitSpeed(request.decision().orbitSpeed());
            if (holdingPosition) {
                if (!SoldierOrder.isReturningToHoldPosition((Human)this.mob)) {
                    this.mob.getNavigation().stop();
                }
                this.updatePathDelay = 0;
                this.strafingTime = -1;
            } else if (retreating) {
                --this.updatePathDelay;
                if (this.updatePathDelay <= 0 && (!(this.mob instanceof Human human)
                        || dev.felix.hostilehumans.navigation.BasicNavigationBudget.tryAcquire(human, 1))) {
                    Vec3 away = DefaultRandomPos.getPosAway(this.mob, 12, 6, livingentity.position());
                    if (away != null && BowRangePolicy.usefulRetreatDestination(
                            d0, livingentity.distanceToSqr(away))) {
                        this.mob.getNavigation().moveTo(away.x, away.y, away.z, this.speedModifier * 1.1D);
                    } else {
                        this.mob.getNavigation().stop();
                        strafe(
                                -1.0F, this.strafingClockwise ? 0.45F : -0.45F
                        );
                    }
                    this.updatePathDelay = 6 + this.mob.getRandom().nextInt(5);
                }
                this.strafingTime = -1;
            } else if (blockedWhileEngaging) {
                // Orbiting blindly can carry the archer behind cover. While
                // still in attack posture, pause lateral input and seek a
                // reachable point with a verified firing lane instead.
                if (this.mob.getNavigation().isDone() || this.mob.getNavigation().isStuck()) {
                    if (this.mob.tickCount >= this.nextFiringPositionTick) {
                        var window = this.mob instanceof Human human
                                ? RangedFiringPosition.searchAttackWindow(
                                        human, livingentity, dev.felix.hostilehumans.behavior.combat.ranged.RangedTerrainCombat.minimumFiringRange(human, 14.0D),
                                        Math.sqrt(request.attackRadiusSqr()), 10, 12)
                                : new RangedFiringPosition.AttackWindow(RangedFiringPosition.AttackStatus.UNAVAILABLE, null);
                        Path firingPath = window.path();
                        boolean moved = firingPath != null && this.mob instanceof Human actor
                                && RangedFiringPosition.startAttackWindow(actor, livingentity, firingPath, this.speedModifier, request.attackRadiusSqr());
                        if (this.mob instanceof Human actor) RangedEngagement.window(actor, livingentity, window, moved);
                        if (firingPath != null) {

                            if (mob instanceof Human human) dev.felix.hostilehumans.behavior.combat.ranged.RangedTerrainCombat.searchCompleted(human, livingentity, moved);
                            this.updatePathDelay = 0;
                        } else if (window.retryApproach()) {
                            if (this.mob instanceof Human human && human.isInWater()) {
                                human.beginCombatFiringShore(livingentity, this.speedModifier);
                            } else {
                                this.mob.getNavigation().stop();
                                if (window.failed() && mob instanceof Human human) dev.felix.hostilehumans.behavior.combat.ranged.RangedTerrainCombat.searchCompleted(human, livingentity, false);
                            }
                            this.updatePathDelay = 0;
                        }
                        if (this.mob instanceof Human actor && (window.retryApproach() || firingPath != null && !moved)
                                && RangedEngagement.mayApproach(actor, livingentity) && !RangedWeaponCustody.isActive(actor))
                            dev.felix.hostilehumans.navigation.PursuitNavigation.update(actor, livingentity, this.speedModifier);
                        this.nextFiringPositionTick = this.mob.tickCount + window.retryTicks();
                    }
                }
                this.strafingTime = -1;
                clearStrafeInput();
                this.mob.getLookControl().setLookAt(livingentity, 30.0F, 30.0F);
                return false;
            } else if (!pursuing) {
                // Losing sight inside shooting range is not permission to
                // charge. Orbit to change the angle while preserving spacing.
                this.mob.getNavigation().stop();
                this.updatePathDelay = 0;
                ++this.strafingTime;
            } else {
                if (this.mob instanceof Human pursuingHuman) {
                    dev.felix.hostilehumans.navigation.PursuitNavigation.update(pursuingHuman, livingentity, this.speedModifier);
                } else if (this.mob.tickCount >= this.nextWaterApproachPathTick
                        && (!(this.mob instanceof Human human)
                        || dev.felix.hostilehumans.navigation.BasicNavigationBudget.tryAcquire(human, 1))) {
                    this.mob.getNavigation().moveTo((Entity)livingentity, this.speedModifier);
                    this.nextWaterApproachPathTick = this.mob.tickCount + 12;
                }
                if (waterApproach && this.mob instanceof Human human) {
                    human.approachCombatTargetInWater(livingentity, 22.0D, this.speedModifier);
                }
                this.updatePathDelay = 0;
                this.strafingTime = -1;
            }
            if (this.strafingTime >= 20) {
                if ((double)this.mob.getRandom().nextFloat() < 0.3) {
                    boolean bl2 = this.strafingClockwise = !this.strafingClockwise;
                }
                this.strafingTime = 0;
            }
            if (this.strafingTime > -1) {
                // One controller owns both radial spacing and lateral orbiting.
                // The 14-16 block buffer deliberately mixes backward and side
                // input so circling cannot silently erase the retreat margin.
                float side = BowRangePolicy.orbitSideInput(d0);
                strafe(
                        BowRangePolicy.orbitForwardInput(d0, request.minimumBand(), request.maximumBand()),
                        this.strafingClockwise ? side : -side
                );
                this.mob.lookAt((Entity)livingentity, 30.0f, 30.0f);
            } else if (retreating && this.mob.getNavigation().isDone()) {
                // Pathfinding can fail in cramped terrain; keep backpedalling
                // and firing instead of freezing beside the attacker.
                strafe(-1.0F, this.strafingClockwise ? 0.45F : -0.45F);
                this.mob.lookAt((Entity)livingentity, 30.0F, 30.0F);
            } else {
                this.mob.getLookControl().setLookAt((Entity)livingentity, 30.0f, 30.0f);
            }
            return flag;
    }

    void clearStrafeInput() {
        if (this.mob instanceof Human human) {
            human.clearRangedStrafeMotion();
        } else {
            strafe(0.0F, 0.0F);
        }
        this.mob.setZza(0.0F);
        this.mob.setXxa(0.0F);
    }

    private void strafe(float forward, float right) {
        if (mob instanceof Human human) dev.felix.hostilehumans.behavior.combat.ranged.RangedTerrainCombat.strafe(human, forward, right);
        else mob.getMoveControl().strafe(forward, right);
    }

    void setOrbitSpeed(boolean orbiting) {
        if (this.mob instanceof Human human) {
            RangedStrafeSpeed.setBow(human, orbiting);
        }
    }
}

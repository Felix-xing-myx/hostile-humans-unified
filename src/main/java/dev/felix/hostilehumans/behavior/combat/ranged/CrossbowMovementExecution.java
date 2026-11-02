package dev.felix.hostilehumans.behavior.combat.ranged;

import dev.felix.hostilehumans.entity.HumanEntity;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedMovementDecision;
import dev.felix.hostilehumans.entity.Human;


import dev.felix.hostilehumans.behavior.combat.ranged.RangedMovementDecision.Mode;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedFiringPosition;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedStrafeSpeed;
import dev.felix.hostilehumans.soldier.SoldierOrder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Movement adapter under the original crossbow Goal; owns no charge or shot clock. */
final class CrossbowMovementExecution<T extends HumanEntity & CrossbowAttackMob> {
    record Request(double attackRadiusSqr, double minimumBand, double maximumBand,
                   RangedMovementDecision decision, boolean canRun) { }
    record Result(boolean visible, boolean canEngage) { }
    private final T mob;
    private final double speedModifier;
    private int updatePathDelay;
    private int nextFiringPositionTick;
    private boolean pursuingTarget;
    private boolean strafingClockwise;
    private int strafingTime;
    // Start retreating at 16 blocks, but keep retreating until 20 blocks so
    // a single step does not repeatedly switch between orbit and retreat.
    private static final double RETREAT_DISTANCE_SQR = 16.0D * 16.0D;
    private static final double RETREAT_RESUME_DISTANCE_SQR = 20.0D * 20.0D;
    private static final double HOLD_DISTANCE_SQR = 24.0D * 24.0D;

    CrossbowMovementExecution(T mob, double speedModifier) { this.mob = mob; this.speedModifier = speedModifier; }
    void reset() { pursuingTarget = false; strafingTime = 0; }
    void start() { nextFiringPositionTick = mob.tickCount; reset(); }
    Result tick(LivingEntity livingentity, double d0, boolean flag, Request request) {
            Mode mode = request.decision().mode();
            if (mode == Mode.UNAVAILABLE) { setOrbitSpeed(false); strafe(0, 0); return new Result(false, false); }
            boolean holdingPosition = mode == Mode.HOLD;
            if (mob instanceof Human human && mode == Mode.ADVANCE) {
                setOrbitSpeed(false);
                human.clearRangedStrafeMotion();
                dev.felix.hostilehumans.navigation.PursuitNavigation.update(human, livingentity, speedModifier);
                mob.getLookControl().setLookAt(livingentity, 45, 35);
                return new Result(flag, d0 <= request.attackRadiusSqr() && request.decision().canEngage());
            }
            if(mob instanceof Human human) {
                RangedEngagement.observe(human, livingentity, flag && d0 <= request.attackRadiusSqr());
                dev.felix.hostilehumans.behavior.combat.ranged.RangedFiringMemory.observe(human,livingentity,
                        flag && d0<=request.attackRadiusSqr(),request.attackRadiusSqr());
                if(dev.felix.hostilehumans.behavior.combat.ranged.RangedFiringMemory.recover(human,livingentity,speedModifier)) {
                    setOrbitSpeed(false);
                    human.clearRangedStrafeMotion();mob.setXxa(0);mob.setZza(0);this.strafingTime=0;
                    this.pursuingTarget=false;
                    mob.getLookControl().setLookAt(livingentity,45.0F,35.0F);
                    return new Result(flag, d0<=request.attackRadiusSqr() && request.decision().canEngage());
                }
            }
            if (mob instanceof Human human) {
                var terrain = dev.felix.hostilehumans.navigation.CombatTerrainNavigation.update(human, livingentity, speedModifier, flag && d0 <= request.attackRadiusSqr());
                if (terrain == dev.felix.hostilehumans.navigation.CombatTerrainNavigation.Decision.EXHAUSTED)
                    RangedEngagement.failure(human, livingentity, RangedEngagement.Failure.TERRAIN_EXHAUSTED);
                if (terrain.ownsMovement()) {
                    setOrbitSpeed(false); strafe(0, 0); this.strafingTime = 0;
                    mob.getLookControl().setLookAt(livingentity, 45.0F, 35.0F);
                    return new Result(flag, d0 <= request.attackRadiusSqr() && request.decision().canEngage());
                }
            }
            boolean retreating = mode == Mode.RETREAT;
            boolean needsShootingAngle = mode == Mode.WAIT_AIM;
            boolean canEngage = request.decision().canEngage();
            setOrbitSpeed(request.decision().orbitSpeed());
            if (holdingPosition) {
                this.pursuingTarget = false;
                if (!SoldierOrder.isReturningToHoldPosition((Human)this.mob)) {
                    this.mob.getNavigation().stop();
                }
                this.updatePathDelay = 0;
                this.strafingTime = 0;
            } else if (mode == Mode.SEARCH_WINDOW) {
                if (this.pursuingTarget && (!(mob instanceof Human actor) || !RangedEngagement.mayApproach(actor, livingentity))) {
                    this.mob.getNavigation().stop(); this.updatePathDelay = 0;
                }
                this.pursuingTarget = false;
                if ((this.mob.getNavigation().isDone() || this.mob.getNavigation().isStuck())
                        && this.mob.tickCount >= this.nextFiringPositionTick) {
                    var window = this.mob instanceof Human human
                            ? RangedFiringPosition.searchAttackWindow(human, livingentity,
                                    RangedTerrainCombat.minimumFiringRange(human, Math.sqrt(RETREAT_DISTANCE_SQR)),
                                    Math.sqrt(request.attackRadiusSqr()), 10, 12)
                            : new RangedFiringPosition.AttackWindow(RangedFiringPosition.AttackStatus.UNAVAILABLE, null);
                    Path firingPath = window.path();
                    double speed = request.canRun() ? this.speedModifier : this.speedModifier * .65D;
                    boolean moved = firingPath != null && this.mob instanceof Human actor
                            && RangedFiringPosition.startAttackWindow(actor, livingentity, firingPath, speed, request.attackRadiusSqr());
                    if (this.mob instanceof Human actor) RangedEngagement.window(actor, livingentity, window, moved);
                    if (firingPath != null) {
                        if (mob instanceof Human human) RangedTerrainCombat.searchCompleted(human, livingentity, moved);
                    } else if (window.retryApproach()) {
                        if (this.mob instanceof Human human && human.isInWater()) human.beginCombatFiringShore(livingentity, this.speedModifier);
                        else {
                            this.mob.getNavigation().stop();
                            if (window.failed() && mob instanceof Human human) RangedTerrainCombat.searchCompleted(human, livingentity, false);
                        }
                    }
                    if (this.mob instanceof Human actor && (window.retryApproach() || firingPath != null && !moved)
                            && RangedEngagement.mayApproach(actor, livingentity) && !RangedWeaponCustody.isActive(actor))
                        dev.felix.hostilehumans.navigation.PursuitNavigation.update(actor, livingentity, speed);
                    this.nextFiringPositionTick = this.mob.tickCount + window.retryTicks();
                }
                this.strafingTime = 0; strafe(0, 0);
                this.mob.getLookControl().setLookAt(livingentity, 30.0F, 30.0F);
                return new Result(false, false);
            } else if (retreating && mob instanceof Human human) {
                human.getNavigation().stop();
                this.pursuingTarget = false;
                strafe(-0.80F, this.strafingClockwise ? 0.45F : -0.45F);
            } else if (retreating) {
                if (this.pursuingTarget) {
                    this.mob.getNavigation().stop();
                    this.updatePathDelay = 0;
                    this.pursuingTarget = false;
                }
                --this.updatePathDelay;
                if (this.updatePathDelay <= 0 && (!(this.mob instanceof Human human)
                        || dev.felix.hostilehumans.navigation.BasicNavigationBudget.tryAcquire(human, 1))) {
                    Vec3 away = DefaultRandomPos.getPosAway(this.mob, 14, 6, livingentity.position());
                    if (away != null && livingentity.distanceToSqr(away) > d0 + 4.0D) {
                        this.mob.getNavigation().moveTo(
                                away.x, away.y, away.z,
                                request.canRun() ? this.speedModifier : this.speedModifier * 0.65D
                        );
                    } else {
                        this.mob.getNavigation().stop();
                        strafe(-0.80F, this.strafingClockwise ? 0.45F : -0.45F);
                    }
                    this.updatePathDelay = 7 + this.mob.getRandom().nextInt(6);
                }
                this.strafingTime = 0;
            } else if (mode == Mode.APPROACH) {
                --this.updatePathDelay;
                if (this.mob instanceof Human pursuingHuman) {
                    this.pursuingTarget = dev.felix.hostilehumans.navigation.PursuitNavigation.update(pursuingHuman, livingentity,
                            request.canRun() ? this.speedModifier : this.speedModifier * 0.5D);
                } else if (this.updatePathDelay <= 0 && (!(this.mob instanceof Human human)
                        || dev.felix.hostilehumans.navigation.BasicNavigationBudget.tryAcquire(human, 1))) {
                    // On land, close only beyond firing reach. In water,
                    // continue toward the 24-block tactical band even while
                    // the target remains within crossbow firing reach.
                    this.mob.getNavigation().moveTo(
                            (Entity)livingentity,
                            request.canRun() ? this.speedModifier : this.speedModifier * 0.5D
                    );
                    this.pursuingTarget = true;
                    this.updatePathDelay = CrossbowGoal.PATHFINDING_DELAY_RANGE.sample(this.mob.getRandom());
                }
                if (this.mob instanceof Human human) {
                    human.approachCombatTargetInWater(livingentity,
                            request.maximumBand(), this.speedModifier);
                }
                this.strafingTime = 0;
            } else if (needsShootingAngle) {
                if (this.pursuingTarget) {
                    this.mob.getNavigation().stop();
                    this.updatePathDelay = 0;
                    this.pursuingTarget = false;
                }
                // The target is visible; keep this firing lane while the
                // five-tick visibility confirmation accumulates. Blindly
                // stepping away here could put a newly found angle behind cover.
                this.mob.getNavigation().stop();
                this.strafingTime = 0;
                strafe(0.0F, 0.0F);
            } else {
                this.pursuingTarget = false;
                this.updatePathDelay = 0;
                this.mob.getNavigation().stop();
                if (d0 <= request.maximumBand() * request.maximumBand()) {
                    if (++this.strafingTime >= 36) {
                        if (this.mob.getRandom().nextFloat() < 0.35F) {
                            this.strafingClockwise = !this.strafingClockwise;
                        }
                        this.strafingTime = 0;
                    }
                    // Crossbow users orbit inside the 16-24 block band at a
                    // lower lateral speed than bow users.
                    strafe(0.0F, this.strafingClockwise ? 0.75F : -0.75F);
                } else {
                    this.strafingTime = 0;
                }
            }
            if (retreating && this.mob.getNavigation().isDone()) {
                strafe(-0.80F, this.strafingClockwise ? 0.45F : -0.45F);
            }
            this.mob.getLookControl().setLookAt((Entity)livingentity, 30.0f, 30.0f);
            return new Result(flag, canEngage);
    }

    void strafe(float forward, float right) {
        if (mob instanceof Human human) dev.felix.hostilehumans.behavior.combat.ranged.RangedTerrainCombat.strafe(human, forward, right);
        else mob.getMoveControl().strafe(forward, right);
    }

    void setOrbitSpeed(boolean orbiting) {
        if (this.mob instanceof Human human) {
            RangedStrafeSpeed.setCrossbow(human, orbiting);
        }
    }

}

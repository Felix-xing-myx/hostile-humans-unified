package com.craftix.hostile_humans.entity.ai.goal;


import com.craftix.hostile_humans.entity.HumanEntity;
import dev.felix.hostilehumans.core.RangedMovementDecision;
import dev.felix.hostilehumans.core.RangedMovementDecision.Mode;
import com.craftix.hostile_humans.entity.entities.Human;
import club.someoneice.humangunner.RangedFiringPosition;
import club.someoneice.humangunner.RangedStrafeSpeed;
import club.someoneice.humangunner.SoldierOrder;
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
                club.someoneice.humangunner.PursuitNavigation.update(human, livingentity, speedModifier);
                mob.getLookControl().setLookAt(livingentity, 45, 35);
                return new Result(flag, d0 <= request.attackRadiusSqr() && request.decision().canEngage());
            }
            if(mob instanceof Human human) {
                club.someoneice.humangunner.RangedFiringMemory.observe(human,livingentity,
                        flag && d0<=request.attackRadiusSqr(),request.attackRadiusSqr());
                if(club.someoneice.humangunner.RangedFiringMemory.recover(human,livingentity,speedModifier)) {
                    setOrbitSpeed(false);
                    human.clearRangedStrafeMotion();mob.setXxa(0);mob.setZza(0);this.strafingTime=0;
                    this.pursuingTarget=false;
                    mob.getLookControl().setLookAt(livingentity,45.0F,35.0F);
                    return new Result(flag, d0<=request.attackRadiusSqr() && request.decision().canEngage());
                }
            }
            if (mob instanceof Human human && club.someoneice.humangunner.CombatTerrainNavigation
                    .update(human, livingentity, speedModifier, flag && d0 <= request.attackRadiusSqr()).ownsMovement()) {
                setOrbitSpeed(false);
                human.clearRangedStrafeMotion();
                mob.setXxa(0); mob.setZza(0);
                this.strafingTime = 0;
                mob.getLookControl().setLookAt(livingentity, 45.0F, 35.0F);
                return new Result(flag, d0 <= request.attackRadiusSqr() && request.decision().canEngage());
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
                // A blocked firing lane is not a reason to circle blindly.
                // Find an accessible nearby shot instead; the fleeing branch
                // above returns before this offensive repositioning logic.
                if (this.pursuingTarget) {
                    this.mob.getNavigation().stop();
                    this.updatePathDelay = 0;
                    this.pursuingTarget = false;
                }
                if ((this.mob.getNavigation().isDone() || this.mob.getNavigation().isStuck())
                        && this.mob.tickCount >= this.nextFiringPositionTick) {
                    Path firingPath = this.mob instanceof Human human
                            ? RangedFiringPosition.findAttackWindowPath(
                                    human, livingentity, club.someoneice.humangunner.RangedTerrainCombat.minimumFiringRange(human, Math.sqrt(RETREAT_DISTANCE_SQR)),
                                    Math.sqrt(request.attackRadiusSqr()), 10, 12)
                            : null;
                    if (firingPath != null) {
                        boolean moved = this.mob.getNavigation().moveTo(firingPath,
                                request.canRun() ? this.speedModifier : this.speedModifier * 0.65D);
                        if (moved && this.mob instanceof Human human) {
                            club.someoneice.humangunner.RangedFiringMemory.selected(human,livingentity,firingPath,request.attackRadiusSqr());
                            club.someoneice.humangunner.SoldierDialogue.event(human, "reposition");
                        }
                        if (mob instanceof Human human) club.someoneice.humangunner.RangedTerrainCombat.searchCompleted(human, livingentity, moved);
                    } else if (!(this.mob instanceof Human human && RangedFiringPosition.hasPendingVisibleSearch(human))) {
                        if (this.mob instanceof Human human && human.isInWater()) {
                            human.beginCombatFiringShore(livingentity, this.speedModifier);
                        } else {
                            this.mob.getNavigation().stop();
                            if (mob instanceof Human human) club.someoneice.humangunner.RangedTerrainCombat.searchCompleted(human, livingentity, false);
                        }
                    }
                    this.nextFiringPositionTick = this.mob.tickCount
                            + (this.mob instanceof Human human ? RangedFiringPosition.retryDelay(human, firingPath, 40)
                            : firingPath == null ? 40 : 10);
                }
                this.strafingTime = 0;
                strafe(0.0F, 0.0F);
                this.mob.setZza(0.0F);
                this.mob.setXxa(0.0F);
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
                        || club.someoneice.humangunner.BasicNavigationBudget.tryAcquire(human, 1))) {
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
                    this.pursuingTarget = club.someoneice.humangunner.PursuitNavigation.update(pursuingHuman, livingentity,
                            request.canRun() ? this.speedModifier : this.speedModifier * 0.5D);
                } else if (this.updatePathDelay <= 0 && (!(this.mob instanceof Human human)
                        || club.someoneice.humangunner.BasicNavigationBudget.tryAcquire(human, 1))) {
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
        if (mob instanceof Human human) club.someoneice.humangunner.RangedTerrainCombat.strafe(human, forward, right);
        else mob.getMoveControl().strafe(forward, right);
    }

    void setOrbitSpeed(boolean orbiting) {
        if (this.mob instanceof Human human) {
            RangedStrafeSpeed.setCrossbow(human, orbiting);
        }
    }

}

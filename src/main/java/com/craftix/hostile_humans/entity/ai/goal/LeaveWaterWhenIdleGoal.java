package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.entities.Human;
import club.someoneice.humangunner.MovementSpeedController;
import club.someoneice.humangunner.SoldierOrder;
import club.someoneice.humangunner.ShoreSeekingPolicy;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;

/** Gives water exit priority over combat and idle movement until the Human is ashore. */
public final class LeaveWaterWhenIdleGoal extends Goal {
    private static final int FAILED_PATH_RETRY_TICKS = 40;
    private static final int REPATH_DELAY_TICKS = 12;
    private static final int STUCK_REPATH_TICKS = 40;
    private static final double MOVE_SPEED = 1.0D;
    private static final double MIN_PROGRESS_SQUARED = 0.0004D;

    private final Human human;
    @Nullable
    private Path shorePath;
    @Nullable
    private LivingEntity interruptedCombatTarget;
    @Nullable
    private LivingEntity interruptedFleeTarget;
    private boolean interruptedFleeing;
    @Nullable
    private BlockPos lastDestination;
    private int nextPathAttemptTick;
    private int stuckTicks;
    private double lastX;
    private double lastY;
    private double lastZ;

    public LeaveWaterWhenIdleGoal(Human human) {
        this.human = human;
        // Stagger expensive shore searches when a wave enters water together.
        this.nextPathAttemptTick = human.tickCount + Math.floorMod(human.getId(), REPATH_DELAY_TICKS);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!ShoreSeekingPolicy.shouldAttemptShore(
                    !this.human.level().isClientSide,
                    this.human.isEffectiveAi(),
                    this.human.isInWater(),
                    this.human.isInLava())
                || this.human.tickCount < this.nextPathAttemptTick) {
            return false;
        }

        this.shorePath = this.human.findNearestShorePath(null, this.retreatThreat());
        this.nextPathAttemptTick = this.human.tickCount + (this.shorePath == null
                ? FAILED_PATH_RETRY_TICKS
                : REPATH_DELAY_TICKS);
        // Keep shore seeking active even when a path cannot be found on this
        // attempt. Otherwise combat movement takes over for the retry delay and
        // can send the Human deeper into water or leave it stuck in place.
        this.interruptedCombatTarget = this.human.getTarget();
        this.interruptedFleeing = this.human.isFleeing;
        this.interruptedFleeTarget = this.human.toAvoid;
        this.human.requestShoreTransition();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return ShoreSeekingPolicy.shouldContinueSeekingShore(
                this.human.isInWater(), this.human.isInLava());
    }

    @Override
    public void start() {
        // Conflicting combat movement goals may clear their target from stop().
        // Preserve it across the short shore approach so the Human resumes the
        // same fight once it has reached land.
        boolean returningToOwner = this.human.hasOwner()
                && SoldierOrder.isReturningFromRetreat(this.human);
        if (!returningToOwner
                && this.interruptedCombatTarget != null
                && this.interruptedCombatTarget.isAlive()
                && (this.human.getTarget() == null || !this.human.getTarget().isAlive())) {
            this.human.setTarget(this.interruptedCombatTarget);
        }
        if (!returningToOwner && this.interruptedFleeing) {
            LivingEntity fleeTarget = this.interruptedFleeTarget != null
                    && this.interruptedFleeTarget.isAlive()
                    ? this.interruptedFleeTarget
                    : this.interruptedCombatTarget != null && this.interruptedCombatTarget.isAlive()
                    ? this.interruptedCombatTarget
                    : null;
            if (fleeTarget != null) {
                this.human.toAvoid = fleeTarget;
                this.human.isFleeing = true;
                MovementSpeedController.retreat(this.human, true);
            }
        }
        if (this.shorePath != null) {
            this.stuckTicks = 0;
            this.rememberPosition();
            this.lastDestination = this.shorePath.getTarget();
            this.human.startSeekingShore(this.shorePath, MOVE_SPEED);
        } else {
            this.stuckTicks = 0;
            this.rememberPosition();
            this.lastDestination = null;
            this.human.startSeekingShoreWithoutPath();
        }
    }

    @Override
    public void tick() {
        double dx = this.human.getX() - this.lastX;
        double dy = this.human.getY() - this.lastY;
        double dz = this.human.getZ() - this.lastZ;
        if (dx * dx + dy * dy + dz * dz >= MIN_PROGRESS_SQUARED) {
            this.stuckTicks = 0;
            this.rememberPosition();
        } else {
            this.stuckTicks++;
        }

        boolean pathNeedsRefresh = this.shorePath == null
                || this.human.getNavigation().isDone()
                || this.stuckTicks >= STUCK_REPATH_TICKS;
        if (pathNeedsRefresh && this.human.tickCount >= this.nextPathAttemptTick) {
            LivingEntity retreatThreat = this.retreatThreat();
            Path nextPath = this.human.findNearestShorePath(
                    this.stuckTicks >= STUCK_REPATH_TICKS ? this.lastDestination : null,
                    retreatThreat);
            if (nextPath == null && this.stuckTicks >= STUCK_REPATH_TICKS) {
                // If every alternate dry point failed, retry the previous
                // destination once rather than abandoning a still-usable path.
                nextPath = this.human.findNearestShorePath(null, retreatThreat);
            }
            if (nextPath == null) {
                this.shorePath = null;
                this.lastDestination = null;
                this.stuckTicks = 0;
                this.rememberPosition();
                this.human.pauseSeekingShore();
                this.nextPathAttemptTick = this.human.tickCount + FAILED_PATH_RETRY_TICKS;
                return;
            }
            this.shorePath = nextPath;
            this.lastDestination = nextPath.getTarget();
            this.stuckTicks = 0;
            this.rememberPosition();
            this.nextPathAttemptTick = this.human.tickCount + REPATH_DELAY_TICKS;
            this.human.continueSeekingShore(nextPath, MOVE_SPEED);
        }
    }

    @Override
    public void stop() {
        this.shorePath = null;
        this.interruptedCombatTarget = null;
        this.interruptedFleeTarget = null;
        this.interruptedFleeing = false;
        this.lastDestination = null;
        this.stuckTicks = 0;
        this.human.stopSeekingShore();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private void rememberPosition() {
        this.lastX = this.human.getX();
        this.lastY = this.human.getY();
        this.lastZ = this.human.getZ();
    }

    @Nullable
    private LivingEntity retreatThreat() {
        if (!this.human.isFleeing) {
            return null;
        }
        if (this.human.toAvoid != null && this.human.toAvoid.isAlive()) {
            return this.human.toAvoid;
        }
        LivingEntity target = this.human.getTarget();
        return target != null && target.isAlive() ? target : null;
    }

}

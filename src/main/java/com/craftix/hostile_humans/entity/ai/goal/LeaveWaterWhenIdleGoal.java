package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.entities.Human;
import club.someoneice.humangunner.ShoreSeekingPolicy;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;

/** Guides idle Humans toward land without taking movement from active combat. */
public final class LeaveWaterWhenIdleGoal extends Goal {
    private static final int FAILED_PATH_RETRY_TICKS = 100;
    private static final int REPATH_DELAY_TICKS = 40;
    private static final int STUCK_REPATH_TICKS = 80;
    private static final double MOVE_SPEED = 1.0D;
    private static final double MIN_PROGRESS_SQUARED = 0.0004D;

    private final Human human;
    @Nullable
    private Path shorePath;
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
        // Swimming ascent is handled by HumanMoveControl. Claiming JUMP here
        // needlessly blocked unrelated combat and movement reactions.
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!ShoreSeekingPolicy.shouldAttemptShore(
                    !this.human.level().isClientSide,
                    this.human.isEffectiveAi(),
                    this.human.isInWater(),
                    this.human.isInLava(),
                    this.hasLivingCombatTarget(),
                    this.human.isFleeing)) {
            return false;
        }

        if (ShoreSeekingPolicy.shouldSearchForShorePath(
                this.human.tickCount, this.nextPathAttemptTick)) {
            this.shorePath = this.human.findNearestShorePath(null);
            this.nextPathAttemptTick = this.human.tickCount + (this.shorePath == null
                    ? FAILED_PATH_RETRY_TICKS
                    : REPATH_DELAY_TICKS);
        } else {
            // Acquire an idle movement lease, but defer pathfinding until this
            // Human's staggered search slot to avoid a server-thread burst.
            this.shorePath = null;
        }
        // Keep idle shore assistance active even when this path attempt fails;
        // movement orders/wandering must not replace the dry-bank fallback.
        this.human.requestShoreTransition();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return ShoreSeekingPolicy.shouldContinueSeekingShore(
                this.human.isInWater(), this.human.isInLava(),
                this.hasLivingCombatTarget(), this.human.isFleeing);
    }

    @Override
    public void start() {
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
        if (this.shorePath == null || this.human.getNavigation().isDone()) {
            this.human.continueSeekingShoreWithoutPath(MOVE_SPEED);
        }
        if (pathNeedsRefresh && this.human.tickCount >= this.nextPathAttemptTick) {
            Path nextPath = this.human.findNearestShorePath(
                    this.stuckTicks >= STUCK_REPATH_TICKS ? this.lastDestination : null);
            if (nextPath == null) {
                this.shorePath = null;
                this.lastDestination = null;
                this.stuckTicks = 0;
                this.rememberPosition();
                this.human.continueSeekingShoreWithoutPath(MOVE_SPEED);
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

    private boolean hasLivingCombatTarget() {
        LivingEntity target = this.human.getTarget();
        return target != null && target.isAlive();
    }

}

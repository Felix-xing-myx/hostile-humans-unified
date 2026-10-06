package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.entities.Human;
import club.someoneice.humangunner.ShoreSeekingPolicy;
import club.someoneice.humangunner.ShoreProgressTracker;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;

/** Guides idle Humans toward land without taking movement from active combat. */
public final class LeaveWaterWhenIdleGoal extends Goal {
    private static final int FAILED_PATH_RETRY_TICKS = 40;
    private static final int REPATH_DELAY_TICKS = 40;
    private static final double MOVE_SPEED = 1.0D;

    private final Human human;
    @Nullable
    private Path shorePath;
    @Nullable
    private BlockPos lastDestination;
    private int nextPathAttemptTick;
    private final ShoreProgressTracker progress = new ShoreProgressTracker();

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
                    this.human.isFleeing,
                    this.human.isPursuingWaterLoot())) {
            return false;
        }

        if (this.maySearchForShorePath()) {
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
                this.hasLivingCombatTarget(), this.human.isFleeing,
                this.human.isPursuingWaterLoot());
    }

    @Override
    public void start() {
        if (this.shorePath != null) {
            this.progress.reset(this.human.tickCount);
            this.lastDestination = this.shorePath.getTarget();
            this.human.startSeekingShore(this.shorePath, MOVE_SPEED);
        } else {
            this.progress.reset(this.human.tickCount);
            this.human.startSeekingShoreWithoutPath();
            this.lastDestination = this.human.getShoreDestination();
        }
    }

    @Override
    public void tick() {
        // A brief water/land boundary crossing can release Human's shore lease
        // while this Goal is still running. Reacquire it without waiting for damage.
        if (!this.human.isSeekingShore()) {
            if (this.shorePath != null && !this.shorePath.isDone())
                this.human.startSeekingShore(this.shorePath, MOVE_SPEED);
            else this.human.startSeekingShoreWithoutPath();
        }
        Path livePath = this.human.getNavigation().getPath();
        BlockPos waypoint = livePath != null && !livePath.isDone()
                ? livePath.getNextNodePos() : this.human.getShoreDestination();
        if (waypoint != null) {
            double dx = waypoint.getX() + 0.5D - this.human.getX();
            double dz = waypoint.getZ() + 0.5D - this.human.getZ();
            this.progress.observe(this.human.tickCount,
                    livePath != null && !livePath.isDone() ? livePath.getNextNodeIndex() : 0,
                    Math.sqrt(dx * dx + dz * dz));
        }
        boolean stalled = this.progress.isStalled(this.human.tickCount);
        boolean pathNeedsRefresh = this.shorePath == null
                || this.human.getNavigation().isDone()
                || stalled;
        if (this.shorePath == null || this.human.getNavigation().isDone()) {
            this.human.continueSeekingShoreWithoutPath(MOVE_SPEED);
        }
        if (pathNeedsRefresh && this.maySearchForShorePath()) {
            Path nextPath = this.human.findNearestShorePath(
                    stalled ? this.lastDestination : null);
            if (nextPath == null) {
                this.shorePath = null;
                // Stop the old live route and switch to direct bank steering;
                // otherwise navigation.isDone() remains false and blocks fallback.
                BlockPos previousDestination = this.lastDestination;
                this.human.startSeekingShoreWithoutPath(stalled ? null : previousDestination,
                        stalled ? previousDestination : null);
                this.lastDestination = this.human.getShoreDestination();
                if (stalled || !java.util.Objects.equals(previousDestination, this.lastDestination))
                    this.progress.reset(this.human.tickCount);
                this.nextPathAttemptTick = this.human.tickCount + FAILED_PATH_RETRY_TICKS;
                return;
            }
            this.shorePath = nextPath;
            this.lastDestination = nextPath.getTarget();
            this.progress.reset(this.human.tickCount);
            this.nextPathAttemptTick = this.human.tickCount + REPATH_DELAY_TICKS;
            this.human.continueSeekingShore(nextPath, MOVE_SPEED);
        }
    }

    @Override
    public void stop() {
        this.shorePath = null;
        this.lastDestination = null;
        this.progress.reset(this.human.tickCount);
        this.human.stopSeekingShore();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean hasLivingCombatTarget() {
        LivingEntity target = this.human.getTarget();
        return target != null && target.isAlive();
    }

    private boolean maySearchForShorePath() {
        return ShoreSeekingPolicy.shouldSearchForShorePath(
                this.human.tickCount, this.nextPathAttemptTick)
                && ShoreSeekingPolicy.reserveShorePathSearch(
                this.human.getServer(), this.human.getServer().getTickCount());
    }

}

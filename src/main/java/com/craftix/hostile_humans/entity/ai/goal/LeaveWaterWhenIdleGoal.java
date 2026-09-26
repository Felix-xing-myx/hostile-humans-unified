package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.entities.Human;
import club.someoneice.humangunner.SoldierOrder;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;

/** Guides Humans to dry ground while preserving combat targets and safety orders. */
public final class LeaveWaterWhenIdleGoal extends Goal {
    private static final int FAILED_PATH_RETRY_TICKS = 100;
    private static final int REPATH_DELAY_TICKS = 12;
    private static final int STUCK_REPATH_TICKS = 60;
    private static final double MOVE_SPEED = 1.0D;
    private static final double MIN_PROGRESS_SQUARED = 0.0004D;

    private final Human human;
    @Nullable
    private Path shorePath;
    @Nullable
    private LivingEntity interruptedCombatTarget;
    @Nullable
    private BlockPos lastDestination;
    private int nextPathAttemptTick;
    private int stuckTicks;
    private double lastX;
    private double lastY;
    private double lastZ;

    public LeaveWaterWhenIdleGoal(Human human) {
        this.human = human;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        boolean emergencyEscape = this.isCriticalBreathEmergency();
        LivingEntity target = this.human.getTarget();
        boolean targetNeedsShore = this.isShoreCombatTarget(target);
        if (this.human.level().isClientSide
                || !this.human.isEffectiveAi()
                // A land target must not keep the combat goal in charge of
                // water navigation: let the shore route take movement until
                // the Human reaches dry ground, then combat resumes there.
                || target != null && !emergencyEscape && !targetNeedsShore
                || this.human.isFleeing && !emergencyEscape
                || this.shouldRemainAtPost() && !emergencyEscape
                || this.isFollowingOwnerInWater() && !emergencyEscape
                || !this.human.isInWater()
                || this.human.isInLava()
                || this.human.isUsingItem()
                || this.human.tickCount < this.nextPathAttemptTick) {
            return false;
        }

        this.shorePath = this.human.findNearestShorePath();
        this.nextPathAttemptTick = this.human.tickCount + (this.shorePath == null
                ? FAILED_PATH_RETRY_TICKS
                : REPATH_DELAY_TICKS);
        this.interruptedCombatTarget = this.shorePath != null && targetNeedsShore ? target : null;
        return this.shorePath != null;
    }

    @Override
    public boolean canContinueToUse() {
        boolean emergencyEscape = this.isCriticalBreathEmergency();
        LivingEntity target = this.human.getTarget();
        return this.shorePath != null
                && (target == null || emergencyEscape || this.isShoreCombatTarget(target))
                && (!this.human.isFleeing || emergencyEscape)
                && (!this.shouldRemainAtPost() || emergencyEscape)
                && (!this.isFollowingOwnerInWater() || emergencyEscape)
                && this.human.isInWater()
                && !this.human.isInLava()
                && !this.human.isUsingItem();
    }

    @Override
    public void start() {
        // Conflicting combat movement goals may clear their target from stop().
        // Preserve it across the short shore approach so the Human resumes the
        // same fight once it has reached land.
        if (this.interruptedCombatTarget != null
                && this.interruptedCombatTarget.isAlive()
                && this.human.getTarget() == null) {
            this.human.setTarget(this.interruptedCombatTarget);
        }
        if (this.shorePath != null) {
            this.stuckTicks = 0;
            this.rememberPosition();
            this.lastDestination = this.shorePath.getTarget();
            this.human.startSeekingShore(this.shorePath, MOVE_SPEED);
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

        boolean pathNeedsRefresh = this.human.getNavigation().isDone()
                || this.stuckTicks >= STUCK_REPATH_TICKS;
        if (pathNeedsRefresh && this.human.tickCount >= this.nextPathAttemptTick) {
            Path nextPath = this.human.findNearestShorePath(this.stuckTicks >= STUCK_REPATH_TICKS
                    ? this.lastDestination
                    : null);
            if (nextPath == null && this.stuckTicks >= STUCK_REPATH_TICKS) {
                // If every alternate dry point failed, retry the previous
                // destination once rather than abandoning a still-usable path.
                nextPath = this.human.findNearestShorePath();
            }
            if (nextPath == null) {
                this.shorePath = null;
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
        this.lastDestination = null;
        this.stuckTicks = 0;
        this.human.stopSeekingShore();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean isFollowingOwnerInWater() {
        if (!this.human.hasOwner() || SoldierOrder.get(this.human) != SoldierOrder.FOLLOW) {
            return false;
        }
        LivingEntity owner = this.human.getOwner();
        return owner != null && owner.isInWater();
    }

    private void rememberPosition() {
        this.lastX = this.human.getX();
        this.lastY = this.human.getY();
        this.lastZ = this.human.getZ();
    }

    private boolean shouldRemainAtPost() {
        if (this.human.isOrderedToSit()) {
            return true;
        }
        if (!this.human.hasOwner()) {
            return false;
        }
        SoldierOrder order = SoldierOrder.get(this.human);
        return order == SoldierOrder.HOLD_POSITION || order == SoldierOrder.GUARD;
    }

    private boolean isCriticalBreathEmergency() {
        return this.human.shouldCatchBreath
                && this.human.getAirSupply() <= this.human.getMaxAirSupply() / 4;
    }

    private boolean isShoreCombatTarget(@Nullable LivingEntity target) {
        return target != null && target.isAlive() && !target.isInWater();
    }
}

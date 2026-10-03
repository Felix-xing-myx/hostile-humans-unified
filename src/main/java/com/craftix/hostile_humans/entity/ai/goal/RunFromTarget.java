package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.entities.Human;
import java.util.EnumSet;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class RunFromTarget
extends Goal {
    protected final Human human;
    protected final float maxDist;
    protected final Predicate<LivingEntity> avoidPredicate;
    protected final Predicate<LivingEntity> predicateOnAvoidEntity;
    private final double walkSpeedModifier;
    private final double sprintSpeedModifier;
    @Nullable
    protected Path path;
    Vec3 targetPos = null;
    private int nextRouteRetryTick;
    private Vec3 lastRoutePosition = Vec3.ZERO;
    private int stalledTicks;

    public RunFromTarget(Human p_25027_, float p_25029_, double p_25030_, double p_25031_) {
        this(p_25027_, p_25052_ -> true, p_25029_, p_25030_, p_25031_, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test);
    }

    public RunFromTarget(Human p_25040_, Predicate<LivingEntity> p_25042_, float p_25043_, double p_25044_, double p_25045_, Predicate<LivingEntity> p_25046_) {
        this.human = p_25040_;
        this.avoidPredicate = p_25042_;
        this.maxDist = p_25043_;
        this.walkSpeedModifier = p_25044_;
        this.sprintSpeedModifier = p_25045_;
        this.predicateOnAvoidEntity = p_25046_;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    public boolean canUse() {
        if (club.someoneice.humangunner.SoldierOrder.isHoldingPosition(this.human)) return false;
        boolean desperate = club.someoneice.humangunner.RetreatRecoveryPolicy.shouldForceRetreat(
                this.human.getHealth() / Math.max(1.0F, this.human.getMaxHealth()));
        if (!desperate && this.human.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            return false;
        }
        if (!desperate && !HumanUtil.isLowHp((LivingEntity)this.human)) {
            return false;
        }
        if (!this.human.shouldStartFleeingThisCombat()) {
            return false;
        }
        if (this.human.getTarget() != null || this.human.toAvoid == null || !(this.human.toAvoid.distanceTo((Entity)this.human) < 15.0f)) {
            this.human.toAvoid = this.human.getTarget();
        }
        if (this.human.toAvoid == null) {
            return false;
        }
        if (this.human.tickCount < this.nextRouteRetryTick) return false;
        // Enter escape even when the planner is queued; safe local movement bridges it.
        this.generatePathAwayFromAttacker();
        return true;
    }

    private boolean generatePathAwayFromAttacker() {
        double currentDistanceSqr = this.human.toAvoid.distanceToSqr(this.human);
        int budget = club.someoneice.humangunner.RetreatRouteContinuity.claimSearch(this.human, 4);
        // Queued work retries cheaply each tick; an actual failed search backs off.
        this.nextRouteRetryTick = this.human.tickCount + (budget == 0 ? 1 : 5);
        for (int i = 0; i < budget; ++i) {
            Vec3 candidate = DefaultRandomPos.getPosAway(
                    this.human, 64, 7, this.human.toAvoid.position());
            if (candidate == null || !club.someoneice.humangunner.RetreatRecoveryPolicy
                    .isUsefulRetreatDestination(currentDistanceSqr,
                            this.human.toAvoid.distanceToSqr(candidate))) {
                continue;
            }
            Path candidatePath = this.human.getNavigation().createPath(
                    candidate.x, candidate.y, candidate.z, 0);
            if (candidatePath != null && candidatePath.canReach()) {
                this.path = candidatePath;
                this.targetPos = candidate;
                club.someoneice.humangunner.RetreatRouteContinuity.cancelSearch(this.human);
                return true;
            }
        }
        if (budget > 0) club.someoneice.humangunner.RetreatRouteContinuity.cancelSearch(this.human);
        return false;
    }

    public boolean canContinueToUse() {
        if (club.someoneice.humangunner.SoldierOrder.isHoldingPosition(this.human)) return false;
        Player player;
        if (!club.someoneice.humangunner.RetreatRecoveryPolicy.shouldForceRetreat(
                this.human.getHealth() / Math.max(1.0F, this.human.getMaxHealth()))
                && !HumanUtil.isLowHp((LivingEntity)this.human)) {
            return false;
        }
        LivingEntity livingEntity = this.human.toAvoid;
        if (livingEntity instanceof Player && ((player = (Player)livingEntity).isSpectator() || player.isCreative())) {
            return false;
        }
        if (this.human.toAvoid == null) {
            return false;
        }
        if (this.human.distanceToSqr((Entity)this.human.toAvoid) > 576.0) {
            return false;
        }
        // Airborne/landing transitions do not mean the escape has ended.
        // A temporarily absent path is repaired by tick(), not by stop().
        return this.human.toAvoid.isAlive();
    }

    public void start() {
        this.human.isFleeing = true;
        this.human.setTarget(null);
        this.human.getNavigation().moveTo(this.path, this.walkSpeedModifier);
        this.nextRouteRetryTick = this.human.tickCount + (this.path == null ? 1 : 5);
        this.lastRoutePosition = this.human.position();
        this.stalledTicks = 0;
    }

    public void stop() {
        club.someoneice.humangunner.RetreatRouteContinuity.cancelSearch(this.human);
        // Do not leave a long escape route running after health has recovered.
        if (club.someoneice.humangunner.MovementContinuity.ownsRoute(this.human, this.path)) {
            this.human.getNavigation().stop();
        }
        this.path = null;
        this.targetPos = null;
        this.stalledTicks = 0;
        this.human.isFleeing = false;
        this.human.toAvoid = null;
    }

    public void tick() {
        if (this.human.toAvoid == null) {
            return;
        }
        this.human.isFleeing = true;
        club.someoneice.humangunner.MovementContinuity.escapeWhilePlanning(this.human, this.human.toAvoid);
        double moved = this.human.position().distanceToSqr(this.lastRoutePosition);
        this.stalledTicks = this.human.onGround() && moved < 0.0025D ? this.stalledTicks + 1 : 0;
        this.lastRoutePosition = this.human.position();
        boolean extension = club.someoneice.humangunner.RetreatRouteContinuity
                .needsExtension(this.human, this.human.toAvoid);
        if (this.human.tickCount >= this.nextRouteRetryTick
                && (extension || this.stalledTicks >= 5)) {
            // Keep the old escape until a replacement is actually available.
            if (this.generatePathAwayFromAttacker()) {
                this.human.getNavigation().moveTo(this.path, this.walkSpeedModifier);
                this.stalledTicks = 0;
                this.nextRouteRetryTick = this.human.tickCount + 10;
            }
        }
        if (this.human.distanceToSqr((Entity)this.human.toAvoid) < 144.0) {
            this.human.getNavigation().setSpeedModifier(this.sprintSpeedModifier);
        } else {
            this.human.getNavigation().setSpeedModifier(this.walkSpeedModifier);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }
}


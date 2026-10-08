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
    private Path pendingPath;
    private final club.someoneice.humangunner.RetreatShoreEscape shoreEscape =
            new club.someoneice.humangunner.RetreatShoreEscape();
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
        // One retreat controller at a time; this is the fallback when adaptive AI is disabled.
        if (club.someoneice.humangunner.CombatAiConfig.forHuman(this.human).enabled()) return false;
        if (club.someoneice.humangunner.LowHealthCombatController.shouldHoldGround(this.human, this.human.getTarget())) return false;
        if (club.someoneice.humangunner.SoldierOrder.isHoldingPosition(this.human)
                && !club.someoneice.humangunner.RetreatRecoveryPolicy.shouldForceRetreat(this.human)) return false;
        boolean desperate = club.someoneice.humangunner.RetreatRecoveryPolicy.shouldForceRetreat(this.human);
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
        this.pendingPath = this.human.isInWater() ? null : this.generatePathAwayFromAttacker();
        return true;
    }

    private Path generatePathAwayFromAttacker() {
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
            if (club.someoneice.humangunner.RetreatPathSafety.isSafe(
                    this.human, this.human.toAvoid, candidatePath, true)) {
                club.someoneice.humangunner.RetreatRouteContinuity.cancelSearch(this.human);
                return candidatePath;
            }
        }
        if (budget > 0) club.someoneice.humangunner.RetreatRouteContinuity.cancelSearch(this.human);
        return null;
    }

    public boolean canContinueToUse() {
        if (club.someoneice.humangunner.LowHealthCombatController.shouldHoldGround(this.human, this.human.toAvoid)) return false;
        if (club.someoneice.humangunner.SoldierOrder.isHoldingPosition(this.human)
                && !club.someoneice.humangunner.RetreatRecoveryPolicy.shouldForceRetreat(this.human)) return false;
        Player player;
        if (!club.someoneice.humangunner.RetreatRecoveryPolicy.shouldForceRetreat(this.human)
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
        updateSprint();
        if (club.someoneice.humangunner.RetreatPathSafety.tryMove(this.human, this.pendingPath, movementSpeed())) {
            this.path = this.pendingPath;
            this.targetPos = Vec3.atBottomCenterOf(this.path.getTarget());
        }
        this.pendingPath = null;
        if (this.human.isInWater()) this.path = this.shoreEscape.tick(this.human, this.human.toAvoid, movementSpeed());
        this.nextRouteRetryTick = this.human.tickCount + (this.path == null ? 1 : 5);
        this.lastRoutePosition = this.human.position();
        this.stalledTicks = 0;
    }

    public void stop() {
        club.someoneice.humangunner.RetreatRouteContinuity.cancelSearch(this.human);
        // Do not leave a long escape route running after health has recovered.
        if (club.someoneice.humangunner.MovementContinuity.ownsRoute(this.human, this.path)) {
            this.human.getNavigation().stop();
            if (this.human.isSeekingShore()) this.human.stopSeekingShore();
        }
        this.shoreEscape.reset();
        this.pendingPath = null;
        this.path = null;
        this.targetPos = null;
        this.stalledTicks = 0;
        if (this.human.explosionThreat == null) this.human.isFleeing = false;
        if (this.human.getTarget() == null) club.someoneice.humangunner.MovementSpeedController.normal(this.human);
        else club.someoneice.humangunner.MovementSpeedController.combat(this.human, false);
        LivingEntity previousThreat = this.human.toAvoid;
        if (previousThreat != null && previousThreat.isAlive() && this.human.canAttack(previousThreat)
                && club.someoneice.humangunner.LowHealthCombatController.shouldHoldGround(this.human, previousThreat))
            this.human.setTarget(previousThreat);
        this.human.toAvoid = null;
    }

    public void tick() {
        if (this.human.toAvoid == null) {
            return;
        }
        this.human.isFleeing = true;
        updateSprint();
        if (this.human.isInWater()) {
            Path shore = this.shoreEscape.tick(this.human, this.human.toAvoid, movementSpeed());
            if (shore != null) this.path = shore;
            this.human.getNavigation().setSpeedModifier(movementSpeed());
            return;
        }
        club.someoneice.humangunner.MovementContinuity.escapeWhilePlanning(this.human, this.human.toAvoid);
        double moved = this.human.position().distanceToSqr(this.lastRoutePosition);
        this.stalledTicks = this.human.onGround() && moved < 0.0025D ? this.stalledTicks + 1 : 0;
        this.lastRoutePosition = this.human.position();
        boolean extension = club.someoneice.humangunner.RetreatRouteContinuity
                .needsExtension(this.human, this.human.toAvoid);
        if (this.human.tickCount >= this.nextRouteRetryTick
                && (extension || this.stalledTicks >= 5)) {
            // Keep the old escape until a replacement is actually available.
            Path replacement = this.generatePathAwayFromAttacker();
            if (club.someoneice.humangunner.RetreatPathSafety.tryMove(this.human, replacement, movementSpeed())) {
                this.path = replacement;
                this.targetPos = Vec3.atBottomCenterOf(replacement.getTarget());
                this.stalledTicks = 0;
                this.nextRouteRetryTick = this.human.tickCount + 10;
            }
        }
        if (club.someoneice.humangunner.MovementContinuity.ownsRoute(this.human, this.path)
                && !club.someoneice.humangunner.RetreatPathSafety.isSafe(
                this.human, this.human.toAvoid, this.human.getNavigation().getPath(), false)) {
            // Keep valid detours during budget waits; cancel only a route that
            // has actually become unsafe as the pursuer changes position.
            this.human.getNavigation().stop();
            this.path = null;
            club.someoneice.humangunner.RetreatPathSafety.tryLocalStep(this.human, this.human.toAvoid, movementSpeed());
        }
        this.human.getNavigation().setSpeedModifier(movementSpeed());
    }

    private double movementSpeed() {
        return this.human.toAvoid != null && this.human.distanceToSqr(this.human.toAvoid) < 144.0D
                ? this.sprintSpeedModifier : this.walkSpeedModifier;
    }

    private void updateSprint() {
        club.someoneice.humangunner.MovementSpeedController.retreat(this.human,
                this.human.toAvoid != null && this.human.distanceToSqr(this.human.toAvoid) < 144.0D);
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }
}


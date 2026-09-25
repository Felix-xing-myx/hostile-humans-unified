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
    boolean jump;

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
        return this.generatePathAwayFromAttacker();
    }

    private boolean generatePathAwayFromAttacker() {
        double currentDistanceSqr = this.human.toAvoid.distanceToSqr(this.human);
        for (int i = 0; i < 10; ++i) {
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
                return true;
            }
        }
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
        if (!this.human.onGround()) {
            this.jump = true;
        } else if (this.jump) {
            this.jump = false;
            this.human.getNavigation().stop();
            if (this.generatePathAwayFromAttacker()) {
                this.human.getNavigation().moveTo(this.path, this.walkSpeedModifier);
            }
        }
        return !this.human.getNavigation().isDone();
    }

    public void start() {
        this.human.isFleeing = true;
        this.human.setTarget(null);
        this.human.getNavigation().moveTo(this.path, this.walkSpeedModifier);
    }

    public void stop() {
        this.human.isFleeing = false;
        this.human.onPlayerJumpCoolDown = 20;
        this.human.toAvoid = null;
    }

    public void tick() {
        if (this.human.toAvoid == null) {
            return;
        }
        this.human.isFleeing = true;
        if (this.human.distanceToSqr((Entity)this.human.toAvoid) < 144.0) {
            this.human.getNavigation().setSpeedModifier(this.sprintSpeedModifier);
        } else {
            this.human.getNavigation().setSpeedModifier(this.walkSpeedModifier);
        }
    }
}


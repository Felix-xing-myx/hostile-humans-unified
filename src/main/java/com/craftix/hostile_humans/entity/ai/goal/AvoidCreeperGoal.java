package com.craftix.hostile_humans.entity.ai.goal;

import club.someoneice.humangunner.BodyguardProtection;
import club.someoneice.humangunner.CreeperSafetyPolicy;
import club.someoneice.humangunner.CreeperMeleeCommitment;
import com.craftix.hostile_humans.entity.entities.Human;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class AvoidCreeperGoal
extends Goal {
    protected final PathfinderMob mob;
    protected final float maxDist;
    protected final Predicate<LivingEntity> avoidPredicate;
    protected final Predicate<LivingEntity> predicateOnAvoidEntity;
    private final double walkSpeedModifier;
    private final double sprintSpeedModifier;
    @Nullable
    protected Creeper toAvoid;
    @Nullable
    protected Path path;

    public AvoidCreeperGoal(PathfinderMob p_25027_, float p_25029_, double p_25030_, double p_25031_) {
        this(p_25027_, p_25052_ -> true, p_25029_, p_25030_, p_25031_, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test);
    }

    public AvoidCreeperGoal(PathfinderMob p_25040_, Predicate<LivingEntity> p_25042_, float p_25043_, double p_25044_, double p_25045_, Predicate<LivingEntity> p_25046_) {
        this.mob = p_25040_;
        this.avoidPredicate = p_25042_;
        this.maxDist = p_25043_;
        this.walkSpeedModifier = p_25044_;
        this.sprintSpeedModifier = p_25045_;
        this.predicateOnAvoidEntity = p_25046_;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Nullable
    Creeper getNearestEntity(List<Creeper> p_45983_, double p_45986_, double p_45987_, double p_45988_) {
        double d0 = -1.0;
        Creeper t = null;
        for (Creeper t1 : p_45983_) {
            if (!shouldEscape(t1) || !avoidPredicate.test(t1) || !predicateOnAvoidEntity.test(t1)) continue;
            double d1 = t1.distanceToSqr(p_45986_, p_45987_, p_45988_);
            boolean candidatePrimed = t1.swell > 0;
            boolean selectedPrimed = t != null && t.swell > 0;
            if (t == null || (candidatePrimed && !selectedPrimed)
                    || (candidatePrimed == selectedPrimed && d1 < d0)) {
                d0 = d1;
                t = t1;
            }
        }
        return t;
    }

    public boolean canUse() {
        this.toAvoid = this.getNearestEntity(this.mob.level().getEntitiesOfClass(Creeper.class, this.mob.getBoundingBox().inflate((double)this.maxDist, 10.0, (double)this.maxDist)), this.mob.getX(), this.mob.getY(), this.mob.getZ());
        if (this.toAvoid == null) {
            return false;
        }
        double minimumEscapeDistanceSqr = CreeperSafetyPolicy.safeDistanceSquared(this.toAvoid.isPowered());
        Vec3 vec3 = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            Vec3 candidate = DefaultRandomPos.getPosAway(
                    (PathfinderMob)this.mob, this.toAvoid.isPowered() ? 9 : 6, 3, this.toAvoid.position()
            );
            if (candidate != null
                    && this.toAvoid.distanceToSqr(candidate.x, candidate.y, candidate.z)
                    >= minimumEscapeDistanceSqr
                    && this.toAvoid.distanceToSqr(candidate.x, candidate.y, candidate.z)
                    <= Math.pow(Math.sqrt(minimumEscapeDistanceSqr) + 2.0D, 2.0D)) {
                vec3 = candidate;
                break;
            }
        }
        if (vec3 == null) {
            return false;
        }
        if (this.mob instanceof Human human && club.someoneice.humangunner.OptionalPathBudget.claim(
                human, club.someoneice.humangunner.OptionalPathBudget.Kind.EXPLOSION, 1) == 0) return false;
        this.path = this.mob.getNavigation().createPath(vec3.x, vec3.y, vec3.z, 0);
        return this.path != null;
    }

    public boolean canContinueToUse() {
        return shouldEscape(this.toAvoid) && !this.mob.getNavigation().isDone();
    }

    private boolean shouldEscape(@Nullable Creeper creeper) {
        if (creeper == null) return false;
        boolean escape = CreeperSafetyPolicy.escape(creeper.isAlive() && !creeper.isRemoved(),
                creeper.isIgnited(), creeper.swell, creeper.getSwellDir(), creeper.distanceToSqr(this.mob),
                creeper.isPowered(), this.mob instanceof Human human
                        && BodyguardProtection.protectingFromExplosion(human, creeper));
        return escape && !(this.mob instanceof Human human && CreeperMeleeCommitment.canFinish(human, creeper));
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    public void start() {
        PathfinderMob pathfinderMob = this.mob;
        if (pathfinderMob instanceof Human) {
            Human human = (Human)pathfinderMob;
            human.isFleeing = true;
            human.toAvoid = this.toAvoid;
        }
        this.mob.getNavigation().moveTo(this.path, this.walkSpeedModifier);
    }

    public void stop() {
        this.mob.getNavigation().stop();
        PathfinderMob pathfinderMob = this.mob;
        if (pathfinderMob instanceof Human) {
            Human human = (Human)pathfinderMob;
            if (human.explosionThreat == null) human.isFleeing = false;
            if (human.toAvoid == this.toAvoid) {
                human.toAvoid = null;
            }
        }
        this.toAvoid = null;
        this.path = null;
    }

    public void tick() {
        if (!shouldEscape(this.toAvoid)) {
            this.mob.getNavigation().stop();
            return;
        }
        PathfinderMob pathfinderMob = this.mob;
        if (pathfinderMob instanceof Human) {
            Human human = (Human)pathfinderMob;
            human.isFleeing = true;
            human.toAvoid = this.toAvoid;
        }
        this.mob.setTarget(null);
        if (this.mob.distanceToSqr((Entity)this.toAvoid) < 49.0) {
            this.mob.getNavigation().setSpeedModifier(this.sprintSpeedModifier);
        } else {
            this.mob.getNavigation().setSpeedModifier(this.walkSpeedModifier);
        }
    }
}


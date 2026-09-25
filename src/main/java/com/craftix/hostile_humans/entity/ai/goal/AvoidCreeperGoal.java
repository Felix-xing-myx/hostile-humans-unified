package com.craftix.hostile_humans.entity.ai.goal;

import club.someoneice.humangunner.HumanTargeting;
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
        if (this.toAvoid.swell == 0 && this.mob instanceof Human human) {
            boolean autonomousAttack = HumanTargeting.isAutonomousPlayerEnemy(human, this.toAvoid);
            boolean authorizedDefense = human.getTarget() == this.toAvoid
                    && human.canAttack(this.toAvoid);
            if (autonomousAttack || authorizedDefense) {
                return false;
            }
        }
        double currentDistanceSqr = this.toAvoid.distanceToSqr((Entity)this.mob);
        double minimumEscapeDistanceSqr = this.toAvoid.swell > 0
                ? Math.max(currentDistanceSqr, 36.0D)
                : currentDistanceSqr;
        Vec3 vec3 = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            Vec3 candidate = DefaultRandomPos.getPosAway(
                    (PathfinderMob)this.mob, 16, 7, this.toAvoid.position()
            );
            if (candidate != null
                    && this.toAvoid.distanceToSqr(candidate.x, candidate.y, candidate.z)
                    >= minimumEscapeDistanceSqr) {
                vec3 = candidate;
                break;
            }
        }
        if (vec3 == null) {
            return false;
        }
        this.path = this.mob.getNavigation().createPath(vec3.x, vec3.y, vec3.z, 0);
        return this.path != null;
    }

    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone();
    }

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
        PathfinderMob pathfinderMob = this.mob;
        if (pathfinderMob instanceof Human) {
            Human human = (Human)pathfinderMob;
            human.isFleeing = false;
            if (human.toAvoid == this.toAvoid) {
                human.toAvoid = null;
            }
        }
        this.toAvoid = null;
    }

    public void tick() {
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


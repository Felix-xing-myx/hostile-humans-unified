package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.Config;
import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.entities.Human;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;

public class NearestAttackableTargetGoalWithHumanLimiter<T extends LivingEntity>
extends NearestAttackableTargetGoal<T> {
    Human human;
    private boolean reacquireAfterDeath;

    public NearestAttackableTargetGoalWithHumanLimiter(Human p_26060_, Class<T> p_26061_, boolean p_26062_) {
        super((Mob)p_26060_, p_26061_, p_26062_);
        this.human = p_26060_;
    }

    public boolean canContinueToUse() {
        LivingEntity target1 = this.mob.getTarget();
        if (target1 == null) {
            target1 = this.targetMob;
        }
        if (target1 == null) {
            return false;
        }
        if (!target1.isAlive() || target1.isRemoved() || target1.level() != this.mob.level()) {
            reacquireAfterDeath = club.someoneice.humangunner.GunSupport.isGunItem(this.mob.getMainHandItem());
            return false;
        }
        if (!this.mob.canAttack(target1)) {
            return false;
        }
        Team team = this.mob.getTeam();
        Team team1 = target1.getTeam();
        if (team != null && team1 == team) {
            return false;
        }
        double d0 = this.getFollowDistance();
        if (this.mob.distanceToSqr((Entity)target1) > d0 * d0) {
            return false;
        }
        this.mob.setTarget(target1);
        return true;
    }

    @Override
    public boolean canUse() {
        if (club.someoneice.humangunner.RetreatRecoveryPolicy.shouldForceRetreat(this.human)
                || this.human.isFleeing) {
            return false;
        }
        if (!findCandidate()) return false;
        LivingEntity candidate = this.target;
        if (candidate == null) return false;
        if (candidate instanceof Player player && !canSelectPlayer(player)) return false;
        if (candidate instanceof net.minecraft.server.level.ServerPlayer player) {
            club.someoneice.humangunner.SoldierDialogue.hostile(this.human, player);
        }
        return true;
    }

    // Keep candidate search, player alerting and population queries out of the
    // entry method implicated in the C2 crash report. This is not a JVM fix:
    // HotSpot can still inline these methods during optimization.
    private boolean findCandidate() {
        this.targetConditions.range(this.getFollowDistance());
        if (reacquireAfterDeath || this.human.humanGunner$shouldFastGunRetarget()) {
            reacquireAfterDeath = false;
            this.findTarget();
            return this.target != null;
        }
        return super.canUse();
    }

    private boolean canSelectPlayer(Player player) {
        if (HumanUtil.isLookingAtTarget(this.human, player)) {
            this.human.isAlert = true;
            alertNearbyAllies();
        } else if (!this.human.isAlert) {
            return false;
        }
        if (isPlayerTargetLimitReached(player)) {
            this.setTarget(null);
            return false;
        }
        return true;
    }

    private void alertNearbyAllies() {
        Human observer = this.human;
        List<Human> allies = observer.level().getEntitiesOfClass(Human.class,
                observer.getBoundingBox().inflate(25.0),
                other -> other != observer && other.team.equals(observer.team));
        for (Human ally : allies) ally.isAlert = true;
    }

    private boolean isPlayerTargetLimitReached(Player player) {
        int limit = Config.maxTargeting();
        if (limit <= 0) return true;
        Human observer = this.human;
        List<Human> targetters = new java.util.ArrayList<>(Math.min(limit, 16));
        // Capture this query's candidate, not the goal's mutable target field.
        player.level().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Human.class),
                player.getBoundingBox().inflate(15.0),
                other -> other.getTarget() == player && other != observer, targetters, limit);
        return targetters.size() >= limit;
    }
}

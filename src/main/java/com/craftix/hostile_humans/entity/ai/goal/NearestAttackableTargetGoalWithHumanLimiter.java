package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.Config;
import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.entities.Human;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;

public class NearestAttackableTargetGoalWithHumanLimiter<T extends LivingEntity>
extends NearestAttackableTargetGoal<T> {
    Human human;

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

    public boolean canUse() {
        LivingEntity livingEntity;
        if (HumanUtil.isLowHp((LivingEntity)this.mob)) {
            return false;
        }
        this.targetConditions.range(this.getFollowDistance());
        boolean usable = super.canUse();
        if (usable && (livingEntity = this.target) instanceof Player) {
            Player player = (Player)livingEntity;
            if (HumanUtil.isLookingAtTarget((LivingEntity)this.human, (Entity)this.target)) {
                this.human.isAlert = true;
                List<Human> otherHumansOnTeam = this.human.level().getEntitiesOfClass(Human.class,
                        this.human.getBoundingBox().inflate(25.0), otherHuman ->
                                otherHuman != this.human && otherHuman.team.equals(this.human.team));
                for (Human otherHuman : otherHumansOnTeam) {
                    otherHuman.isAlert = true;
                }
            } else if (!this.human.isAlert) {
                return false;
            }
            int limit = Config.maxTargeting.get();
            List<Human> targetters = new java.util.ArrayList<>(Math.min(Math.max(0, limit), 16));
            if (limit > 0) {
                player.level().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Human.class),
                        player.getBoundingBox().inflate(15.0),
                        human -> human.getTarget() == this.target && human != this.mob, targetters, limit);
            }
            if (targetters.size() >= limit) {
                this.setTarget(null);
                return false;
            }
        }
        if (usable && !this.mob.getTags().contains("greeted") && Math.random() < (Double)Config.greetChance.get()) {
            this.mob.addTag("greeted");
            String name = "";
            if (this.mob.hasCustomName()) {
                name = this.mob.getCustomName().getString();
            }
            if (name.isEmpty()) {
                name = "Human";
            }
            if (this.target != null) {
                this.target.sendSystemMessage((Component)Component.literal((String)("<" + name + "> " + HumanUtil.greetings[(int)(Math.random() * (double)HumanUtil.greetings.length)])));
            }
        }
        return usable;
    }
}


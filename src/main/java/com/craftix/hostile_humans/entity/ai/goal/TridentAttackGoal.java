package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.Items;

public class TridentAttackGoal
extends RangedAttackGoal {
    private final Human human;

    public TridentAttackGoal(Human p_32450_, double p_32451_, int p_32452_, float p_32453_) {
        super((RangedAttackMob)p_32450_, p_32451_, p_32452_, p_32453_);
        this.human = p_32450_;
    }

    public boolean canUse() {
        if (this.human.getTarget() == null) {
            return false;
        }
        return super.canUse() && this.human.getMainHandItem().is(Items.TRIDENT) && (double)this.human.getTarget().distanceTo((Entity)this.human) > 2.5;
    }

    public void tick() {
        this.human.getNavigation().stop();
        super.tick();
    }

    public boolean canContinueToUse() {
        if (!this.human.getMainHandItem().is(Items.TRIDENT)) {
            return false;
        }
        return super.canContinueToUse();
    }

    public void start() {
        super.start();
        this.human.setAggressive(true);
        this.human.startUsingItem(InteractionHand.MAIN_HAND);
    }

    public void stop() {
        super.stop();
        this.human.stopUsingItem();
        this.human.setAggressive(false);
    }
}


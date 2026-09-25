package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.HumanEntity;
import java.util.Random;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

public class HumanGoal
extends Goal {
    protected final Random random = new Random();
    protected final Level level;
    protected final PathNavigation navigation;
    protected final HumanEntity mob;

    public HumanGoal(HumanEntity humanEntity) {
        this.mob = humanEntity;
        this.level = this.mob.level();
        this.navigation = this.mob.getNavigation();
    }

    public boolean canUse() {
        return this.mob.hasOwnerAndIsAlive();
    }
}


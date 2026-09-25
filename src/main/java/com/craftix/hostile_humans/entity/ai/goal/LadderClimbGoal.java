package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.HumanUtil;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

public class LadderClimbGoal
extends Goal {
    private final Mob entity;
    private Path path;

    public LadderClimbGoal(Mob entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    public boolean canUse() {
        if (this.entity.getTarget() == null) {
            return false;
        }
        if (!this.entity.getNavigation().isDone()) {
            this.path = this.entity.getNavigation().getPath();
            return this.path != null && this.entity.onClimbable();
        }
        return false;
    }

    public void tick() {
        int i = this.path.getNextNodeIndex();
        if (i + 1 < this.path.getNodeCount()) {
            int y = this.path.getNode((int)i).y;
            Node pointNext = this.path.getNode(i + 1);
            BlockState down = this.entity.level().getBlockState(this.entity.blockPosition().below());
            double yMotion = pointNext.y < y || pointNext.y == y && !HumanUtil.isLadder(down, (LivingEntity)this.entity, this.entity.blockPosition().below()) ? -0.15 : 0.15;
            this.entity.setDeltaMovement(this.entity.getDeltaMovement().add(0.0, yMotion, 0.0));
        }
    }
}


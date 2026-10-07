package com.craftix.hostile_humans.entity.ai.goal;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;

/** Vertical assistance only: the combat/order goal keeps ownership of MOVE and its route. */
public class LadderClimbGoal extends Goal {
    private final Mob entity;

    public LadderClimbGoal(Mob entity) { this.entity = entity; }

    @Override public boolean canUse() {
        Path path = entity.getNavigation().getPath();
        return entity.onClimbable() && path != null && !path.isDone();
    }

    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void tick() {
        Path path = entity.getNavigation().getPath();
        if (!entity.onClimbable() || path == null || path.isDone()) return;
        int next = path.getNextNodeIndex();
        if (next >= path.getNodeCount()) return;
        int targetY = path.getNode(next).y;
        if (Math.abs(targetY - entity.getY()) < 0.25D && next + 1 < path.getNodeCount())
            targetY = path.getNode(next + 1).y;
        double delta = targetY - entity.getY();
        if (Math.abs(delta) < 0.25D) return;
        double vertical = delta > 0 ? 0.15D : -0.15D;
        var motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x, vertical, motion.z);
    }
}

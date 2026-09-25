package com.craftix.hostile_humans.entity.ai.goal;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

public abstract class TrapdoorInteractGoal
extends Goal {
    protected Mob mob;
    protected BlockPos doorPos = BlockPos.ZERO;
    protected boolean hasDoor;
    private boolean passed;
    private float trapdoorOpenDirY;

    public TrapdoorInteractGoal(Mob p_25193_) {
        this.mob = p_25193_;
        if (!GoalUtils.hasGroundPathNavigation((Mob)p_25193_)) {
            throw new IllegalArgumentException("Unsupported mob type for TrapdoorInteractGoal");
        }
    }

    protected boolean isOpen() {
        if (!this.hasDoor) {
            return false;
        }
        BlockState blockstate = this.mob.level().getBlockState(this.doorPos);
        if (!(blockstate.getBlock() instanceof TrapDoorBlock)) {
            this.hasDoor = false;
            return false;
        }
        return (Boolean)blockstate.getValue((Property)TrapDoorBlock.OPEN);
    }

    protected void setOpen(boolean p_25196_) {
        BlockState blockstate;
        if (this.hasDoor && (blockstate = this.mob.level().getBlockState(this.doorPos)).getBlock() instanceof TrapDoorBlock) {
            blockstate.setValue((Property)TrapDoorBlock.OPEN, (Comparable)Boolean.valueOf(p_25196_));
            this.setOpenTrapdoor((Entity)this.mob, this.mob.level(), blockstate, this.doorPos, p_25196_);
        }
    }

    public boolean canUse() {
        if (!GoalUtils.hasGroundPathNavigation((Mob)this.mob)) {
            return false;
        }
        if (!this.mob.verticalCollision) {
            return false;
        }
        GroundPathNavigation groundpathnavigation = (GroundPathNavigation)this.mob.getNavigation();
        Path path = groundpathnavigation.getPath();
        if (path != null && !path.isDone() && groundpathnavigation.canOpenDoors()) {
            for (int i = 0; i < Math.min(path.getNextNodeIndex() + 2, path.getNodeCount()); ++i) {
                Node node = path.getNode(i);
                this.doorPos = new BlockPos(node.x, node.y, node.z);
                for (int j = 0; j < 3; ++j) {
                    this.doorPos = this.doorPos.above();
                    this.hasDoor = TrapdoorInteractGoal.isWoodenTrapdoor(this.mob.level(), this.doorPos);
                    if (!this.hasDoor) continue;
                    return true;
                }
            }
            this.doorPos = this.mob.blockPosition().above();
            this.hasDoor = TrapdoorInteractGoal.isWoodenTrapdoor(this.mob.level(), this.doorPos);
            return this.hasDoor;
        }
        return false;
    }

    public boolean canContinueToUse() {
        return !this.passed;
    }

    public void start() {
        this.passed = false;
        this.trapdoorOpenDirY = (float)((double)this.doorPos.getY() + 0.5 - this.mob.getY());
    }

    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void tick() {
        float f = (float)((double)this.doorPos.getY() + 0.5 - this.mob.getY());
        float f2 = this.trapdoorOpenDirY * f;
        if (f2 < 0.0f) {
            this.passed = true;
        }
    }

    public static boolean isWoodenTrapdoor(Level p_52746_, BlockPos p_52747_) {
        return TrapdoorInteractGoal.isWoodenTrapdoor(p_52746_.getBlockState(p_52747_));
    }

    public static boolean isWoodenTrapdoor(BlockState p_52818_) {
        return p_52818_.getBlock() instanceof TrapDoorBlock && p_52818_.is(BlockTags.WOODEN_TRAPDOORS);
    }

    public void setOpenTrapdoor(@Nullable Entity p_153166_, Level p_153167_, BlockState p_153168_, BlockPos p_153169_, boolean p_153170_) {
        if ((Boolean)p_153168_.getValue((Property)TrapDoorBlock.OPEN) != p_153170_) {
            p_153167_.setBlock(p_153169_, (BlockState)p_153168_.setValue((Property)TrapDoorBlock.OPEN, (Comparable)Boolean.valueOf(p_153170_)), 10);
            p_153167_.gameEvent(p_153166_, p_153170_ ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, p_153169_);
        }
    }
}


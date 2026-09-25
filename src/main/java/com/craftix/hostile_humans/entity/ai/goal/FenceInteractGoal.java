package com.craftix.hostile_humans.entity.ai.goal;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

public abstract class FenceInteractGoal
extends Goal {
    protected Mob mob;
    protected BlockPos fencePosPos = BlockPos.ZERO;
    protected boolean hasFence;
    private boolean passed;
    private float doorOpenDirX;
    private float fenceOpenDirZ;

    public FenceInteractGoal(Mob p_25193_) {
        this.mob = p_25193_;
        if (!GoalUtils.hasGroundPathNavigation((Mob)p_25193_)) {
            throw new IllegalArgumentException("Unsupported mob type for FenceInteractGoal");
        }
    }

    public static boolean isFence(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof FenceGateBlock;
    }

    protected boolean isOpen() {
        if (!this.hasFence) {
            return false;
        }
        BlockState blockstate = this.mob.level().getBlockState(this.fencePosPos);
        if (!(blockstate.getBlock() instanceof FenceGateBlock)) {
            this.hasFence = false;
            return false;
        }
        return (Boolean)blockstate.getValue((Property)FenceGateBlock.OPEN);
    }

    protected void setOpen(boolean p_25196_) {
        BlockState blockstate;
        if (this.hasFence && (blockstate = this.mob.level().getBlockState(this.fencePosPos)).getBlock() instanceof FenceGateBlock) {
            this.setOpen((FenceGateBlock)blockstate.getBlock(), (Entity)this.mob, this.mob.level(), blockstate, this.fencePosPos, p_25196_);
        }
    }

    public void setOpen(FenceGateBlock fenceGateBlock, @Nullable Entity p_153166_, Level p_153167_, BlockState p_153168_, BlockPos p_153169_, boolean p_153170_) {
        if (p_153168_.is((Block)fenceGateBlock) && (Boolean)p_153168_.getValue((Property)FenceGateBlock.OPEN) != p_153170_) {
            p_153167_.setBlock(p_153169_, (BlockState)p_153168_.setValue((Property)FenceGateBlock.OPEN, (Comparable)Boolean.valueOf(p_153170_)), 10);
            this.playSound(fenceGateBlock, p_153167_, p_153169_, p_153170_);
            p_153167_.gameEvent(p_153166_, p_153170_ ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, p_153169_);
        }
    }

    private void playSound(FenceGateBlock fenceGateBlock, Level p_52760_, BlockPos p_52761_, boolean p_52762_) {
        p_52760_.levelEvent((Player)null, p_52762_ ? this.getOpenSound() : this.getCloseSound(), p_52761_, 0);
    }

    private int getCloseSound() {
        return 1012;
    }

    private int getOpenSound() {
        return 1006;
    }

    public boolean canUse() {
        if (!GoalUtils.hasGroundPathNavigation((Mob)this.mob)) {
            return false;
        }
        GroundPathNavigation groundpathnavigation = (GroundPathNavigation)this.mob.getNavigation();
        Path path = groundpathnavigation.getPath();
        if (path != null && !path.isDone() && groundpathnavigation.canOpenDoors()) {
            for (int i = 0; i < Math.min(path.getNextNodeIndex() + 2, path.getNodeCount()); ++i) {
                Node node = path.getNode(i);
                this.fencePosPos = new BlockPos(node.x, node.y + 1, node.z);
                if (this.mob.distanceToSqr((double)this.fencePosPos.getX(), this.mob.getY(), (double)this.fencePosPos.getZ()) > 2.25) continue;
                this.hasFence = FenceInteractGoal.isFence(this.mob.level(), this.fencePosPos);
                if (!this.hasFence) continue;
                return true;
            }
            this.fencePosPos = this.mob.blockPosition();
            this.hasFence = FenceInteractGoal.isFence(this.mob.level(), this.fencePosPos);
            return this.hasFence;
        }
        return false;
    }

    public boolean canContinueToUse() {
        return !this.passed;
    }

    public void start() {
        this.passed = false;
        this.doorOpenDirX = (float)((double)this.fencePosPos.getX() + 0.5 - this.mob.getX());
        this.fenceOpenDirZ = (float)((double)this.fencePosPos.getZ() + 0.5 - this.mob.getZ());
    }

    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void tick() {
        float f1;
        float f = (float)((double)this.fencePosPos.getX() + 0.5 - this.mob.getX());
        float f2 = this.doorOpenDirX * f + this.fenceOpenDirZ * (f1 = (float)((double)this.fencePosPos.getZ() + 0.5 - this.mob.getZ()));
        if (f2 < 0.0f) {
            this.passed = true;
        }
    }
}


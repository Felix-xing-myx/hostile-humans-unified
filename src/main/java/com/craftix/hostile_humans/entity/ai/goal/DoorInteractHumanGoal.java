package com.craftix.hostile_humans.entity.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public abstract class DoorInteractHumanGoal
extends Goal {
    protected final Mob mob;
    protected BlockPos doorPos = BlockPos.ZERO;
    protected boolean hasDoor;
    private boolean passed;
    private float doorOpenDirX;
    private float doorOpenDirZ;

    protected DoorInteractHumanGoal(Mob mob) {
        this.mob = mob;
        if (!GoalUtils.hasGroundPathNavigation((Mob)mob)) {
            throw new IllegalArgumentException("Unsupported mob type for DoorInteractHumanGoal");
        }
    }

    protected boolean isOpen() {
        if (!this.hasDoor) {
            return false;
        }
        BlockState blockState = this.mob.level().getBlockState(this.doorPos);
        if (!(blockState.getBlock() instanceof DoorBlock)) {
            this.hasDoor = false;
            return false;
        }
        if (blockState.getValue((Property)DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            this.doorPos = this.doorPos.below();
            blockState = this.mob.level().getBlockState(this.doorPos);
            if (!(blockState.getBlock() instanceof DoorBlock)) {
                this.hasDoor = false;
                return false;
            }
        }
        return (Boolean)blockState.getValue((Property)DoorBlock.OPEN);
    }

    protected void setOpen(boolean open) {
        if (!this.hasDoor) {
            return;
        }
        BlockState blockState = this.mob.level().getBlockState(this.doorPos);
        Block block = blockState.getBlock();
        if (!(block instanceof DoorBlock)) {
            this.hasDoor = false;
            return;
        }
        DoorBlock doorBlock = (DoorBlock)block;
        if (blockState.getValue((Property)DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            this.doorPos = this.doorPos.below();
            blockState = this.mob.level().getBlockState(this.doorPos);
            Block block2 = blockState.getBlock();
            if (!(block2 instanceof DoorBlock)) {
                this.hasDoor = false;
                return;
            }
            DoorBlock normalizedDoorBlock = (DoorBlock)block2;
            doorBlock = normalizedDoorBlock;
        }
        if (DoorBlock.isWoodenDoor((Level)this.mob.level(), (BlockPos)this.doorPos)) {
            doorBlock.setOpen((Entity)this.mob, this.mob.level(), blockState, this.doorPos, open);
            return;
        }
        if (open) {
            DoorInteractHumanGoal.triggerNearbyMetalDoorOpeners(this.mob.level(), this.mob.getEyePosition(), this.doorPos);
        }
    }

    public boolean canUse() {
        PathNavigation pathNavigation = this.mob.getNavigation();
        if (!(pathNavigation instanceof GroundPathNavigation)) {
            return false;
        }
        GroundPathNavigation groundPathNavigation = (GroundPathNavigation)pathNavigation;
        Path path = groundPathNavigation.getPath();
        if (path != null && !path.isDone() && groundPathNavigation.canOpenDoors()) {
            for (int i = 0; i < Math.min(path.getNextNodeIndex() + 2, path.getNodeCount()); ++i) {
                Node node = path.getNode(i);
                this.doorPos = DoorInteractHumanGoal.normalizeDoorPos(this.mob.level(), new BlockPos(node.x, node.y + 1, node.z));
                if (this.mob.distanceToSqr((double)this.doorPos.getX(), this.mob.getY(), (double)this.doorPos.getZ()) > 2.25) continue;
                this.hasDoor = DoorInteractHumanGoal.isDoor(this.mob.level(), this.doorPos);
                if (!this.hasDoor) continue;
                return true;
            }
            this.doorPos = DoorInteractHumanGoal.normalizeDoorPos(this.mob.level(), this.mob.blockPosition().above());
            this.hasDoor = DoorInteractHumanGoal.isDoor(this.mob.level(), this.doorPos);
            return this.hasDoor;
        }
        return false;
    }

    public boolean canContinueToUse() {
        return !this.passed;
    }

    public void start() {
        this.passed = false;
        this.doorOpenDirX = (float)((double)this.doorPos.getX() + 0.5 - this.mob.getX());
        this.doorOpenDirZ = (float)((double)this.doorPos.getZ() + 0.5 - this.mob.getZ());
    }

    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void tick() {
        float f1;
        float f = (float)((double)this.doorPos.getX() + 0.5 - this.mob.getX());
        float f2 = this.doorOpenDirX * f + this.doorOpenDirZ * (f1 = (float)((double)this.doorPos.getZ() + 0.5 - this.mob.getZ()));
        if (f2 < 0.0f) {
            this.passed = true;
        }
    }

    protected static boolean isDoor(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof DoorBlock && state.is(BlockTags.DOORS);
    }

    protected static BlockPos normalizeDoorPos(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock && state.getValue((Property)DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        return pos;
    }

    private static void triggerNearbyMetalDoorOpeners(Level level, Vec3 from, BlockPos doorPos) {
        for (BlockPos nearbyPos : BlockPos.betweenClosed((int)(doorPos.getX() - 2), (int)(doorPos.getY() - 2), (int)(doorPos.getZ() - 2), (int)(doorPos.getX() + 2), (int)(doorPos.getY() + 2), (int)(doorPos.getZ() + 2))) {
            BlockState state = level.getBlockState(nearbyPos);
            Block block = state.getBlock();
            if (!DoorInteractHumanGoal.canMobSeeBlock(level, from, nearbyPos)) continue;
            if (block instanceof ButtonBlock) {
                ButtonBlock buttonBlock = (ButtonBlock)block;
                buttonBlock.press(state, level, nearbyPos);
                continue;
            }
            if (!(block instanceof LeverBlock)) continue;
            LeverBlock leverBlock = (LeverBlock)block;
            leverBlock.pull(state, level, nearbyPos);
            level.gameEvent((Entity)null, GameEvent.BLOCK_ACTIVATE, nearbyPos);
        }
    }

    private static boolean canMobSeeBlock(Level level, Vec3 from, BlockPos to) {
        return level.clip(new ClipContext(from, new Vec3((double)to.getX() + 0.5, (double)to.getY() + 0.5, (double)to.getZ() + 0.5), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, null)).getType() == HitResult.Type.MISS;
    }
}


package com.craftix.hostile_humans.mixin;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={DoorBlock.class, TrapDoorBlock.class, FenceGateBlock.class})
public abstract class DoorMixin {
    @Inject(method={"use"}, at={@At(value="RETURN")})
    public void useInject(BlockState p_57540_, Level pLevel, BlockPos p_57542_, Player pPlayer, InteractionHand p_57544_, BlockHitResult p_57545_, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue() == InteractionResult.CONSUME && !pLevel.isClientSide && !pPlayer.isCreative()) {
            for (Human human : pPlayer.level().getEntitiesOfClass(Human.class, pPlayer.getBoundingBox().inflate(16.0))) {
                human.setInvestigateSound(p_57545_.getBlockPos());
            }
        }
    }
}


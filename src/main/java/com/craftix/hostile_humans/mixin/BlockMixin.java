package com.craftix.hostile_humans.mixin;

import com.craftix.hostile_humans.entity.entities.Human;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Block.class})
public abstract class BlockMixin {
    @Inject(method={"playerWillDestroy"}, at={@At(value="TAIL")})
    public void useInject(Level pLevel, BlockPos pPos, BlockState pState, Player pPlayer, CallbackInfo ci) {
        if (!pLevel.isClientSide && !pPlayer.isCreative()) {
            for (Human human : pPlayer.level().getEntitiesOfClass(Human.class, pPlayer.getBoundingBox().inflate(16.0))) {
                human.setInvestigateSound(pPos);
            }
        }
    }

    @Inject(method={"setPlacedBy"}, at={@At(value="TAIL")})
    public void useInject(Level pLevel, BlockPos pPos, BlockState p_49849_, @Nullable LivingEntity entity, ItemStack p_49851_, CallbackInfo ci) {
        ServerPlayer pPlayer;
        if (entity instanceof ServerPlayer && !(pPlayer = (ServerPlayer)entity).isCreative()) {
            for (Human human : pPlayer.level().getEntitiesOfClass(Human.class, pPlayer.getBoundingBox().inflate(16.0))) {
                human.setInvestigateSound(pPos);
            }
        }
    }
}


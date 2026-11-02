package dev.felix.hostilehumans.cultivation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Three-seat destination, never an independent AI or a forced chunk loader. */
public final class CultivationTrainingBlock extends BaseEntityBlock {
    public CultivationTrainingBlock() {
        super(
                net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                                Blocks.IRON_BLOCK)
                        .strength(3));
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new CultivationTrainingBlockEntity(p, s);
    }

    @Override
    public void setPlacedBy(
            Level l, BlockPos p, BlockState s, LivingEntity actor, ItemStack stack) {
        super.setPlacedBy(l, p, s, actor, stack);
        if (!l.isClientSide && l.getBlockEntity(p) instanceof CultivationTrainingBlockEntity b)
            b.testMode(actor instanceof Player player && player.isCreative());
    }

    @Override
    public InteractionResult use(
            BlockState s,
            Level l,
            BlockPos p,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (l.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof net.minecraft.server.level.ServerPlayer server
                && l.getBlockEntity(p) instanceof CultivationTrainingBlockEntity b) {
            CultivationFacade.open(server, b);
            if (player.isShiftKeyDown()
                    && player.getItemInHand(hand).is(CultivationContent.SUPPLIES.get()))
                CultivationFacade.depositHeld(server, b, player.getItemInHand(hand).getCount());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        if (s.getBlock() != next.getBlock()
                && !l.isClientSide
                && l.getBlockEntity(p) instanceof CultivationTrainingBlockEntity b)
            CultivationService.removeFacility(b);
        super.onRemove(s, l, p, next, moving);
    }
}

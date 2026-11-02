package dev.felix.hostilehumans.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class SettlementInformationBlock extends Block implements EntityBlock {
    public SettlementInformationBlock(Properties properties) {
        super(properties);
    }

    @Override
    public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.INVISIBLE;
    }

    @Override
    public int getLightBlock(
            BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos position) {
        return 0;
    }

    @Override
    public boolean propagatesSkylightDown(
            BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos position) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new SettlementInformationBlockEntity(position, state);
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos position,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof SettlementSelectionTool)
            return InteractionResult.PASS;
        if (player instanceof ServerPlayer server) SettlementNetwork.openAuthor(server, position);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

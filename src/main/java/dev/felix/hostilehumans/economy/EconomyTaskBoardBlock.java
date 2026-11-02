package dev.felix.hostilehumans.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class EconomyTaskBoardBlock extends Block {
    public EconomyTaskBoardBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (player instanceof ServerPlayer author
                && author.getMainHandItem().getItem()
                        instanceof dev.felix.hostilehumans.settlement.SettlementSelectionTool
                && dev.felix.hostilehumans.settlement.SettlementAccess.author(author))
            return InteractionResult.PASS;
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer server)
            EconomyNetwork.openFacility(server, pos, EconomyView.Page.TASKS);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

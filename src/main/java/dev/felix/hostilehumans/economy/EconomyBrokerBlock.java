package dev.felix.hostilehumans.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class EconomyBrokerBlock extends BaseEntityBlock {
    private final Currency currency;

    public Currency currency() {
        return currency;
    }

    public EconomyBrokerBlock(Currency currency, Properties properties) {
        super(properties);
        this.currency = currency;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EconomyBrokerEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
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
            EconomyNetwork.openBroker(server, pos, currency);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock())
                && level instanceof net.minecraft.server.level.ServerLevel server)
            BrokerIndex.get(server).remove(pos);
        super.onRemove(state, level, pos, next, moving);
    }
}

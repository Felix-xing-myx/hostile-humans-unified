package dev.felix.hostilehumans.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class EconomyBrokerEntity extends BlockEntity {
    public EconomyBrokerEntity(BlockPos pos, BlockState state) {
        super(EconomyContent.BROKER_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof net.minecraft.server.level.ServerLevel server
                && getBlockState().getBlock() instanceof EconomyBrokerBlock broker)
            BrokerIndex.get(server).register(worldPosition, broker.currency());
    }
}

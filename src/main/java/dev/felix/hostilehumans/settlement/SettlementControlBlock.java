package dev.felix.hostilehumans.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Four faction appearances share one non-ticking interaction implementation. */
public final class SettlementControlBlock extends Block {
    private final String faction;

    public SettlementControlBlock(String faction, Properties properties) {
        super(properties);
        this.faction = faction;
    }

    public String faction() {
        return faction;
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos position,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (player instanceof ServerPlayer server) SettlementNetwork.openControl(server, position);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

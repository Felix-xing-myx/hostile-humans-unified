package dev.felix.hostilehumans.settlement;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class SettlementPlacementTool extends Item {
    SettlementPlacementTool(Properties properties) {
        super(properties);
    }

    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        if (!SettlementAccess.author(player)) return InteractionResult.FAIL;
        if (player.isShiftKeyDown() && SettlementTemplateFiles.hasPreview(player)) confirm(player);
        else if (!player.isShiftKeyDown() && SettlementWandPlacement.armed(player)) {
            try {
                SettlementWandPlacement.preview(player, context.getClickedPos().relative(context.getClickedFace()));
            } catch (IllegalArgumentException bad) {
                player.sendSystemMessage(Component.literal(bad.getMessage()));
            }
        } else if (player.isShiftKeyDown()) SettlementWandPlacement.open(player);
        return InteractionResult.CONSUME;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            if (server.isShiftKeyDown()) {
                if (SettlementTemplateFiles.hasPreview(server)) confirm(server);
                else SettlementWandPlacement.open(server);
            } else {
                SettlementWandPlacement.cancel(server);
                SettlementTemplateFiles.cancel(server);
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    private static void confirm(ServerPlayer player) {
        try {
            player.sendSystemMessage(Component.literal(SettlementTemplateFiles.confirm(player)));
            SettlementWandPlacement.cancel(player);
        } catch (IllegalArgumentException bad) {
            player.sendSystemMessage(Component.literal(bad.getMessage()));
        }
    }
}

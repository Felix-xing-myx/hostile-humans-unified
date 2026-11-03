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
        if (SettlementWandPlacement.openAdjustment(player)) return InteractionResult.CONSUME;
        if (player.isShiftKeyDown()) SettlementWandPlacement.open(player);
        else if (SettlementWandPlacement.armed(player)) {
            try {
                SettlementWandPlacement.preview(player, context.getClickedPos().relative(context.getClickedFace()));
            } catch (IllegalArgumentException bad) {
                player.sendSystemMessage(Component.literal(bad.getMessage()));
            }
        } else chooseFirst(player);
        return InteractionResult.CONSUME;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            if (!SettlementAccess.author(server)) return InteractionResultHolder.fail(player.getItemInHand(hand));
            if (!SettlementWandPlacement.openAdjustment(server)) {
                if (server.isShiftKeyDown()) SettlementWandPlacement.open(server);
                else if (!SettlementWandPlacement.armed(server)) chooseFirst(server);
                else server.displayClientMessage(Component.translatable(
                        "item.hostile_humans_unified.settlement_selection_tool.pick_placement"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    private static void chooseFirst(ServerPlayer player) {
        player.displayClientMessage(Component.translatable(
                "gui.hostile_humans_unified.settlement.wand_choose_first"), true);
    }
}

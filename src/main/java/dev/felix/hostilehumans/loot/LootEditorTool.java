package dev.felix.hostilehumans.loot;

import dev.felix.hostilehumans.settlement.SettlementAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Use-first intercepts container opening, so choosing a chest never rolls its contents. */
public final class LootEditorTool extends Item {
    public LootEditorTool(Properties properties) { super(properties); }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player && SettlementAccess.author(player))
            LootEditorNetwork.open(player, player.isShiftKeyDown() ? null : context.getClickedPos());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server && SettlementAccess.author(server)) LootEditorNetwork.open(server, null);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, Level level, java.util.List<net.minecraft.network.chat.Component> lines, TooltipFlag flag) {
        lines.add(net.minecraft.network.chat.Component.translatable("developer_guide.hover"));
    }
}

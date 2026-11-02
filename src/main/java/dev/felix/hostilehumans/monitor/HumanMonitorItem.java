package dev.felix.hostilehumans.monitor;

import net.minecraft.world.item.Item;

/** Creative-only entry; the server interaction handler opens the authenticated viewing session. */
public final class HumanMonitorItem extends Item {
    public HumanMonitorItem(Properties properties) { super(properties.stacksTo(1)); }
    @Override public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(
            net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        if (player instanceof net.minecraft.server.level.ServerPlayer server) MonitorTargetBinding.open(server, player.getItemInHand(hand));
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer server) MonitorTargetBinding.open(server, context.getItemInHand());
        return net.minecraft.world.InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}

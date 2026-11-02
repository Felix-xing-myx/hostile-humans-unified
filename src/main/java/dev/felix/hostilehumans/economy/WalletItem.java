package dev.felix.hostilehumans.economy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public final class WalletItem extends Item {
    public WalletItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) EconomyNetwork.openWallet(server);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, Level level, List<Component> lines, TooltipFlag flags) {
        var data = WalletCodec.read(stack);
        if (data.isEmpty()) {
            lines.add(Component.literal("Unknown wallet format; contents retained read-only."));
            return;
        }
        for (var currency : Currency.values())
            lines.add(
                    Component.literal(
                            currency.key()
                                    + ": "
                                    + data.get().balance().amount(currency)
                                    + " copper"));
        lines.add(Component.literal("Automatic coin pickup: " + data.get().balance().autoPickup()));
    }
}

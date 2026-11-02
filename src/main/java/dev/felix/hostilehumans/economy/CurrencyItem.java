package dev.felix.hostilehumans.economy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class CurrencyItem extends Item {
    private final Currency currency;
    private final Currency.Denomination denomination;

    public CurrencyItem(
            Currency currency, Currency.Denomination denomination, Properties properties) {
        super(properties);
        this.currency = currency;
        this.denomination = denomination;
    }

    public Currency currency() {
        return currency;
    }

    public Currency.Denomination denomination() {
        return denomination;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server && hand == InteractionHand.MAIN_HAND)
            dev.felix.hostilehumans.faction.FactionPassage.raiseHere(server, currency.faction());
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}

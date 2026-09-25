package club.someoneice.humangunner;

import net.minecraft.ChatFormatting;
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

/** Reports recruitment capacity and recalls the owner's hired humans. */
final class SoldierRosterItem extends Item {
    SoldierRosterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        RecruitmentLedger ledger = RecruitmentLedger.get(serverPlayer.server);
        player.displayClientMessage(Component.translatable("message.humangunner.roster.header"), false);
        for (int tier = 0; tier < 4; tier++) {
            player.displayClientMessage(Component.translatable(
                    "message.humangunner.roster.tier." + tier,
                    ledger.count(player.getUUID(), tier),
                    RecruitmentPolicy.limit(tier)
            ), false);
        }
        if (player.isShiftKeyDown()) {
            int recalled = HiredHumanRecall.recallAll(serverPlayer);
            player.displayClientMessage(Component.translatable(
                    "message.humangunner.roster.recalled", recalled
            ).withStyle(ChatFormatting.GREEN), false);
            player.getCooldowns().addCooldown(this, 20);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.humangunner.soldier_roster.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.humangunner.soldier_roster.recall_tooltip")
                .withStyle(ChatFormatting.GOLD));
    }
}

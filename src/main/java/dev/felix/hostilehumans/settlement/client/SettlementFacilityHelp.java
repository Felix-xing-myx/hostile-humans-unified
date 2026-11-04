package dev.felix.hostilehumans.settlement.client;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.cultivation.CultivationContent;
import dev.felix.hostilehumans.economy.EconomyBrokerBlock;
import dev.felix.hostilehumans.economy.EconomyContent;
import dev.felix.hostilehumans.faction.FactionContent;
import dev.felix.hostilehumans.settlement.SettlementContent;
import dev.felix.hostilehumans.settlement.SettlementControlBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID, value = Dist.CLIENT)
public final class SettlementFacilityHelp {
    private static final String PREFIX = "gui.hostile_humans_unified.settlement.help.facility.";

    private SettlementFacilityHelp() {}

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof BlockItem item)) return;
        var block = item.getBlock();
        String role;
        if (block instanceof SettlementControlBlock) role = "CONTROL";
        else if (block == FactionContent.TRIBUTE_CHEST.get()) role = "SUPPLY";
        else if (block == EconomyContent.TRADER.get()) role = "TRADER";
        else if (block == CultivationContent.TRAINING_BLOCK.get()) role = "TRAINING";
        else if (block == EconomyContent.TASK_BOARD.get()) role = "TASK_BOARD";
        else if (block instanceof EconomyBrokerBlock) role = "BROKER";
        else if (block == SettlementContent.INFORMATION.get()) role = "INFORMATION";
        else return;
        event.getToolTip().add(Component.translatable(PREFIX + role).withStyle(ChatFormatting.GRAY));
        switch (role) {
            case "CONTROL", "SUPPLY", "TRADER", "TRAINING" -> {
                event.getToolTip().add(Component.translatable(PREFIX + "prepare").withStyle(ChatFormatting.GRAY));
                event.getToolTip().add(Component.translatable(PREFIX + "wand").withStyle(ChatFormatting.GRAY));
            }
            case "TASK_BOARD" -> event.getToolTip().add(Component.translatable(PREFIX + "task_open").withStyle(ChatFormatting.GRAY));
            case "BROKER" -> event.getToolTip().add(Component.translatable(PREFIX + "broker_open").withStyle(ChatFormatting.GRAY));
            default -> { }
        }
    }
}

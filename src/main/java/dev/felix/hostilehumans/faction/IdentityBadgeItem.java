package dev.felix.hostilehumans.faction;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public final class IdentityBadgeItem extends Item {

    public IdentityBadgeItem(int legacyVariant, Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hostile_humans_unified.identity_badge.retired")
                .withStyle(ChatFormatting.GRAY));
    }
}

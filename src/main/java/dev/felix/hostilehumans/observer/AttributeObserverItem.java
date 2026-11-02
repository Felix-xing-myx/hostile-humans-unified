package dev.felix.hostilehumans.observer;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import java.util.List;

/** An inspection request tool; the network service owns all server authorization. */
public final class AttributeObserverItem extends Item {
    public AttributeObserverItem(Properties properties) { super(properties.stacksTo(1)); }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flags) {
        lines.add(Component.translatable("attribute_detail.observer_tooltip"));
    }
}

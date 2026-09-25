package club.someoneice.humangunner;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public final class IdentityBadgeItem extends Item {
    private final int clearance;

    IdentityBadgeItem(int clearance, Properties properties) {
        super(properties);
        this.clearance = clearance;
    }

    public int clearance() {
        return clearance;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.humangunner.identity_badge.tooltip." + clearance)
                .withStyle(ChatFormatting.GRAY));
    }
}

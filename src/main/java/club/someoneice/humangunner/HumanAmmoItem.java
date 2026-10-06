package club.someoneice.humangunner;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Supply tokens for human inventories; they are not player arrows or TaCZ ammunition. */
public final class HumanAmmoItem extends Item {
    private final boolean magazine;

    HumanAmmoItem(boolean magazine, Properties properties) {
        super(properties);
        this.magazine = magazine;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.humangunner.human_ammo.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(magazine
                ? "item.humangunner.human_magazine.tooltip"
                : "item.humangunner.human_arrow.tooltip").withStyle(ChatFormatting.GRAY));
    }
}

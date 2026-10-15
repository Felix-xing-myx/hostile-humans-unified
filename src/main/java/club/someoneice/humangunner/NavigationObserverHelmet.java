package club.someoneice.humangunner;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Uses vanilla leather armor rendering; only the selected observer receives debug data. */
public final class NavigationObserverHelmet extends ArmorItem {
    public NavigationObserverHelmet(Properties properties) { super(ArmorMaterials.LEATHER, Type.HELMET, properties); }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("navigation_debug.helmet_tip"));
    }
}

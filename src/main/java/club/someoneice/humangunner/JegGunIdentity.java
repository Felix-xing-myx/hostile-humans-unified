package club.someoneice.humangunner;

import net.minecraft.world.item.ItemStack;
import ttv.migami.jeg.item.GunItem;
import ttv.migami.jeg.item.AnimatedBowItem;
import ttv.migami.jeg.item.HarvestingGunItem;

/** Physical identity is independent of the shared NPC integration toggle. */
final class JegGunIdentity implements java.util.function.Predicate<ItemStack> {
    public boolean test(ItemStack stack) {
        return stack.getItem() instanceof GunItem && !(stack.getItem() instanceof AnimatedBowItem)
                && !(stack.getItem() instanceof HarvestingGunItem);
    }
}

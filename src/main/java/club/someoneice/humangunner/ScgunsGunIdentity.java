package club.someoneice.humangunner;
import net.minecraft.world.item.ItemStack;
/** Optional API type stays inside a class loaded only when scguns is installed. */
final class ScgunsGunIdentity implements java.util.function.Predicate<ItemStack> {
    public boolean test(ItemStack stack) { return stack.getItem() instanceof top.ribs.scguns.item.GunItem; }
}

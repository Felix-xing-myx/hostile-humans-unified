package club.someoneice.humangunner;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.item.ItemStack;
/** Shared recovery boundary; selection/custody never depends on a particular optional mod. */
final class MedicalSupplies {
    private MedicalSupplies() {}
    static boolean isSupply(ItemStack stack) { return FirstAidCompat.isSupply(stack) || ScgunsMedical.isSupply(stack); }
    static boolean isMedicine(ItemStack stack) { return FirstAidCompat.isMedicine(stack) || ScgunsMedical.isSupply(stack); }
    static boolean isMorphine(ItemStack stack) { return FirstAidCompat.isMorphine(stack); }
    static boolean canUse(Human human, ItemStack stack) { return FirstAidCompat.isSupply(stack) ? FirstAidCompat.canUse(human, stack) : ScgunsMedical.canUse(human, stack); }
    static double inventoryValue(ItemStack stack) { return FirstAidCompat.isSupply(stack) ? FirstAidCompat.inventoryValue(stack) : ScgunsMedical.inventoryValue(stack); }
    static int useDuration(ItemStack stack) { return FirstAidCompat.isSupply(stack) ? FirstAidCompat.useDuration(stack) : ScgunsMedical.useDuration(stack); }
    static float pendingHealing(Human human) { return FirstAidCompat.pendingHealing(human); }
    static void tick(Human human) { FirstAidCompat.tick(human); ScgunsMedical.tick(human); }
}

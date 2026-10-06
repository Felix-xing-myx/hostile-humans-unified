package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Ammunition lives in the actual backpack: no virtual replenishment or optional API types. */
public final class HumanAmmo {
    public static final ResourceLocation ARROW_ID = new ResourceLocation("humangunner", "human_arrow");
    public static final ResourceLocation MAGAZINE_ID = new ResourceLocation("humangunner", "human_magazine");

    private HumanAmmo() {}

    public static boolean isArrow(ItemStack stack) { return matches(stack, ARROW_ID); }
    public static boolean isMagazine(ItemStack stack) { return matches(stack, MAGAZINE_ID); }
    public static boolean isAmmo(ItemStack stack) { return isArrow(stack) || isMagazine(stack); }

    private static boolean matches(ItemStack stack, ResourceLocation id) {
        return !stack.isEmpty() && id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static int arrowCount(Human human) { return count(human, ARROW_ID); }
    public static int magazineCount(Human human) { return count(human, MAGAZINE_ID); }
    public static boolean hasArrows(Human human) { return arrowCount(human) > 0; }
    public static boolean consumeArrow(Human human) { return consume(human, ARROW_ID, 1); }
    public static int magazineCost(ItemStack gun) {
        return HumanAmmoPolicy.magazineCost(GunSupport.get().gunType(gun));
    }
    public static boolean canReload(Human human, ItemStack gun) {
        return GunSupport.get().isGun(gun) && magazineCount(human) >= magazineCost(gun);
    }
    public static boolean consumeMagazines(Human human, ItemStack gun) {
        return GunSupport.get().isGun(gun) && consume(human, MAGAZINE_ID, magazineCost(gun));
    }
    public static boolean canUseGun(Human human, ItemStack gun) {
        return GunSupport.get().isGun(gun)
                && (GunSupport.get().hasLoadedAmmo(gun) || canReload(human, gun));
    }

    private static int count(Human human, ResourceLocation id) {
        HumanData data = human.getData();
        if (data == null) return 0;
        int total = 0;
        for (int slot = 0; slot < data.getInventoryItemsSize(); slot++) {
            ItemStack stack = data.getInventoryItem(slot);
            if (matches(stack, id)) total += stack.getCount();
        }
        return total;
    }

    private static boolean consume(Human human, ResourceLocation id, int amount) {
        if (human.level().isClientSide || amount <= 0 || count(human, id) < amount) return false;
        HumanData data = human.getData();
        int remaining = amount;
        for (int slot = 0; slot < data.getInventoryItemsSize() && remaining > 0; slot++) {
            ItemStack stack = data.getInventoryItem(slot);
            if (!matches(stack, id)) continue;
            int taken = Math.min(stack.getCount(), remaining);
            ItemStack updated = stack.copy();
            updated.shrink(taken);
            data.setInventoryItem(slot, updated.isEmpty() ? ItemStack.EMPTY : updated);
            remaining -= taken;
        }
        return true;
    }
}

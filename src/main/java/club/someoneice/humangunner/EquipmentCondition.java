package club.someoneice.humangunner;

import net.minecraft.world.item.ItemStack;

/** One condition factor for equipment quality; never edits or discards a stack. */
final class EquipmentCondition {
    static final int MIN_AUTOMATIC_PICKUP_PERCENT = 10;

    private EquipmentCondition() { }

    static double remainingFraction(ItemStack stack) {
        if (stack.isEmpty()) return 0.0D;
        // Includes genuinely indestructible equipment and NBT Unbreakable.
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) return 1.0D;
        return Math.max(0.0D, Math.min(1.0D,
                1.0D - (double) stack.getDamageValue() / stack.getMaxDamage()));
    }

    static double value(ItemStack stack, double fullValue) {
        return Math.max(0.0D, fullValue) * remainingFraction(stack);
    }

    static boolean canAutomaticallyCollect(ItemStack stack) {
        // Integer comparison keeps the exactly-10% boundary independent of rounding.
        if (stack.isEmpty()) return false;
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) return true;
        long remaining = (long) stack.getMaxDamage() - Math.max(0, stack.getDamageValue());
        return remaining * 100L > (long) stack.getMaxDamage() * MIN_AUTOMATIC_PICKUP_PERCENT;
    }
}

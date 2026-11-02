package dev.felix.hostilehumans.inventory;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.equipment.EquipmentWearLimit;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/** One native durability seam covers incoming armor wear and enchantment-triggered wear alike. */
public final class WildArmorWear {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final Map<Human, EquipmentWearLimit> LIMITS = new WeakHashMap<>();
    private WildArmorWear() { }

    public static boolean hurt(ItemStack stack, int amount, RandomSource random, ServerPlayer player, LivingEntity owner) {
        if (!(owner instanceof Human human) || human.hasOwner() || human.level().isClientSide
                || amount <= 0 || !(stack.getItem() instanceof ArmorItem))
            return stack.hurt(amount, random, player);
        int slot = -1;
        for (int i = 0; i < SLOTS.length; i++) if (human.getItemBySlot(SLOTS[i]) == stack) { slot = i; break; }
        if (slot < 0) return stack.hurt(amount, random, player); // Backpack wear is not worn armor wear.
        EquipmentWearLimit limit = LIMITS.computeIfAbsent(human, ignored -> new EquipmentWearLimit(4, 10, 20));
        long now = human.level().getGameTime();
        int admitted = Math.min(amount, limit.remaining(slot, now));
        if (admitted == 0) return false;
        int before = stack.getDamageValue(), durability = Math.max(0, stack.getMaxDamage() - before);
        // Native Unbreaking and events run exactly once; the outer hurtAndBreak owns break callbacks and shrinking.
        boolean broken = stack.hurt(admitted, random, player);
        int lost = Math.min(admitted, Math.min(durability, Math.max(0, stack.getDamageValue() - before)));
        limit.lostDurability(slot, now, lost);
        return broken;
    }
}

package dev.felix.hostilehumans.inventory;

import dev.felix.hostilehumans.behavior.combat.HumanCombatDirector;
import dev.felix.hostilehumans.behavior.combat.melee.BetterCombatMeleeCombat;
import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.inventory.HumanLootManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;

/** Reusable controlled loadout capability, shared by menu validation and explicit equipment requests. */
public final class ControlledEquipmentPolicy {
    private ControlledEquipmentPolicy() { }
    public static boolean dualOffhand(Human human) { return HumanCombatDirector.strategies(human).dualWield(); }
    public static boolean handAllowed(Human human, ItemStack stack) {
        return !dualOffhand(human) || !SpartanEquipmentCompat.isShield(stack);
    }
    public static boolean oneHandWeapon(ItemStack stack) {
        return HumanLootManager.isNonGunWeapon(stack) && BetterCombatMeleeCombat.supportsOneHand(stack);
    }
    /** Explicit food switching never substitutes a potion, even if another mod marks potions edible. */
    public static boolean offhandFood(ItemStack stack) {
        return !stack.isEmpty() && stack.isEdible() && !(stack.getItem() instanceof PotionItem);
    }
    /** Empty vanilla consumption vessels are inventory items, not a sticky offhand equipment mode. */
    public static boolean supplyRemainder(ItemStack stack) {
        return stack.is(Items.GLASS_BOTTLE) || stack.is(Items.BOWL);
    }
    public static boolean defenseAllowed(Human human) { return !dualOffhand(human); }
}

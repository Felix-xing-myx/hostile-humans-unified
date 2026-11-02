package dev.felix.hostilehumans.behavior.combat;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.ranged.AmmoWeaponSelection;
import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
import dev.felix.hostilehumans.compat.spartan.SpartanRangedCompat;

import dev.felix.hostilehumans.entity.HumanUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;

/** Shared weapon classification and threat policy; never changes equipment or acquires a target. */
public final class CombatWeaponPolicy {
    private CombatWeaponPolicy() { }

    public static boolean isBowOrCrossbow(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem
                || SpartanEquipmentCompat.isSpartanRangedWeapon(stack));
    }

    public static boolean isCrossbow(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof CrossbowItem
                || SpartanRangedCompat.isHeavyCrossbow(stack));
    }

    public static boolean isRangedWeapon(ItemStack stack) {
        return GunSupport.get().isGun(stack) || isBowOrCrossbow(stack);
    }

    /** Bow/trident goals already own spacing; do not stack random tactical flanking on them. */
    public static boolean usesTacticalRangedMovement(Human human) {
        if (AmmoWeaponSelection.usableGun(human, human.getMainHandItem())) return true;
        return usesOtherProjectileWeapon(human.getMainHandItem())
                || usesOtherProjectileWeapon(human.getOffhandItem());
    }

    private static boolean usesOtherProjectileWeapon(ItemStack stack) {
        return stack.getItem() instanceof ProjectileWeaponItem && !isBowOrCrossbow(stack);
    }

    private static boolean isGunThreat(LivingEntity target) {
        return GunSupport.get().isGun(target.getMainHandItem()) || GunSupport.get().isGun(target.getOffhandItem());
    }

    public static boolean isRangedThreat(LivingEntity target) {
        // An off-hand ranged weapon does not override an actively held sword/axe.
        if (HumanUtil.isMeleeWeapon(target.getMainHandItem())) return false;
        return isGunThreat(target) || isProjectileThreat(target.getMainHandItem())
                || isProjectileThreat(target.getOffhandItem());
    }

    private static boolean isProjectileThreat(ItemStack stack) {
        return isBowOrCrossbow(stack) || stack.getItem() instanceof ProjectileWeaponItem
                || stack.getItem() instanceof TridentItem;
    }
}

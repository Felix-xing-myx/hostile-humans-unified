package dev.felix.hostilehumans.behavior.combat.defense;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.damage.HumanDamagePipeline;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/** Passive protection follows the equipment present at each effective hit, never item-use state. */
public final class GunnerShieldProtection {
    private static final TagKey<DamageType> TRUE_DAMAGE = TagKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation("hostile_humans_unified", "true_damage"));
    private GunnerShieldProtection() { }

    static boolean isUsableShield(ItemStack shield) {
        return !shield.isEmpty() && SpartanEquipmentCompat.isShield(shield)
                && (shield.getItem() instanceof ShieldItem
                || shield.canPerformAction(ToolActions.SHIELD_BLOCK)
                || shield.getUseAnimation() == UseAnim.BLOCK);
    }

    public static double multiplier(LivingEntity target, DamageSource source) {
        if (!(target instanceof Human human) || human.level().isClientSide
                || !GunSupport.isGunItem(human.getMainHandItem())
                || !isUsableShield(human.getOffhandItem()) || source.is(TRUE_DAMAGE)
                || source.is(DamageTypeTags.BYPASSES_ARMOR) || !HumanDamagePipeline.physical(source)) return 1;
        double reduction = UnifiedConfig.get().gunSetting("offhand_shield_damage_reduction", .1);
        if (!Double.isFinite(reduction) || reduction <= 0.0D) return 1;
        reduction = Math.min(1.0D, reduction);
        return 1 - reduction;
    }

    /** Retained event-bus entry for binary/source compatibility; the shared armor seam owns mitigation. */
    public static void onDamage(LivingDamageEvent event) { }
}

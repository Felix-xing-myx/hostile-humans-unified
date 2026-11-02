package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.behavior.combat.gun.GunFireAuthorization;
import dev.felix.hostilehumans.entity.Human;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Native projectile creation/spread stays upstream; only an authorized manual shot may lack a target. */
@Pseudo
@Mixin(targets = "top.ribs.scguns.entity.ai.AIGunEvent", remap = false)
public abstract class ScgunsMonitorShotMixin {
    @Redirect(method = "performGunAttack(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Ltop/ribs/scguns/common/Gun;F)V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;m_21023_(Lnet/minecraft/world/effect/MobEffect;)Z"), remap = false, require = 1, allow = 1)
    private static boolean hostileHumans$manualEffect(LivingEntity target, MobEffect effect,
            Mob shooter, LivingEntity originalTarget, ItemStack stack, @Coerce Object gun, float spread) {
        if (target == null && shooter instanceof Human human && GunFireAuthorization.allows(human, stack, null)) return false;
        return target.hasEffect(effect);
    }

    @Redirect(method = "getDirection(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Ltop/ribs/scguns/item/GunItem;Ltop/ribs/scguns/common/Gun;F)Lnet/minecraft/world/phys/Vec3;", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;m_20184_()Lnet/minecraft/world/phys/Vec3;"), remap = false, require = 1, allow = 1)
    private static net.minecraft.world.phys.Vec3 hostileHumans$manualMotion(LivingEntity target,
            LivingEntity shooter, LivingEntity originalTarget, ItemStack stack, @Coerce Object item,
            @Coerce Object gun, float spread) {
        if (target == null && shooter instanceof Human human && GunFireAuthorization.allows(human, stack, null))
            return net.minecraft.world.phys.Vec3.ZERO;
        return target.getDeltaMovement();
    }
}

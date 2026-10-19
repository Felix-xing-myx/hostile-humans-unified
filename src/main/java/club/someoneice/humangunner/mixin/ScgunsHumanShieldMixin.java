package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.EffectiveAttributes;
import club.someoneice.humangunner.GunSupport;
import club.someoneice.humangunner.UnifiedConfig;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.ribs.scguns.entity.projectile.ProjectileEntity;

/** Native player-only shield interception otherwise returns before hurt and discards the AP portion. */
@Pseudo
@Mixin(targets = "top.ribs.scguns.entity.projectile.ProjectileEntity", remap = false)
public abstract class ScgunsHumanShieldMixin {
    @Shadow public abstract LivingEntity getShooter();
    @Shadow public abstract ItemStack getWeapon();

    @Redirect(method = "onHitEntity", at = @At(value = "INVOKE",
            target = "Ltop/ribs/scguns/entity/projectile/ProjectileEntity$ProjectileHelper;handleShieldHit(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;F)Z"),
            remap = false, require = 1, allow = 1)
    private boolean hostileHumansUnified$preservePiercingPart(Entity victim, Entity projectile, float amount) {
        if (getShooter() instanceof Human human && GunSupport.isScgunsGunItem(getWeapon())
                && UnifiedConfig.get().damageFlag("armor_penetration_enabled", true)) {
            double fraction = EffectiveAttributes.armorPenetration(human);
            if (Double.isFinite(fraction) && fraction > 0) return false;
        }
        return ProjectileEntity.ProjectileHelper.handleShieldHit(victim, projectile, amount);
    }
}

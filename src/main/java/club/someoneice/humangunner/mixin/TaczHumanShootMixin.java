package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.TaczAmmoMixinContract;

import com.craftix.hostile_humans.entity.entities.Human;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityShoot", remap = false)
public abstract class TaczHumanShootMixin {
    @Shadow @Final private LivingEntity shooter;

    @Redirect(method = {TaczAmmoMixinContract.SHOOT, TaczAmmoMixinContract.SHOOT_LEGACY},
            at = @At(value = "INVOKE", target = TaczAmmoMixinContract.SHOOT_INVENTORY_AMMO), require = 1, allow = 1)
    private boolean humanGunner$useNpcMagazine(IGun gun, ItemStack weapon) {
        return !(shooter instanceof Human) && gun.useInventoryAmmo(weapon);
    }
}

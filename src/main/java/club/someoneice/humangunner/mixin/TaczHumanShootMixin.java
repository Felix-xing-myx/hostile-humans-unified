package club.someoneice.humangunner.mixin;

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

    @Redirect(method = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)Lcom/tacz/guns/api/entity/ShootResult;",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/IGun;useInventoryAmmo(Lnet/minecraft/world/item/ItemStack;)Z"), require = 1)
    private boolean humanGunner$useNpcMagazine(IGun gun, ItemStack weapon) {
        return !(shooter instanceof Human) && gun.useInventoryAmmo(weapon);
    }
}

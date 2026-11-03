package dev.felix.hostilehumans.mixin;

import com.tacz.guns.api.item.gun.AbstractGunItem;
import dev.felix.hostilehumans.entity.Human;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityBolt", remap = false)
public abstract class TaczHumanBoltMixin {
    @Shadow @Final private LivingEntity shooter;

    @Redirect(method = "lambda$bolt$0", at = @At(value = "INVOKE", target =
            "Lcom/tacz/guns/api/item/gun/AbstractGunItem;useInventoryAmmo(Lnet/minecraft/world/item/ItemStack;)Z"), require = 1)
    private boolean hhu$checkVirtualMagazine(@Coerce Object gun, ItemStack stack) {
        return !(shooter instanceof Human) && ((AbstractGunItem) gun).useInventoryAmmo(stack);
    }
}

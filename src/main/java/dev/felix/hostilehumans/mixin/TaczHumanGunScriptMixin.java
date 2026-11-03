package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.compat.tacz.TaczAmmoMixinContract;

import com.tacz.guns.api.item.gun.AbstractGunItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public abstract class TaczHumanGunScriptMixin {
    @Shadow private LivingEntity shooter;
    @Shadow private net.minecraft.world.item.ItemStack itemStack;

    @Inject(method = "consumeAmmoFromPlayer(I)I", at = @At("HEAD"), cancellable = true, require = 1)
    private void hhu$consumeVirtualReserve(int requested, CallbackInfoReturnable<Integer> cir) {
        if (!(shooter instanceof Human human) || human.level().isClientSide || !(itemStack.getItem() instanceof com.tacz.guns.api.item.gun.AbstractGunItem abstractGunItem)
                || !abstractGunItem.useInventoryAmmo(itemStack)) return;
        int available = Math.max(0, abstractGunItem.getCurrentAmmoCount(itemStack));
        int used = Math.min(Math.max(0, requested), available);
        if (used > 0) abstractGunItem.setCurrentAmmoCount(itemStack, available - used);
        cir.setReturnValue(used);
    }

    @Inject(method = TaczAmmoMixinContract.SCRIPT_INVENTORY_AMMO, at = @At("HEAD"), cancellable = true, require = 1)
    private void hhu$useNpcMagazine(CallbackInfoReturnable<Boolean> cir) {
        if (shooter instanceof Human) cir.setReturnValue(false);
    }

    @Redirect(method = "reduceAmmoOnce()Z", at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/api/item/gun/AbstractGunItem;reduceCurrentAmmoCount(Lnet/minecraft/world/item/ItemStack;)V"),
            require = 2, allow = 2)
    private void hhu$reduceNpcMagazine(AbstractGunItem gun, ItemStack weapon) {
        if (shooter instanceof Human && gun.useInventoryAmmo(weapon)) {
            // TaCZ's native decrement deliberately does nothing for inventory-fed stacks.
            gun.setCurrentAmmoCount(weapon, Math.max(0, gun.getCurrentAmmoCount(weapon) - 1));
        } else {
            gun.reduceCurrentAmmoCount(weapon);
        }
    }

    @ModifyVariable(method = TaczAmmoMixinContract.SCRIPT_SHOOT_ONCE, at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private boolean hhu$consumeNpcRounds(boolean consume) {
        return shooter instanceof Human || consume;
    }
}

package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.TaczAmmoMixinContract;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public abstract class TaczHumanGunScriptMixin {
    @Shadow private LivingEntity shooter;

    @Inject(method = TaczAmmoMixinContract.SCRIPT_INVENTORY_AMMO, at = @At("HEAD"), cancellable = true, require = 1)
    private void humanGunner$useNpcMagazine(CallbackInfoReturnable<Boolean> cir) {
        if (shooter instanceof Human) cir.setReturnValue(false);
    }

    @ModifyVariable(method = TaczAmmoMixinContract.SCRIPT_SHOOT_ONCE, at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private boolean humanGunner$consumeNpcRounds(boolean consume) {
        return shooter instanceof Human || consume;
    }
}

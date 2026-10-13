package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.ScgunsCompat;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla 1.20.1 boundary; other damage sources retain their difficulty rules. */
@Mixin(DamageSource.class)
public abstract class ScgunsHumanDifficultyMixin {
    @Inject(method = "m_7986_()Z", at = @At("HEAD"), cancellable = true, remap = false, require = 1, allow = 1)
    private void hostileHumansUnified$independentGunDamage(CallbackInfoReturnable<Boolean> cir) {
        if (ScgunsCompat.usesIndependentDamage((DamageSource) (Object) this)
                || club.someoneice.humangunner.JegCompat.isHumanBullet((DamageSource) (Object) this)) cir.setReturnValue(false);
    }
}

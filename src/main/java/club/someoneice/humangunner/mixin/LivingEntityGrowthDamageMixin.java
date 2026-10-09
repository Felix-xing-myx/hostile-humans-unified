package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.GrowthCombat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stable Forge 1.20.1 SRG boundary; observational injections never replace damage handling. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityGrowthDamageMixin {
    @Inject(method = "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), remap = false)
    private void hostileHumansUnified$beginGrowthDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        GrowthCombat.beginHit((LivingEntity) (Object) this, source);
    }

    @Inject(method = "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("RETURN"), remap = false)
    private void hostileHumansUnified$finishGrowthDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        GrowthCombat.endHit((LivingEntity) (Object) this);
    }

    @Inject(method = "m_21153_(F)V", at = @At("HEAD"), remap = false)
    private void hostileHumansUnified$beforeHealthWrite(float health, CallbackInfo ci) {
        GrowthCombat.beforeHealthChange((LivingEntity) (Object) this);
    }

    @Inject(method = "m_21153_(F)V", at = @At("RETURN"), remap = false)
    private void hostileHumansUnified$afterHealthWrite(float health, CallbackInfo ci) {
        GrowthCombat.afterHealthChange((LivingEntity) (Object) this);
    }
}

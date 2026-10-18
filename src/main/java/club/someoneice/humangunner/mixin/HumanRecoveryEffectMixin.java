package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.HumanRecoveryEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SRG descriptors verified against the local Forge 1.20.1 mappings. */
@Mixin(MobEffect.class)
public abstract class HumanRecoveryEffectMixin {
    @Inject(method = "m_6742_(Lnet/minecraft/world/entity/LivingEntity;I)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void hostileHumansUnified$replaceRecoveryTick(LivingEntity target, int amplifier, CallbackInfo callback) {
        if (HumanRecoveryEffects.replaceTick((MobEffect) (Object) this, target)) callback.cancel();
    }

    @Inject(method = "m_19461_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void hostileHumansUnified$replaceInstantHealing(Entity source, Entity indirectSource,
            LivingEntity target, int amplifier, double exposure, CallbackInfo callback) {
        if (HumanRecoveryEffects.replaceInstant((MobEffect) (Object) this, target, exposure)) callback.cancel();
    }
}

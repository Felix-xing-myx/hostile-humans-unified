package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.damage.DamagePipelineState;

import dev.felix.hostilehumans.damage.HumanDamagePipeline;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exact Forge 47.4.16 SRG seams; no replacement damage sources or extra hurt calls. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamagePipelineMixin implements DamagePipelineState.Access {
    @Unique private final DamagePipelineState hostileHumansUnified$damageState = new DamagePipelineState();
    @Override public DamagePipelineState hostileHumansUnified$damageState() { return hostileHumansUnified$damageState; }
    @Shadow(remap = false) protected abstract float m_21161_(DamageSource source, float amount);

    @Inject(method = "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), remap = false)
    private void hostileHumansUnified$beginDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        hostileHumansUnified$damageState.begin();
    }
    @Inject(method = "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("RETURN"), remap = false)
    private void hostileHumansUnified$endDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        hostileHumansUnified$damageState.clear();
    }
    @Redirect(method = "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/ForgeHooks;onShieldBlock(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)Lnet/minecraftforge/event/entity/living/ShieldBlockEvent;"), require = 1, allow = 1)
    private ShieldBlockEvent hostileHumansUnified$partitionShield(LivingEntity entity, DamageSource source, float amount) {
        return HumanDamagePipeline.shieldBlock(entity, source, amount, ForgeHooks.onShieldBlock(entity, source, amount));
    }
    @Redirect(method = "m_6475_(Lnet/minecraft/world/damagesource/DamageSource;F)V", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_21161_(Lnet/minecraft/world/damagesource/DamageSource;F)F"), require = 1, allow = 1)
    private float hostileHumansUnified$partitionArmor(LivingEntity entity, DamageSource source, float amount) {
        return HumanDamagePipeline.armor(entity, source, amount, normal -> m_21161_(source, (float) normal));
    }
}

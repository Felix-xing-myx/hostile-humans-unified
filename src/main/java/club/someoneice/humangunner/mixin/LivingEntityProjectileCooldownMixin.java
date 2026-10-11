package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.HumanAttackCooldownPolicy;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/** Forge 1.20.1 SRG contract: one tag query shared by scoped melee/projectile policies. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityProjectileCooldownMixin {
    @Redirect(method = "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            slice = @Slice(
                    from = @At(value = "FIELD", target = "Lnet/minecraft/tags/DamageTypeTags;f_273918_:Lnet/minecraft/tags/TagKey;"),
                    to = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;f_20898_:F", ordinal = 0)),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSource;m_269533_(Lnet/minecraft/tags/TagKey;)Z"),
            remap = false, require = 1, allow = 1)
    private boolean hostileHumansUnified$projectileCooldown(DamageSource source, TagKey<DamageType> tag) {
        return source.is(tag) || HumanAttackCooldownPolicy.bypassesCooldown((LivingEntity) (Object) this, source);
    }
}

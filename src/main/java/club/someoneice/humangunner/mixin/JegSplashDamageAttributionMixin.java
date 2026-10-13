package club.someoneice.humangunner.mixin;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep the native damage type, but retain the NPC cause for shared scaling and training. */
@Pseudo
@Mixin(targets = "ttv.migami.jeg.entity.Splash", remap = false)
public abstract class JegSplashDamageAttributionMixin {
    @Shadow public abstract LivingEntity getOwner();
    @Redirect(method = "m_8119_()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
            require = 2, allow = 2)
    private boolean hostileHumans$attributeWaterDamage(LivingEntity victim, DamageSource original, float amount) {
        LivingEntity owner = getOwner();
        DamageSource attributed = owner instanceof Human
                ? new DamageSource(original.typeHolder(), (Entity) (Object) this, owner) : original;
        return victim.hurt(attributed, amount);
    }
}

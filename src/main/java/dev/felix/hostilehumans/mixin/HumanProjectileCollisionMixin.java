package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.damage.HumanProjectileRelations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shared vanilla collision predicate covers arrows, crossbow bolts and tridents. */
@Mixin(Projectile.class)
public abstract class HumanProjectileCollisionMixin {
    @Inject(method = "m_5603_", at = @At("HEAD"), cancellable = true, remap = false)
    private void hhu$skipMutualAllies(Entity candidate, CallbackInfoReturnable<Boolean> cir) {
        if (HumanProjectileRelations.passesThrough((Projectile) (Object) this, candidate)) cir.setReturnValue(false);
    }
}

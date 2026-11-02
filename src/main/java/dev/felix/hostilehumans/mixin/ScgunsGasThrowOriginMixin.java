package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.behavior.combat.throwable.ThrowableEffectContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "top.ribs.scguns.entity.throwable.ThrowableGasGrenadeEntity", remap = false)
public abstract class ScgunsGasThrowOriginMixin {
    @Redirect(method = "applyGasEffects", at = @At(value = "INVOKE", target =
            "Ltop/ribs/scguns/common/SulfurGasCloud;applyGasEffects(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;DII)V"), require = 1, allow = 1)
    private void hostileHumans$gasOrigin(Level level, Vec3 point, double radius, int duration, int amplifier) {
        ThrowableEffectContext.run((Entity) (Object) this,
                () -> top.ribs.scguns.common.SulfurGasCloud.applyGasEffects(level, point, radius, duration, amplifier));
    }
    @Redirect(method = "m_8119_()V", at = @At(value = "INVOKE", target =
            "Ltop/ribs/scguns/common/SulfurGasCloud;checkAndHandleFireExplosion(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;D)Z"), require = 1, allow = 1)
    private boolean hostileHumans$gasExplosionOrigin(Level level, Vec3 point, double radius) {
        return ThrowableEffectContext.call((Entity) (Object) this,
                () -> top.ribs.scguns.common.SulfurGasCloud.checkAndHandleFireExplosion(level, point, radius));
    }
}

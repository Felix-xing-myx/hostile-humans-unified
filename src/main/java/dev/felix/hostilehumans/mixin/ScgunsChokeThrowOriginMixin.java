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
@Mixin(targets = "top.ribs.scguns.entity.throwable.ThrowableChokeBombEntity", remap = false)
public abstract class ScgunsChokeThrowOriginMixin {
    @Redirect(method = "applyChokeEffects", at = @At(value = "INVOKE", target =
            "Ltop/ribs/scguns/common/ChokeBombCloud;applyChokeEffects(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;D)V"), require = 1, allow = 1)
    private void hostileHumans$chokeOrigin(Level level, Vec3 point, double radius) {
        ThrowableEffectContext.run((Entity) (Object) this,
                () -> top.ribs.scguns.common.ChokeBombCloud.applyChokeEffects(level, point, radius));
    }
}

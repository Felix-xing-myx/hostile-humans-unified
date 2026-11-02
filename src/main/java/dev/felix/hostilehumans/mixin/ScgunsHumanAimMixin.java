package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.entity.Human;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shared gun AI already predicts with modified speed; retain native spread but remove its second lead. */
@Pseudo
@Mixin(targets = "top.ribs.scguns.entity.ai.AIGunEvent", remap = false)
public abstract class ScgunsHumanAimMixin {
    @Inject(method = "getLeadingDirection", at = @At("HEAD"), cancellable = true,
            remap = false, require = 1, allow = 1)
    private static void hostileHumansUnified$singleIntercept(LivingEntity shooter, LivingEntity target,
            double speed, CallbackInfoReturnable<Vec3> cir) {
        if (shooter instanceof Human) cir.setReturnValue(Vec3.ZERO);
    }
}

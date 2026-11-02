package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.client.render.tacz.TaczOpticRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native acceleration defers optic work beyond the observation scope; use its synchronous public path. */
@Pseudo
@Mixin(targets = "com.tacz.guns.compat.ar.ARCompat", remap = false)
public abstract class TaczScopeRenderCompatibilityMixin {
    @Inject(method = "shouldAccelerate", at = @At("HEAD"), cancellable = true, remap = false)
    private static void hostileHumans$synchronousOptic(CallbackInfoReturnable<Boolean> callback) {
        if (TaczOpticRenderContext.active()) callback.setReturnValue(false);
    }
}

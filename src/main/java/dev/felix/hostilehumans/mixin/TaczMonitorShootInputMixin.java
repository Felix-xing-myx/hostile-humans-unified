package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.monitor.client.HumanMonitorClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.input.ShootKey", remap = false)
public abstract class TaczMonitorShootInputMixin {
    @Inject(method = "autoShoot", at = @At("HEAD"), cancellable = true, require = 1)
    private static void hostileHumans$blockPlayerAuto(CallbackInfo callback) {
        if (HumanMonitorClient.active()) callback.cancel();
    }
    @Inject(method = "semiShoot", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hostileHumans$blockLegacySemi(CallbackInfo callback) {
        if (HumanMonitorClient.active()) callback.cancel();
    }
    @Inject(method = {"autoShootController", "semiShootController", "shootControllerTick"}, at = @At("HEAD"), cancellable = true, require = 0)
    private static void hostileHumans$blockPlayerController(CallbackInfoReturnable<Boolean> callback) {
        if (HumanMonitorClient.active()) callback.setReturnValue(false);
    }
}

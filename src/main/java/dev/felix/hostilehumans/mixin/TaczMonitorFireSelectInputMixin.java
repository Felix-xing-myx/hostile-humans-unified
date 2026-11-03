package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.monitor.client.HumanMonitorClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.input.FireSelectKey", remap = false)
public abstract class TaczMonitorFireSelectInputMixin {
    @Inject(method = "doFireSelectLogic", at = @At("HEAD"), cancellable = true, require = 1)
    private static void hhu$blockRealPlayerFireSelect(CallbackInfo callback) {
        if (HumanMonitorClient.active()) callback.cancel();
    }
}

package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.monitor.client.HumanMonitorClient;
import dev.felix.hostilehumans.compat.jeg.JegMonitorInput;
import net.minecraftforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Monitoring owns input; native handlers must never act on Minecraft.player's real weapon. */
@Pseudo
@Mixin(targets = "ttv.migami.jeg.client.handler.ReloadHandler", remap = false)
public abstract class JegMonitorReloadInputMixin {
    @Inject(method = {"onClientTick(Lnet/minecraftforge/event/TickEvent$ClientTickEvent;)V", "setReloading(Z)V"}, at = @At("HEAD"), cancellable = true, remap = false, require = 2, allow = 2)
    private void hhuBlockRealPlayer(CallbackInfo callback) {
        if (HumanMonitorClient.active()) callback.cancel();
    }
    @Inject(method = "onKeyPressed(Lnet/minecraftforge/client/event/InputEvent$Key;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1, allow = 1)
    private void hhuCaptureKey(InputEvent.Key event, CallbackInfo callback) {
        if (!HumanMonitorClient.active()) return;
        JegMonitorInput.captureKey(event);
        callback.cancel();
    }
}

package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.monitor.client.HumanMonitorClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Monitoring owns input; native handlers must never act on Minecraft.player's real weapon. */
@Pseudo
@Mixin(targets = "ttv.migami.jeg.client.handler.AimingHandler", remap = false)
public abstract class JegMonitorAimingInputMixin {
    @Inject(method = {"onClientTick(Lnet/minecraftforge/event/TickEvent$ClientTickEvent;)V"}, at = @At("HEAD"), cancellable = true, remap = false, require = 1, allow = 1)
    private void hhuBlockRealPlayer(CallbackInfo callback) {
        if (HumanMonitorClient.active()) callback.cancel();
    }
}

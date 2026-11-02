package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.monitor.client.HumanMonitorClient;
import dev.felix.hostilehumans.compat.scguns.ScgunsMonitorInput;
import net.minecraftforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Monitoring owns input; native handlers must never act on Minecraft.player's real weapon. */
@Pseudo
@Mixin(targets = "top.ribs.scguns.client.handler.ShootingHandler", remap = false)
public abstract class ScgunsMonitorShootingInputMixin {
    @Inject(method = {"onHandleShooting(Lnet/minecraftforge/event/TickEvent$ClientTickEvent;)V", "onPostClientTick(Lnet/minecraftforge/event/TickEvent$ClientTickEvent;)V", "fire(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V"}, at = @At("HEAD"), cancellable = true, remap = false, require = 3, allow = 3)
    private void hhuBlockRealPlayer(CallbackInfo callback) {
        if (HumanMonitorClient.active()) callback.cancel();
    }
    @Inject(method = "onMouseClick(Lnet/minecraftforge/client/event/InputEvent$InteractionKeyMappingTriggered;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1, allow = 1)
    private void hhuCaptureInteraction(InputEvent.InteractionKeyMappingTriggered event, CallbackInfo callback) {
        if (!HumanMonitorClient.active()) return;
        ScgunsMonitorInput.captureInteraction();
        callback.cancel();
    }
}

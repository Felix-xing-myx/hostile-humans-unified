package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.monitor.client.HumanMonitorClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, remap = false)
public abstract class HumanMonitorInputMixin {
    @Inject(method = "m_91279_()V", at = @At("HEAD"))
    private void hostileHumans$monitorKeys(CallbackInfo callback) {
        // Consume only monitor operations; native chat, command and pause keep their own handling.
        HumanMonitorClient.handleKeys();
    }
}

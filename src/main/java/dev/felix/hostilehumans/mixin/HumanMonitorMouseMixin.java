package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.monitor.client.HumanMonitorClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MouseHandler.class, remap = false)
public abstract class HumanMonitorMouseMixin {
    @Redirect(method = "m_91523_()V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/player/LocalPlayer;m_19884_(DD)V"))
    private void hostileHumans$monitorLook(LocalPlayer player, double dx, double dy) {
        // Vanilla already applied sensitivity, smoothing and inverted-Y before this boundary.
        if (!HumanMonitorClient.turn(dx, dy)) player.turn(dx, dy);
    }
}

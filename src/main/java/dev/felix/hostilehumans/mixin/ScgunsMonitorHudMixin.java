package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.monitor.client.HumanMonitorClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Upstream listens to every overlay Post; suppress only its real-player HUD while observing. */
@Pseudo
@Mixin(targets="top.ribs.scguns.client.handler.HUDRenderHandler",remap=false)
public abstract class ScgunsMonitorHudMixin {
    @Inject(method="onRenderGuiOverlay(Lnet/minecraftforge/client/event/RenderGuiOverlayEvent$Post;)V",at=@At("HEAD"),cancellable=true,remap=false,require=0,allow=1)
    private static void hhuMonitorHud(CallbackInfo callback) {if(HumanMonitorClient.active())callback.cancel();}
}

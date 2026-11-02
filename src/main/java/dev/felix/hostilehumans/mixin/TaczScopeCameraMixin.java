package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.client.render.HumanCameraView;
import dev.felix.hostilehumans.entity.Human;
import net.minecraftforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native handlers use the player's KeepingItemRenderer and global springs, even for an NPC camera. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.event.CameraSetupEvent", remap = false)
public abstract class TaczScopeCameraMixin {
    @Inject(method = {"applyScopeMagnification", "applyGunModelFovModifying"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void hostileHumans$cameraFov(ViewportEvent.ComputeFov event, CallbackInfo callback) {
        // The player's kept gun must stay isolated even while the NPC is unarmed, using medicine,
        // switching to melee, or viewed in third person. NPC optic FOV has its own narrower gate.
        if (event.getCamera().getEntity() instanceof Human human && HumanCameraView.attached(human)) callback.cancel();
    }
}

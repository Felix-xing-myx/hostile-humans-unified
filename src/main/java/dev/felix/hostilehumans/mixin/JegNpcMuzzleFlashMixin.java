package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.client.render.JegNpcAnimator;
import dev.felix.hostilehumans.compat.OptionalMixinPlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ttv.migami.jeg.client.handler.GunRenderingHandler;
import ttv.migami.jeg.client.handler.ShootingHandler;

/** Native attached flash follows the NPC packet, not the local player's mouse state. */
@Pseudo
@Mixin(targets = "ttv.migami.jeg.client.handler.GunRenderingHandler", remap = false)
public abstract class JegNpcMuzzleFlashMixin {
    @Redirect(method = OptionalMixinPlugin.JEG_NPC_FLASH, at = @At(value = "INVOKE",
            target = OptionalMixinPlugin.JEG_FLASH_SHOOTING_CALL), require = 1, allow = 1)
    private boolean hostileHumans$npcFlash(ShootingHandler original) {
        var human = JegNpcAnimator.drawing();
        return human == null ? original.isShooting() : GunRenderingHandler.entityIdForMuzzleFlash.contains(human.getId());
    }
}

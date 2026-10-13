package club.someoneice.humangunner.mixin;

import com.craftix.hostile_humans.client.renderer.JegNpcAnimator;
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
    @Redirect(method = "renderWeapon", at = @At(value = "INVOKE",
            target = "Lttv/migami/jeg/client/handler/ShootingHandler;isShooting()Z"), require = 1, allow = 1)
    private boolean hostileHumans$npcFlash(ShootingHandler original) {
        var human = JegNpcAnimator.drawing();
        return human == null ? original.isShooting() : GunRenderingHandler.entityIdForMuzzleFlash.contains(human.getId());
    }
}

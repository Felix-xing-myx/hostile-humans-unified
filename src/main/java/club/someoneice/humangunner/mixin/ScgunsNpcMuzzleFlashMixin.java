package club.someoneice.humangunner.mixin;

import com.craftix.hostile_humans.client.renderer.ScgunsNpcAnimator;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraftforge.client.event.RenderLivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only HHU's approximate world-space flash with the held weapon's native flash. */
@Pseudo
@Mixin(targets = "top.ribs.scguns.client.handler.EntityMuzzleFlashHandler", remap = false)
public abstract class ScgunsNpcMuzzleFlashMixin {
    @Inject(method = "onRenderLivingPost", at = @At("HEAD"), cancellable = true, remap = false)
    private void hostileHumans$useAttachedMuzzleFlash(RenderLivingEvent.Post<?, ?> event, CallbackInfo callback) {
        if (event.getEntity() instanceof Human human && ScgunsNpcAnimator.consumeAttachedRender(human))
            callback.cancel();
    }
}

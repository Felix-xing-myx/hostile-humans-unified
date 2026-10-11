package club.someoneice.humangunner.mixin;

import com.craftix.hostile_humans.client.renderer.TaczNpcEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional visual hook: retain native player behavior and use the exact muzzle-node pose. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.model.functional.MuzzleFlashRender", remap = false)
public abstract class TaczNpcMuzzleFlashMixin {
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void hostileHumans$renderNpcMuzzle(PoseStack poses, VertexConsumer consumer,
            ItemDisplayContext context, int light, int overlay, CallbackInfo callback) {
        TaczNpcEffects.renderMuzzle(poses, context);
    }
}

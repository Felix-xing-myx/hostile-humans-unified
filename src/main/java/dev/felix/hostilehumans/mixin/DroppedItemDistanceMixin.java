package dev.felix.hostilehumans.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.felix.hostilehumans.client.render.DroppedItemRenderDetail;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class DroppedItemDistanceMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void hhu$skipDistantItems(ItemEntity item, float yaw, float partialTick, PoseStack poses,
                                      MultiBufferSource buffers, int light, CallbackInfo callback) {
        if (DroppedItemRenderDetail.hide(item)) callback.cancel();
    }
}

package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.client.render.DroppedItemRenderDetail;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntityRenderer.class)
public abstract class DroppedItemRenderAmountMixin {
    @Inject(method = "getRenderAmount", at = @At("RETURN"), cancellable = true)
    private void hhu$limitRepeatedStackModels(ItemStack stack, CallbackInfoReturnable<Integer> result) {
        int original = result.getReturnValueI();
        int amount = DroppedItemRenderDetail.copies(original);
        if (amount != original) result.setReturnValue(amount);
    }
}

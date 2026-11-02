package dev.felix.hostilehumans.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.felix.hostilehumans.client.render.HumanSpectatorMotion;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla only bobs Player cameras; extend that same presentation seam to attached Humans. */
@Mixin(value = GameRenderer.class, remap = false)
public abstract class HumanSpectatorBobMixin {
    @Inject(method = "m_109138_(Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"), remap = false)
    private void hostileHumans$humanBob(PoseStack poses, float partialTick, CallbackInfo callback) {
        HumanSpectatorMotion.bob(poses, partialTick);
    }
}

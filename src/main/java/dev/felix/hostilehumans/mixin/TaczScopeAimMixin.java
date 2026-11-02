package dev.felix.hostilehumans.mixin;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import dev.felix.hostilehumans.client.render.tacz.TaczOpticRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep the native reticle/stencil algorithm, supplying only this draw's NPC aiming progress. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.model.BedrockAttachmentModel", remap = false)
public abstract class TaczScopeAimMixin {
    @Redirect(method = "renderOcularAndDivision", at = @At(value = "INVOKE", target =
            "Lcom/tacz/guns/api/client/gameplay/IClientPlayerGunOperator;getClientAimingProgress(F)F"), remap = false)
    private float hostileHumans$opticAim(IClientPlayerGunOperator player, float partialTick) {
        // Lazy fallback avoids reading any local-player aiming state while the owned optic is rendering.
        float scoped = TaczOpticRenderContext.aimFor(this, Float.NaN);
        return Float.isNaN(scoped) ? player.getClientAimingProgress(partialTick) : scoped;
    }
}

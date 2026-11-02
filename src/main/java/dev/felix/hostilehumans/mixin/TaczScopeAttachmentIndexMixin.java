package dev.felix.hostilehumans.mixin;

import com.tacz.guns.client.model.BedrockAttachmentModel;
import dev.felix.hostilehumans.client.render.tacz.TaczOpticRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.resource.index.ClientAttachmentIndex", remap = false)
public abstract class TaczScopeAttachmentIndexMixin {
    @Inject(method = "getAttachmentModel()Lcom/tacz/guns/client/model/BedrockAttachmentModel;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void hostileHumans$ownedOptic(CallbackInfoReturnable<BedrockAttachmentModel> callback) {
        var model = TaczOpticRenderContext.modelFor(this);
        if (model instanceof BedrockAttachmentModel optic) callback.setReturnValue(optic);
    }
}

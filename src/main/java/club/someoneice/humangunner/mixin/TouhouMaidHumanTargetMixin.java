package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.TouhouMaidCompat;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional maid hook; both development and production names are explicit. */
@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid", remap = false)
public abstract class TouhouMaidHumanTargetMixin {
    @Inject(method = {
            "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z",
            "m_6779_(Lnet/minecraft/world/entity/LivingEntity;)Z"
    }, at = @At("HEAD"), cancellable = true, require = 1)
    private void humanGunner$recognizeHumans(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        Boolean decision = TouhouMaidCompat.humanAttackDecision((LivingEntity) (Object) this, target);
        if (decision != null) cir.setReturnValue(decision);
    }
}

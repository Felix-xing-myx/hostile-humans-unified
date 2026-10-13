package club.someoneice.humangunner.mixin;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also guard pre-existing or externally attached native goals, before free fire/reload/grenades. */
@Pseudo
@Mixin(targets = "ttv.migami.jeg.entity.ai.GunAttackGoal", remap = false)
public abstract class JegNativeGunGoalMixin {
    @Shadow @Final protected PathfinderMob shooter;
    @Inject(method = "m_8036_()Z", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void hostileHumans$denySecondController(CallbackInfoReturnable<Boolean> cir) {
        if (shooter instanceof Human) cir.setReturnValue(false);
    }
    @Inject(method = "m_8037_()V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void hostileHumans$denyUnpaidNativeActions(CallbackInfo ci) {
        if (shooter instanceof Human) ci.cancel();
    }
}

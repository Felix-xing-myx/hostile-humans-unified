package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Finalizes Human arrow trajectories after all launch-time initialization. */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowBallisticsMixin {
    // This compatibility project intentionally has no refmap. Forge 1.20.1's
    // stable SRG name for Entity#tick is used, matching the trident mixin.
    @Inject(method = "m_8119_", at = @At("HEAD"), remap = false)
    private void hhu$applyAdaptiveBallistics(CallbackInfo ci) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (arrow.level().isClientSide
                || arrow instanceof ThrownTrident
                || arrow.getPersistentData().getBoolean(HumanRuntime.ADAPTIVE_BALLISTICS_APPLIED)
                || !(arrow.getOwner() instanceof Human shooter)) {
            return;
        }
        HumanRuntime.applyFirstTickArrowBallistics(shooter, arrow);
    }
}

package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.monitor.HumanMonitorControl;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pause selectors/navigation/controllers, retaining vanilla travel, collision, gravity and effects. */
@Mixin(value = Mob.class, remap = false)
public abstract class HumanMonitorAiStepMixin {
    @Inject(method = "m_6140_()V", at = @At("HEAD"), cancellable = true)
    private void hostileHumans$manualMovement(CallbackInfo callback) {
        if ((Object) this instanceof Human human && HumanMonitorControl.controls(human)) callback.cancel();
    }
}

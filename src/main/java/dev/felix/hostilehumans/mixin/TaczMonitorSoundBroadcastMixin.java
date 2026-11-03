package dev.felix.hostilehumans.mixin;

import com.tacz.guns.network.message.ServerMessageSound;
import dev.felix.hostilehumans.compat.OptionalMixinPlugin;
import dev.felix.hostilehumans.compat.tacz.TaczMonitorAudio;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.network.message.ServerMessageSound", remap = false)
public abstract class TaczMonitorSoundBroadcastMixin {
    // Native TaCZ and its Arcana replacement both construct this completed payload.
    @Inject(method = OptionalMixinPlugin.TACZ_SOUND_PACKET, at = @At("RETURN"), remap = false)
    private void hostileHumans$monitorSound(CallbackInfo callback) {
        TaczMonitorAudio.supplement((ServerMessageSound) (Object) this);
    }
}

package dev.felix.hostilehumans.mixin;

import com.tacz.guns.network.message.ServerMessageSound;
import dev.felix.hostilehumans.compat.tacz.TaczMonitorShotSoundClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scoped native sound-message handoff; ordinary players and AI retain upstream playback. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.sound.SoundPlayManager", remap = false)
public abstract class TaczMonitorShotSoundMixin {
    @Inject(method = "playMessageSound(Lcom/tacz/guns/network/message/ServerMessageSound;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1, remap = false)
    private static void hhu$controlledShotSound(ServerMessageSound message, CallbackInfo ci) {
        if (TaczMonitorShotSoundClient.play(message)) ci.cancel();
    }
}

package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.client.render.SpectatorAudioListener;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** TaCZ animation cues pre-attenuate before constructing GunSoundInstance; fix that stage too. */
@Pseudo
@Mixin(targets = "com.tacz.guns.api.client.animation.ObjectAnimationSoundChannel", remap = false)
public abstract class SpectatorTaczAnimationSoundDistanceMixin {
    @Redirect(method = "playSound(DDLnet/minecraft/world/entity/Entity;IFF)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;sqrt(D)D"),
            require = 1, allow = 1, remap = false)
    private double hostileHumans$animationListenerDistance(double original, double from, double to,
            Entity source, int distance, float volume, float pitch) {
        return Math.sqrt(SpectatorAudioListener.distanceSquared(source, original));
    }
}

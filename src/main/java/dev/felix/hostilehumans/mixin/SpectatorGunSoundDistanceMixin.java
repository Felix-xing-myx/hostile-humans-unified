package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.client.render.SpectatorAudioListener;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** OptionalMixinPlugin verifies the supported constructor layout and unique sqrt call before applying. */
@Pseudo
@Mixin(targets = {"com.tacz.guns.client.sound.GunSoundInstance",
        "top.ribs.scguns.client.audio.GunShotSound", "ttv.migami.jeg.client.audio.GunShotSound"}, remap = false)
public abstract class SpectatorGunSoundDistanceMixin {
    // A bare <init> selects only the first overload (the 1.1.8 delegating constructor).
    // Scan every validated overload; require/allow still demand one distance call per class.
    @ModifyArg(method = "<init>*", at = @At(value = "INVOKE", target = "Ljava/lang/Math;sqrt(D)D"),
            index = 0, require = 1, allow = 1, remap = false)
    private double hostileHumans$listenerDistance(double original) {
        return SpectatorAudioListener.distanceSquared((SoundInstance) (Object) this, original);
    }
}

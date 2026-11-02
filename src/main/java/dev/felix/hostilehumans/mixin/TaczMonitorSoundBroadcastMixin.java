package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.compat.OptionalMixinPlugin;
import dev.felix.hostilehumans.compat.tacz.TaczMonitorAudio;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.sound.SoundManager", remap = false)
public abstract class TaczMonitorSoundBroadcastMixin {
    @Inject(method = OptionalMixinPlugin.TACZ_SOUND_NEARBY, at = @At("TAIL"), remap = false)
    private static void hostileHumans$monitorSound(LivingEntity shooter, int distance, ResourceLocation gun,
            ResourceLocation display, String name, float volume, float pitch, CallbackInfo callback) {
        TaczMonitorAudio.supplement(shooter, distance, gun, display, name, volume, pitch);
    }
}

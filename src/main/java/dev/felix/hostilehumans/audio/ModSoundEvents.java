package dev.felix.hostilehumans.audio;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, dev.felix.hostilehumans.bootstrap.HostileHumansUnified.MOD_ID);
    /** Variant selection belongs to sounds.json; gameplay sends one event without another AI random draw. */
    public static final RegistryObject<SoundEvent> DODGE = SOUNDS.register("ability.dodge",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(
                    dev.felix.hostilehumans.bootstrap.HostileHumansUnified.MOD_ID, "ability.dodge")));

    protected ModSoundEvents() {
    }
}

package dev.felix.hostilehumans.compat.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.network.message.ServerMessageSound;
import com.tacz.guns.sound.SoundManager;
import dev.felix.hostilehumans.client.render.HumanCameraView;
import dev.felix.hostilehumans.entity.Human;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Native message presentation, never a predicted click/trigger sound or a second server emission. */
public final class TaczMonitorShotSoundClient {
    private TaczMonitorShotSoundClient() {}

    /** True only when this observed subject's native shot message was played and its 3P copy must be consumed. */
    public static boolean play(ServerMessageSound message) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || message == null
                || !(client.getCameraEntity() instanceof Human human)
                || !HumanCameraView.attached(human)
                || !client.options.getCameraType().isFirstPerson() || !human.isAlive()
                || human.isRemoved() || human.isSilent() || human.level() != client.level
                || message.getEntityId() != human.getId()) return false;
        String name = message.getSoundName();
        String firstPerson;
        if (SoundManager.SHOOT_3P_SOUND.equals(name)) firstPerson = SoundManager.SHOOT_SOUND;
        else if (SoundManager.SILENCE_3P_SOUND.equals(name)) firstPerson = SoundManager.SILENCE_SOUND;
        else return false; // Reloads, bolts, custom cues and all other shooters remain native.
        var display = TimelessAPI.getGunDisplay(message.getGunDisplayId(), message.getGunId()).orElse(null);
        if (display == null) return false;
        ResourceLocation sound = display.getSounds(firstPerson);
        if (sound == null || !client.getResourceManager().getResource(new ResourceLocation(
                sound.getNamespace(), "tacz_sounds/" + sound.getPath() + ".ogg")).isPresent()) return false;
        // Respect the packet's gun/display, suppressor, volume, pitch and range. A late sound still
        // belongs to its fired gun even when the selected equipment has since changed.
        return SoundPlayManager.playClientSound(human, sound, message.getVolume(), message.getPitch(),
                message.getDistance(), false) != null;
    }
}

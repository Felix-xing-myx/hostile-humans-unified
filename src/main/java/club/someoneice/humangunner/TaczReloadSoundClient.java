package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.sound.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

/** Client-only companion to the optional TaCZ integration. */
final class TaczReloadSoundClient {
    private TaczReloadSoundClient() {}

    static void register() {
        MinecraftForge.EVENT_BUS.addListener(TaczReloadSoundClient::onReload);
    }

    private static void onReload(GunReloadEvent event) {
        if (!event.getLogicalSide().isClient() || !(event.getEntity() instanceof Human human)) return;
        ItemStack weapon = event.getGunItemStack();
        IGun gun = IGun.getIGunOrNull(weapon);
        if (gun == null) return;
        ResourceLocation gunId = gun.getGunId(weapon);
        if (gunId == null) return;
        var index = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
        if (index == null) return;
        boolean empty = index.getGunData().getBolt() == Bolt.OPEN_BOLT
                ? gun.getCurrentAmmoCount(weapon) <= 0
                : !gun.hasBulletInBarrel(weapon);
        TimelessAPI.getGunDisplay(weapon).ifPresent(display -> {
            // TaCZ's player helper stores the sound in one global "last gun
            // sound" slot. Use its per-entity player here so one NPC reload
            // cannot stop another gunner's (or the local player's) sound.
            String sound = empty ? SoundManager.RELOAD_EMPTY_SOUND
                    : SoundManager.RELOAD_TACTICAL_SOUND;
            SoundPlayManager.playClientSound(human, display.getSounds(sound),
                    1.0F, 1.0F, GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
        });
    }
}

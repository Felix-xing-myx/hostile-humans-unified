package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.mrcrayfish.framework.api.network.LevelLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import top.ribs.scguns.Config;
import top.ribs.scguns.common.Gun;
import top.ribs.scguns.network.PacketHandler;
import top.ribs.scguns.network.message.S2CMessageGunSound;

/** Optional server adapter to the upstream public sound packet, never a client animation trigger. */
final class ScgunsSounds {
    private ScgunsSounds() { }

    static void fire(Human human, Gun gun) {
        // Values above one saturate the mixer; halving 7.5 to 3.75 was still full volume.
        double original = Math.max(0, Config.COMMON.gameplay.mobGunfireVolume.get() - .5);
        // Native GunShotSound squares its input after distance attenuation. sqrt gives 50% gain.
        float input = (float) Math.sqrt(Math.min(1, original) * .5);
        send(human, gun.getSounds().getFire(), input, false);
    }

    static void reload(Human human, Gun gun, boolean finished) {
        ResourceLocation id = finished ? gun.getSounds().getCock() : gun.getSounds().getReload();
        if (id == null) id = finished ? top.ribs.scguns.init.ModSounds.ITEM_PISTOL_COCK.getId()
                : top.ribs.scguns.init.ModSounds.ITEM_PISTOL_RELOAD.getId();
        send(human, id, 1, true);
    }

    private static void send(Human human, ResourceLocation id, float volume, boolean reload) {
        if (human.level().isClientSide || id == null || volume <= 0) return;
        double range = reload ? Config.SERVER.reloadMaxDistance.get() : Config.SERVER.gunShotMaxDistance.get();
        if (!Double.isFinite(range) || range <= 0) return;
        // false prevents a second muzzle flash. AIGunEvent already owns the NPC flash.
        var message = new S2CMessageGunSound(id, SoundSource.HOSTILE,
                (float) human.getX(), (float) human.getEyeY(), (float) human.getZ(),
                volume, 1, human.getId(), false, reload);
        PacketHandler.getPlayChannel().sendToNearbyPlayers(
                () -> LevelLocation.create(human.level(), human.getX(), human.getEyeY(), human.getZ(), range), message);
    }
}

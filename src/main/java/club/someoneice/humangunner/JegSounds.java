package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.mrcrayfish.framework.api.network.LevelLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import ttv.migami.jeg.Config;
import ttv.migami.jeg.network.PacketHandler;
import ttv.migami.jeg.network.message.S2CMessageGunSound;

/** Server-owned native sound/flash packet; never fabricates a player. */
final class JegSounds {
    private JegSounds() {}
    static void send(Human human, ResourceLocation id, float volume, boolean muzzle, boolean reload) {
        if (human.level().isClientSide || id == null) return;
        double range = reload ? Config.SERVER.reloadMaxDistance.get() : Config.SERVER.gunShotMaxDistance.get();
        if (!Double.isFinite(range) || range <= 0) return;
        var message = new S2CMessageGunSound(id, SoundSource.HOSTILE,
                (float) human.getX(), (float) human.getEyeY(), (float) human.getZ(), volume, 1,
                human.getId(), muzzle, reload);
        PacketHandler.getPlayChannel().sendToNearbyPlayers(
                () -> LevelLocation.create(human.level(), human.getX(), human.getEyeY(), human.getZ(), range), message);
    }
}

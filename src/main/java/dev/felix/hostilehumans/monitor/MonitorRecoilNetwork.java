package dev.felix.hostilehumans.monitor;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.entity.Human;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * Presentation-only S2C route from the existing paid/authorized NPC shot commit; never accepts
 * recoil from clients.
 */
public final class MonitorRecoilNetwork {
    private static final class Connection {
        static final SimpleChannel CHANNEL =
                NetworkRegistry.newSimpleChannel(
                        new ResourceLocation(HumanRuntime.MOD_ID, "monitor_recoil"),
                        () -> "1",
                        "1"::equals,
                        "1"::equals);
    }

    private static boolean registered;
    private static long sequence;

    private MonitorRecoilNetwork() {}

    public static void register() {
        if (registered) return;
        Connection.CHANNEL.registerMessage(
                0,
                MonitorRecoilSnapshot.class,
                MonitorRecoilSnapshot::encode,
                MonitorRecoilSnapshot::decode,
                (packet, supplier) -> {
                    var c = supplier.get();
                    c.enqueueWork(
                            () ->
                                    DistExecutor.unsafeRunWhenOn(
                                            Dist.CLIENT,
                                            () ->
                                                    () ->
                                                            dev.felix.hostilehumans.monitor.client
                                                                    .MonitorGunRecoil.accept(
                                                                    packet)));
                    c.setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registered = true;
    }

    public static void publishAcceptedShot(
            Human human, String backend, ItemStack weapon, float amplitude, float adsReduction) {
        if (!registered
                || human.level().isClientSide
                || !HumanMonitorControl.combatInputLive(human)) return;
        long shot = ++sequence;
        HumanMonitorService.forEachRecoilLease(
                human,
                (player, token, generation) -> {
                    var packet =
                            new MonitorRecoilSnapshot(
                                    token,
                                    human.getUUID(),
                                    human.getId(),
                                    generation,
                                    shot,
                                    backend,
                                    BuiltInRegistries.ITEM.getKey(weapon.getItem()),
                                    amplitude,
                                    adsReduction);
                    Connection.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
                });
    }
}

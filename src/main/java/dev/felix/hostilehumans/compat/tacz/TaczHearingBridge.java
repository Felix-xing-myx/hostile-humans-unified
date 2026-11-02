package dev.felix.hostilehumans.compat.tacz;

import com.tacz.guns.api.event.common.GunFireEvent;

import dev.felix.hostilehumans.perception.HumanAwareness;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;

/**
 * Loaded only by the installed TaCZ integration; GunShoot is a trigger attempt, GunFire is
 * discharge.
 */
public final class TaczHearingBridge {
    private static boolean registered;

    private TaczHearingBridge() {}

    public static void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, TaczHearingBridge::onFire);
    }

    private static void onFire(GunFireEvent event) {
        if (event.isCanceled() || event.getLogicalSide() != LogicalSide.SERVER) return;
        var shooter = event.getShooter();
        if (shooter == null || !(shooter.level() instanceof ServerLevel level)) return;
        var origin = shooter.position();
        Runnable deliver =
                () -> {
                    if (!shooter.isRemoved() && shooter.level() == level)
                        HumanAwareness.gunshot(shooter, origin);
                };
        if (level.getServer().isSameThread()) deliver.run();
        else level.getServer().execute(deliver);
    }
}

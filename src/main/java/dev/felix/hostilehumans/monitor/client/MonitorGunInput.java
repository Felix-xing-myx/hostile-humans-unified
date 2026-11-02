package dev.felix.hostilehumans.monitor.client;

import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client bindings only. Optional native handlers never receive authority over the real player. */
@Mod.EventBusSubscriber(modid = "hostile_humans_unified", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MonitorGunInput {
    private MonitorGunInput() {}
    static boolean nativeBindings(ItemStack stack) {
        return GunSupport.isTaczGunItem(stack) || GunSupport.isScgunsGunItem(stack) || GunSupport.isJegGunItem(stack);
    }
    static boolean attack(ItemStack stack) {
        if (GunSupport.isTaczGunItem(stack)) return dev.felix.hostilehumans.compat.tacz.TaczMonitorInput.attackDown();
        if (GunSupport.isScgunsGunItem(stack)) return dev.felix.hostilehumans.compat.scguns.ScgunsMonitorInput.attackDown();
        if (GunSupport.isJegGunItem(stack)) return dev.felix.hostilehumans.compat.jeg.JegMonitorInput.attackDown();
        return Minecraft.getInstance().options.keyAttack.isDown();
    }
    static boolean aim(ItemStack stack) {
        if (GunSupport.isTaczGunItem(stack)) return dev.felix.hostilehumans.compat.tacz.TaczMonitorInput.aimDown();
        if (GunSupport.isScgunsGunItem(stack)) return dev.felix.hostilehumans.compat.scguns.ScgunsMonitorInput.aimDown();
        if (GunSupport.isJegGunItem(stack)) return dev.felix.hostilehumans.compat.jeg.JegMonitorInput.aimDown();
        return Minecraft.getInstance().options.keyUse.isDown();
    }
    static boolean reload(ItemStack stack) {
        if (GunSupport.isTaczGunItem(stack)) return dev.felix.hostilehumans.compat.tacz.TaczMonitorInput.reloadDown();
        if (GunSupport.isScgunsGunItem(stack)) return dev.felix.hostilehumans.compat.scguns.ScgunsMonitorInput.reloadDown();
        if (GunSupport.isJegGunItem(stack)) return dev.felix.hostilehumans.compat.jeg.JegMonitorInput.reloadDown();
        return false;
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("scguns")) dev.felix.hostilehumans.compat.scguns.ScgunsMonitorInput.register();
            if (ModList.get().isLoaded("jeg")) dev.felix.hostilehumans.compat.jeg.JegMonitorInput.register();
        });
    }
}

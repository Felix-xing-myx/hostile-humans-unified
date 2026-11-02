package dev.felix.hostilehumans.monitor.combat;

import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.compat.NativeMonitorGunCompatibility;
import dev.felix.hostilehumans.entity.Human;
import net.minecraft.world.item.ItemStack;

/** Server-side optional adapter selection. Unsupported native seams never enter the attack menu. */
public final class MonitorGunBackends {
    private MonitorGunBackends() {}
    public static boolean supports(ItemStack stack) {
        return GunSupport.get().isGun(stack)
                && NativeMonitorGunCompatibility.available(GunSupport.get().backendId(stack));
    }
    public static MonitorGunActions create(Human human, MonitorCombatSession session) {
        ItemStack held = human.getMainHandItem();
        if (!supports(held)) return MonitorGunActions.NONE;
        return switch (GunSupport.get().backendId(held)) {
            case "tacz" -> new dev.felix.hostilehumans.compat.tacz.TaczMonitorWeapons(human, session);
            case "scguns" -> new dev.felix.hostilehumans.compat.scguns.ScgunsMonitorWeapons(human, session);
            case "jeg" -> new dev.felix.hostilehumans.compat.jeg.JegMonitorWeapons(human, session);
            default -> MonitorGunActions.NONE;
        };
    }
}

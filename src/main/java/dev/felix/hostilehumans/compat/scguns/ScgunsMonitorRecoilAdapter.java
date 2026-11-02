package dev.felix.hostilehumans.compat.scguns;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.monitor.HumanMonitorControl;
import dev.felix.hostilehumans.monitor.MonitorRecoilNetwork;

import net.minecraft.world.item.ItemStack;

import top.ribs.scguns.common.Gun;
import top.ribs.scguns.util.GunEnchantmentHelper;
import top.ribs.scguns.util.GunModifierHelper;

/** Optional native data/math seam; no client singleton, synthetic player or global recoil state. */
public final class ScgunsMonitorRecoilAdapter {
    private static boolean warned;

    private ScgunsMonitorRecoilAdapter() {}

    public static double aimSpeed(ItemStack weapon) {
        return GunModifierHelper.getModifiedAimDownSightSpeed(
                weapon, GunEnchantmentHelper.getAimDownSightSpeed(weapon));
    }

    public static void committed(Human human, ItemStack weapon, Gun gun) {
        if (human.level().isClientSide || !HumanMonitorControl.combatInputLive(human)) return;
        try {
            if (!top.ribs.scguns.Config.SERVER.enableCameraRecoil.get()) return;
            // Matches native Post camera formula. Player-only HotBarrel history has no NPC
            // analogue;
            // use the native stack overload instead of borrowing the observer's player history.
            float amplitude =
                    gun.getProjectile().getRecoilAngle()
                            * (1 - GunModifierHelper.getRecoilModifier(weapon))
                            * GunEnchantmentHelper.getRecoilModifier(weapon);
            MonitorRecoilNetwork.publishAcceptedShot(
                    human,
                    "scguns",
                    weapon,
                    Math.max(0, amplitude),
                    gun.getGeneral().getRecoilAdsReduction());
        } catch (RuntimeException | LinkageError failure) {
            // Presentation failure cannot undo the existing paid shot commit.
            if (!warned) {
                warned = true;
                com.mojang.logging.LogUtils.getLogger()
                        .warn("Scguns NPC monitor recoil unavailable", failure);
            }
        }
    }
}

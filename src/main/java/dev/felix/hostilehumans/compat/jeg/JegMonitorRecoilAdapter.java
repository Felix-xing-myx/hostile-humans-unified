package dev.felix.hostilehumans.compat.jeg;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.monitor.HumanMonitorControl;
import dev.felix.hostilehumans.monitor.MonitorRecoilNetwork;

import net.minecraft.world.item.ItemStack;

import ttv.migami.jeg.common.Gun;
import ttv.migami.jeg.util.GunEnchantmentHelper;
import ttv.migami.jeg.util.GunModifierHelper;

/** Isolates JEG's actual General recoil field and attachment helper from the common facade. */
public final class JegMonitorRecoilAdapter {
    private static boolean warned;

    private JegMonitorRecoilAdapter() {}

    public static double aimSpeed(ItemStack weapon) {
        return GunModifierHelper.getModifiedAimDownSightSpeed(
                weapon, GunEnchantmentHelper.getAimDownSightSpeed(weapon));
    }

    public static void committed(Human human, ItemStack weapon, Gun gun) {
        if (human.level().isClientSide || !HumanMonitorControl.combatInputLive(human)) return;
        try {
            if (!ttv.migami.jeg.Config.SERVER.enableCameraRecoil.get()) return;
            float amplitude =
                    gun.getGeneral().getRecoilAngle()
                            * (1 - GunModifierHelper.getRecoilModifier(weapon));
            MonitorRecoilNetwork.publishAcceptedShot(
                    human,
                    "jeg",
                    weapon,
                    Math.max(0, amplitude),
                    gun.getGeneral().getRecoilAdsReduction());
        } catch (RuntimeException | LinkageError failure) {
            // Presentation failure cannot undo the existing paid shot commit.
            if (!warned) {
                warned = true;
                com.mojang.logging.LogUtils.getLogger()
                        .warn("JEG NPC monitor recoil unavailable", failure);
            }
        }
    }
}

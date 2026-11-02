package dev.felix.hostilehumans.behavior.combat.gun;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.HumanCombatDirector;
import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
import dev.felix.hostilehumans.equipment.EquipmentArchetypes;

import net.minecraft.world.item.ItemStack;

/** Current real weapon drives doctrine. No entity role or copied weapon state. */
public final class GunCombatProfile {
    private GunCombatProfile() {}
    public static GunDoctrine.Style of(Human human,ItemStack gun) {
        var identity=EquipmentArchetypes.describe(human,gun);
        return GunDoctrine.style(HumanCombatDirector.strategies(human).gun(),GunDoctrine.category(identity.family()),identity.core());
    }
    public static double spread(Human human,ItemStack gun) { return of(human,gun).spread(); }
    public static boolean backgroundReload(Human human,ItemStack gun) {
        return gun!=human.getMainHandItem()||human.isUsingItem()&&SpartanEquipmentCompat.isShield(human.getUseItem());
    }
    public static double reloadWork(Human human,ItemStack gun) {
        var style=of(human,gun);
        return GunDoctrine.workPerTick(style.reloadDuration(),style.backgroundDuration(),backgroundReload(human,gun));
    }
    public static int reloadHardLimit(Human human, ItemStack gun, int nativeRecoveryDelay) {
        var style = of(human, gun);
        return GunDoctrine.reloadHardLimit(GunReloadPolicy.hardLimitTicks(nativeRecoveryDelay),
                style.reloadDuration(), style.backgroundDuration());
    }
    public static boolean canReloadDuringUse(Human human) {
        return !human.isUsingItem()||SpartanEquipmentCompat.isShield(human.getUseItem());
    }
    static boolean shouldForceMelee(Human human,ItemStack gun) { return of(human,gun).forceMelee(); }
}

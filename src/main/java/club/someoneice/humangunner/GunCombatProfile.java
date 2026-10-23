package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.item.ItemStack;

/** Current real weapon drives doctrine. No entity role or copied weapon state. */
public final class GunCombatProfile {
    private GunCombatProfile() {}
    static GunDoctrine.Style of(Human human,ItemStack gun) {
        var identity=EquipmentArchetypes.describe(human,gun);
        return GunDoctrine.style(HumanCombatDirector.strategies(human).gun(),GunDoctrine.category(identity.family()),identity.core());
    }
    public static double spread(Human human,ItemStack gun) { return of(human,gun).spread(); }
    static boolean silent(Human human,ItemStack gun) {
        return gun!=human.getMainHandItem()||human.isUsingItem()&&SpartanEquipmentCompat.isShield(human.getUseItem());
    }
    static double reloadWork(Human human,ItemStack gun) {
        var style=of(human,gun);
        return GunDoctrine.workPerTick(style.reloadDuration(),style.backgroundDuration(),silent(human,gun));
    }
    static boolean canReloadDuringUse(Human human) {
        return !human.isUsingItem()||SpartanEquipmentCompat.isShield(human.getUseItem());
    }
    static boolean shouldForceMelee(Human human,ItemStack gun) { return of(human,gun).forceMelee(); }
}

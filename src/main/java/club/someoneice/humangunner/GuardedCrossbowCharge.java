package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/** Server-only loading progress while vanilla's sole active-use slot belongs to the shield. */
final class GuardedCrossbowCharge {
    private ItemStack charging;
    private int progress;

    void reset() { charging = null; progress = 0; }

    void tick(Human human) {
        ItemStack weapon = human.getMainHandItem();
        if (human.level().isClientSide || human.isFleeing
                || !human.isUsingItem() || !SpartanEquipmentCompat.isShield(human.getUseItem())
                || !(weapon.getItem() instanceof CrossbowItem crossbow)
                || !HumanAmmo.hasArrows(human) || CrossbowItem.isCharged(weapon)) {
            reset();
            return;
        }
        if (charging != weapon) { charging = weapon; progress = 0; }
        // Match the existing NPC crossbow goal's light/heavy loading rate.
        progress += SpartanRangedCompat.isHeavyCrossbow(weapon) ? 2 : 4;
        if (progress < CrossbowItem.getChargeDuration(weapon)) return;
        // Native release validates/loading ammunition through Human#getProjectile.
        // No second startUsingItem call: that would cancel the shield.
        crossbow.releaseUsing(weapon, human.level(), human,
                crossbow.getUseDuration(weapon) - progress);
        if (SpartanRangedCompat.isHeavyCrossbow(weapon)) SpartanRangedCompat.markChargedForNpc(weapon);
        if (CrossbowItem.isCharged(weapon)) HumanArrowCombat.markNpcLoad(weapon);
        // Human's existing volley path pays one backpack arrow when it actually fires.
        reset();
    }
}

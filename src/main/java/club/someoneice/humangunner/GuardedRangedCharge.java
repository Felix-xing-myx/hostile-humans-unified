package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/** Server-only loading progress while vanilla's sole active-use slot belongs to the shield. */
final class GuardedRangedCharge {
    private ItemStack charging;
    private int progress;
    private int expiresAt;
    private int lastTick = Integer.MIN_VALUE;

    void reset() { charging = null; progress = 0; expiresAt = 0; }

    void capture(Human human) {
        if (!human.isUsingItem() || human.getUseItem() != human.getMainHandItem()) return;
        ItemStack weapon = human.getUseItem();
        if (!(weapon.getItem() instanceof BowItem || weapon.getItem() instanceof CrossbowItem)) return;
        if (charging != weapon) { charging = weapon; progress = 0; }
        progress = Math.max(progress, human.getTicksUsingItem());
        lastTick = human.tickCount; // The active weapon already advanced on this tick.
        expiresAt = human.tickCount + 60;
    }

    void restore(Human human) {
        if (charging == null) return;
        if (human.tickCount > expiresAt || human.getMainHandItem() != charging
                || human.getTarget() == null || !human.getTarget().isAlive() || !HumanAmmo.hasArrows(human)) {
            reset();
            return;
        }
        if (human.isUsingItem() && human.getUseItem() == charging) {
            human.advanceUseTime(Math.max(0, progress - human.getTicksUsingItem()));
            reset();
        }
    }

    boolean ready(Human human) {
        ItemStack weapon = human.getMainHandItem();
        return weapon.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(weapon)
                || weapon == charging && weapon.getItem() instanceof BowItem && progress >= 20;
    }

    int counterWindowTicks(Human human) {
        ItemStack weapon = human.getMainHandItem();
        if (!(weapon.getItem() instanceof CrossbowItem)) return 32;
        boolean heavy = SpartanRangedCompat.isHeavyCrossbow(weapon);
        int rate = heavy ? 2 : 4;
        int remaining = CrossbowItem.isCharged(weapon) ? 0
                : Math.max(0, CrossbowItem.getChargeDuration(weapon) - (weapon == charging ? progress : 0));
        int loadingTicks = (remaining + rate - 1) / rate;
        // A counter lease must fit the normal loaded wait too, not bypass it to fit the lease.
        int loadedWait = ArrowAttackCadence.crossbowWaitTicks(human.tickCount + loadingTicks,
                ArrowAttackCadence.CROSSBOW_DELAY_MIN + ArrowAttackCadence.CROSSBOW_DELAY_VARIANCE - 1, heavy);
        return Math.max(32, loadingTicks + loadedWait + 2);
    }

    void tick(Human human) {
        ItemStack weapon = human.getMainHandItem();
        if (human.level().isClientSide || human.isFleeing
                || !human.isUsingItem() || !SpartanEquipmentCompat.isShield(human.getUseItem())
                || !(weapon.getItem() instanceof CrossbowItem || weapon.getItem() instanceof BowItem)
                || !HumanAmmo.hasArrows(human) || human.getTarget() == null || !human.getTarget().isAlive()) {
            reset();
            return;
        }
        if (lastTick == human.tickCount) return;
        lastTick = human.tickCount;
        expiresAt = human.tickCount + 60;
        if (weapon.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(weapon)) return;
        if (charging != weapon) { charging = weapon; progress = 0; }
        if (weapon.getItem() instanceof BowItem) {
            if (!HumanArrowCombat.canDrawBow(human)) return;
            progress = Math.min(20, progress + 3);
            return;
        }
        CrossbowItem crossbow = (CrossbowItem) weapon.getItem();
        HumanArrowCombat.beginCrossbowLoad(human, weapon);
        // Match the existing NPC crossbow goal's light/heavy loading rate.
        progress += SpartanRangedCompat.isHeavyCrossbow(weapon) ? 2 : 4;
        if (progress < CrossbowItem.getChargeDuration(weapon)) return;
        // Native release validates/loading ammunition through Human#getProjectile.
        // No second startUsingItem call: that would cancel the shield.
        crossbow.releaseUsing(weapon, human.level(), human,
                crossbow.getUseDuration(weapon) - progress);
        if (SpartanRangedCompat.isHeavyCrossbow(weapon)) SpartanRangedCompat.markChargedForNpc(weapon);
        if (CrossbowItem.isCharged(weapon)) {
            HumanArrowCombat.markNpcLoad(weapon);
            HumanArrowCombat.crossbowLoaded(human, weapon);
        }
        // Human's existing volley path pays one backpack arrow when it actually fires.
        reset();
    }
}

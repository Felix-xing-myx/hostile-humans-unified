package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/** A normalized draw follows the original real weapon across legal equipment exchanges. */
final class GuardedRangedCharge {
    private HumanEquipmentExchange.Handle charging;
    private double progress;
    private int lastTick = Integer.MIN_VALUE;

    void reset() { charging = null; progress = 0; lastTick = Integer.MIN_VALUE; }
    private ItemStack actual(Human human) { return HumanEquipmentExchange.locate(human, charging); }
    private static int duration(ItemStack weapon) {
        return weapon.getItem() instanceof BowItem ? 20 : CrossbowItem.getChargeDuration(weapon);
    }
    private static double foregroundRate(Human human, ItemStack weapon) {
        return weapon.getItem() instanceof BowItem ? 3 * RangedCombatProfile.of(human, weapon).attackRate()
                : SpartanRangedCompat.isHeavyCrossbow(weapon) ? 2 : 4;
    }
    void capture(Human human) {
        if (human.level().isClientSide || !human.isUsingItem()) return;
        ItemStack weapon = human.getUseItem();
        if (!(weapon.getItem() instanceof BowItem || weapon.getItem() instanceof CrossbowItem)) return;
        if (actual(human) != weapon) {
            charging = HumanEquipmentExchange.bind(human, weapon); progress = 0;
        }
        if (charging == null) return;
        progress = Math.max(progress, Math.min(1, (double) human.getTicksUsingItem() / duration(weapon)));
        lastTick = human.tickCount;
    }
    void restore(Human human) {
        if (charging == null) return;
        ItemStack weapon = actual(human);
        if (weapon.isEmpty() || !human.isAlive() || human.getTarget() == null
                || !human.getTarget().isAlive() || HumanAmmo.arrowCount(human) == 0) { reset(); return; }
        if (human.isUsingItem() && human.getUseItem() == weapon) {
            human.advanceUseTime(Math.max(0, (int) Math.floor(progress * duration(weapon)) - human.getTicksUsingItem()));
            reset();
        }
    }
    boolean ready(Human human) {
        ItemStack weapon = actual(human);
        return !weapon.isEmpty() && (weapon.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(weapon)
                || weapon.getItem() instanceof BowItem && progress >= 1);
    }
    int counterWindowTicks(Human human) {
        ItemStack weapon = actual(human);
        if (weapon.isEmpty()) weapon = human.getMainHandItem();
        if (!(weapon.getItem() instanceof BowItem || weapon.getItem() instanceof CrossbowItem)) return 32;
        int remaining = (int) Math.ceil((1 - progress) * duration(weapon) / foregroundRate(human, weapon));
        return Math.max(32, remaining + 2);
    }
    void tick(Human human) {
        if (human.level().isClientSide) return;
        if (!human.isAlive() || human.isFleeing || human.getTarget() == null
                || !human.getTarget().isAlive() || HumanAmmo.arrowCount(human) == 0
                || HumanInventorySession.isEditing(human)) { reset(); return; }
        if (!human.isUsingItem() || !SpartanEquipmentCompat.isShield(human.getUseItem())) return;
        ItemStack weapon = actual(human);
        if (weapon.isEmpty()) {
            if (charging != null) { reset(); return; } // Missing identity cannot bind a replacement.
            weapon = human.getMainHandItem();
            if (!(weapon.getItem() instanceof BowItem || weapon.getItem() instanceof CrossbowItem)) return;
            charging = HumanEquipmentExchange.bind(human, weapon);
            if (charging == null) return;
        }
        if (lastTick == human.tickCount) return;
        lastTick = human.tickCount;
        if (weapon.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(weapon)) return;
        if (weapon.getItem() instanceof BowItem && !HumanArrowCombat.canDrawBow(human)) return;
        progress = Math.min(1, progress + foregroundRate(human, weapon) / (1.5 * duration(weapon)));
        if (progress < 1 || !(weapon.getItem() instanceof CrossbowItem crossbow)) return;
        HumanArrowCombat.beginCrossbowLoad(human, weapon);
        crossbow.releaseUsing(weapon, human.level(), human, crossbow.getUseDuration(weapon) - duration(weapon));
        if (SpartanRangedCompat.isHeavyCrossbow(weapon)) SpartanRangedCompat.markChargedForNpc(weapon);
        if (CrossbowItem.isCharged(weapon)) {
            HumanArrowCombat.markNpcLoad(weapon);
            HumanArrowCombat.crossbowCompletedBehindGuard(human, weapon);
        }
    }
}

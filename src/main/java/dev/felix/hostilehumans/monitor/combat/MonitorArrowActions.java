package dev.felix.hostilehumans.monitor.combat;

import dev.felix.hostilehumans.behavior.ActionCoordinator;
import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.behavior.combat.ranged.HumanAmmo;
import dev.felix.hostilehumans.behavior.combat.ranged.HumanArrowCombat;
import dev.felix.hostilehumans.entity.Human;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import java.util.EnumSet;

/** Draw/load input lifecycle. Projectile publication and backpack payment remain in HumanArrowCombat. */
final class MonitorArrowActions {
    private final Human human;
    private ItemStack drawing = ItemStack.EMPTY;
    private ActionCoordinator.Token charge;
    private boolean previousUse;
    MonitorArrowActions(Human human) { this.human = human; }
    void tick(boolean use, MonitorCombatSession session) {
        ItemStack weapon = human.getMainHandItem();
        boolean crossbow = weapon.getItem() instanceof CrossbowItem;
        if (crossbow && CrossbowItem.isCharged(weapon)) {
            if (use && !previousUse) {
                HumanArrowCombat.crossbowCompletedBehindGuard(human, weapon);
                session.arrowDispatch(() -> {
                    if (dev.felix.hostilehumans.compat.spartan.SpartanRangedCompat.isHeavyCrossbow(weapon)) {
                        dev.felix.hostilehumans.compat.spartan.SpartanRangedCompat.fire(human, null);
                        return;
                    }
                    int emitted = HumanArrowCombat.fireNativeCrossbow(human, null, weapon);
                    if (emitted > 0) {
                        human.hhu$wearControlledWeapon(weapon);
                        human.playSound(SoundEvents.CROSSBOW_SHOOT, 1, 1);
                    }
                });
            }
            previousUse = use;
            return;
        }
        if (use && charge == null && HumanAmmo.hasArrows(human)
                && (crossbow ? HumanArrowCombat.beginCrossbowLoad(human, weapon) : HumanArrowCombat.canDrawBow(human))) {
            var actions = HumanBehaviorControl.actions(human);
            charge = actions.requestLease("monitor:draw", EnumSet.of(ActionCoordinator.Resource.USE_ITEM,
                    ActionCoordinator.Resource.MAIN_HAND), 70, true, 8);
            if (actions.acquire(charge)) {
                drawing = weapon; human.startUsingItem(InteractionHand.MAIN_HAND);
                if (crossbow) human.setChargingCrossbow(true);
            }
            else { actions.finish(charge, ActionCoordinator.Status.CANCELLED, "manual draw busy"); charge = null; }
        }
        if (charge != null) {
            var actions = HumanBehaviorControl.actions(human);
            if (drawing != weapon || human.getUseItem() != weapon || !actions.heartbeat(charge)) { cancel(); return; }
            if (crossbow) {
                if (use) human.advanceUseTime(3);
                if (human.getTicksUsingItem() >= CrossbowItem.getChargeDuration(weapon)) {
                    human.releaseUsingItem();
                    dev.felix.hostilehumans.compat.spartan.SpartanRangedCompat.markChargedForNpc(weapon);
                    if (CrossbowItem.isCharged(weapon)) {
                        HumanArrowCombat.markNpcLoad(weapon);
                        HumanArrowCombat.crossbowCompletedBehindGuard(human, weapon);
                    }
                    endCharge(true);
                } else if (!use) { human.stopUsingItem(); endCharge(false); }
            } else if (use) HumanArrowCombat.advanceBowDraw(human);
            else {
                float power = BowItem.getPowerForTime(human.getTicksUsingItem());
                human.stopUsingItem(); endCharge(power >= .1F);
                if (power >= .1F && weapon.getItem() instanceof BowItem bow) session.arrowDispatch(() -> {
                    int emitted = HumanArrowCombat.fireVolley(human, null, weapon, () -> {
                        AbstractArrow arrow = ProjectileUtil.getMobArrow(human, HumanArrowCombat.projectileFor(human), power);
                        arrow = bow.customArrow(arrow);
                        dev.felix.hostilehumans.behavior.combat.ranged.ManualArrowBallistics.aim(human, arrow, 2.7F * power, false);
                        return arrow;
                    });
                    if (emitted > 0) {
                        human.hhu$wearControlledWeapon(weapon);
                        human.playSound(SoundEvents.ARROW_SHOOT, 1, 1);
                    }
                });
            }
        }
        previousUse = use;
    }
    private void endCharge(boolean completed) {
        if (charge != null) HumanBehaviorControl.actions(human).finish(charge,
                completed ? ActionCoordinator.Status.COMPLETED : ActionCoordinator.Status.CANCELLED,
                completed ? "manual draw/load completed" : "manual draw interrupted");
        charge = null; drawing = ItemStack.EMPTY;
        human.setChargingCrossbow(false);
    }
    void cancel() {
        if (charge != null) { human.stopUsingItem(); endCharge(false); }
        previousUse = false;
    }
}

package dev.felix.hostilehumans.monitor.combat;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.behavior.ActionCoordinator;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.melee.BetterCombatMeleeCombat;
import dev.felix.hostilehumans.behavior.combat.melee.MeleeActionControl;
import dev.felix.hostilehumans.behavior.combat.melee.MeleeAttackTiming;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.faction.HumanRelations;
import dev.felix.hostilehumans.monitor.HumanMonitorControl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.InteractionHand;

/** One server takeover owns one input interpreter. AI perception/target selection is not an input dependency. */
public final class MonitorCombatSession implements dev.felix.hostilehumans.behavior.combat.HumanCombatAuthority.Controller {
    private final Human human;
    private ItemStack weapon = ItemStack.EMPTY;
    private MonitorGunActions gun = MonitorGunActions.NONE;
    private final MonitorArrowActions arrows;
    private final MonitorCombatInputGate inputGate = new MonitorCombatInputGate();
    private final MonitorLoadout loadout;
    private boolean dispatchGun, dispatchArrow, dispatchReload;
    private int airReady;
    private LivingEntity selectedTarget;
    private ActionCoordinator.Token airAction;
    private boolean airBetterCombat;
    public MonitorCombatSession(Human human) { this.human = human; arrows = new MonitorArrowActions(human); loadout = new MonitorLoadout(human); }
    public MonitorLoadout loadout() { return loadout; }

    @Override public boolean active() { return HumanMonitorControl.combatInputLive(human); }
    @Override public boolean targetAllowed(LivingEntity target) { return target != null && target != human && target.isAlive() && !target.isRemoved()
                && !target.isSpectator() && target.level() == human.level() && !HumanRelations.allied(human, target); }
    @Override public boolean targetOwned(LivingEntity target) { return selectedTarget == target && targetAllowed(target); }
    @Override public boolean weaponOwned(ItemStack held) { return held == weapon && held == human.getMainHandItem(); }
    @Override public boolean gunRequested(ItemStack held) { return dispatchGun && weaponOwned(held); }
    @Override public boolean reloadRequested(ItemStack held) { return dispatchReload && weaponOwned(held); }
    @Override public boolean arrowRequested(ItemStack held, boolean followup) { return (dispatchArrow || followup) && weaponOwned(held); }

    public void arrowDispatch(Runnable action) {
        dispatchArrow = true;
        try { action.run(); } finally { dispatchArrow = false; }
    }
    /** The optional backend invokes this only around the paid native reload call. */
    public void reloadDispatch(Runnable action) {
        dispatchReload = true;
        try { action.run(); } finally { dispatchReload = false; }
    }

    public void tick(boolean enabled, boolean attack, boolean use, boolean reload) {
        tick(enabled, attack, use, reload, false);
    }
    public void tick(boolean enabled, boolean attack, boolean use, boolean reload, boolean supply) {
        if (!enabled) { suspend(); return; }
        var held = human.getMainHandItem();
        if (held != weapon) {
            suspend(); weapon = held;
            gun = MonitorGunBackends.create(human, this);
        }
        if (!inputGate.accept(true, attack || supply, use, reload)) {
            dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies.tickManualUse(human, false);
            return;
        }
        HumanBehaviorControl.actions(human);
        dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies.tickManualUse(human, supply);
        boolean ranged = GunSupport.isGunItem(held) || held.getItem() instanceof BowItem || held.getItem() instanceof CrossbowItem;
        human.hhu$shieldDefense().tickManualGuard(!ranged && use && !attack && !supply);
        if (human.isUsingItem() && human.getUseItem().getUseAnimation() == net.minecraft.world.item.UseAnim.BLOCK) return;
        if (dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies.isDrinkingPotion(human)) {
            gun.tick(false, false, false); return;
        }
        if (airAction != null && human.tickCount >= airReady) {
            boolean completed = HumanBehaviorControl.actions(human).finish(airAction, ActionCoordinator.Status.COMPLETED, "manual air swing finished");
            if (completed && airBetterCombat) BetterCombatMeleeCombat.completeAttack(human);
            airAction = null;
        }
        MeleeActionControl.tick(human);
        dev.felix.hostilehumans.behavior.combat.ranged.HumanArrowCombat.tickFollowup(human);
        if (GunSupport.isGunItem(held)) {
            dispatchGun = gun.fireIntent(attack);
            try { gun.tick(attack, use, reload); } finally { dispatchGun = false; }
            return;
        }
        boolean rangedUse = use || human.isUsingItem();
        if (held.getItem() instanceof BowItem || held.getItem() instanceof CrossbowItem) arrows.tick(use, this);
        else arrows.cancel();
        if (attack && !rangedUse && !human.isUsingItem() && !human.hhu$hasPendingMeleeAttack() && human.tickCount >= airReady) melee();
        if (!human.hhu$hasPendingMeleeAttack()) { selectedTarget = null; human.setTarget(null); }
    }

    private void melee() {
        double reach = BetterCombatMeleeCombat.hasWeaponProfile(human) ? BetterCombatMeleeCombat.attackRange(human)
                : human.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
        reach = Double.isFinite(reach) ? Math.max(.1, Math.min(16, reach)) : 3;
        var origin = human.getEyePosition();
        var desired = origin.add(human.getViewVector(1).scale(reach));
        var block = human.level().clip(new net.minecraft.world.level.ClipContext(origin, desired,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, human));
        var end = block.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? desired : block.getLocation();
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(human.level(), human, origin, end,
                human.getBoundingBox().expandTowards(end.subtract(origin)).inflate(1),
                entity -> entity instanceof LivingEntity target && targetAllowed(target) && human.canAttack(target));
        if (hit != null && hit.getEntity() instanceof LivingEntity target) {
            selectedTarget = target;
            human.setTarget(target);
            if (human.doHurtTarget(target)) airReady = human.tickCount + MeleeAttackTiming.interval(human);
        } else {
            selectedTarget = null;
            var backend = BetterCombatMeleeCombat.backend(human);
            if (backend == BetterCombatMeleeCombat.Backend.WAIT) return;
            var actions = HumanBehaviorControl.actions(human);
            airAction = actions.request("monitor:air-swing", java.util.EnumSet.of(ActionCoordinator.Resource.ATTACK,
                    ActionCoordinator.Resource.MAIN_HAND, ActionCoordinator.Resource.FACING), 60, true, MeleeAttackTiming.interval(human) + 1L);
            if (!actions.acquire(airAction)) { actions.finish(airAction, ActionCoordinator.Status.CANCELLED, "air swing busy"); airAction = null; return; }
            airBetterCombat = backend == BetterCombatMeleeCombat.Backend.BETTER_COMBAT;
            var profile = airBetterCombat ? BetterCombatMeleeCombat.manualSwingProfile(human) : null;
            if (profile != null) human.setNpcBetterCombatAttackAnimation(profile.animationName(), profile.cooldownTicks(),
                    profile.animationUpswing(), profile.offhandAttack());
            human.swing(profile != null && profile.offhandAttack() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            airReady = human.tickCount + MeleeAttackTiming.interval(human);
            human.setTarget(null);
        }
    }
    public void suspend() {
        inputGate.reset();
        dispatchGun = dispatchArrow = dispatchReload = false;
        gun.cancel(); arrows.cancel();
        dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies.cancelManualUse(human);
        human.hhu$shieldDefense().tickManualGuard(false);
        if (airAction != null) HumanBehaviorControl.actions(human).finish(airAction, ActionCoordinator.Status.CANCELLED, "manual input suspended");
        airAction = null;
        dev.felix.hostilehumans.behavior.combat.ranged.HumanArrowCombat.clear(human);
        human.stopUsingItem(); human.hhu$cancelMeleeAttackForDefense();
        selectedTarget = null;
        human.setTarget(null);
    }
    public void close() {
        Throwable failure = null;
        try {
            suspend();
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            try {
                HumanBehaviorControl.cancel(human, "monitor combat session closed");
            } catch (RuntimeException | Error cleanup) {
                if (failure == null) throw cleanup;
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
        }
    }
}

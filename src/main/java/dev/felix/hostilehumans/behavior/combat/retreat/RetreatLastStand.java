package dev.felix.hostilehumans.behavior.combat.retreat;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.HumanTargetCoordinator;
import dev.felix.hostilehumans.behavior.combat.gun.GunCustody;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.melee.MeleeActionControl;
import dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies;
import dev.felix.hostilehumans.inventory.HumanEquipmentExchange;
import dev.felix.hostilehumans.inventory.HumanInventorySession;
import dev.felix.hostilehumans.inventory.HumanLootManager;
import dev.felix.hostilehumans.perception.HumanPerception;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Detects failed escape and grants a short-lived counter window against the known retreat threat. */
public final class RetreatLastStand {
    static final double THREAT_DISTANCE_SQR = 36;
    static final int LEASE_TICKS = 2;
    private static final class State {
        final RetreatEscapeProgress progress = new RetreatEscapeProgress();
        java.lang.ref.WeakReference<LivingEntity> threat;
        long updated;
        boolean active;
        int windowStart, hiddenSince = -1;
        double baselineDistance;
        boolean routeThreatened;
        State(LivingEntity threat) { this.threat = new java.lang.ref.WeakReference<>(threat); }
    }
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private RetreatLastStand() { }

    public static void observe(Human human, LivingEntity threat, boolean routeUnavailable) {
        if (!context(human) || threat == null || !HumanPerception.canTarget(human, threat)) {
            clear(human); return;
        }
        State state = STATES.get(human);
        if (state == null) {
            state = new State(threat); STATES.put(human, state);
            state.windowStart = human.tickCount;
            state.baselineDistance = human.distanceTo(threat);
        } else if (state.threat.get() != threat) {
            if (state.active) cancelAttack(human);
            state.windowStart = human.tickCount;
            state.baselineDistance = human.distanceTo(threat);
            state.hiddenSince = -1;
            state.active = false;
            state.threat = new java.lang.ref.WeakReference<>(threat); // Changing enemies does not erase a stationary retreat's progress history.
        }
        boolean previous = state.active;
        state.updated = human.level().getGameTime();
        if (human.tickCount % 6 == 0) state.routeThreatened = routeThreatened(human, threat);
        boolean stalled = state.progress.update(state.updated, human.getX(), human.getY(), human.getZ(),
                routeUnavailable, human.onClimbable() || human.isInWater());
        int now = human.tickCount;
        boolean visible = human.getSensing().hasLineOfSight(threat);
        if (visible) state.hiddenSince = -1;
        else if (state.hiddenSince < 0) state.hiddenSince = now;
        double distance = human.distanceTo(threat);
        boolean separated = distance >= state.baselineDistance + 1.5
                || state.hiddenSince >= 0 && now - state.hiddenSince >= 10;
        if (separated && !state.routeThreatened) {
            state.active = human.hhu$hasPendingMeleeAttack();
            state.windowStart = now;
            state.baselineDistance = distance;
        } else if (visible && (stalled || now - state.windowStart >= 20
                && LowHealthCombatController.recentLoss(human, 20) > 0)) {
            state.active = true;
        }
        if (previous && !state.active) cancelAttack(human);
    }
    private static boolean routeThreatened(Human human, LivingEntity primary) {
        var path = human.getNavigation().getPath();
        if (path == null || path.isDone()) return false;
        var next = path.getEntityPosAtNode(human, path.getNextNodeIndex());
        int visited = 0;
        for (LivingEntity enemy : HumanPerception.knownDynamicTargets(human)) {
            if (++visited > 16) break;
            if (enemy != primary && enemy.isAlive() && human.canAttack(enemy)
                    && human.distanceToSqr(enemy) <= 16 && enemy.distanceToSqr(next) <= 4
                    && enemy.distanceToSqr(next) < human.distanceToSqr(enemy)) return true;
        }
        return false;
    }

    private static boolean context(Human human) {
        return !human.level().isClientSide && human.isFleeing && human.explosionThreat == null
                && human.isAlive() && !human.isRemoved() && !human.isNoAi()
                && !HumanInventorySession.isEditing(human) && !HumanTargetCoordinator.suspended(human);
    }
    public static boolean active(Human human) {
        State state = STATES.get(human);
        long now = human.level().getGameTime();
        LivingEntity threat = state == null ? null : state.threat.get();
        return context(human) && state != null && state.active && now >= state.updated && now - state.updated <= LEASE_TICKS
                && threat != null && threat.isAlive()
                && HumanPerception.canTarget(human, threat);
    }
    public static boolean permitsMelee(Human human) {
        State state = STATES.get(human);
        return active(human) && state.threat.get() == human.getTarget() && human.distanceToSqr(human.getTarget()) <= THREAT_DISTANCE_SQR && human.canAttack(human.getTarget());
    }
    public static boolean prepareWeapon(Human human) {
        if (!permitsMelee(human) || human.isUsingItem() || RecoverySupplies.hasActiveUse(human)
                || human.hhu$hasPendingMeleeAttack() || human.hhu$shieldDefense().shouldSuppressRecovery()) return false;
        if (GunSupport.isGunItem(human.getMainHandItem())) return GunCustody.beginRetreatMeleeCounter(human);
        if (HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem())) return true;
        ItemStack melee = HumanLootManager.bestOwnedMeleeWeapon(human);
        if (melee.isEmpty()) return true; // A nongun held item can still use native unarmed melee; never mint a weapon.
        return HumanEquipmentExchange.request(human, "retreat-last-stand", java.util.List.of(
                new HumanEquipmentExchange.Need(EquipmentSlot.MAINHAND, stack -> stack == melee)))
                == HumanEquipmentExchange.Result.SUCCESS;
    }
    public static void clear(Human human) {
        State state = STATES.remove(human);
        if (state != null && state.active) cancelAttack(human);
    }
    private static void cancelAttack(Human human) {
        MeleeActionControl.finish(human, false, "retreat escape became available or context changed");
        human.hhu$cancelMeleeAttackForDefense();
    }
}

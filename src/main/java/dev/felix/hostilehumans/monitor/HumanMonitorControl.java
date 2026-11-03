package dev.felix.hostilehumans.monitor;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.inventory.HumanInventorySession;
import dev.felix.hostilehumans.observer.NavigationDebugService;
import dev.felix.hostilehumans.perception.HumanTurnControl;
import dev.felix.hostilehumans.soldier.SoldierRecallProtection;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import dev.felix.hostilehumans.monitor.combat.MonitorCombatSession;

/** Server movement lease, separate from observation, AI policies and inventory editing.
 * Does not change NoAI, ownership or persisted orders; no temporary flag can survive a save. */
public final class HumanMonitorControl {
    private static long nextGeneration;
    private static final Map<Human, Lease> LEASES = new WeakHashMap<>();
    private static final class Lease {
        final ServerPlayer player;
        final UUID token;
        long generation = ++nextGeneration;
        boolean inventoryPaused;
        long lastSnapshotTick = -1;
        dev.felix.hostilehumans.monitor.MonitorSequencedInput<HumanMonitorNetwork.Control> inputs =
                new dev.felix.hostilehumans.monitor.MonitorSequencedInput<>(5);
        float forward, sideways, yaw, pitch;
        boolean jump, sprint, sneak;
        boolean active, attack, use, reload, supply;
        MonitorLoadoutSnapshot sentLoadout;
        MonitorMotionSnapshot.Physics travelPhysics;
        final MonitorCombatSession combat;
        Lease(ServerPlayer player, UUID token, Human human) {
            this.player = player; this.token = token;
            yaw = human.getYHeadRot(); pitch = human.getXRot();
            combat = new MonitorCombatSession(human);

        }
    }
    private HumanMonitorControl() {}
    public static boolean controls(Human human) { return !human.level().isClientSide && LEASES.containsKey(human); }
    public static boolean combatInputLive(Human human) {
        Lease lease = LEASES.get(human);
        return lease != null && !lease.inventoryPaused && lease.active && lease.inputs.fresh(human.level().getGameTime())
                && HumanMonitorService.target(lease.player) == human && human.isAlive() && !human.isRemoved()
                && !human.isNoAi() && !human.isSleeping() && !human.isPassenger() && !HumanInventorySession.isEditing(human);
    }
    static long generation(Human human, ServerPlayer player, UUID token) {
        Lease lease = LEASES.get(human);
        return lease != null && lease.player == player && lease.token.equals(token) ? lease.generation : 0;
    }
    static boolean ownedBy(Human human, ServerPlayer player, UUID token) {
        Lease lease = LEASES.get(human);
        return lease != null && lease.player == player && lease.token.equals(token);
    }
    public static boolean pausedForInventory(Human human, ServerPlayer player) {
        Lease lease = LEASES.get(human);
        return lease != null && lease.player == player && lease.inventoryPaused;
    }
    static boolean pauseForInventory(ServerPlayer player, UUID token, Human human) {
        Lease lease = LEASES.get(human);
        if (lease == null) return true;
        if (!ownedBy(human, player, token)) return false;
        lease.inventoryPaused = true;
        resetInput(human, lease);
        lease.combat.suspend();
        return true;
    }
    public static void inventoryClosed(Human human, ServerPlayer player) {
        Lease lease = LEASES.get(human);
        if (lease == null || lease.player != player || !lease.inventoryPaused) return;
        lease.inventoryPaused = false;
        resetInput(human, lease);
        HumanMonitorService.refresh(player);
    }
    private static void resetInput(Human human, Lease lease) {
        lease.active = lease.attack = lease.use = lease.reload = lease.supply = false;
        lease.inputs = new dev.felix.hostilehumans.monitor.MonitorSequencedInput<>(5);
        lease.generation = ++nextGeneration;
        lease.lastSnapshotTick = -1;
        lease.travelPhysics = null;
        human.xxa = human.zza = 0;
        human.setJumping(false); human.setSprinting(false); human.setShiftKeyDown(false);
    }
    static boolean begin(ServerPlayer player, UUID token, Human human) {
        if (LEASES.containsKey(human) || human.isNoAi() || human.isSleeping() || human.isPassenger()
                || HumanInventorySession.isEditing(human) || NavigationDebugService.controls(human)
                || SoldierRecallProtection.pendingRescue(human)) return false;
        dev.felix.hostilehumans.cultivation.CultivationService.interrupt(human,"Player takeover",true);
        HumanBehaviorControl.cancel(human, "monitor player takeover");
        human.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
        human.targetSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
        human.stopUsingItem();
        human.hhu$shieldDefense().cancelDefenseSession();
        human.getNavigation().stop(); human.setTarget(null);
        HumanTurnControl.clear(human);
        if (!dev.felix.hostilehumans.monitor.combat.MonitorLoadout.prepare(human)) return false;
        Lease lease = new Lease(player, token, human);
        LEASES.put(human, lease);
        dev.felix.hostilehumans.behavior.combat.HumanCombatAuthority.bind(human, lease.combat);
        return true;
    }
    static void release(ServerPlayer player, UUID token) {
        for (var entry : java.util.List.copyOf(LEASES.entrySet())) {
            if (entry.getValue().player != player || !entry.getValue().token.equals(token)) continue;
            Human human = entry.getKey();
            try { entry.getValue().combat.close(); }
            finally {
                dev.felix.hostilehumans.behavior.combat.HumanCombatAuthority.release(human, entry.getValue().combat);
                LEASES.remove(human);
                human.xxa = human.zza = 0;
                human.setJumping(false);
                human.setSprinting(false);
                human.setShiftKeyDown(false);
                HumanTurnControl.clear(human);
                human.getNavigation().stop();
            }
        }
    }
    static void input(ServerPlayer player, HumanMonitorNetwork.Control input) {
        Human human = HumanMonitorService.target(player);
        Lease lease = human == null ? null : LEASES.get(human);
        if (lease == null || lease.player != player || !lease.token.equals(input.token())
                || lease.generation != input.generation()) return;
        lease.inputs.offer(input.sequence(), input, human.level().getGameTime());
    }
    static void publishLoadout(ServerPlayer player, UUID token, Human human) {
        Lease lease = LEASES.get(human);
        if (lease == null || !ownedBy(human, player, token)) return;
        var view = lease.combat.loadout().snapshot(token, lease.generation);
        if (!view.equals(lease.sentLoadout)) { lease.sentLoadout = view; HumanMonitorNetwork.sendLoadout(player, view); }
    }
    static boolean select(ServerPlayer player, UUID token, Human human, long generation, long revision, UUID weapon) {
        Lease lease = LEASES.get(human);
        if (lease == null || !ownedBy(human, player, token) || lease.generation != generation
                || lease.inventoryPaused || HumanInventorySession.isEditing(human)) return false;
        lease.combat.loadout().snapshot(token, generation);
        lease.sentLoadout = null;
        lease.combat.suspend();
        return lease.combat.loadout().select(weapon, revision) == dev.felix.hostilehumans.inventory.HumanEquipmentExchange.Result.SUCCESS;
    }
    static void cycleFireMode(ServerPlayer player, UUID token, Human human) {
        Lease lease = LEASES.get(human);
        if (lease == null || !ownedBy(human, player, token) || lease.inventoryPaused
                || HumanInventorySession.isEditing(human)) return;
        if (dev.felix.hostilehumans.behavior.combat.gun.GunSupport.isTaczGunItem(human.getMainHandItem()))
            dev.felix.hostilehumans.compat.tacz.TaczMonitorWeapons.cycleFireMode(human);
    }

    static boolean toggleOffhand(ServerPlayer player, UUID token, Human human) {
        Lease lease = LEASES.get(human);
        if (lease == null || !ownedBy(human, player, token) || lease.inventoryPaused || HumanInventorySession.isEditing(human)) return false;
        lease.combat.suspend();
        lease.sentLoadout = null;
        return lease.combat.loadout().toggleOffhand() == dev.felix.hostilehumans.inventory.HumanEquipmentExchange.Result.SUCCESS;
    }
    private static void consumeInput(Lease lease, long tick) {
        HumanMonitorNetwork.Control input = lease.inputs.drain(tick);
        if (input == null) return;
        lease.forward = input.forward(); lease.sideways = input.sideways();
        lease.yaw = Mth.wrapDegrees(input.yaw()); lease.pitch = Mth.clamp(input.pitch(), -90, 90);
        lease.jump = input.jump(); lease.sprint = input.sprint(); lease.sneak = input.sneak();
        lease.active = input.active(); lease.attack = input.attack(); lease.use = input.use(); lease.reload = input.reload();
        lease.supply = input.supply();
    }
    /** Called before vanilla LivingEntity travel, while the Mob AI step is suppressed. */
    public static void beforeTravel(Human human) {
        Lease lease = LEASES.get(human);
        if (lease == null) return;
        if (lease.inventoryPaused) {
            if (HumanMonitorService.target(lease.player) != human) { release(lease.player, lease.token); return; }
            human.xxa = human.zza = 0; human.setJumping(false);
            return;
        }
        boolean live = HumanMonitorService.target(lease.player) == human && !human.isNoAi()
                && !HumanInventorySession.isEditing(human) && !SoldierRecallProtection.pendingRescue(human)
                && !human.isPassenger() && !human.isSleeping();
        if (!live) { release(lease.player, lease.token); return; }
        long tick = human.level().getGameTime();
        consumeInput(lease, tick);
        boolean fresh = lease.active && lease.inputs.fresh(tick);
        float factor = lease.sneak ? .3F : 1;
        human.setSprinting(fresh && lease.sprint && lease.forward > 0 && !lease.sneak && !lease.attack && !lease.use);
        human.setShiftKeyDown(fresh && lease.sneak);
        human.xxa = fresh ? lease.sideways * factor : 0;
        human.zza = fresh ? lease.forward * factor : 0;
        human.setJumping(fresh && lease.jump);
        orient(human, lease.yaw, lease.pitch, fresh && (lease.forward != 0 || lease.sideways != 0));
        lease.combat.tick(fresh && lease.active, fresh && lease.attack, fresh && lease.use, fresh && lease.reload, fresh && lease.supply);
        // Native TaCZ can update gun weight/ADS/reload modifiers at LivingEntity.tick RETURN,
        // after travel. Preserve the actual parameters used by this travel, not those later values.
        human.setSpeed((float) human.getAttributeValue(Attributes.MOVEMENT_SPEED));
        human.xxa = fresh ? lease.sideways * factor : 0;
        human.zza = fresh ? lease.forward * factor : 0;
        lease.travelPhysics = MonitorPhysicsSnapshot.capture(human, human.getSpeed());
    }
    public static void finishTick(Human human) {
        HumanMonitorService.publishWeapon(human);
        Lease lease = LEASES.get(human);
        if (lease == null || lease.inventoryPaused) return;
        orient(human, lease.yaw, lease.pitch, human.xxa != 0 || human.zza != 0);
        long tick = human.level().getGameTime();
        if (lease.lastSnapshotTick == tick || HumanMonitorService.target(lease.player) != human) return;
        lease.lastSnapshotTick = tick;
        HumanMonitorNetwork.sendMotion(lease.player, new MonitorMotionSnapshot(lease.token, human.getUUID(), human.getId(),
                lease.generation, tick, lease.inputs.acknowledged(), human.position(), human.getDeltaMovement(), human.onGround(),
                MonitorPhysicsSnapshot.jumpCooldown(human), lease.travelPhysics == null
                        ? MonitorPhysicsSnapshot.capture(human, human.getSpeed()) : lease.travelPhysics));
    }
    /** Player-like coupling: moving body follows view, standing head has at most 60 degrees of freedom. */
    public static void orient(Human human, float yaw, float pitch, boolean moving) {
        human.setYRot(yaw); human.setXRot(pitch); human.setYHeadRot(yaw);
        float delta = Mth.wrapDegrees(yaw - human.yBodyRot);
        float turn = moving ? Mth.clamp(delta, -20, 20) : Mth.clamp(delta - Mth.clamp(delta, -60, 60), -20, 20);
        float body = human.yBodyRot + turn;
        human.setYBodyRot(yaw - Mth.clamp(Mth.wrapDegrees(yaw - body), -60, 60));
    }
}

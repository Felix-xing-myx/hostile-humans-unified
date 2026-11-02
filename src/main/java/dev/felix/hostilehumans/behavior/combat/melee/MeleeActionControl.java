package dev.felix.hostilehumans.behavior.combat.melee;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.behavior.combat.retreat.RetreatLastStand;
import dev.felix.hostilehumans.inventory.HumanInventorySession;
import dev.felix.hostilehumans.soldier.OrderDistances;

import dev.felix.hostilehumans.behavior.ActionCoordinator;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Authority for Human.doHurtTarget and its delayed native backend; never deals damage itself. */
public final class MeleeActionControl {
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final class State {
        ActionCoordinator.Token token;
        LivingEntity target;
        ItemStack weapon, primary, offhand;
        net.minecraft.world.entity.EquipmentSlot slot;
        int orderSignature;
        int ready;
    }
    private MeleeActionControl() { }
    public static boolean movementAllowsMelee(Human human) {
        return dev.felix.hostilehumans.behavior.combat.HumanCombatAuthority.live(human) || !human.isFleeing || RetreatLastStand.permitsMelee(human);
    }
    public static boolean begin(Human human, LivingEntity target) {
        if (!available(human, target)) return false;
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        if (state.token != null) {
            if (!valid(human)) finish(human, false, "stale attack cancelled");
            else return false;
        }
        if (human.tickCount < state.ready) return false;
        var actions = HumanBehaviorControl.actions(human);
        var resources = EnumSet.of(ActionCoordinator.Resource.ATTACK,
                ActionCoordinator.Resource.MAIN_HAND, ActionCoordinator.Resource.FACING);
        if (NpcDualWield.active(human)) resources.add(ActionCoordinator.Resource.OFF_HAND);
        var token = actions.request("melee:attack", resources, 60, true,
                MeleeAttackTiming.interval(human) + 60L);
        if (!actions.acquire(token)) {
            actions.finish(token, ActionCoordinator.Status.CANCELLED, "attack authority unavailable"); return false;
        }
        state.token = token; state.target = target;
        state.slot = NpcDualWield.attackSlot(human); state.weapon = human.getItemBySlot(state.slot);
        state.primary = human.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        state.offhand = NpcDualWield.active(human) ? human.getOffhandItem() : null;
        state.orderSignature = OrderDistances.signature(human);
        state.ready = human.tickCount + MeleeAttackTiming.interval(human);
        PursuitJumpAttack.markEngaged(human);
        return true;
    }
    public static ActionCoordinator.Token token(Human human) {
        State state = STATES.get(human); return state == null ? null : state.token;
    }
    public static boolean valid(Human human, ActionCoordinator.Token expected) {
        return expected != null && expected.equals(token(human)) && valid(human);
    }
    /** A committed sweep may finish after its first victim dies, but never after authority changes. */
    public static boolean impactValid(Human human, ActionCoordinator.Token expected) {
        State state = STATES.get(human);
        return state != null && expected != null && expected.equals(state.token)
                && !human.level().isClientSide && human.isAlive() && !human.isRemoved() && !human.isNoAi()
                && !HumanInventorySession.isEditing(human)
                && human.hhu$canMeleeAttack() && weaponValid(human, state)
                && state.target == human.getTarget() && state.orderSignature == OrderDistances.signature(human)
                && HumanBehaviorControl.actions(human).running(expected);
    }
    public static void finish(Human human, ActionCoordinator.Token expected, boolean completed, String reason) {
        if (expected != null && expected.equals(token(human))) finish(human, completed, reason);
    }
    public static boolean valid(Human human) {
        State state = STATES.get(human);
        return state != null && state.token != null && available(human, state.target)
                && state.target == human.getTarget() && weaponValid(human, state)
                && state.orderSignature == OrderDistances.signature(human) && HumanBehaviorControl.actions(human).running(state.token);
    }
    private static boolean weaponValid(Human human, State state) {
        return state.weapon != null
                && state.weapon == human.getItemBySlot(state.slot)
                && state.primary == human.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                && (state.offhand == null || state.offhand == human.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND));
    }
    private static boolean available(Human human, LivingEntity target) {
        return !human.level().isClientSide && human.isAlive() && !human.isRemoved() && !human.isNoAi()
                && !HumanInventorySession.isEditing(human)
                && target != null && target.isAlive() && (!human.isFleeing || target == human.getTarget())
                && human.canAttack(target) && human.hhu$canMeleeAttack();
    }
    public static void finish(Human human, boolean completed, String reason) {
        State state = STATES.get(human);
        if (state == null || state.token == null) return;
        var token = state.token; state.token = null;
        HumanBehaviorControl.actions(human).finish(token, completed ? ActionCoordinator.Status.COMPLETED
                : ActionCoordinator.Status.CANCELLED, reason);
    }
    public static void tick(Human human) {
        State state = STATES.get(human);
        if (state != null && state.token != null && !valid(human)) finish(human, false, "melee context changed");
        if (human.isFleeing || !human.isAlive() || human.isRemoved() || human.isNoAi()
                || HumanInventorySession.isEditing(human)) PursuitJumpAttack.cancel(human, "melee unavailable");
    }
    public static void clear(Human human, String reason) {
        finish(human, false, reason); STATES.remove(human);
        PursuitJumpAttack.clear(human, reason); BetterCombatMeleeCombat.clear(human); MeleeAttackTiming.clear(human);
    }
}

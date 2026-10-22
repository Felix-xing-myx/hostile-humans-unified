package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import dev.felix.hostilehumans.core.ActionCoordinator;
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
        ItemStack weapon;
        int orderSignature;
        int ready;
    }
    private MeleeActionControl() { }
    public static boolean begin(Human human, LivingEntity target) {
        if (!available(human, target)) return false;
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        if (state.token != null) {
            if (!valid(human)) finish(human, false, "stale attack cancelled");
            else return false;
        }
        if (human.tickCount < state.ready) return false;
        var actions = HumanBehaviorControl.actions(human);
        var token = actions.request("melee:attack", EnumSet.of(ActionCoordinator.Resource.ATTACK,
                ActionCoordinator.Resource.MAIN_HAND, ActionCoordinator.Resource.FACING), 60, true,
                MeleeAttackTiming.interval(human) + 60L);
        if (!actions.acquire(token)) {
            actions.finish(token, ActionCoordinator.Status.CANCELLED, "attack authority unavailable"); return false;
        }
        state.token = token; state.target = target; state.weapon = human.getMainHandItem(); state.orderSignature = OrderDistances.signature(human);
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
                && !human.isFleeing && !HumanInventorySession.isEditing(human)
                && human.humanGunner$canMeleeAttack() && state.weapon == human.getMainHandItem()
                && state.target == human.getTarget() && state.orderSignature == OrderDistances.signature(human)
                && HumanBehaviorControl.actions(human).running(expected);
    }
    public static void finish(Human human, ActionCoordinator.Token expected, boolean completed, String reason) {
        if (expected != null && expected.equals(token(human))) finish(human, completed, reason);
    }
    public static boolean valid(Human human) {
        State state = STATES.get(human);
        return state != null && state.token != null && available(human, state.target)
                && state.target == human.getTarget() && state.weapon == human.getMainHandItem()
                && state.orderSignature == OrderDistances.signature(human) && HumanBehaviorControl.actions(human).running(state.token);
    }
    private static boolean available(Human human, LivingEntity target) {
        return !human.level().isClientSide && human.isAlive() && !human.isRemoved() && !human.isNoAi()
                && !human.isFleeing && !HumanInventorySession.isEditing(human)
                && target != null && target.isAlive() && human.canAttack(target) && human.humanGunner$canMeleeAttack();
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

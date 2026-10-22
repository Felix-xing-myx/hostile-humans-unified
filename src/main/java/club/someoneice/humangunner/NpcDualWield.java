package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Real one-handed weapon pair; either member can supply the faction core capability. */
public final class NpcDualWield {
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final class State {
        HumanEquipmentExchange.Handle first, second;
        boolean swap;
        int turns, layoutReady;
    }
    private NpcDualWield() { }
    private static boolean faction(Human human) {
        return HumanFactions.enabled() && HumanFactions.shortId(human).equals("ashen");
    }
    private static boolean candidate(Human human, ItemStack stack) {
        var identity = EquipmentArchetypes.describe(human, stack);
        return identity.category().equals("melee") && identity.enabled()
                && BetterCombatMeleeCombat.supportsOneHand(stack) && unique(human, stack);
    }
    private static boolean pair(Human human, ItemStack first, ItemStack second) {
        return faction(human) && first != second && candidate(human, first) && candidate(human, second)
                && (EquipmentArchetypes.describe(human, first).core()
                    || EquipmentArchetypes.describe(human, second).core());
    }
    private static boolean remembered(Human human, State state) {
        return state != null && pair(human, HumanEquipmentExchange.locate(human, state.first),
                HumanEquipmentExchange.locate(human, state.second));
    }
    public static boolean active(Human human) {
        return pair(human, human.getMainHandItem(), human.getOffhandItem());
    }
    public static boolean offhandTurn(Human human) {
        State state = STATES.get(human);
        return active(human) && remembered(human, state) && (state.turns & 1) != 0;
    }
    /** Changed physical hands wait one tick for native equipment attributes to settle. */
    public static boolean prepare(Human human) {
        if (!faction(human) || !candidate(human, human.getMainHandItem())
                || human.isFleeing || human.isNoAi() || !human.isAlive()
                || HumanInventorySession.isEditing(human) || human.humanGunner$hasPendingMeleeAttack()
                || !human.humanGunner$canMeleeAttack() || RecoverySupplies.hasActiveUse(human)
                || human.humanGunner$shieldDefense().shouldSuppressRecovery() || human.isUsingItem())
            return layoutReady(human);
        State state = STATES.get(human);
        if (state != null && !remembered(human, state)) {
            // Cancel the former pair first; a different same-model weapon is a new decision.
            STATES.remove(human);
            return true;
        }
        if (state == null) {
            ItemStack main = human.getMainHandItem(), other = human.getOffhandItem();
            if (!pair(human, main, other)) {
                other = ItemStack.EMPTY;
                if (human.getData() != null) for (int i = 0; i < human.getData().getInventoryItemsSize(); i++) {
                    ItemStack item = human.getData().getInventoryItem(i);
                    if (pair(human, main, item)) { other = item; break; }
                }
            }
            if (other.isEmpty()) return true;
            state = new State();
            state.first = HumanEquipmentExchange.bind(human, main);
            state.second = HumanEquipmentExchange.bind(human, other);
            if (state.first == null || state.second == null) return true;
            STATES.put(human, state);
        }
        if (human.tickCount < state.layoutReady) return false;
        if (!HumanBehaviorControl.actions(human).owner(dev.felix.hostilehumans.core.ActionCoordinator.Resource.ATTACK).isEmpty())
            return false;
        ItemStack first = HumanEquipmentExchange.locate(human, state.first);
        ItemStack second = HumanEquipmentExchange.locate(human, state.second);
        // The pair remains the same while shields or medicine temporarily borrow either hand.
        ItemStack desiredMain = (state.turns & 1) == 0 ? first : second;
        ItemStack desiredOff = desiredMain == first ? second : first;
        if (human.getMainHandItem() != desiredMain || human.getOffhandItem() != desiredOff) {
            var mainHandle = desiredMain == first ? state.first : state.second;
            var offHandle = desiredMain == first ? state.second : state.first;
            if (HumanEquipmentExchange.request(human, "melee:dual-layout", List.of(
                    new HumanEquipmentExchange.Need(EquipmentSlot.MAINHAND, item -> item == desiredMain, mainHandle),
                    new HumanEquipmentExchange.Need(EquipmentSlot.OFFHAND, item -> item == desiredOff, offHandle)))
                    != HumanEquipmentExchange.Result.SUCCESS) return false;
            state.swap = false;
            state.layoutReady = human.tickCount + 1;
            return false;
        }
        state.swap = false;
        return true;
    }
    public static boolean layoutReady(Human human) {
        State state = STATES.get(human);
        return state == null || human.tickCount >= state.layoutReady;
    }
    static void completed(Human human) {
        State state = STATES.get(human);
        if (active(human) && remembered(human, state)) { state.turns++; state.swap = true; }
    }
    private static boolean unique(Human human, ItemStack stack) {
        if (stack.isEmpty()) return false;
        int refs = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) if (human.getItemBySlot(slot) == stack) refs++;
        if (human.getData() != null) for (int i = 0; i < human.getData().getInventoryItemsSize(); i++)
            if (human.getData().getInventoryItem(i) == stack) refs++;
        return refs == 1;
    }
    public static int ordinaryDefenseCooldown(Human human) {
        return remembered(human, STATES.get(human)) || active(human) ? 120 : 0;
    }
    public static void clear(Human human) { STATES.remove(human); }
}

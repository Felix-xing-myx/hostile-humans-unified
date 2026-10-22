package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** NPC-only real-hand alternation. No Player helper, item-name inference or hidden weapon copy. */
public final class NpcDualWield {
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final class State { boolean swap; int turns, layoutReady; }
    private NpcDualWield() { }
    private static boolean eligible(Human human) {
        var identity = EquipmentArchetypes.describe(human, human.getMainHandItem());
        return HumanFactions.enabled() && HumanFactions.shortId(human).equals("ashen")
                && identity.category().equals("melee") && identity.core() && identity.enabled()
                && BetterCombatMeleeCombat.supportsOneHand(human.getMainHandItem());
    }
    private static boolean candidate(Human human, ItemStack stack) {
        var identity = EquipmentArchetypes.describe(human, stack);
        return identity.category().equals("melee") && identity.core() && identity.enabled()
                && BetterCombatMeleeCombat.supportsOneHand(stack);
    }
    public static boolean active(Human human) {
        return eligible(human) && unique(human, human.getMainHandItem()) && unique(human, human.getOffhandItem())
                && candidate(human, human.getOffhandItem())
                && human.getMainHandItem() != human.getOffhandItem();
    }
    public static boolean offhandTurn(Human human) {
        State state = STATES.get(human);
        return active(human) && state != null && (state.turns & 1) != 0;
    }
    /** Returns false while a changed layout waits for vanilla equipment attributes to settle. */
    public static boolean prepare(Human human) {
        if (!eligible(human) || human.isFleeing || human.isNoAi() || !human.isAlive()
                || HumanInventorySession.isEditing(human) || human.humanGunner$hasPendingMeleeAttack()
                || !human.humanGunner$canMeleeAttack() || RecoverySupplies.hasActiveUse(human)
                || human.humanGunner$shieldDefense().shouldSuppressRecovery() || human.isUsingItem()) return layoutReady(human);
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        if (human.tickCount < state.layoutReady) return false;
        if (!HumanBehaviorControl.actions(human).owner(dev.felix.hostilehumans.core.ActionCoordinator.Resource.ATTACK).isEmpty())
            return layoutReady(human);
        ItemStack main = human.getMainHandItem(), off = human.getOffhandItem();
        if (!active(human)) {
            if (human.getData() == null) return true;
            ItemStack actual = ItemStack.EMPTY;
            for (int i = 0; i < human.getData().getInventoryItemsSize(); i++) {
                ItemStack stack = human.getData().getInventoryItem(i);
                if (stack != main && candidate(human, stack)) { actual = stack; break; }
            }
            if (actual.isEmpty()) return true;
            ItemStack selected = actual;
            var identity = HumanEquipmentExchange.bind(human, selected);
            if (identity == null) return true;
            if (HumanEquipmentExchange.request(human, "melee:dual-offhand", List.of(
                    new HumanEquipmentExchange.Need(EquipmentSlot.OFFHAND, stack -> stack == selected, identity)))
                    != HumanEquipmentExchange.Result.SUCCESS) return true;
            state.turns = 0; state.swap = false; state.layoutReady = human.tickCount + 1;
            return false;
        }
        if (state.swap) {
            var mainIdentity = HumanEquipmentExchange.bind(human, main);
            var offIdentity = HumanEquipmentExchange.bind(human, off);
            if (mainIdentity == null || offIdentity == null) return false;
            if (HumanEquipmentExchange.request(human, "melee:dual-turn", List.of(
                    new HumanEquipmentExchange.Need(EquipmentSlot.MAINHAND, stack -> stack == off, offIdentity),
                    new HumanEquipmentExchange.Need(EquipmentSlot.OFFHAND, stack -> stack == main, mainIdentity)))
                    != HumanEquipmentExchange.Result.SUCCESS) return false;
            state.swap = false; state.layoutReady = human.tickCount + 1;
            return false;
        }
        return true;
    }
    public static boolean layoutReady(Human human) {
        State state = STATES.get(human);
        return state == null || human.tickCount >= state.layoutReady;
    }
    static void completed(Human human) {
        if (!active(human)) return;
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        state.turns++; state.swap = true;
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
        if (!eligible(human)) return 0;
        if (candidate(human, human.getOffhandItem())) return 120;
        if (human.getData() != null) for (int i = 0; i < human.getData().getInventoryItemsSize(); i++)
            if (candidate(human, human.getData().getInventoryItem(i))) return 120;
        return 0;
    }
    public static void clear(Human human) { STATES.remove(human); }
}

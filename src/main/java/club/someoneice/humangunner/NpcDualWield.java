package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Real BC-verified one-handed pair with stable physical hands and alternating strikes. */
public final class NpcDualWield {
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final Map<Human, Integer> NEXT_DEFAULT_LAYOUT = new WeakHashMap<>();
    static final int DEFAULT_LAYOUT_INTERVAL_TICKS = 5;
    private static final class State {
        HumanEquipmentExchange.Handle first, second;
        int turns, layoutReady;
    }
    private NpcDualWield() { }
    private static boolean faction(Human human) {
        return HumanCombatDirector.strategies(human).dualWield();
    }
    private static boolean candidate(Human human, ItemStack stack) {
        var identity = EquipmentArchetypes.describe(human, stack);
        return identity.category().equals("melee") && identity.enabled()
                && BetterCombatMeleeCombat.supportsOneHand(stack) && unique(human, stack);
    }
    private static boolean pair(Human human, ItemStack first, ItemStack second) {
        return faction(human) && first != second && candidate(human, first) && candidate(human, second);
    }
    private static boolean remembered(Human human, State state) {
        return state != null && pair(human, HumanEquipmentExchange.locate(human, state.first),
                HumanEquipmentExchange.locate(human, state.second));
    }
    public static boolean active(Human human) {
        State state = STATES.get(human);
        return state != null && pair(human, human.getItemBySlot(EquipmentSlot.MAINHAND), human.getOffhandItem())
                && HumanEquipmentExchange.locate(human, state.first) == human.getItemBySlot(EquipmentSlot.MAINHAND)
                && HumanEquipmentExchange.locate(human, state.second) == human.getOffhandItem();
    }
    public static boolean offhandTurn(Human human) {
        State state = STATES.get(human);
        return active(human) && remembered(human, state) && (state.turns & 1) != 0;
    }
    public static EquipmentSlot attackSlot(Human human) {
        return offhandTurn(human) ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
    }
    public static ItemStack attackWeapon(Human human) { return human.getItemBySlot(attackSlot(human)); }
    /** Initial pair placement waits one tick; attacks never swap physical hands. */
    public static boolean prepare(Human human) {
        if (!faction(human) || !candidate(human, human.getItemBySlot(EquipmentSlot.MAINHAND))
                || human.isFleeing || human.isNoAi() || !human.isAlive()
                || HumanInventorySession.isEditing(human) || human.humanGunner$hasPendingMeleeAttack()
                || !human.humanGunner$canMeleeAttack() || RecoverySupplies.hasActiveUse(human)
                || human.humanGunner$shieldDefense().shouldSuppressRecovery() || human.isUsingItem())
            return layoutReady(human);
        State state = STATES.get(human);
        if (state != null && (!remembered(human, state)
                || HumanEquipmentExchange.locate(human, state.first) != human.getItemBySlot(EquipmentSlot.MAINHAND))) {
            // Cancel the former pair first; a different same-model weapon is a new decision.
            STATES.remove(human);
            return true;
        }
        if (state == null) {
            ItemStack main = human.getItemBySlot(EquipmentSlot.MAINHAND), other = human.getOffhandItem();
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
        ItemStack desiredMain = first;
        ItemStack desiredOff = second;
        if (human.getItemBySlot(EquipmentSlot.MAINHAND) != desiredMain || human.getOffhandItem() != desiredOff) {
            var mainHandle = state.first;
            var offHandle = state.second;
            if (HumanEquipmentExchange.request(human, "melee:dual-layout", List.of(
                    new HumanEquipmentExchange.Need(EquipmentSlot.MAINHAND, item -> item == desiredMain, mainHandle),
                    new HumanEquipmentExchange.Need(EquipmentSlot.OFFHAND, item -> item == desiredOff, offHandle)))
                    != HumanEquipmentExchange.Result.SUCCESS) return false;
            state.layoutReady = human.tickCount + 1;
            return false;
        }
        return true;
    }
    public static boolean layoutReady(Human human) {
        State state = STATES.get(human);
        return state == null || human.tickCount >= state.layoutReady;
    }
    static void completed(Human human) {
        State state = STATES.get(human);
        if (active(human) && remembered(human, state)) { state.turns++; }
    }
    private static boolean unique(Human human, ItemStack stack) {
        if (stack.isEmpty()) return false;
        int refs = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) if (human.getItemBySlot(slot) == stack) refs++;
        if (human.getData() != null) for (int i = 0; i < human.getData().getInventoryItemsSize(); i++)
            if (human.getData().getInventoryItem(i) == stack) refs++;
        return refs == 1;
    }
    /** Desired daily layout is recomputed after temporary hand users settle, including while idle.
     * Holding a second weapon does not grant dual attacks without the existing BC single-hand capability. */
    static void tickDefaultLayout(Human human) {
        if (!faction(human) || human.level().isClientSide || !human.isAlive() || human.isRemoved()
                || human.isNoAi() || HumanInventorySession.isEditing(human)) return;
        if (human.tickCount < NEXT_DEFAULT_LAYOUT.getOrDefault(human, Integer.MIN_VALUE)) return;
        NEXT_DEFAULT_LAYOUT.put(human, human.tickCount + DEFAULT_LAYOUT_INTERVAL_TICKS);
        if (!hasMainHandMeleeWeapon(human)) {
            // Never restore a remembered melee pair over a gun, bow, consumable or empty main hand.
            STATES.remove(human);
            if (!human.humanGunner$hasPendingMeleeAttack()
                    && HumanLootManager.isDedicatedMeleeWeapon(human.getOffhandItem())) {
                // Only the offhand is reconciled; using the main hand must not indefinitely block cleanup.
                var result = HumanEquipmentExchange.stow(human, EquipmentSlot.OFFHAND);
                if (result == HumanEquipmentExchange.Result.NO_CAPACITY)
                    HumanEquipmentExchange.request(human, "dual:non-melee-offhand", List.of(
                            new HumanEquipmentExchange.Need(EquipmentSlot.OFFHAND,
                                    item -> item != human.getItemBySlot(EquipmentSlot.MAINHAND) && SpartanEquipmentCompat.isShield(item))));
            }
            return;
        }
        if (human.isUsingItem() || human.humanGunner$hasPendingMeleeAttack()
                || RecoverySupplies.hasActiveUse(human)
                || human.humanGunner$shieldDefense().shouldSuppressRecovery()) return;
        if (!prepare(human)) return;
        ItemStack offhand = human.getOffhandItem();
        if (dailyWeapon(human, offhand)) return;
        ItemStack selected = ItemStack.EMPTY;
        if (human.getData() != null) for (int i = 0; i < human.getData().getInventoryItemsSize(); i++) {
            ItemStack item = human.getData().getInventoryItem(i);
            if (dailyWeapon(human, item) && (selected.isEmpty()
                    || HumanLootManager.mainHandWeaponScore(item) > HumanLootManager.mainHandWeaponScore(selected)))
                selected = item;
        }
        if (!selected.isEmpty()) {
            ItemStack desired = selected;
            HumanEquipmentExchange.request(human, "dual:daily-offhand", List.of(
                    new HumanEquipmentExchange.Need(EquipmentSlot.OFFHAND, item -> item == desired)));
        } else if (!offhand.isEmpty() && offhand.getFoodProperties(human) == null) {
            // A full backpack is a normal refusal, never permission to discard equipment.
            HumanEquipmentExchange.stow(human, EquipmentSlot.OFFHAND);
        }
    }
    private static boolean hasMainHandMeleeWeapon(Human human) {
        return HumanLootManager.isDedicatedMeleeWeapon(human.getItemBySlot(EquipmentSlot.MAINHAND));
    }
    private static boolean dailyWeapon(Human human, ItemStack item) {
        return item != human.getItemBySlot(EquipmentSlot.MAINHAND) && HumanLootManager.isDedicatedMeleeWeapon(item)
                && unique(human, item) && (!BetterCombatMeleeCombat.isConfiguredEnabled()
                    || BetterCombatMeleeCombat.supportsOneHand(item));
    }
    public static void clear(Human human) { STATES.remove(human); NEXT_DEFAULT_LAYOUT.remove(human); }
}

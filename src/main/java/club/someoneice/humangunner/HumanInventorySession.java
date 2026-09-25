package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/** Server-only edit lock preventing combat AI from moving stacks under an open menu. */
final class HumanInventorySession {
    private record State(ServerPlayer editor, boolean previousNoAi, int menuId, long openedAt) {}
    private static final Map<Human, State> OPEN = new WeakHashMap<>();

    private HumanInventorySession() {}

    static synchronized boolean begin(Human human, ServerPlayer editor, int menuId) {
        if (isEditing(human)) return false;
        // Any item-use state belongs to the loadout that was visible before
        // the owner started editing it. Never let a bow draw, shield block or
        // TaCZ hand state survive while its backing stack is moved elsewhere.
        if (human.isUsingItem()) human.stopUsingItem();
        RecoverySupplies.prepareForManualInventory(human);
        GunCustody.prepareForManualInventory(human);
        RangedWeaponCustody.prepareForManualInventory(human);
        OPEN.put(human, new State(editor, human.isNoAi(), menuId,
                human.level().getGameTime()));
        human.setTarget(null);
        human.getNavigation().stop();
        human.setNoAi(true);
        return true;
    }

    static synchronized void end(Human human, int menuId) {
        State state = OPEN.get(human);
        if (state == null || state.menuId() != menuId) return;
        OPEN.remove(human);
        if (human.isAlive()) {
            // Restoring NoAI is a session invariant, not a successful-loadout
            // side effect. A malformed third-party item must never leave the
            // hired Human permanently frozen if reconciliation rejects it.
            try {
                HumanManagedLoadout.reconcile(human);
            } catch (RuntimeException exception) {
                HumanGunner.LOGGER.error(
                        "Failed to reconcile owner-edited loadout for Human {}",
                        human.getUUID(), exception
                );
            } finally {
                human.setNoAi(state.previousNoAi());
            }
        }
    }

    static synchronized boolean isEditing(Human human) {
        State state = OPEN.get(human);
        if (state == null) return false;
        // NetworkHooks installs the new container after constructing it.
        // Allow that short transition, then release a lock left behind by a
        // disconnected player or an interrupted container-close packet.
        if (human.level().getGameTime() - state.openedAt() > 2
                && (!(state.editor().containerMenu instanceof HumanInventoryMenu menu)
                || menu.containerId != state.menuId())) {
            end(human, state.menuId());
            return false;
        }
        return true;
    }
}

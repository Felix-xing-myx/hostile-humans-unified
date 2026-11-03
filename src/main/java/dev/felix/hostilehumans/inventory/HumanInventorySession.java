package dev.felix.hostilehumans.inventory;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.behavior.combat.defense.ShieldDurability;
import dev.felix.hostilehumans.behavior.combat.gun.GunCustody;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedWeaponCustody;
import dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.monitor.HumanMonitorControl;
import dev.felix.hostilehumans.soldier.SoldierRecallProtection;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/** Server-only edit lock preventing combat AI from moving stacks under an open menu. */
public final class HumanInventorySession {
    private record State(ServerPlayer editor, boolean previousNoAi, Object token, long openedAt) {}
    private static final Map<Human, State> OPEN = new WeakHashMap<>();
    private static final String PREVIOUS_AI = HumanRuntime.MOD_ID + ":inventory_previous_no_ai";

    private HumanInventorySession() {}

    static synchronized boolean begin(Human human, ServerPlayer editor, Object token) {
        if (HumanMonitorControl.controls(human) && !HumanMonitorControl.pausedForInventory(human, editor)
                || SoldierRecallProtection.pendingRescue(human) || isEditing(human)) return false;
        HumanBehaviorControl.cancel(human, "human inventory editing");
        ShieldDurability.flush(human);
        // Any item-use state belongs to the loadout that was visible before
        // the owner started editing it. Never let a bow draw, shield block or
        // TaCZ hand state survive while its backing stack is moved elsewhere.
        if (human.isUsingItem()) human.stopUsingItem();
        RecoverySupplies.prepareForManualInventory(human);
        GunCustody.prepareForManualInventory(human);
        RangedWeaponCustody.prepareForManualInventory(human);
        human.getPersistentData().putBoolean(PREVIOUS_AI, human.isNoAi());
        OPEN.put(human, new State(editor, human.isNoAi(), token,
                human.level().getGameTime()));
        human.setTarget(null);
        human.getNavigation().stop();
        human.setNoAi(true);
        return true;
    }

    static synchronized boolean owns(Human human, Player player, Object token) {
        State state = OPEN.get(human);
        return state != null && state.editor() == player && state.token() == token;
    }

    static synchronized void end(Human human, Object token) {
        State state = OPEN.get(human);
        if (state == null || state.token() != token) return;
        OPEN.remove(human);
        // Restoration is unconditional; even unload/death and optional item failures cannot retain the lock's NoAI.
        try {
            if (human.getHealth() > 0 && !human.isRemoved() && !HumanMonitorControl.controls(human)) HumanManagedLoadout.reconcile(human);
        } catch (RuntimeException exception) {
            HumanRuntime.LOGGER.error("Failed to reconcile edited loadout for Human {}", human.getUUID(), exception);
        } finally {
            human.setNoAi(state.previousNoAi());
            human.getPersistentData().remove(PREVIOUS_AI);
            HumanMonitorControl.inventoryClosed(human, state.editor());
        }
    }

    /** Relocation closes the real container before taking a snapshot of the old AI state. */
    public static synchronized void interruptForRelocation(Human human) {
        State state = OPEN.get(human);
        if (state == null) return;
        if (state.editor().containerMenu instanceof HumanInventoryMenu menu && menu.holdsEditToken(state.token()))
            state.editor().closeContainer();
        else end(human, state.token());
    }

    /** An autosave taken during editing must not make its temporary NoAI flag permanent. */
    public static synchronized void restoreAfterLoad(Human human) {
        if (!OPEN.containsKey(human) && human.getPersistentData().contains(PREVIOUS_AI, net.minecraft.nbt.Tag.TAG_BYTE)) {
            human.setNoAi(human.getPersistentData().getBoolean(PREVIOUS_AI));
            human.getPersistentData().remove(PREVIOUS_AI);
        }
    }

    public static synchronized boolean isEditing(Human human) {
        State state = OPEN.get(human);
        if (state == null) return false;
        // NetworkHooks installs the new container after constructing it.
        // Allow that short transition, then release a lock left behind by a
        // disconnected player or an interrupted container-close packet.
        if (human.level().getGameTime() - state.openedAt() > 2
                && (!(state.editor().containerMenu instanceof HumanInventoryMenu menu)
                || !menu.holdsEditToken(state.token()))) {
            end(human, state.token());
            return false;
        }
        return true;
    }
}

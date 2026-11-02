package dev.felix.hostilehumans.behavior.combat.gun;

import dev.felix.hostilehumans.entity.HumanData;
import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.behavior.combat.ranged.HumanAmmo;
import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.inventory.HumanEquipmentExchange;
import dev.felix.hostilehumans.inventory.HumanInventorySession;

import dev.felix.hostilehumans.behavior.ActionCoordinator;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Captured source authority and a synchronous inventory debit, never a hand lease. */
public final class GunReloadAuthority {
    record Source(ItemStack actual, ItemStack expected) { }
    public static final class Capture {
        final Human human;
        public final ItemStack weapon;
        public final HumanEquipmentExchange.Handle handle;
        public final HumanData inventory;
        public final long session;
        private final List<Source> sources;
        private boolean attempted, paid;

        private Capture(Human human, ItemStack weapon, HumanEquipmentExchange.Handle handle) {
            this.human = human;
            this.weapon = weapon;
            this.handle = handle;
            inventory = human.getData();
            session = HumanBehaviorControl.actions(human).session();
            sources = new ArrayList<>();
            for (int slot = 0; slot < inventory.getInventoryItemsSize(); slot++) {
                ItemStack actual = inventory.getInventoryItem(slot);
                sources.add(new Source(actual, actual.copy()));
            }
        }

        public boolean validIdentity(boolean held) {
            return ready(human) && human.getData() == inventory
                    && session == HumanBehaviorControl.actions(human).session()
                    && HumanEquipmentExchange.locate(human, handle) == weapon
                    && (!held || human.getMainHandItem() == weapon);
        }

        public boolean validSource() {
            if (!validIdentity(true) || inventory.getInventoryItemsSize() != sources.size()) return false;
            for (int slot = 0; slot < sources.size(); slot++) {
                Source expected = sources.get(slot);
                ItemStack actual = inventory.getInventoryItem(slot);
                if (actual != expected.actual() || !ItemStack.matches(actual, expected.expected())) return false;
            }
            return true;
        }

        /** A successful attempt can be queried again, but never debited twice. */
        public boolean pay(java.util.function.BooleanSupplier permitted) {
            if (attempted) return paid && allowed(permitted);
            attempted = true;
            if (!allowed(permitted)) return false;
            // Resolve optional gun callbacks before acquiring the short commit permission.
            ItemStack expectedWeapon=weapon.copy();
            if(!allowed(permitted)) return false;
            boolean accepted = HumanAmmo.canReload(human, weapon);
            if (!accepted || !allowed(permitted) || !ItemStack.matches(weapon,expectedWeapon)) return false;
            boolean consume = UnifiedConfig.get().ammoConsumptionEnabled();
            int amount = consume ? HumanAmmo.magazineCost(weapon) : 0;
            if ((consume && amount <= 0) || amount < 0 || !allowed(permitted) || !ItemStack.matches(weapon,expectedWeapon)) return false;
            int available = 0;
            for (Source source : sources) if (HumanAmmo.isMagazine(source.actual()))
                available += source.actual().getCount();
            if (available < amount) return false;

            var actions = HumanBehaviorControl.actions(human);
            if (!actions.owner(ActionCoordinator.Resource.INVENTORY).isEmpty()) return false;
            var token = actions.request("gun-reload-payment", EnumSet.of(ActionCoordinator.Resource.INVENTORY),
                    100, false, 1);
            if (!actions.acquire(token)) {
                actions.finish(token, ActionCoordinator.Status.FAILED, "inventory unavailable");
                return false;
            }
            try {
                if (!allowed(permitted) || !ItemStack.matches(weapon,expectedWeapon)) return false;
                int remaining = amount;
                for (int slot = 0; slot < sources.size() && remaining > 0; slot++) {
                    Source source = sources.get(slot);
                    if (!HumanAmmo.isMagazine(source.actual())) continue;
                    if (!allowed(permitted) || !ItemStack.matches(weapon,expectedWeapon)) return false;
                    int take = Math.min(remaining, source.actual().getCount());
                    ItemStack next = source.actual().copy();
                    next.shrink(take);
                    if (next.isEmpty()) next = ItemStack.EMPTY;
                    // Publish this attempt's expected result before the storage write.
                    sources.set(slot, new Source(next, next.copy()));
                    inventory.setInventoryItem(slot, next);
                    remaining -= take;
                }
                paid = remaining == 0 && allowed(permitted) && ItemStack.matches(weapon,expectedWeapon);
                return paid;
            } finally {
                actions.finish(token, paid ? ActionCoordinator.Status.COMPLETED : ActionCoordinator.Status.FAILED,
                        paid ? "reload magazines committed" : "reload payment invalidated");
            }
        }
        private boolean allowed(java.util.function.BooleanSupplier permitted) {
            return permitted.getAsBoolean() && validSource() && permitted.getAsBoolean();
        }

    }

    public static Capture capture(Human human, ItemStack weapon) {
        if (!ready(human) || human.getMainHandItem() != weapon) return null;
        var handle = HumanEquipmentExchange.bind(human, weapon);
        if (handle == null) return null;
        Capture capture = new Capture(human, weapon, handle);
        return capture.validSource() ? capture : null;
    }

    static boolean ready(Human human) {
        return !human.level().isClientSide && human.getServer() != null && human.getServer().isSameThread()
                && human.isAlive() && !human.isRemoved() && !human.isNoAi()
                && !HumanInventorySession.isEditing(human) && human.getData() != null;
    }

    public static boolean validIdentity(Human human, ItemStack weapon, HumanEquipmentExchange.Handle handle, long session) {
        return ready(human) && session == HumanBehaviorControl.actions(human).session()
                && HumanEquipmentExchange.locate(human, handle) == weapon;
    }

    private GunReloadAuthority() { }
}

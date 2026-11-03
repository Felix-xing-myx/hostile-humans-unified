package dev.felix.hostilehumans.behavior.combat.throwable;

import dev.felix.hostilehumans.entity.HumanData;
import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies;
import dev.felix.hostilehumans.faction.HumanRelations;
import dev.felix.hostilehumans.inventory.HumanInventorySession;
import dev.felix.hostilehumans.observer.NavigationDebugService;
import dev.felix.hostilehumans.perception.HumanPerception;
import dev.felix.hostilehumans.soldier.SoldierOrder;

import dev.felix.hostilehumans.behavior.ActionCoordinator;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import java.util.EnumSet;
import java.util.function.Function;
import java.util.function.Predicate;

/** One real inventory unit, one independent projectile; no hands, use, facing, movement or ordinary ATTACK. */
public final class ThrowableTransactions {
    private static final java.util.Set<Human> ACTIVE = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private ThrowableTransactions() { }
    public static boolean available(Human human, LivingEntity target) {
        return !human.level().isClientSide && human.isAlive() && !human.isRemoved() && !human.isNoAi()
                && !human.isSleeping() && !HumanInventorySession.isEditing(human) && human.getData() != null
                && !NavigationDebugService.controls(human) && human.level().getDifficulty() != Difficulty.PEACEFUL
                && human.getTarget() != null && human.getTarget().isAlive()
                && HumanPerception.canTarget(human, human.getTarget())
                && target != null && target.isAlive() && !target.isRemoved() && target.level() == human.level()
                && human.distanceToSqr(target) <= 32 * 32 && human.canAttack(target)
                && HumanPerception.canTarget(human, target) && SoldierOrder.allowsTarget(human, target)
                && !HumanRelations.allied(human, target) && human.getSensing().hasLineOfSight(target);
    }
    /** factory receives only a one-unit copy and must not publish or consume anything itself. */
    public static boolean submit(Human human, LivingEntity target, LivingEntity selected, int slot, Predicate<ItemStack> accepts,
                          Function<ItemStack, ? extends Projectile> factory) {
        if (human.level().isClientSide || !ACTIVE.add(human)) return false;
        ActionCoordinator actions = null;
        ActionCoordinator.Token token = null;
        boolean submitted = false, settled = false;
        try {
            if (human.getTarget() != selected || !available(human, target)) { settled = true; return false; }
            actions = HumanBehaviorControl.actions(human);
            token = actions.request("background-throw", EnumSet.of(ActionCoordinator.Resource.INVENTORY), 70, false, 2);
            if (!actions.acquire(token)) { settled = true; return false; }
            HumanData data = human.getData();
            if (slot < 0 || slot >= data.getInventoryItemsSize() || RecoverySupplies.reservesInventorySlot(human, slot)) {
                settled = true; return false;
            }
            ItemStack actual = data.getInventoryItem(slot);
            if (actual.isEmpty() || !accepts.test(actual)) { settled = true; return false; }
            var inventory = Inventory.capture(data);
            if (inventory.references().stream().filter(reference -> reference == actual).count() != 1) {
                settled = true; return false;
            }
            ItemStack one = actual.copyWithCount(1);
            Projectile projectile = factory.apply(one.copy());
            if (projectile == null || projectile.isAddedToWorld() || projectile.isRemoved()
                    || projectile.level() != human.level() || !actions.running(token)
                    || human.getTarget() != selected || !available(human, target)
                    || human.getData() != data || !inventory.matches(data) || !accepts.test(actual)) {
                settled = true; return false;
            }
            projectile.setOwner(human);
            ThrowableSource.capture(human, projectile, one);
            // Reserve/debit the exact real unit before the native join callback can publish it.
            ItemStack paid = actual.split(1);
            ItemStack remainder = actual.isEmpty() ? ItemStack.EMPTY : actual;
            ItemStack expectedRemainder = remainder.copy();
            try {
                data.setInventoryItem(slot, remainder);
                if (actions.running(token) && human.getTarget() == selected && available(human, target)
                        && human.getData() == data && slot < data.getInventoryItemsSize() && data.getInventoryItem(slot) == remainder
                        && ItemStack.matches(remainder, expectedRemainder) && inventory.matchesExcept(data, slot))
                    submitted = publish(human, projectile);
            } finally {
                // A removed projectile may already have detonated inside a synchronous join hook.
                // Its effects are irreversible; never refund and manufacture a second usable item.
                submitted |= projectile.isAddedToWorld() || projectile.isRemoved();
                boolean slotExists = human.getData() == data && slot < data.getInventoryItemsSize();
                ItemStack current = slotExists ? data.getInventoryItem(slot) : ItemStack.EMPTY;
                boolean stillOurEmpty = remainder.isEmpty() && current == actual && actual.isEmpty();
                if (!submitted && human.isAlive() && !human.isRemoved() && slotExists
                        && (current == remainder || stillOurEmpty) && ItemStack.matches(current, expectedRemainder)) {
                    if (current.isEmpty()) data.setInventoryItem(slot, paid);
                    else { current.grow(paid.getCount()); data.setInventoryItem(slot, current); }
                }
            }
            settled = true;
            return submitted;
        } finally {
            try {
                if (token != null) actions.finish(token, submitted ? ActionCoordinator.Status.COMPLETED
                        : settled ? ActionCoordinator.Status.CANCELLED : ActionCoordinator.Status.FAILED,
                        submitted ? "throw committed" : settled ? "throw cancelled" : "throw callback failed");
            } finally { ACTIVE.remove(human); }
        }
    }
    private static boolean publish(Human human, Projectile projectile) {
        try {
            return human.level().addFreshEntity(projectile);
        } catch (RuntimeException failure) {
            if (!projectile.isAddedToWorld() && !projectile.isRemoved()) throw failure;
            // The native callback already exposed the projectile. Report success so
            // the caller settles cooldown alongside the irrevocable inventory debit.
            HumanRuntime.LOGGER.warn("Throw publication callback failed after projectile commit for {}", human.getUUID(), failure);
            return true;
        }
    }

    private record Inventory(java.util.List<ItemStack> references, java.util.List<ItemStack> expected) {
        static Inventory capture(HumanData data) {
            var references = new java.util.ArrayList<ItemStack>();
            for (int i = 0; i < data.getInventoryItemsSize(); i++) references.add(data.getInventoryItem(i));
            return new Inventory(references, references.stream().map(ItemStack::copy).toList());
        }
        boolean matches(HumanData data) { return matchesExcept(data, -1); }
        boolean matchesExcept(HumanData data, int paidSlot) {
            if (data.getInventoryItemsSize() != references.size()) return false;
            for (int i = 0; i < references.size(); i++)
                if (i != paidSlot && (data.getInventoryItem(i) != references.get(i)
                        || !ItemStack.matches(data.getInventoryItem(i), expected.get(i)))) return false;
            return true;
        }
    }
}

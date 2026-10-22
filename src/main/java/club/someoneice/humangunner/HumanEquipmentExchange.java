package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import dev.felix.hostilehumans.core.ActionCoordinator;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Full-layout exchange on the real hands/backpack. No hidden storage and no slot-based restore chain. */
public final class HumanEquipmentExchange {
    private static final String ID = "hostile_humans_unified:equipment_identity";
    private static final Set<Human> WRITING = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public enum Result { SUCCESS, BUSY, MISSING, NO_CAPACITY, CHANGED, AMBIGUOUS, UNAVAILABLE }
    public record Handle(UUID identity, UUID owner) { }
    public record Need(EquipmentSlot destination, Predicate<ItemStack> accepts, Handle identity) {
        public Need(EquipmentSlot destination, Predicate<ItemStack> accepts) { this(destination, accepts, null); }
    }
    private record Place(EquipmentSlot equipment, int inventory) { }
    private HumanEquipmentExchange() { }

    /** Explicit binding is for an independent real weapon, never every unit of a consumable stack. */
    public static Handle bind(Human human, ItemStack actualWeapon) {
        if (human.level().isClientSide || actualWeapon.isEmpty() || actualWeapon.getCount() != 1) return null;
        int references = 0;
        for (Place place : places(human)) if (read(human, place) == actualWeapon) references++;
        if (references != 1) return null;
        if (!actualWeapon.getOrCreateTag().hasUUID(ID)) actualWeapon.getOrCreateTag().putUUID(ID, UUID.randomUUID());
        Handle handle = new Handle(actualWeapon.getTag().getUUID(ID), human.getUUID());
        return locate(human, handle) == actualWeapon ? handle : null;
    }

    /** A missing or duplicated identity is not permission to pick another same-model weapon. */
    public static ItemStack locate(Human human, Handle handle) {
        if (handle == null || !human.getUUID().equals(handle.owner())) return ItemStack.EMPTY;
        ItemStack found = ItemStack.EMPTY;
        for (Place place : places(human)) {
            ItemStack stack = read(human, place);
            if (!matches(stack, handle)) continue;
            if (!found.isEmpty()) return ItemStack.EMPTY;
            found = stack;
        }
        return found;
    }

    public static Result request(Human human, String purpose, List<Need> needs) {
        if (human.level().isClientSide || !human.isAlive() || human.isRemoved()
                || human.isNoAi() || human.getData() == null || HumanInventorySession.isEditing(human))
            return Result.UNAVAILABLE;
        if (WRITING.contains(human)) return Result.BUSY;
        if (needs.isEmpty()) return Result.SUCCESS;
        var actions = HumanBehaviorControl.actions(human);
        EnumSet<ActionCoordinator.Resource> resources = EnumSet.of(ActionCoordinator.Resource.INVENTORY);
        for (Need need : needs) {
            if (need.destination() == EquipmentSlot.MAINHAND) resources.add(ActionCoordinator.Resource.MAIN_HAND);
            if (need.destination() == EquipmentSlot.OFFHAND) resources.add(ActionCoordinator.Resource.OFF_HAND);
        }
        var token = actions.request("exchange:" + purpose, resources, 100, false, 2);
        if (!actions.acquire(token)) return Result.BUSY;
        Result result;
        WRITING.add(human);
        try { result = commitLayout(human, needs); }
        catch (RuntimeException exception) {
            actions.finish(token, ActionCoordinator.Status.FAILED, "exchange callback failed");
            throw exception;
        }
        finally { WRITING.remove(human); }
        actions.finish(token, result == Result.SUCCESS ? ActionCoordinator.Status.COMPLETED
                : ActionCoordinator.Status.FAILED, result.name());
        return result;
    }

    private static Result commitLayout(Human human, List<Need> needs) {
        List<Place> places = places(human);
        ItemStack[] actual = new ItemStack[places.size()], expected = new ItemStack[places.size()];
        for (int i = 0; i < actual.length; i++) {
            actual[i] = read(human, places.get(i)); expected[i] = actual[i].copy();
        }
        ItemStack[] planned = actual.clone();
        Set<Integer> selected = new HashSet<>(), destinations = new HashSet<>();
        List<Integer> sources = new ArrayList<>(), targets = new ArrayList<>();
        for (Need need : needs) {
            if (need.identity() != null) {
                if (!human.getUUID().equals(need.identity().owner())) return Result.AMBIGUOUS;
                int identityCount = 0;
                for (ItemStack stack : actual) if (matches(stack, need.identity())) identityCount++;
                if (identityCount == 0) return Result.MISSING;
                if (identityCount != 1) return Result.AMBIGUOUS;
            }
            int destination = places.indexOf(new Place(need.destination(), -1));
            if (destination < 0 || !destinations.add(destination)) return Result.AMBIGUOUS;
            if (human.isUsingItem() && (need.destination() == EquipmentSlot.MAINHAND
                    && human.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                    || need.destination() == EquipmentSlot.OFFHAND
                    && human.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND)) return Result.BUSY;
            int source = -1, matches = 0;
            // Retain an already correct destination; otherwise search only actual hands/backpack.
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < places.size(); i++) {
                    if (pass == 0 && i != destination || pass == 1 && i == destination) continue;
                    Place place = places.get(i);
                    if (selected.contains(i) || actual[i].isEmpty() || reserved(human, place)) continue;
                    if (place.equipment() != null && place.equipment() != EquipmentSlot.MAINHAND
                            && place.equipment() != EquipmentSlot.OFFHAND && i != destination) continue;
                    if (need.identity() != null && !matches(actual[i], need.identity())) continue;
                    if (!need.accepts().test(actual[i])) continue;
                    if (source < 0) source = i;
                    matches++;
                    if (need.identity() == null) break;
                }
                if (source >= 0 && need.identity() == null) break;
            }
            if (source < 0) return Result.MISSING;
            if (need.identity() != null && (!human.getUUID().equals(need.identity().owner()) || matches != 1))
                return Result.AMBIGUOUS;
            selected.add(source); sources.add(source); targets.add(destination);
        }
        List<ItemStack> displaced = new ArrayList<>();
        for (int target : targets) if (!selected.contains(target) && !actual[target].isEmpty())
            displaced.add(actual[target]);
        for (int source : sources) planned[source] = ItemStack.EMPTY;
        for (int target : targets) planned[target] = ItemStack.EMPTY;
        for (int i = 0; i < sources.size(); i++) planned[targets.get(i)] = actual[sources.get(i)];
        for (ItemStack stack : displaced) {
            int free = -1;
            for (int i = 0; i < places.size(); i++) {
                Place place = places.get(i);
                if (destinations.contains(i) || !planned[i].isEmpty() || reserved(human, place)) continue;
                // Inventory and emptied hands accept arbitrary stacks; never put a sword into an armor slot.
                if (place.equipment() == null || place.equipment() == EquipmentSlot.MAINHAND
                        || place.equipment() == EquipmentSlot.OFFHAND) { free = i; break; }
            }
            if (free < 0) return Result.NO_CAPACITY;
            planned[free] = stack;
        }
        for (int i = 0; i < actual.length; i++)
            if (!ItemStack.matches(read(human, places.get(i)), expected[i])) return Result.CHANGED;
        // Retain object identity so a native reload/payment handle follows the actual weapon.
        List<Integer> written = new ArrayList<>();
        try {
            for (int i = 0; i < planned.length; i++) if (actual[i] != planned[i]) {
                // Register before the write: an equipment-change callback can throw after changing the slot.
                written.add(i); write(human, places.get(i), planned[i]);
            }
        } catch (RuntimeException exception) {
            // Undo only writes still demonstrably ours. Never restore a copied item snapshot.
            for (int j = written.size() - 1; j >= 0; j--) {
                int i = written.get(j);
                if (read(human, places.get(i)) == planned[i]) write(human, places.get(i), actual[i]);
            }
            throw exception;
        }
        return Result.SUCCESS;
    }

    private static boolean matches(ItemStack stack, Handle handle) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().hasUUID(ID)
                && handle.identity().equals(stack.getTag().getUUID(ID));
    }
    private static boolean reserved(Human human, Place place) {
        return place.equipment() == null && RecoverySupplies.reservesInventorySlot(human, place.inventory());
    }
    private static List<Place> places(Human human) {
        List<Place> places = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) places.add(new Place(slot, -1));
        if (human.getData() != null) for (int i = 0; i < human.getData().getInventoryItemsSize(); i++)
            places.add(new Place(null, i));
        return places;
    }
    private static ItemStack read(Human human, Place place) {
        return place.equipment() == null ? human.getData().getInventoryItem(place.inventory())
                : human.getItemBySlot(place.equipment());
    }
    private static void write(Human human, Place place, ItemStack stack) {
        if (place.equipment() == null) human.getData().setInventoryItem(place.inventory(), stack);
        else human.setItemSlot(place.equipment(), stack);
    }
}

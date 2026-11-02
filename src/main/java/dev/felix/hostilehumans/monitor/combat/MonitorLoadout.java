package dev.felix.hostilehumans.monitor.combat;

import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedWeaponCustody;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.inventory.HumanEquipmentExchange;
import dev.felix.hostilehumans.inventory.ControlledEquipmentPolicy;
import dev.felix.hostilehumans.inventory.HumanInventorySession;
import dev.felix.hostilehumans.inventory.HumanLootManager;
import dev.felix.hostilehumans.monitor.MonitorLoadoutSnapshot;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import java.util.*;

/** Explicit loadout changes on actual inventory references. Failed requests keep the complete old layout. */
public final class MonitorLoadout {
    private final Human human;
    private List<MonitorLoadoutSnapshot.Option> options = List.of();
    private UUID selected = MonitorLoadoutSnapshot.UNARMED;
    private long revision;
    public MonitorLoadout(Human human) { this.human = human; }
    private static boolean attack(ItemStack item) {
        return !item.isEmpty() && item.getCount() == 1 && (MonitorGunBackends.supports(item)
                || item.getItem() instanceof BowItem || item.getItem() instanceof CrossbowItem
                || item.getItem() instanceof TridentItem || HumanLootManager.isDedicatedMeleeWeapon(item));
    }
    private static int kind(ItemStack item) {
        return GunSupport.isGunItem(item) ? 4 : item.getItem() instanceof CrossbowItem ? 3 : item.getItem() instanceof BowItem ? 2 : 1;
    }
    private List<ItemStack> inventory() {
        var items = new ArrayList<ItemStack>(); items.add(human.getMainHandItem()); items.add(human.getOffhandItem());
        if (human.getData() != null) for (int i = 0; i < human.getData().getInventoryItemsSize(); i++) items.add(human.getData().getInventoryItem(i));
        return items;
    }
    public MonitorLoadoutSnapshot snapshot(UUID token, long generation) {
        if (!HumanInventorySession.isEditing(human)) {
            var next = new ArrayList<MonitorLoadoutSnapshot.Option>();
            next.add(new MonitorLoadoutSnapshot.Option(MonitorLoadoutSnapshot.UNARMED, "", 0));
            UUID active = MonitorLoadoutSnapshot.UNARMED;
            for (var item : inventory()) if (attack(item)) {
                var handle = HumanEquipmentExchange.bind(human, item);
                if (handle == null) continue;
                String name = item.getHoverName().getString();
                next.add(new MonitorLoadoutSnapshot.Option(handle.identity(), name.substring(0, Math.min(80, name.length())), kind(item)));
                if (item == human.getMainHandItem()) active = handle.identity();
            }
            next.subList(1, next.size()).sort(Comparator.comparing(MonitorLoadoutSnapshot.Option::id));
            if (!next.equals(options) || !active.equals(selected)) { options = List.copyOf(next); selected = active; revision++; }
        }
        return new MonitorLoadoutSnapshot(token, generation, revision, options, selected);
    }
    public HumanEquipmentExchange.Result select(UUID id, long expectedRevision) {
        if (expectedRevision != revision || options.stream().noneMatch(o -> o.id().equals(id))) return HumanEquipmentExchange.Result.CHANGED;
        var needs = new ArrayList<HumanEquipmentExchange.Need>();
        ItemStack chosen = id.equals(MonitorLoadoutSnapshot.UNARMED) ? ItemStack.EMPTY
                : HumanEquipmentExchange.locate(human, new HumanEquipmentExchange.Handle(id, human.getUUID()));
        if (!id.equals(MonitorLoadoutSnapshot.UNARMED) && !attack(chosen)) return HumanEquipmentExchange.Result.MISSING;
        if (chosen.isEmpty()) needs.add(HumanEquipmentExchange.Need.empty(EquipmentSlot.MAINHAND));
        else needs.add(new HumanEquipmentExchange.Need(EquipmentSlot.MAINHAND, item -> item == chosen));
        if (dev.felix.hostilehumans.inventory.ControlledEquipmentPolicy.dualOffhand(human) && !dev.felix.hostilehumans.inventory.ControlledEquipmentPolicy.oneHandWeapon(chosen)
                && (HumanLootManager.isDedicatedMeleeWeapon(human.getOffhandItem()) || chosen == human.getOffhandItem())) needs.add(HumanEquipmentExchange.Need.empty(EquipmentSlot.OFFHAND));
        return HumanEquipmentExchange.request(human, "manual:main-weapon", needs);
    }
    public HumanEquipmentExchange.Result toggleOffhand() {
        ItemStack current = human.getOffhandItem();
        boolean supply = current.isEdible() || current.getItem() instanceof PotionItem
                || ControlledEquipmentPolicy.supplyRemainder(current);
        ItemStack chosen = findOffhand(supply);
        if (chosen.isEmpty()) {
            // A consumed bottle/bowl may be stowed even if the combat counterpart is unavailable.
            // The exchange returns NO_CAPACITY unchanged instead of dropping or deleting the vessel.
            if (ControlledEquipmentPolicy.supplyRemainder(current))
                return HumanEquipmentExchange.request(human, "manual:stow-container",
                        List.of(HumanEquipmentExchange.Need.empty(EquipmentSlot.OFFHAND)));
            return HumanEquipmentExchange.Result.MISSING;
        }
        ItemStack actual = chosen;
        return HumanEquipmentExchange.request(human, "manual:offhand", List.of(new HumanEquipmentExchange.Need(EquipmentSlot.OFFHAND, item -> item == actual)));
    }
    private ItemStack findOffhand(boolean combat) {
        boolean dual = ControlledEquipmentPolicy.dualOffhand(human);
        for (ItemStack item : inventory()) {
            if (item.isEmpty() || item == human.getMainHandItem() || item == human.getOffhandItem()) continue;
            if (!combat) {
                if (ControlledEquipmentPolicy.offhandFood(item)) return item;
            } else if (dual) {
                if (ControlledEquipmentPolicy.oneHandWeapon(human.getMainHandItem())
                        && ControlledEquipmentPolicy.oneHandWeapon(item)) return item;
            } else if (dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat.isShield(item)) return item;
        }
        return ItemStack.EMPTY;
    }
    public static boolean prepare(Human human) {
        var needs = new ArrayList<HumanEquipmentExchange.Need>();
        for (var hand : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (!dev.felix.hostilehumans.inventory.ControlledEquipmentPolicy.handAllowed(human, human.getItemBySlot(hand))) needs.add(HumanEquipmentExchange.Need.empty(hand));
        if (dev.felix.hostilehumans.inventory.ControlledEquipmentPolicy.dualOffhand(human)
                && !dev.felix.hostilehumans.inventory.ControlledEquipmentPolicy.oneHandWeapon(human.getMainHandItem())
                && HumanLootManager.isDedicatedMeleeWeapon(human.getOffhandItem())) needs.add(HumanEquipmentExchange.Need.empty(EquipmentSlot.OFFHAND));
        return HumanEquipmentExchange.request(human, "manual:legal-hands", needs) == HumanEquipmentExchange.Result.SUCCESS;
    }
}

package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Single owner for every temporary gun/main-hand exchange.
 *
 * <p>The old implementation had two unrelated goals swapping a gun into an
 * inventory slot. One of them had no restore path, while the other remembered
 * only an in-memory slot number. This custody survives goal replacement and
 * entity saving and locates a moved gun by its owner marker. Snapshots are
 * diagnostic metadata and cannot reconstruct missing owned weapons.</p>
 */
final class GunCustody {
    private static final String PRIMARY_OWNER = "hostile_humans_unified:primary_gun_owner";
    private static final String ACTIVE = "hostile_humans_unified:gun_custody_active";
    private static final String RESERVED_SLOT = "hostile_humans_unified:gun_custody_slot";
    private static final String SNAPSHOT = "hostile_humans_unified:gun_custody_snapshot";
    private static final String CLOSE_DEFENSE = "hostile_humans_unified:gun_close_melee_defense";
    private static final double CLOSE_DEFENSE_EXIT_DISTANCE_SQR = 16.0D;
    private static final Map<Human, GunSnapshot> AUDIT = new WeakHashMap<>();

    private record GunSnapshot(int count, String state) {
    }

    private GunCustody() {
    }

    static boolean isActive(Human human) {
        return human.getPersistentData().getBoolean(ACTIVE);
    }

    static boolean isCloseDefenseActive(Human human) {
        return human.getPersistentData().getBoolean(ACTIVE)
                && human.getPersistentData().getBoolean(CLOSE_DEFENSE);
    }

    static boolean hasOwnedGun(Human human) {
        if (!GunSupport.get().enabled()) return false;
        if (club.someoneice.humangunner.GunSupport.get().isGun(human.getMainHandItem())
                || club.someoneice.humangunner.GunSupport.get().isGun(human.getOffhandItem())) {
            return true;
        }
        HumanData data = human.getData();
        return data != null && data.hasStoredGun(human.tickCount);
    }

    static void registerPrimaryGun(Human human, ItemStack gun) {
        if (!club.someoneice.humangunner.GunSupport.get().isGun(gun)) {
            return;
        }
        gun.getOrCreateTag().putString(PRIMARY_OWNER, human.getUUID().toString());
    }

    static boolean beginCloseMeleeCounter(Human human) {
        if (human.getPersistentData().getBoolean(ACTIVE)) {
            human.getPersistentData().putBoolean(CLOSE_DEFENSE, true);
            return true;
        }
        if (human.isUsingItem()) {
            return false;
        }
        // Move the actual authorized stack: native TaCZ reload scripts and the
        // payment lease must keep writing into the gun that will be restored.
        ItemStack gun = human.getMainHandItem();
        if (!club.someoneice.humangunner.GunSupport.get().isGun(gun)) {
            return false;
        }
        HumanData data = human.getData();
        if (data == null) {
            return false;
        }
        int meleeSlot = bestMeleeSlot(data);
        if (meleeSlot < 0) {
            return false;
        }

        registerPrimaryGun(human, gun);
        CompoundTag ownerData = human.getPersistentData();
        ownerData.putBoolean(ACTIVE, true);
        ownerData.putBoolean(CLOSE_DEFENSE, true);
        ownerData.putInt(RESERVED_SLOT, meleeSlot);
        ownerData.put(SNAPSHOT, gun.save(new CompoundTag()));

        ItemStack melee = data.getInventoryItem(meleeSlot).copy();
        // Publish the reservation before moving either actual stack.
        data.setInventoryItem(meleeSlot, gun);
        human.setItemSlot(EquipmentSlot.MAINHAND, melee);
        CloseDefenseHold.begin(human);
        CloseRangedTactics.beginMelee(human);
        human.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        HumanGunner.LOGGER.debug("Gun custody begin npc={} slot={} gun={} melee={}",
                human.getUUID(), meleeSlot, itemId(gun), itemId(melee));
        return true;
    }

    static boolean canBeginPressureDefense(Human human) {
        return !GunSupport.isGunItem(human.getMainHandItem()) || isActive(human)
                || human.getData() != null && bestMeleeSlot(human.getData()) >= 0;
    }

    private static int bestMeleeSlot(HumanData data) {
        int meleeSlot = -1;
        double meleeScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            if (HumanInventoryCustody.isReservedForRecovery(data, i)) continue;
            ItemStack candidate = data.getInventoryItem(i);
            if (!HumanLootManager.isDedicatedMeleeWeapon(candidate)) {
                continue;
            }
            double score = HumanLootManager.mainHandWeaponScore(candidate);
            if (score > meleeScore) {
                meleeScore = score;
                meleeSlot = i;
            }
        }
        return meleeSlot;
    }

    static boolean restorePrimaryGun(Human human, String reason) {
        if (isActive(human) && CloseRangedTactics.keepDefense(human)) return false;
        if (isActive(human) && human.humanGunner$shieldDefense().shouldSuppressRecovery()) return false;
        if (isActive(human) && CloseDefenseHold.shouldHold(human)) return false;
        ItemStack held = human.getMainHandItem();
        if (AmmoWeaponSelection.usableGun(human, held)) {
            registerPrimaryGun(human, held);
            if (isActive(human)) CloseRangedTactics.returnedToGun(human);
            clearLease(human);
            human.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
            return true;
        }
        if (human.isUsingItem()) {
            return false;
        }
        HumanData data = human.getData();
        if (data == null) {
            return false;
        }

        int gunSlot = findPrimaryGunSlot(human, data);
        if (gunSlot < 0 || !AmmoWeaponSelection.usableGun(human, data.getInventoryItem(gunSlot))) {
            gunSlot = findUsableGunSlot(human, data);
        }
        ItemStack gun;
        if (gunSlot >= 0) {
            gun = data.getInventoryItem(gunSlot);
            registerPrimaryGun(human, gun);
            ItemStack displaced = human.getMainHandItem().copy();
            // Duplicate transiently, then complete the exact two-location swap.
            // A crash or callback between these writes can duplicate, not lose,
            // the high-value gun.
            human.setItemSlot(EquipmentSlot.MAINHAND, gun);
            data.setInventoryItem(gunSlot, displaced);
            if (isActive(human)) CloseRangedTactics.returnedToGun(human);
            human.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
            HumanGunner.LOGGER.debug(
                    "Gun custody restore npc={} reason={} slot={} gun={} displaced={}",
                    human.getUUID(), reason, gunSlot, itemId(gun), itemId(displaced)
            );
            clearLease(human);
            return true;
        }

        // The snapshot is diagnostic metadata, never evidence of an owned item.
        // A removed or exhausted gun cannot be recreated or forced back into the hand.
        clearLease(human);
        return false;
    }

    static void tick(Human human) {
        AmmoWeaponSelection.tick(human);
        if (!GunSupport.get().enabled() && !isActive(human)) return;
        ItemStack held = human.getMainHandItem();
        if (club.someoneice.humangunner.GunSupport.get().isGun(held)) {
            registerPrimaryGun(human, held);
            if (human.getPersistentData().getBoolean(ACTIVE)) {
                clearLease(human);
            }
            return;
        }
        if (human.isUsingItem()) {
            return;
        }
        if (!AmmoWeaponSelection.hasUsableGun(human)) {
            clearLease(human);
            return;
        }
        boolean active = human.getPersistentData().getBoolean(ACTIVE);
        if (active) {
            boolean validTarget = human.getTarget() != null
                    && human.getTarget().isAlive()
                    && human.distanceToSqr(human.getTarget()) < CLOSE_DEFENSE_EXIT_DISTANCE_SQR;
            boolean keepMelee = human.getPersistentData().getBoolean(CLOSE_DEFENSE)
                    ? validTarget
                    : human.isFleeing && validTarget
                    && human.distanceToSqr(human.getTarget()) <= 4.0D;
            if (!keepMelee) {
                restorePrimaryGun(human, "lease_watchdog");
            }
            return;
        }

        // Reconcile available guns stranded in the backpack. A Human should
        // prefer a usable gun whenever it is not performing the explicit
        // two-block retreat melee counterattack.
        HumanData data = human.getData();
        if (data != null && data.hasStoredGun(human.tickCount) && (human.getTarget() == null
                || !human.isFleeing
                || human.distanceToSqr(human.getTarget()) > 4.0D)
                && (findPrimaryGunSlot(human, data) >= 0 || findAnyGunSlot(data) >= 0)) {
            restorePrimaryGun(human, "orphan_reconciliation");
        }
    }

    static void prepareForManualInventory(Human human) {
        CloseRangedTactics.clear(human);
        CloseDefenseHold.clear(human);
        if (!isActive(human)) return;
        if (human.isUsingItem()) human.stopUsingItem();
        if (!restorePrimaryGun(human, "owner_inventory_open")) clearLease(human);
    }

    static void adoptManualLoadout(Human human) {
        CloseRangedTactics.clear(human);
        CloseDefenseHold.clear(human);
        clearLease(human);
        HumanData data = human.getData();
        ItemStack preferred = preferredManualGun(human, data);
        clearPrimaryMarker(human.getMainHandItem());
        clearPrimaryMarker(human.getOffhandItem());
        if (data != null) {
            for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                clearPrimaryMarker(data.getInventoryItem(i));
            }
        }
        registerPrimaryGun(human, preferred);
    }

    /**
     * Preserve a gun deliberately moved from the main hand into the backpack.
     * The ItemStack carries its owner marker through the menu move, so prefer
     * that exact stack before falling back to the first stored gun.
     */
    private static ItemStack preferredManualGun(Human human, HumanData data) {
        ItemStack main = human.getMainHandItem();
        if (club.someoneice.humangunner.GunSupport.get().isGun(main)) return main;
        if (data == null) return ItemStack.EMPTY;
        int markedSlot = findPrimaryGunSlot(human, data);
        if (markedSlot >= 0) return data.getInventoryItem(markedSlot);
        int anySlot = findAnyGunSlot(data);
        return anySlot >= 0 ? data.getInventoryItem(anySlot) : ItemStack.EMPTY;
    }

    static void auditEquipmentChange(
            Human human, EquipmentSlot slot, ItemStack previous, ItemStack current
    ) {
        if (slot != EquipmentSlot.MAINHAND
                || (!club.someoneice.humangunner.GunSupport.get().isGun(previous) && !club.someoneice.humangunner.GunSupport.get().isGun(current))) {
            return;
        }
        HumanGunner.LOGGER.debug(
                "Gun mainhand transition npc={} from={} to={} activeCustody={} reservedSlot={}",
                human.getUUID(), itemId(previous), itemId(current),
                human.getPersistentData().getBoolean(ACTIVE),
                human.getPersistentData().getInt(RESERVED_SLOT)
        );
    }

    static void auditOwnedGuns(Human human) {
        GunSnapshot current = snapshot(human);
        GunSnapshot previous = AUDIT.put(human, current);
        if (previous == null || (previous.count() == current.count() && previous.state().equals(current.state()))) {
            return;
        }
        HumanGunner.LOGGER.debug(
                "Gun ownership changed npc={} beforeCount={} afterCount={} activeCustody={} before=[{}] after=[{}]",
                human.getUUID(), previous.count(), current.count(),
                human.getPersistentData().getBoolean(ACTIVE), previous.state(), current.state()
        );
        if (current.count() < previous.count() && human.isAlive()) {
            HumanGunner.LOGGER.warn(
                    "Gun ownership decreased npc={} beforeCount={} afterCount={} activeCustody={} before=[{}] after=[{}]",
                    human.getUUID(), previous.count(), current.count(),
                    human.getPersistentData().getBoolean(ACTIVE), previous.state(), current.state()
            );
        }
    }

    private static int findPrimaryGunSlot(Human human, HumanData data) {
        if (!GunSupport.get().enabled()) return -1;
        String owner = human.getUUID().toString();
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            if (HumanInventoryCustody.isReservedForRecovery(data, i)) continue;
            ItemStack stack = data.getInventoryItem(i);
            if (club.someoneice.humangunner.GunSupport.get().isGun(stack)
                    && stack.hasTag()
                    && owner.equals(stack.getTag().getString(PRIMARY_OWNER))) {
                return i;
            }
        }
        return -1;
    }

    private static int findAnyGunSlot(HumanData data) {
        if (!GunSupport.get().enabled()) return -1;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            if (HumanInventoryCustody.isReservedForRecovery(data, i)) continue;
            if (club.someoneice.humangunner.GunSupport.get().isGun(data.getInventoryItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int findUsableGunSlot(Human human, HumanData data) {
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            if (HumanInventoryCustody.isReservedForRecovery(data, i)) continue;
            if (AmmoWeaponSelection.usableGun(human, data.getInventoryItem(i))) return i;
        }
        return -1;
    }

    private static void clearLease(Human human) {
        if (isActive(human) && !CloseDefenseHold.shouldHold(human)) CloseDefenseHold.clear(human);
        CompoundTag data = human.getPersistentData();
        data.remove(ACTIVE);
        data.remove(RESERVED_SLOT);
        data.remove(SNAPSHOT);
        data.remove(CLOSE_DEFENSE);
    }

    private static void clearPrimaryMarker(ItemStack stack) {
        if (stack.hasTag()) stack.getTag().remove(PRIMARY_OWNER);
    }

    private static GunSnapshot snapshot(Human human) {
        if (!GunSupport.get().enabled()) return new GunSnapshot(0, "");
        int count = 0;
        StringBuilder state = HumanGunner.LOGGER.isDebugEnabled() ? new StringBuilder() : null;
        ItemStack main = human.getMainHandItem();
        if (club.someoneice.humangunner.GunSupport.get().isGun(main)) {
            count++;
            if (state != null) state.append("main=").append(itemId(main));
        }
        ItemStack off = human.getOffhandItem();
        if (club.someoneice.humangunner.GunSupport.get().isGun(off)) {
            count++;
            if (state != null) append(state, "off=" + itemId(off));
        }
        HumanData data = human.getData();
        if (data != null) {
            for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                ItemStack stored = data.getInventoryItem(i);
                if (club.someoneice.humangunner.GunSupport.get().isGun(stored)) {
                    count++;
                    if (state != null) append(state, "inv" + i + "=" + itemId(stored));
                }
            }
        }
        return new GunSnapshot(count, state == null ? "" : state.toString());
    }

    private static void append(StringBuilder state, String value) {
        if (!state.isEmpty()) {
            state.append(',');
        }
        state.append(value);
    }

    private static String itemId(ItemStack stack) {
        return stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}

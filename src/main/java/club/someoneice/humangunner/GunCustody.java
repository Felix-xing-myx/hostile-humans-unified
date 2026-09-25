package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Single owner for every temporary gun/main-hand exchange.
 *
 * <p>The old implementation had two unrelated goals swapping a gun into an
 * inventory slot. One of them had no restore path, while the other remembered
 * only an in-memory slot number. This custody survives goal replacement and
 * entity saving, locates a moved gun by its owner marker, and retains a full
 * snapshot as a last-resort loss repair.</p>
 */
final class GunCustody {
    private static final String PRIMARY_OWNER = "humangunner:primary_gun_owner";
    private static final String ACTIVE = "humangunner:gun_custody_active";
    private static final String RESERVED_SLOT = "humangunner:gun_custody_slot";
    private static final String SNAPSHOT = "humangunner:gun_custody_snapshot";
    private static final String CLOSE_DEFENSE = "humangunner:gun_close_melee_defense";
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
        if (club.someoneice.humangunner.GunSupport.get().isGun(human.getMainHandItem())
                || club.someoneice.humangunner.GunSupport.get().isGun(human.getOffhandItem())) {
            return true;
        }
        HumanData data = human.getData();
        return data != null && findAnyGunSlot(data) >= 0;
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
        ItemStack gun = human.getMainHandItem().copy();
        if (!club.someoneice.humangunner.GunSupport.get().isGun(gun)) {
            return false;
        }
        HumanData data = human.getData();
        if (data == null) {
            return false;
        }
        int meleeSlot = -1;
        double meleeScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
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
        // Publish the reservation first. A synchronous equipment callback can
        // therefore never observe a gun that has left both owned locations.
        data.setInventoryItem(meleeSlot, gun);
        human.setItemSlot(EquipmentSlot.MAINHAND, melee);
        human.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        HumanGunner.LOGGER.debug(
                "Gun custody begin npc={} slot={} gun={} melee={}",
                human.getUUID(), meleeSlot, itemId(gun), itemId(melee)
        );
        return true;
    }

    static boolean restorePrimaryGun(Human human, String reason) {
        ItemStack held = human.getMainHandItem();
        if (club.someoneice.humangunner.GunSupport.get().isGun(held)) {
            registerPrimaryGun(human, held);
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
        if (gunSlot < 0) {
            gunSlot = findAnyGunSlot(data);
        }
        ItemStack gun;
        if (gunSlot >= 0) {
            gun = data.getInventoryItem(gunSlot).copy();
            registerPrimaryGun(human, gun);
            ItemStack displaced = human.getMainHandItem().copy();
            // Duplicate transiently, then complete the exact two-location swap.
            // A crash or callback between these writes can duplicate, not lose,
            // the high-value gun.
            human.setItemSlot(EquipmentSlot.MAINHAND, gun);
            data.setInventoryItem(gunSlot, displaced);
            human.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
            HumanGunner.LOGGER.debug(
                    "Gun custody restore npc={} reason={} slot={} gun={} displaced={}",
                    human.getUUID(), reason, gunSlot, itemId(gun), itemId(displaced)
            );
            clearLease(human);
            return true;
        }

        CompoundTag ownerData = human.getPersistentData();
        if (!ownerData.getBoolean(ACTIVE) || !ownerData.contains(SNAPSHOT)) {
            return false;
        }
        gun = ItemStack.of(ownerData.getCompound(SNAPSHOT));
        if (!club.someoneice.humangunner.GunSupport.get().isGun(gun)) {
            HumanGunner.LOGGER.error(
                    "Gun custody snapshot invalid npc={} reason={}", human.getUUID(), reason
            );
            clearLease(human);
            return false;
        }
        registerPrimaryGun(human, gun);
        ItemStack displaced = human.getMainHandItem().copy();
        if (!displaced.isEmpty()) {
            stowWithoutDeleting(human, data, displaced);
        }
        human.setItemSlot(EquipmentSlot.MAINHAND, gun);
        human.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
        HumanGunner.LOGGER.error(
                "Recovered missing primary gun from custody snapshot npc={} reason={} gun={}",
                human.getUUID(), reason, itemId(gun)
        );
        clearLease(human);
        return true;
    }

    static void tick(Human human) {
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

        // Repair guns stranded by older builds. A Human that owns a backpack
        // gun should prefer it whenever it is not performing the explicit
        // two-block retreat melee counterattack.
        HumanData data = human.getData();
        if (data != null && (human.getTarget() == null
                || !human.isFleeing
                || human.distanceToSqr(human.getTarget()) > 4.0D)
                && (findPrimaryGunSlot(human, data) >= 0 || findAnyGunSlot(data) >= 0)) {
            restorePrimaryGun(human, "orphan_reconciliation");
        }
    }

    static void prepareForManualInventory(Human human) {
        if (!isActive(human)) return;
        if (human.isUsingItem()) human.stopUsingItem();
        if (!restorePrimaryGun(human, "owner_inventory_open")) clearLease(human);
    }

    static void adoptManualLoadout(Human human) {
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
        if (previous == null || previous.state().equals(current.state())) {
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
        String owner = human.getUUID().toString();
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
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
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            if (club.someoneice.humangunner.GunSupport.get().isGun(data.getInventoryItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private static void stowWithoutDeleting(Human human, HumanData data, ItemStack stack) {
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            if (data.getInventoryItem(i).isEmpty()) {
                data.setInventoryItem(i, stack);
                return;
            }
        }
        int evictionSlot = HumanLootManager.lowestEvictableSlot(human);
        if (evictionSlot >= 0) {
            ItemStack evicted = data.getInventoryItem(evictionSlot).copy();
            data.setInventoryItem(evictionSlot, stack);
            drop(human, evicted);
            return;
        }
        drop(human, stack);
    }

    private static void drop(Human human, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity drop = new ItemEntity(
                human.level(), human.getX(), human.getY() + 0.4D, human.getZ(), stack
        );
        drop.setPickUpDelay(60);
        human.level().addFreshEntity(drop);
    }

    private static void clearLease(Human human) {
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
        int count = 0;
        StringBuilder state = new StringBuilder();
        ItemStack main = human.getMainHandItem();
        if (club.someoneice.humangunner.GunSupport.get().isGun(main)) {
            count++;
            state.append("main=").append(itemId(main));
        }
        ItemStack off = human.getOffhandItem();
        if (club.someoneice.humangunner.GunSupport.get().isGun(off)) {
            count++;
            append(state, "off=" + itemId(off));
        }
        HumanData data = human.getData();
        if (data != null) {
            for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                ItemStack stored = data.getInventoryItem(i);
                if (club.someoneice.humangunner.GunSupport.get().isGun(stored)) {
                    count++;
                    append(state, "inv" + i + "=" + itemId(stored));
                }
            }
        }
        return new GunSnapshot(count, state.toString());
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

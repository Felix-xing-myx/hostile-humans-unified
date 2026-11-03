package dev.felix.hostilehumans.behavior.combat.gun;

import dev.felix.hostilehumans.entity.HumanData;
import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.CloseDefenseHold;
import dev.felix.hostilehumans.behavior.combat.CloseRangedTactics;
import dev.felix.hostilehumans.behavior.combat.HumanCombatDirector;
import dev.felix.hostilehumans.behavior.combat.WeaponTransitionPolicy;
import dev.felix.hostilehumans.behavior.combat.ranged.AmmoWeaponSelection;
import dev.felix.hostilehumans.behavior.combat.retreat.RetreatLastStand;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.inventory.HumanEquipmentExchange;
import dev.felix.hostilehumans.inventory.HumanInventoryCustody;
import dev.felix.hostilehumans.inventory.HumanInventorySession;
import dev.felix.hostilehumans.inventory.HumanLootManager;

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
 * entity saving and locates a moved gun by its unique real-weapon handle.
 * Owner markers only migrate old saves; no snapshot reconstructs a weapon.</p>
 */
public final class GunCustody {
    private static final String PRIMARY_HANDLE="hostile_humans_unified:primary_gun_identity";
    private static final String PRIMARY_OWNER = "hostile_humans_unified:primary_gun_owner";
    private static final String ACTIVE = "hostile_humans_unified:gun_custody_active";
    private static final String RESERVED_SLOT = "hostile_humans_unified:gun_custody_slot";
    private static final String SNAPSHOT = "hostile_humans_unified:gun_custody_snapshot";
    private static final String CLOSE_DEFENSE = "hostile_humans_unified:gun_close_melee_defense";
    private static final Map<Human, GunSnapshot> AUDIT = new WeakHashMap<>();

    private record GunSnapshot(int count, String state) {
    }

    private GunCustody() {
    }

    public static boolean isActive(Human human) {
        return human.getPersistentData().getBoolean(ACTIVE);
    }

    public static boolean isCloseDefenseActive(Human human) {
        return human.getPersistentData().getBoolean(ACTIVE)
                && human.getPersistentData().getBoolean(CLOSE_DEFENSE);
    }

    public static boolean hasOwnedGun(Human human) {
        if (!GunSupport.get().enabled()) return false;
        if (dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(human.getMainHandItem())
                || dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(human.getOffhandItem())) {
            return true;
        }
        HumanData data = human.getData();
        return data != null && data.hasStoredGun(human.tickCount);
    }

    public static void registerPrimaryGun(Human human, ItemStack gun) {
        if(!GunSupport.get().isGun(gun)) return;
        var handle=HumanEquipmentExchange.bind(human,gun);
        if(handle==null) return;
        var tag=human.getPersistentData();
        if(tag.hasUUID(PRIMARY_HANDLE) && !tag.getUUID(PRIMARY_HANDLE).equals(handle.identity())) {
            ItemStack previous=HumanEquipmentExchange.locate(human,
                    new HumanEquipmentExchange.Handle(tag.getUUID(PRIMARY_HANDLE),human.getUUID()));
            if(!previous.isEmpty()) GunSupport.get().cancelReload(human,previous);
        }
        gun.getOrCreateTag().putString(PRIMARY_OWNER,human.getUUID().toString());
        tag.putUUID(PRIMARY_HANDLE,handle.identity());
    }
    private static ItemStack primary(Human human) {
        var tag=human.getPersistentData();
        if(tag.hasUUID(PRIMARY_HANDLE)) return HumanEquipmentExchange.locate(human,
                new HumanEquipmentExchange.Handle(tag.getUUID(PRIMARY_HANDLE),human.getUUID()));
        ItemStack held=human.getMainHandItem();
        if(GunSupport.get().isGun(held)) { registerPrimaryGun(human,held); return held; }
        // Old saves may have an owner marker, but never authorize an unmarked same-model replacement.
        ItemStack found=ItemStack.EMPTY;
        if(human.getData()!=null) for(int i=0;i<human.getData().getInventoryItemsSize();i++) {
            ItemStack candidate=human.getData().getInventoryItem(i);
            if(!GunSupport.get().isGun(candidate)||!candidate.hasTag()
                    ||!human.getStringUUID().equals(candidate.getTag().getString(PRIMARY_OWNER))) continue;
            if(!found.isEmpty()) return ItemStack.EMPTY;
            found=candidate;
        }
        if(!found.isEmpty()) registerPrimaryGun(human,found);
        return found;
    }
    static boolean beginCloseMeleeCounter(Human human) {
        return beginCloseMeleeCounter(human, false);
    }
    public static boolean beginRetreatMeleeCounter(Human human) {
        return RetreatLastStand.permitsMelee(human) && beginCloseMeleeCounter(human, true);
    }
    private static boolean beginCloseMeleeCounter(Human human, boolean lastStand) {
        if(isActive(human)) return true;
        ItemStack gun=human.getMainHandItem();
        var target=human.getTarget();
        if(!GunSupport.get().isGun(gun)||target==null||!target.isAlive()
                ||human.distanceToSqr(target)>WeaponTransitionPolicy.GUN_ENTER*WeaponTransitionPolicy.GUN_ENTER||human.isUsingItem()) return false;
        if (!lastStand && HumanCombatDirector.keepsRangedDistance(human, gun)) return false;
        boolean forced=WeaponTransitionPolicy.holdsMelee(human.distanceToSqr(target), false,
                GunCombatProfile.shouldForceMelee(human,gun));
        if(!lastStand && !forced && (GunSupport.get().hasLoadedAmmo(gun)||!GunSupport.get().isReloading(human,gun))) return false;
        ItemStack melee=HumanLootManager.bestOwnedMeleeWeapon(human);
        if(melee.isEmpty()) return false;
        registerPrimaryGun(human,gun);
        var result=HumanEquipmentExchange.request(human,"gun-close-melee",java.util.List.of(
                new HumanEquipmentExchange.Need(EquipmentSlot.MAINHAND,stack->stack==melee)));
        if(result!=HumanEquipmentExchange.Result.SUCCESS) return false;
        human.getPersistentData().putBoolean(ACTIVE,true);
        human.getPersistentData().putBoolean(CLOSE_DEFENSE,true);
        human.setDropChance(EquipmentSlot.MAINHAND,1);
        CloseRangedTactics.beginMelee(human);
        return true;
    }
    // High pressure is permitted with the actual gun retained. No melee fallback is required.
    static boolean canBeginPressureDefense(Human human) { return true; }

    public static boolean restorePrimaryGun(Human human, String reason) {
        ItemStack gun=primary(human);
        if(gun.isEmpty()||!AmmoWeaponSelection.usableGun(human,gun)) { clearLease(human); return false; }
        boolean editing=reason.equals("owner_inventory_open");
        if(!editing) {
            var target=human.getTarget();
            boolean close=RetreatLastStand.permitsMelee(human) || target!=null&&target.isAlive()&&WeaponTransitionPolicy.holdsMelee(
                    human.distanceToSqr(target), true, GunCombatProfile.shouldForceMelee(human,gun));
            boolean melee=HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem());
            if(isActive(human)&&melee&&(human.hhu$hasPendingMeleeAttack()||human.swinging)) return false;
            if(isActive(human)&&close&&melee) return false;
            if(isActive(human)&&melee&&GunSupport.get().isReloading(human,gun)
                    && !HumanCombatDirector.keepsRangedDistance(human, gun)) return false;
            if(!AmmoWeaponSelection.mayRestore(human,gun)) return false;
        }
        if(human.getMainHandItem()==gun) { clearLease(human); return true; }
        var tag=human.getPersistentData();
        if(!tag.hasUUID(PRIMARY_HANDLE)) return false;
        var handle=new HumanEquipmentExchange.Handle(tag.getUUID(PRIMARY_HANDLE),human.getUUID());
        var result=HumanEquipmentExchange.request(human,"gun-return",java.util.List.of(
                new HumanEquipmentExchange.Need(EquipmentSlot.MAINHAND,GunSupport.get()::isGun,handle)));
        if(result!=HumanEquipmentExchange.Result.SUCCESS) return false;
        clearLease(human);
        human.setDropChance(EquipmentSlot.MAINHAND,1);
        return true;
    }

    public static void tick(Human human) {
        if(human.level().isClientSide) return;
        if(!human.isAlive()||human.isRemoved()||human.isNoAi()||HumanInventorySession.isEditing(human)) {
            ItemStack gun=primary(human);
            if(!gun.isEmpty()) GunSupport.get().cancelReload(human,gun);
            clearLease(human); return;
        }
        if(isActive(human)) {
            ItemStack gun=primary(human);
            if(gun.isEmpty()) { clearLease(human); return; }
            var target=human.getTarget();
            boolean close=RetreatLastStand.permitsMelee(human) || target!=null&&target.isAlive()&&WeaponTransitionPolicy.holdsMelee(
                    human.distanceToSqr(target), true, GunCombatProfile.shouldForceMelee(human,gun));
            boolean reloadMelee = GunSupport.get().isReloading(human,gun)
                    && !HumanCombatDirector.keepsRangedDistance(human, gun);
            if((close||reloadMelee)
                    &&!HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem())) {
                ItemStack selected=HumanLootManager.bestOwnedMeleeWeapon(human);
                if(!selected.isEmpty()) {
                    HumanEquipmentExchange.request(human,"gun-melee-replacement",java.util.List.of(
                            new HumanEquipmentExchange.Need(EquipmentSlot.MAINHAND,stack->stack==selected)));
                    return;
                }
            }
            if(close&&HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem())) return;
            if(HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem())
                    &&reloadMelee) return;
            // A busy hand is transient; retain the original handle and retry.
            restorePrimaryGun(human,"reload_or_target_completed");
            return;
        }
        AmmoWeaponSelection.tick(human);
        ItemStack held=human.getMainHandItem();
        if(GunSupport.get().isGun(held)) {
            registerPrimaryGun(human,held);
            if(!GunSupport.get().hasLoadedAmmo(held)) GunSupport.get().beginReload(human,held);
            beginCloseMeleeCounter(human);
            return;
        }
        ItemStack gun=primary(human);
        if(!gun.isEmpty()) restorePrimaryGun(human,"orphan_reconciliation");
    }

    public static void prepareForManualInventory(Human human) {
        ItemStack reloading=primary(human);
        if(!reloading.isEmpty()) GunSupport.get().cancelReload(human,reloading);
        CloseRangedTactics.clear(human);
        CloseDefenseHold.clear(human);
        if (!isActive(human)) return;
        if (human.isUsingItem()) human.stopUsingItem();
        if (!restorePrimaryGun(human, "owner_inventory_open")) clearLease(human);
    }

    /** Immediate physical cleanup accompanies session revocation; no item is moved or restored. */
    public static void cancelBackgroundReloads(Human human) {
        if (human.level().isClientSide) return;
        var actual = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ItemStack, Boolean>());
        actual.add(human.getMainHandItem());
        actual.add(human.getOffhandItem());
        if (human.getData() != null) for (int i = 0; i < human.getData().getInventoryItemsSize(); i++)
            actual.add(human.getData().getInventoryItem(i));
        RuntimeException failure = null;
        try {
            var guns = GunSupport.get();
            for (ItemStack stack : actual) {
                if (stack.isEmpty()) continue;
                try {
                    if (guns.isGun(stack)) guns.cancelReload(human, stack);
                } catch (RuntimeException exception) {
                    // One optional backend must not strand the other real weapons.
                    if (failure == null) failure = exception;
                    else if (failure != exception) failure.addSuppressed(exception);
                }
            }
        } finally {
            clearLease(human);
            CloseRangedTactics.clear(human);
            CloseDefenseHold.clear(human);
        }
        if (failure != null) throw failure;
    }

    public static void adoptManualLoadout(Human human) {
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
        human.getPersistentData().remove(PRIMARY_HANDLE);
        registerPrimaryGun(human, preferred);
    }

    /**
     * Preserve a gun deliberately moved from the main hand into the backpack.
     * The ItemStack carries its owner marker through the menu move, so prefer
     * that exact stack before falling back to the first stored gun.
     */
    private static ItemStack preferredManualGun(Human human, HumanData data) {
        ItemStack main = human.getMainHandItem();
        if (dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(main)) return main;
        if (data == null) return ItemStack.EMPTY;
        int markedSlot = findPrimaryGunSlot(human, data);
        if (markedSlot >= 0) return data.getInventoryItem(markedSlot);
        int anySlot = findAnyGunSlot(data);
        return anySlot >= 0 ? data.getInventoryItem(anySlot) : ItemStack.EMPTY;
    }

    public static void auditEquipmentChange(
            Human human, EquipmentSlot slot, ItemStack previous, ItemStack current
    ) {
        if (slot != EquipmentSlot.MAINHAND
                || (!dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(previous) && !dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(current))) {
            return;
        }
        HumanRuntime.LOGGER.debug(
                "Gun mainhand transition npc={} from={} to={} activeCustody={} reservedSlot={}",
                human.getUUID(), itemId(previous), itemId(current),
                human.getPersistentData().getBoolean(ACTIVE),
                human.getPersistentData().getInt(RESERVED_SLOT)
        );
    }

    public static void auditOwnedGuns(Human human) {
        GunSnapshot current = snapshot(human);
        GunSnapshot previous = AUDIT.put(human, current);
        if (previous == null || (previous.count() == current.count() && previous.state().equals(current.state()))) {
            return;
        }
        HumanRuntime.LOGGER.debug(
                "Gun ownership changed npc={} beforeCount={} afterCount={} activeCustody={} before=[{}] after=[{}]",
                human.getUUID(), previous.count(), current.count(),
                human.getPersistentData().getBoolean(ACTIVE), previous.state(), current.state()
        );
        if (current.count() < previous.count() && human.isAlive()) {
            HumanRuntime.LOGGER.warn(
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
            if (dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(stack)
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
            if (dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(data.getInventoryItem(i))) {
                return i;
            }
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
        StringBuilder state = HumanRuntime.LOGGER.isDebugEnabled() ? new StringBuilder() : null;
        ItemStack main = human.getMainHandItem();
        if (dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(main)) {
            count++;
            if (state != null) state.append("main=").append(itemId(main));
        }
        ItemStack off = human.getOffhandItem();
        if (dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(off)) {
            count++;
            if (state != null) append(state, "off=" + itemId(off));
        }
        HumanData data = human.getData();
        if (data != null) {
            for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                ItemStack stored = data.getInventoryItem(i);
                if (dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(stored)) {
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

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
 * entity saving and locates a moved gun by its unique real-weapon handle.
 * Owner markers only migrate old saves; no snapshot reconstructs a weapon.</p>
 */
final class GunCustody {
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
        if(!GunSupport.get().isGun(gun)) return;
        gun.getOrCreateTag().putString(PRIMARY_OWNER,human.getUUID().toString());
        var handle=HumanEquipmentExchange.bind(human,gun);
        if(handle!=null) human.getPersistentData().putUUID(PRIMARY_HANDLE,handle.identity());
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
        if(isActive(human)) return true;
        ItemStack gun=human.getMainHandItem();
        var target=human.getTarget();
        if(!GunSupport.get().isGun(gun)||target==null||!target.isAlive()
                ||human.distanceToSqr(target)>4.5*4.5||human.isUsingItem()) return false;
        boolean forced=GunCombatProfile.shouldForceMelee(human,gun);
        if(!forced && (GunSupport.get().hasLoadedAmmo(gun)||!GunSupport.get().isReloading(human,gun))) return false;
        HumanData data=human.getData();
        int slot=data==null?-1:bestMeleeSlot(data);
        if(slot<0) return false;
        ItemStack melee=data.getInventoryItem(slot);
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
        ItemStack gun=primary(human);
        if(gun.isEmpty()||!AmmoWeaponSelection.usableGun(human,gun)) { clearLease(human); return false; }
        boolean editing=reason.equals("owner_inventory_open");
        if(!editing) {
            var target=human.getTarget();
            boolean close=target!=null&&target.isAlive()&&human.distanceToSqr(target)<=4.5*4.5;
            boolean melee=HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem());
            if(isActive(human)&&melee&&(human.humanGunner$hasPendingMeleeAttack()||human.swinging)) return false;
            if(isActive(human)&&close&&melee&&GunCombatProfile.shouldForceMelee(human,gun)) return false;
            if(isActive(human)&&melee&&GunSupport.get().isReloading(human,gun)) return false;
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

    static void tick(Human human) {
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
            boolean close=target!=null&&target.isAlive()&&human.distanceToSqr(target)<=4.5*4.5;
            if((close&&GunCombatProfile.shouldForceMelee(human,gun)||GunSupport.get().isReloading(human,gun))
                    &&!HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem())) {
                int fallback=human.getData()==null?-1:bestMeleeSlot(human.getData());
                if(fallback>=0) {
                    ItemStack selected=human.getData().getInventoryItem(fallback);
                    HumanEquipmentExchange.request(human,"gun-melee-replacement",java.util.List.of(
                            new HumanEquipmentExchange.Need(EquipmentSlot.MAINHAND,stack->stack==selected)));
                    return;
                }
            }
            if(close&&HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem())
                    &&GunCombatProfile.shouldForceMelee(human,gun)) return;
            if(HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem())
                    &&GunSupport.get().isReloading(human,gun)) return;
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

    static void prepareForManualInventory(Human human) {
        ItemStack reloading=primary(human);
        if(!reloading.isEmpty()) GunSupport.get().cancelReload(human,reloading);
        CloseRangedTactics.clear(human);
        CloseDefenseHold.clear(human);
        if (!isActive(human)) return;
        if (human.isUsingItem()) human.stopUsingItem();
        if (!restorePrimaryGun(human, "owner_inventory_open")) clearLease(human);
    }

    /** Immediate physical cleanup accompanies session revocation; no item is moved or restored. */
    static void cancelBackgroundReloads(Human human) {
        if (human.level().isClientSide) return;
        var actual = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ItemStack, Boolean>());
        actual.add(human.getMainHandItem());
        actual.add(human.getOffhandItem());
        if (human.getData() != null) for (int i = 0; i < human.getData().getInventoryItemsSize(); i++)
            actual.add(human.getData().getInventoryItem(i));
        for (ItemStack stack : actual) if (GunSupport.get().isGun(stack)) GunSupport.get().cancelReload(human, stack);
        clearLease(human);
        CloseRangedTactics.clear(human);
        CloseDefenseHold.clear(human);
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

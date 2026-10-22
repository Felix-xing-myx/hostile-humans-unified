package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.data.HumanData;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Authoritative weighted pools -> firearm allocation -> reserves -> growth pipeline. */
public final class HumanSpawnEquipment {
    // Elite gear's bound-drop marker belongs to the unified save namespace.
    public static final String BOUND_GEAR = "hostile_humans_unified:tier3_bound_gear";
    private static final String PENDING = "hostile_humans_unified:spawn_equipment_pending";
    private static final String GENERATED = "hostile_humans_unified:spawn_equipment_generated";
    private static final String FORCE_RANGED = "hostile_humans_unified:spawn_force_ranged";
    private static final String EXPLICIT_RANGED = "hostile_humans_unified:spawn_explicit_ranged";
    private static final String RANGED_REQUESTED = "hostile_humans_unified:spawn_ranged_requested";
    private static final String AUTHORED = "hostile_humans_unified:authored_loadout";
    private HumanSpawnEquipment() { }
    /** An explicit author loadout, including empty slots, is authoritative across deferred initialization. */
    public static void preserveAuthored(Human human) {
        var tag = human.getPersistentData();
        tag.putBoolean(AUTHORED, true);
        for (String key : java.util.List.of(PENDING, FORCE_RANGED, EXPLICIT_RANGED, RANGED_REQUESTED,
                "hostile_humans_unified:pending_loadout", "hostile_humans_unified:pending_ranged")) tag.remove(key);
    }

    public static boolean isBoundGear(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().getBoolean(BOUND_GEAR);
    }

    /** Native rank selection happens here, not as a destructive post-spawn tier-three patch. */
    public static void generate(Human human, boolean forceRanged) {
        if (human.level().isClientSide || human.getPersistentData().getBoolean(AUTHORED)) return;
        boolean namedRequest=human.hasCustomName()&&human.getCustomName().getString().contains("give_random_gear");
        // Only explicit author commands can force a non-gun ranged fallback. Natural tier requests cannot.
        forceRanged=human.getPersistentData().getBoolean(EXPLICIT_RANGED)
                || namedRequest&&human.getCustomName().getString().contains("ranged");
        if(forceRanged) human.getPersistentData().putBoolean(EXPLICIT_RANGED,true);
        if (human.getData() == null) {
            human.getPersistentData().putBoolean("hostile_humans_unified:pending_loadout", true);
            human.getPersistentData().putBoolean("hostile_humans_unified:pending_ranged", forceRanged);
            return;
        }
        if (human.getPersistentData().getBoolean(GENERATED) || human.getPersistentData().getBoolean(PENDING)) return;
        human.getPersistentData().putBoolean(PENDING, true);
        human.getPersistentData().putBoolean(FORCE_RANGED, forceRanged);
        // Optional mod APIs may require the server thread (chunk population can be asynchronous).
        // Final completion is owned by initializeHumanIfReady, after insertion/data readiness.
    }

    /** Explicit legacy command/structure request; never erase an owner's loadout. */
    public static void generateRequested(Human human, boolean forceRanged) {
        if (human.hasOwner()) return;
        if(forceRanged) {
            human.getPersistentData().putBoolean(EXPLICIT_RANGED,true);
            if(human.getPersistentData().getBoolean(PENDING)) human.getPersistentData().putBoolean(FORCE_RANGED,true);
        }
        human.getPersistentData().remove(AUTHORED);
        if (human.getPersistentData().getBoolean(PENDING)
                || human.getPersistentData().getBoolean("hostile_humans_unified:pending_loadout")) {
            // finalizeSpawn already honored the special name. Do not clear/re-roll its gear.
            return;
        }
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            human.setItemSlot(slot, ItemStack.EMPTY);
        }
        var tag = human.getPersistentData();
        tag.remove(GENERATED);
        tag.remove("hostile_humans_unified:spartan_loadout_generated");
        tag.remove("hostile_humans_unified:guaranteed_shields_generated");
        tag.remove("hostile_humans_unified:spawn_shield_count");
        tag.remove("hostile_humans_unified:gun_roll_done");
        NaturalEquipmentGrowth.excludeRequestedSpawn(human);
        generate(human, forceRanged);
    }

    static boolean completeIfReady(Human human) {
        if (human.getData() == null || human.getServer() == null || !human.getServer().isSameThread()) return false;
        var tag = human.getPersistentData();
        if (tag.getBoolean(AUTHORED)) { preserveAuthored(human); return true; }
        if (!tag.getBoolean(PENDING) || tag.getBoolean(GENERATED)) return true;
        // Do not regenerate old/save-loaded or explicitly equipped entities: only generate() creates this lease.
        boolean ranged = tag.getBoolean(FORCE_RANGED)&&tag.getBoolean(EXPLICIT_RANGED);
        // Resolve the additional firearm chance first. A gunner's ordinary
        // allocation is melee, so the two weapons coexist within the rank cap.
        ItemStack gun = FirearmSpawnEquipment.roll(human, ranged);
        tag.putBoolean(RANGED_REQUESTED,gun.isEmpty()&&(ranged
                ||human.getRandom().nextDouble()<FactionEquipmentConfig.rangedChance(human)));
        ConfiguredHumanEquipment.generate(human, ranged, gun.isEmpty());
        InventoryTotemProtection.stowOffhandTotem(human);
        // Legacy automatic Traveler's Backpack equipment is suspended.
        FirearmSpawnEquipment.equip(human, gun);
        ConfiguredHumanEquipment.finishRanged(human, ranged);
        GuaranteedShieldLoadout.applyOnce(human);
        ensureMeleeFallback(human);
        ConfiguredHumanEquipment.generateReserves(human);
        NaturalEquipmentGrowth.applyOnce(human);
        HumanAmmoSpawn.applyOnce(human);
        tag.putBoolean(GENERATED, true);
        tag.remove(PENDING);
        tag.remove(FORCE_RANGED);
        tag.remove(EXPLICIT_RANGED);
        tag.remove(RANGED_REQUESTED);
        return true;
    }

    static boolean wantsRanged(Human human) {
        return human.getPersistentData().getBoolean(RANGED_REQUESTED);
    }

    static void ensureMeleeFallback(Human human) {
        HumanData data = human.getData();
        if (data == null) {
            return;
        }
        boolean hasGun = GunSupport.get().isGun(human.getMainHandItem())
                || GunSupport.get().isGun(human.getOffhandItem());
        // Count the hand copy only while custody has temporarily withdrawn the
        // backpack fallback. At every stable gunner state the required melee
        // weapon must occupy an actual HumanData inventory slot.
        boolean hasMelee = GunCustody.isActive(human)
                && HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem());
        int replacementSlot = -1;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stored = data.getInventoryItem(i);
            hasGun |= GunSupport.get().isGun(stored);
            hasMelee |= HumanLootManager.isDedicatedMeleeWeapon(stored);
            if (replacementSlot < 0 && stored.isEmpty()) {
                replacementSlot = i;
            }
        }
        if (!hasGun || hasMelee) {
            return;
        }

        // Birth allocation may only use free capacity; never spill existing supplies to add a weapon.
        if (replacementSlot < 0) return;
        ItemStack fallback = ConfiguredHumanEquipment.backupMelee(human);
        if (fallback.isEmpty()) return;
        data.setInventoryItem(replacementSlot, fallback);
        human.getPersistentData().putBoolean(HumanGunner.MOD_ID + ":melee_fallback_added", true);
    }
}

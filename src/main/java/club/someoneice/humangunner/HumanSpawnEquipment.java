package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.data.HumanData;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/** Authoritative weighted pools -> firearm allocation -> reserves -> growth pipeline. */
public final class HumanSpawnEquipment {
    // Keep the saved key unchanged so elite gear from older releases keeps its drop rules.
    public static final String BOUND_GEAR = "humangunner:tier3_bound_gear";
    private static final String PENDING = "humangunner:spawn_equipment_pending";
    private static final String GENERATED = "humangunner:spawn_equipment_generated";
    private static final String FORCE_RANGED = "humangunner:spawn_force_ranged";
    private static final String RANGED_REQUESTED = "humangunner:spawn_ranged_requested";
    private HumanSpawnEquipment() { }

    public static boolean isBoundGear(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().getBoolean(BOUND_GEAR);
    }

    /** Native rank selection happens here, not as a destructive post-spawn tier-three patch. */
    public static void generate(Human human, boolean forceRanged) {
        if (human.level().isClientSide) return;
        if (human.hasCustomName() && human.getCustomName().getString().contains("give_random_gear")) {
            forceRanged |= human.getCustomName().getString().contains("ranged");
        }
        if (human.getData() == null) {
            human.getPersistentData().putBoolean("hostile_humans:pending_loadout", true);
            human.getPersistentData().putBoolean("hostile_humans:pending_ranged", forceRanged);
            return;
        }
        if (human.getPersistentData().getBoolean(GENERATED) || human.getPersistentData().getBoolean(PENDING)) return;
        // One non-firearm ranged decision, shared by the configured pools and
        // reserve guarantee, without a second integration probability roll.
        human.getPersistentData().putBoolean(RANGED_REQUESTED, forceRanged
                || human.getRandom().nextFloat() < ConfiguredHumanEquipment.of(human).rangedChance);
        human.getPersistentData().putBoolean(PENDING, true);
        human.getPersistentData().putBoolean(FORCE_RANGED, forceRanged);
        // Optional mod APIs may require the server thread (chunk population can be asynchronous).
        // Final completion is owned by initializeHumanIfReady, after insertion/data readiness.
    }

    /** Explicit legacy command/structure request; never erase an owner's loadout. */
    public static void generateRequested(Human human, boolean forceRanged) {
        if (human.hasOwner()) return;
        if (human.getPersistentData().getBoolean(PENDING)
                || human.getPersistentData().getBoolean("hostile_humans:pending_loadout")) {
            // finalizeSpawn already honored the special name. Do not clear/re-roll its gear.
            return;
        }
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            human.setItemSlot(slot, ItemStack.EMPTY);
        }
        var tag = human.getPersistentData();
        tag.remove(GENERATED);
        tag.remove("humangunner:spartan_loadout_generated");
        tag.remove("humangunner:guaranteed_shields_generated");
        tag.remove("humangunner:spawn_shield_count");
        tag.remove("humangunner:gun_roll_done");
        NaturalEquipmentGrowth.excludeRequestedSpawn(human);
        generate(human, forceRanged);
    }

    static boolean completeIfReady(Human human) {
        if (human.getData() == null || human.getServer() == null || !human.getServer().isSameThread()) return false;
        var tag = human.getPersistentData();
        if (!tag.getBoolean(PENDING) || tag.getBoolean(GENERATED)) return true;
        // Do not regenerate old/save-loaded or explicitly equipped entities: only generate() creates this lease.
        boolean ranged = tag.getBoolean(FORCE_RANGED);
        // Resolve the additional firearm chance first. A gunner's ordinary
        // allocation is melee, so the two weapons coexist within the rank cap.
        ItemStack gun = FirearmSpawnEquipment.roll(human, ranged);
        ConfiguredHumanEquipment.generate(human, ranged, gun.isEmpty());
        InventoryTotemProtection.stowOffhandTotem(human);
        if (ModList.get().isLoaded("travelersbackpack") && ModList.get().isLoaded("curios"))
            com.craftix.hostile_humans.compat.TravelersBackpack.apply(human);
        FirearmSpawnEquipment.equip(human, gun);
        ConfiguredHumanEquipment.finishRanged(human, ranged);
        GuaranteedShieldLoadout.applyOnce(human);
        ShieldEnchantmentRoll.applyOnce(human);
        ensureMeleeFallback(human);
        ConfiguredHumanEquipment.generateReserves(human);
        NaturalEquipmentGrowth.applyOnce(human);
        tag.putBoolean(GENERATED, true);
        tag.remove(PENDING);
        tag.remove(FORCE_RANGED);
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

        // A firearm owner must always retain one dedicated melee fallback in
        // the backpack. If supplies filled every slot, evict the lowest-value
        // candidate through the centralized valuation policy instead of
        // silently leaving the gunner unable to defend at contact distance.
        if (replacementSlot < 0) {
            replacementSlot = HumanLootManager.lowestMandatoryMeleeEvictionSlot(human);
            if (replacementSlot < 0) {
                return;
            }
            ItemStack evicted = data.getInventoryItem(replacementSlot).copy();
            if (!evicted.isEmpty() && human.spawnAtLocation(evicted) == null) return;
        }

        ItemStack fallback = ConfiguredHumanEquipment.backupMelee(human);
        data.setInventoryItem(replacementSlot, fallback);
        human.getPersistentData().putBoolean(HumanGunner.MOD_ID + ":melee_fallback_added", true);
    }
}

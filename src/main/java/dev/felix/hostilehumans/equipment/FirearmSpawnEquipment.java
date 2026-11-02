package dev.felix.hostilehumans.equipment;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.gun.GunCustody;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.config.UnifiedConfig;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.util.concurrent.atomic.AtomicBoolean;

/** TaCZ equipment adapter; no TaCZ classes are linked by the native generator. */
public final class FirearmSpawnEquipment {
    private static final org.slf4j.Logger LOGGER = HumanRuntime.LOGGER;
    private static final String GUN_ROLL_DONE = "hostile_humans_unified:gun_roll_done";
    private static final AtomicBoolean WARNED_EMPTY_GUN_POOL = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_FIRST_EQUIP = new AtomicBoolean();

    private FirearmSpawnEquipment() { }

    static ItemStack roll(Human human, boolean forceRanged) {
        if (!ConfiguredHumanEquipment.of(human).firearms || !GunSupport.get().enabled()
                || (NaturalEquipmentGrowth.isNaturalSpawn(human)
                    && !UnifiedConfig.get().naturalFirearmsEnabled())) return ItemStack.EMPTY;
        if (human.getPersistentData().getBoolean(GUN_ROLL_DONE)) return ItemStack.EMPTY;
        human.getPersistentData().putBoolean(GUN_ROLL_DONE, true);
        // Every rank, including forced-ranged tier two, shares this single faction roll.
        double chance = FactionEquipmentConfig.of(human).number("gun_chance",0,0,1);
        if (ConfiguredHumanEquipment.of(human).guns(human).isEmpty()) {
            if (WARNED_EMPTY_GUN_POOL.compareAndSet(false, true)) {
                LOGGER.warn("Hostile Humans Unified found no configured firearms in the loaded optional gun catalogs");
            }
            return ItemStack.EMPTY;
        }
        chance *= NaturalEquipmentGrowth.gunChanceMultiplier(human);
        if (human.getRandom().nextDouble() >= chance) {
            return ItemStack.EMPTY;
        }
        return ConfiguredHumanEquipment.prepare(human,ConfiguredHumanEquipment.of(human).guns(human).roll(human),EquipmentSlot.MAINHAND);
    }

    static void equip(Human human, ItemStack gun) {
            if (gun.isEmpty()) return;
            ItemStack previousWeapon = human.getMainHandItem().copy();
            // The ordinary melee allocation is retained in the backpack.
            if (!previousWeapon.isEmpty() && !RecoverySupplies.insertInventory(human.getData(),previousWeapon)) return;
            GunCustody.registerPrimaryGun(human, gun);
            human.setItemSlot(EquipmentSlot.MAINHAND, gun);
            // Drop probability is still settled by the existing unified loot policy.
            human.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
            // Keep one melee fallback for the custody-owned two-block retreat
            // counterattack. Do not retain an old bow/crossbow: the gun remains
            // the authoritative ranged weapon for this NPC.
            // The spawn pipeline provides reserves after all
            // providers have finished; this adapter only supplies the firearm.
            if (LOGGED_FIRST_EQUIP.compareAndSet(false, true)) {
                LOGGER.info("Hostile Humans Unified first successful additional firearm allocation: {}", human.getTier());
            }
            LOGGER.debug(
                    "Equipped npc={} tier={} with an additional TaCZ firearm",
                    human.getUUID(), human.getTier()
            );
    }
}

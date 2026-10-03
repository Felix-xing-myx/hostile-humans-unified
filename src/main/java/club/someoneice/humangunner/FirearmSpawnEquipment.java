package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.util.concurrent.atomic.AtomicBoolean;

/** TaCZ equipment adapter; no TaCZ classes are linked by the native generator. */
final class FirearmSpawnEquipment {
    private static final org.slf4j.Logger LOGGER = HumanGunner.LOGGER;
    private static final String GUN_ROLL_DONE = "humangunner:gun_roll_done";
    private static final AtomicBoolean WARNED_EMPTY_GUN_POOL = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_FIRST_EQUIP = new AtomicBoolean();

    private FirearmSpawnEquipment() { }

    static ItemStack roll(Human human, boolean forceRanged) {
        if (!ConfiguredHumanEquipment.of(human).firearms || !GunSupport.get().enabled()
                || (NaturalEquipmentGrowth.isNaturalSpawn(human)
                    && !UnifiedConfig.get().naturalFirearmsEnabled())) return ItemStack.EMPTY;
        if (human.getPersistentData().getBoolean(GUN_ROLL_DONE)) return ItemStack.EMPTY;
        human.getPersistentData().putBoolean(GUN_ROLL_DONE, true);
        if (HumanGunnerConfig.get().availableGunCount() == 0) {
            if (WARNED_EMPTY_GUN_POOL.compareAndSet(false, true)) {
                LOGGER.warn("Human Gunner found no configured firearms in the loaded TaCZ common gun index");
            }
            return ItemStack.EMPTY;
        }
        HumanGunnerConfig config = HumanGunnerConfig.get();
        double chance;
        String tierGunTypeKey;
        if (TierThreeHuman.isTierThree(human)) {
            chance = config.tier3GunChance();
            tierGunTypeKey = "tier3";
        } else if (human.getTier() == HumanTier.LEVEL1) {
            chance = config.tier1GunChance();
            tierGunTypeKey = "tier1";
        } else if (human.getTier() == HumanTier.LEVEL2) {
            chance = forceRanged ? config.forcedRangedChance() : config.tier2GunChance();
            tierGunTypeKey = "tier2";
        } else if (human.getTier() == HumanTier.ROAMER) {
            chance = config.roamerGunChance();
            tierGunTypeKey = "roamer";
        } else {
            return ItemStack.EMPTY;
        }
        chance *= NaturalEquipmentGrowth.gunChanceMultiplier(human);
        if (human.getRandom().nextDouble() >= chance) {
            return ItemStack.EMPTY;
        }
        return config.rollAvailableGun(human.getRandom(), tierGunTypeKey)
                .map(gunId -> GunSupport.get().createLoadedGun(gunId)).orElse(ItemStack.EMPTY);
    }

    static void equip(Human human, ItemStack gun) {
            if (gun.isEmpty()) return;
            ItemStack previousWeapon = human.getMainHandItem().copy();
            // The ordinary melee allocation is retained in the backpack.
            if (!previousWeapon.isEmpty() && !human.putItemAway(previousWeapon)) return;
            GunCustody.registerPrimaryGun(human, gun);
            human.setItemSlot(EquipmentSlot.MAINHAND, gun);
            // Make the gun enter Forge's drop collection reliably; the global
            // drop filter below is the sole 1% gate. Bound tier-3 gear remains
            // unconditionally removed.
            human.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
            // Keep one melee fallback for the custody-owned two-block retreat
            // counterattack. Do not retain an old bow/crossbow: the gun remains
            // the authoritative ranged weapon for this NPC.
            // The spawn pipeline provides reserves after all
            // providers have finished; this adapter only supplies the firearm.
            if (LOGGED_FIRST_EQUIP.compareAndSet(false, true)) {
                LOGGER.info("Human Gunner first successful additional firearm allocation: {}", human.getTier());
            }
            LOGGER.debug(
                    "Equipped npc={} tier={} with an additional TaCZ firearm",
                    human.getUUID(), human.getTier()
            );
    }
}

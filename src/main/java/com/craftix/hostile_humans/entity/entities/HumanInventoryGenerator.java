package com.craftix.hostile_humans.entity.entities;

import com.craftix.hostile_humans.HostileHumans;
import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.compat.TravelersBackpack;
import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import com.craftix.hostile_humans.entity.loadout.HumanLoadoutManager;
import club.someoneice.humangunner.RangedSpawnChance;
import club.someoneice.humangunner.TierThreeHuman;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public class HumanInventoryGenerator {
    public static void generateInventory(Human human, boolean forceRanged) {
        ItemStack bonusMainhand;
        HumanLoadoutManager.ArmorSetEntry armorSet;
        ItemStack backupWeapon;
        ItemStack offhand;
        RandomSource random = human.getRandom();
        if (human.getData() == null) {
            if (!human.level().isClientSide) {
                human.getPersistentData().putBoolean("hostile_humans:pending_loadout", true);
                human.getPersistentData().putBoolean("hostile_humans:pending_ranged", forceRanged);
            }
            return;
        }
        HumanLoadoutManager.HumanLoadout loadout = HumanLoadoutManager.get(human.getTier());
        if (loadout == null) {
            HumanInventoryGenerator.applyFallbackInventory(human, forceRanged);
            return;
        }
        // Spartan Weaponry supplies its own ranged roll later. Without it,
        // choose vanilla bows/crossbows explicitly instead of relying on the
        // melee pool's weights. Tier-three equipment is finalized separately.
        boolean useRanged = forceRanged || (!ModList.get().isLoaded("spartanweaponry")
                && !TierThreeHuman.isTierThree(human)
                && random.nextFloat() < RangedSpawnChance.forHuman(human));
        HumanLoadoutManager.ItemPool mainhandPool = useRanged && !loadout.rangedMainhand.isEmpty()
                ? loadout.rangedMainhand : loadout.mainhand;
        ItemStack mainhand = HumanInventoryGenerator.createStack((HumanLoadoutManager.ItemEntry)mainhandPool.roll(random), human, loadout.rules.damagePercentMin, loadout.rules.damagePercentMax);
        if (mainhand.isEmpty()) {
            HumanInventoryGenerator.applyFallbackInventory(human, forceRanged);
            return;
        }
        human.setItemSlot(EquipmentSlot.MAINHAND, mainhand);
        if (random.nextFloat() < loadout.offhand.chance && !(offhand = HumanInventoryGenerator.createStack((HumanLoadoutManager.ItemEntry)loadout.offhand.roll(random), human, loadout.rules.damagePercentMin, loadout.rules.damagePercentMax)).isEmpty()) {
            human.setItemSlot(EquipmentSlot.OFFHAND, offhand);
        }
        if (HumanUtil.isRangedWeapon(human.getItemBySlot(EquipmentSlot.MAINHAND)) && !(backupWeapon = HumanInventoryGenerator.createStack((HumanLoadoutManager.ItemEntry)loadout.inventory.roll(random), human, loadout.rules.damagePercentMin, loadout.rules.damagePercentMax)).isEmpty()) {
            backupWeapon.enchant(Enchantments.VANISHING_CURSE, 1);
            human.getData().setInventoryItem(0, backupWeapon);
        }
        if ((armorSet = (HumanLoadoutManager.ArmorSetEntry)loadout.armorSets.roll(random)) != null) {
            HumanInventoryGenerator.equipArmorSet(human, armorSet, loadout.rules.damagePercentMin, loadout.rules.damagePercentMax);
        }
        human.applySpawnedWeaponEnchantments(random, loadout.rules.enchantChance);
        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            if (equipmentSlot.getType() != EquipmentSlot.Type.ARMOR) continue;
            human.applySpawnedArmorEnchantments(random, loadout.rules.enchantChance, equipmentSlot);
        }
        if (!HumanUtil.isRangedWeapon(human.getMainHandItem())
                && random.nextFloat() < loadout.bonusMainhand.chance
                && !(bonusMainhand = HumanInventoryGenerator.createStack((HumanLoadoutManager.ItemEntry)loadout.bonusMainhand.roll(random), human, loadout.rules.damagePercentMin, loadout.rules.damagePercentMax)).isEmpty()) {
            human.setItemSlot(EquipmentSlot.MAINHAND, bonusMainhand);
        }
        if (ModList.get().isLoaded("travelersbackpack") && ModList.get().isLoaded("curios")) {
            TravelersBackpack.apply((LivingEntity)human);
        }
    }

    private static void equipArmorSet(Human human, HumanLoadoutManager.ArmorSetEntry armorSet, float damagePercentMin, float damagePercentMax) {
        HumanInventoryGenerator.equipArmorSlot(human, EquipmentSlot.HEAD, armorSet.head, damagePercentMin, damagePercentMax);
        HumanInventoryGenerator.equipArmorSlot(human, EquipmentSlot.CHEST, armorSet.chest, damagePercentMin, damagePercentMax);
        HumanInventoryGenerator.equipArmorSlot(human, EquipmentSlot.LEGS, armorSet.legs, damagePercentMin, damagePercentMax);
        HumanInventoryGenerator.equipArmorSlot(human, EquipmentSlot.FEET, armorSet.feet, damagePercentMin, damagePercentMax);
    }

    private static void equipArmorSlot(Human human, EquipmentSlot slot, ResourceLocation itemId, float damagePercentMin, float damagePercentMax) {
        if (itemId == null) {
            return;
        }
        Item item = (Item)ForgeRegistries.ITEMS.getValue(itemId);
        if (item != null && item != Items.AIR) {
            human.setItemSlot(slot, HumanInventoryGenerator.damage(human, item.getDefaultInstance(), damagePercentMin, damagePercentMax));
        }
    }

    private static ItemStack createStack(HumanLoadoutManager.ItemEntry entry, Human human, float damagePercentMin, float damagePercentMax) {
        if (entry == null) {
            return ItemStack.EMPTY;
        }
        Item item = (Item)ForgeRegistries.ITEMS.getValue(entry.itemId);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return HumanInventoryGenerator.damage(human, item.getDefaultInstance(), damagePercentMin, damagePercentMax);
    }

    private static void applyFallbackInventory(Human human, boolean forceRanged) {
        HostileHumans.LOGGER.warn("Missing human loadout for tier {}, using fallback defaults", human.getTier());
        ItemStack fallbackWeapon = forceRanged ? Items.BOW.getDefaultInstance() : (human.getTier() == HumanTier.LEVEL2 ? Items.DIAMOND_SWORD.getDefaultInstance() : Items.IRON_SWORD.getDefaultInstance());
        human.setItemSlot(EquipmentSlot.MAINHAND, HumanInventoryGenerator.damage(human, fallbackWeapon, 0.0f, 0.85f));
        if (!HumanUtil.isRangedWeapon(fallbackWeapon)) {
            human.setItemSlot(EquipmentSlot.OFFHAND, HumanInventoryGenerator.damage(human, Items.SHIELD.getDefaultInstance(), 0.0f, 0.85f));
        }
    }

    public static ItemStack damage(Human human, ItemStack inStack, float damagePercentMin, float damagePercentMax) {
        if (!inStack.isDamageableItem()) {
            return inStack;
        }
        float min = Math.max(0.0f, damagePercentMin);
        float max = Math.max(min, damagePercentMax);
        int maxDamage = Math.max(inStack.getMaxDamage() - 1, 1);
        float percent = min + human.getRandom().nextFloat() * (max - min);
        int damage = (int)((float)maxDamage * percent);
        inStack.setDamageValue(Math.min(damage, maxDamage));
        return inStack;
    }
}


package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/** Generates elite equipment without a hard dependency on Immersive Armors. */
public final class TierThreeLoadout {
    public static final String BOUND_GEAR = HumanGunner.MOD_ID + ":tier3_bound_gear";
    private static final String GENERATED = HumanGunner.MOD_ID + ":tier3_loadout_generated";
    // Highest raw defensive sets in Immersive Armors 1.7.2:
    // prismarine = 20 armor, heavy = 18 armor/4 toughness per piece,
    // divine = 18 armor. Exclude the visually elite but much weaker wither,
    // steampunk and warrior sets from tier-three generation.
    private static final List<String> IMMERSIVE_SETS = List.of("prismarine", "heavy", "divine");

    private TierThreeLoadout() {
    }

    public static boolean configureOnce(Human human) {
        if (!TierThreeHuman.isTierThree(human) || human.getPersistentData().getBoolean(GENERATED)) {
            return false;
        }
        human.getPersistentData().putBoolean(GENERATED, true);
        human.getPersistentData().putBoolean(TierThreeHuman.MARKER, true);
        human.team = "human_level3";

        HumanData data = human.getData();
        List<ItemStack> preservedShields = new ArrayList<>();
        if (data != null) {
            for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                ItemStack stored = data.getInventoryItem(i);
                if (SpartanEquipmentCompat.isShield(stored)) {
                    preservedShields.add(stored.copy());
                }
                data.setInventoryItem(i, ItemStack.EMPTY);
            }
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack equipped = human.getItemBySlot(slot);
            if (SpartanEquipmentCompat.isShield(equipped)) {
                preservedShields.add(equipped.copy());
            }
            human.setItemSlot(slot, ItemStack.EMPTY);
        }

        equipArmor(human);
        ItemStack melee = eliteMelee(human, human.getRandom().nextFloat() < 0.72F
                ? Items.NETHERITE_SWORD : Items.NETHERITE_AXE);
        human.setItemSlot(EquipmentSlot.MAINHAND, melee);
        human.setDropChance(EquipmentSlot.MAINHAND, 0.0F);

        ItemStack shield = enchanted(human, Items.SHIELD, 24, 32);
        ensureMinimumEnchantment(shield, Enchantments.UNBREAKING, 3);
        ensureMinimumEnchantment(shield, Enchantments.MENDING, 1);
        human.setItemSlot(EquipmentSlot.OFFHAND, shield);
        human.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
        preservePreviousShields(human, data, preservedShields);
        human.setCombatTask();
        return true;
    }

    /** Fill the new elite loadout after optional Spartan weapons have been rolled. */
    public static void ensureRangedWeapon(Human human) {
        HumanData data = human.getData();
        if (!TierThreeHuman.isTierThree(human) || data == null
                || RangedWeaponCustody.isBowOrCrossbow(human.getMainHandItem())
                || RangedWeaponCustody.isBowOrCrossbow(human.getOffhandItem())) {
            return;
        }
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            if (RangedWeaponCustody.isBowOrCrossbow(data.getInventoryItem(i))) {
                return;
            }
        }

        Item item = human.getRandom().nextBoolean() ? Items.BOW : Items.CROSSBOW;
        ItemStack ranged = enchanted(human, item, 34, 44);
        RangedWeaponCustody.registerPreferred(human, ranged);
        if (!HumanLootManager.storeWithEviction(human, ranged)) {
            HumanGunner.LOGGER.warn("Could not store guaranteed ranged weapon for tier-three human {}", human.getUUID());
        }
    }

    private static void preservePreviousShields(
            Human human,
            HumanData data,
            List<ItemStack> shields
    ) {
        if (shields.isEmpty()) {
            return;
        }
        for (ItemStack previous : shields) {
            boolean stored = false;
            if (data != null) {
                for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                    if (!data.getInventoryItem(i).isEmpty()) {
                        continue;
                    }
                    data.setInventoryItem(i, previous.copy());
                    stored = true;
                    break;
                }
            }
            if (!stored) {
                human.spawnAtLocation(previous.copy());
            }
        }
    }

    public static boolean isBoundGear(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().getBoolean(BOUND_GEAR);
    }

    private static void equipArmor(Human human) {
        int roll = human.getRandom().nextInt(100);
        if (roll < 62) {
            equipVanillaSet(human, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
                    Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS, 34, 44);
        } else {
            String set = IMMERSIVE_SETS.get(human.getRandom().nextInt(IMMERSIVE_SETS.size()));
            Item helmet = resolveImmersive(set + "_helmet", Items.NETHERITE_HELMET);
            Item chest = resolveImmersive(set + "_chestplate", Items.NETHERITE_CHESTPLATE);
            Item legs = resolveImmersive(set + "_leggings", Items.NETHERITE_LEGGINGS);
            Item boots = resolveImmersive(set + "_boots", Items.NETHERITE_BOOTS);
            equipVanillaSet(human, helmet, chest, legs, boots, 34, 44);
        }
    }

    private static void equipVanillaSet(
            Human human, Item helmet, Item chest, Item legs, Item boots, int minEnchant, int maxEnchant
    ) {
        equip(human, EquipmentSlot.HEAD, eliteArmor(human, helmet, minEnchant, maxEnchant));
        equip(human, EquipmentSlot.CHEST, eliteArmor(human, chest, minEnchant, maxEnchant));
        equip(human, EquipmentSlot.LEGS, eliteArmor(human, legs, minEnchant, maxEnchant));
        equip(human, EquipmentSlot.FEET, eliteArmor(human, boots, minEnchant, maxEnchant));
    }

    private static void equip(Human human, EquipmentSlot slot, ItemStack stack) {
        human.setItemSlot(slot, stack);
        human.setDropChance(slot, 0.0F);
    }

    private static ItemStack enchanted(Human human, Item item, int minimum, int maximum) {
        int level = human.getRandom().nextInt(minimum, maximum + 1);
        return marked(EnchantmentHelper.enchantItem(human.getRandom(), new ItemStack(item), level, false));
    }

    private static ItemStack eliteArmor(Human human, Item item, int minimum, int maximum) {
        ItemStack stack = enchanted(human, item, minimum, maximum);
        ensureMinimumEnchantment(
                stack,
                Enchantments.ALL_DAMAGE_PROTECTION,
                human.getRandom().nextFloat() < 0.45F ? 4 : 3
        );
        ensureMinimumEnchantment(stack, Enchantments.UNBREAKING, 3);
        if (human.getRandom().nextFloat() < 0.35F) {
            ensureMinimumEnchantment(stack, Enchantments.MENDING, 1);
        }
        return stack;
    }

    private static ItemStack eliteMelee(Human human, Item item) {
        ItemStack stack = enchanted(human, item, 38, 46);
        ensureMinimumEnchantment(
                stack,
                Enchantments.SHARPNESS,
                human.getRandom().nextFloat() < 0.65F ? 5 : 4
        );
        ensureMinimumEnchantment(stack, Enchantments.UNBREAKING, 3);
        ensureMinimumEnchantment(stack, Enchantments.MENDING, 1);
        return stack;
    }

    private static void ensureMinimumEnchantment(ItemStack stack, Enchantment enchantment, int level) {
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
        if (enchantments.getOrDefault(enchantment, 0) >= level) {
            return;
        }
        enchantments.put(enchantment, level);
        EnchantmentHelper.setEnchantments(enchantments, stack);
    }

    private static ItemStack marked(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(BOUND_GEAR, true);
        return stack;
    }

    private static Item resolveImmersive(String path, Item fallback) {
        ResourceLocation id = new ResourceLocation("immersive_armors", path);
        return BuiltInRegistries.ITEM.getOptional(id).orElse(fallback);
    }
}

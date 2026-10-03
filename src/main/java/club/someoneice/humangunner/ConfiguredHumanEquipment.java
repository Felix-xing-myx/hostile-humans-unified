package club.someoneice.humangunner;

import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Sole config-to-equipment generation module, shared by all four ranks. */
final class ConfiguredHumanEquipment {
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private ConfiguredHumanEquipment() { }
    static ConfiguredEquipmentLoadout of(Human human) {
        return UnifiedConfig.get().equipmentLoadout(TierAttributes.key(human));
    }

    static void generate(Human human, boolean forceRanged, boolean nativeRangedRoll) {
        var loadout = of(human);
        var random = human.getRandom();
        if (TierThreeHuman.isTierThree(human)) {
            human.getPersistentData().putBoolean(TierThreeHuman.MARKER, true);
            human.team = "human_level3";
        }
        boolean useRanged = !loadout.rangedInBackpack && nativeRangedRoll
                && HumanSpawnEquipment.wantsRanged(human);
        var entry = useRanged ? rangedEntry(human) : loadout.mainhand.roll(random, e -> {
            ItemStack candidate = new ItemStack(ForgeRegistries.ITEMS.getValue(e.id()));
            return !GunSupport.get().isGun(candidate)
                    && (nativeRangedRoll || (weapon(candidate) && !nonGunRanged(candidate)));
        });
        ItemStack main = stack(human, entry, EquipmentSlot.MAINHAND);
        if (main.isEmpty()) {
            warn(TierAttributes.key(human) + ".mainhand", "No available configured weapon; using safe fallback");
            main = damage(human, new ItemStack(useRanged || (nativeRangedRoll && forceRanged) ? Items.BOW : fallbackMelee(human)));
        }
        equip(human, EquipmentSlot.MAINHAND, main);
        if (random.nextDouble() < loadout.offhandChance) {
            ItemStack offhand = stack(human, loadout.offhand.roll(random), EquipmentSlot.OFFHAND);
            int limit = loadout.weaponMax - (nativeRangedRoll ? 0 : 1);
            if (!weapon(offhand) || weaponCount(human) < limit) equip(human, EquipmentSlot.OFFHAND, offhand);
        }
        if (nonGunRanged(main) && weaponCount(human) < loadout.weaponMax) {
            ItemStack backup = stack(human, meleeEntry(human), EquipmentSlot.MAINHAND);
            if (!backup.isEmpty()) {
                backup.enchant(Enchantments.VANISHING_CURSE, 1);
                HumanLootManager.storeWithEviction(human, backup);
            }
        }
        var armor = loadout.armorSets.roll(random);
        if (armor != null) {
            armor(human, EquipmentSlot.HEAD, armor.head());
            armor(human, EquipmentSlot.CHEST, armor.chest());
            armor(human, EquipmentSlot.LEGS, armor.legs());
            armor(human, EquipmentSlot.FEET, armor.feet());
        }
        if (!loadout.rules.eliteEnchantments()) {
            human.applySpawnedWeaponEnchantments(random, loadout.rules.enchantChance());
            for (EquipmentSlot slot : EquipmentSlot.values())
                if (slot.getType() == EquipmentSlot.Type.ARMOR)
                    human.applySpawnedArmorEnchantments(random, loadout.rules.enchantChance(), slot);
        }
        applyEntryEnchantments(human.getMainHandItem(), entry);
    }

    /** Final stored-ranged roll follows optional adapters, so an existing ranged reserve is not duplicated. */
    static void finishRanged(Human human, boolean forceRanged) {
        var loadout = of(human);
        if (GunCustody.hasOwnedGun(human)) return;
        if (!loadout.rangedInBackpack || hasRanged(human) || weaponCount(human) >= loadout.weaponMax) return;
        if (!forceRanged && !HumanSpawnEquipment.wantsRanged(human)) return;
        ItemStack ranged = stack(human, rangedEntry(human), EquipmentSlot.MAINHAND);
        if (ranged.isEmpty()) ranged = damage(human, new ItemStack(Items.BOW));
        RangedWeaponCustody.registerPreferred(human, ranged);
        HumanLootManager.storeWithEviction(human, ranged);
    }

    private static boolean hasRanged(Human human) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack item = human.getItemBySlot(slot);
            if (nonGunRanged(item)) return true;
        }
        var data = human.getData();
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack item = data.getInventoryItem(i);
            if (nonGunRanged(item)) return true;
        }
        return false;
    }
    private static ConfiguredEquipmentLoadout.ItemEntry rangedEntry(Human human) {
        return of(human).rangedMainhand.roll(human.getRandom(), entry -> {
            ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(entry.id()));
            return nonGunRanged(stack);
        });
    }
    private static ConfiguredEquipmentLoadout.ItemEntry meleeEntry(Human human) {
        return of(human).inventory.roll(human.getRandom(), entry ->
                HumanLootManager.isDedicatedMeleeWeapon(new ItemStack(ForgeRegistries.ITEMS.getValue(entry.id()))));
    }
    static ItemStack backupMelee(Human human) {
        ItemStack configured = stack(human, meleeEntry(human), EquipmentSlot.MAINHAND);
        return configured.isEmpty() ? damage(human, new ItemStack(fallbackMelee(human))) : configured;
    }
    static ItemStack shield(Human human) {
        var entry = of(human).shields.roll(human.getRandom(), e ->
                SpartanEquipmentCompat.isShield(new ItemStack(ForgeRegistries.ITEMS.getValue(e.id()))));
        ItemStack shield = stack(human, entry, EquipmentSlot.OFFHAND);
        return shield;
    }

    static void generateReserves(Human human) {
        var loadout = of(human);
        int target = SpawnWeaponCountPolicy.roll(loadout.weaponMax, loadout.secondWeaponChance,
                loadout.thirdWeaponChance, human.getRandom().nextDouble(), human.getRandom().nextDouble());
        int owned = weaponCount(human);
        while (owned < target) {
            var entry = human.getRandom().nextDouble() < loadout.bonusChance
                    ? loadout.bonusMainhand.roll(human.getRandom(), e ->
                        weapon(new ItemStack(ForgeRegistries.ITEMS.getValue(e.id())))) : null;
            if (entry == null) entry = loadout.inventory.roll(human.getRandom(), e ->
                    HumanLootManager.isDedicatedMeleeWeapon(new ItemStack(ForgeRegistries.ITEMS.getValue(e.id()))));
            ItemStack spare = stack(human, entry, EquipmentSlot.MAINHAND);
            // An intentionally empty reserve pool must not manufacture gear.
            if (spare.isEmpty()) break;
            spare.setCount(1);
            if (!HumanLootManager.storeWithEviction(human, spare)) break;
            owned++;
        }
    }

    static int weaponCount(Human human) {
        int owned = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (weapon(human.getItemBySlot(slot))) owned += human.getItemBySlot(slot).getCount();
        var data = human.getData();
        for (int i = 0; i < data.getInventoryItemsSize(); i++)
            if (weapon(data.getInventoryItem(i))) owned += data.getInventoryItem(i).getCount();
        return owned;
    }

    private static boolean weapon(ItemStack stack) {
        return GunSupport.get().isGun(stack) || nonGunRanged(stack)
                || stack.getItem() instanceof net.minecraft.world.item.DiggerItem
                || HumanLootManager.isDedicatedMeleeWeapon(stack);
    }
    private static void armor(Human human, EquipmentSlot slot, ResourceLocation id) {
        if (id != null) equip(human, slot, enchant(human, damage(human,
                new ItemStack(ForgeRegistries.ITEMS.getValue(id))), slot));
    }
    private static ItemStack stack(Human human, ConfiguredEquipmentLoadout.ItemEntry entry, EquipmentSlot slot) {
        if (entry == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(entry.id());
        if (item == null || item == Items.AIR || GunSupport.get().isGun(new ItemStack(item))) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, Math.min(entry.count(), item.getMaxStackSize()));
        if (weapon(stack)) stack.setCount(1);
        stack = enchant(human, damage(human, stack), slot);
        applyEntryEnchantments(stack, entry);
        if (RangedWeaponCustody.isBowOrCrossbow(stack)) RangedWeaponCustody.registerPreferred(human, stack);
        return stack;
    }
    private static ItemStack damage(Human human, ItemStack stack) {
        var rules = of(human).rules;
        if (stack.isDamageableItem()) {
            int maximum = Math.max(stack.getMaxDamage() - 1, 1);
            float percent = rules.damageMin() + human.getRandom().nextFloat()
                    * (rules.damageMax() - rules.damageMin());
            stack.setDamageValue(Math.min((int) (maximum * percent), maximum));
        }
        if (TierThreeHuman.isTierThree(human)) stack.getOrCreateTag().putBoolean(HumanSpawnEquipment.BOUND_GEAR, true);
        return stack;
    }
    private static ItemStack enchant(Human human, ItemStack stack, EquipmentSlot slot) {
        var rules = of(human).rules;
        if (!rules.eliteEnchantments() || stack.isEmpty() || human.getRandom().nextFloat() >= rules.enchantChance()) return stack;
        boolean armor = slot.getType() == EquipmentSlot.Type.ARMOR;
        boolean shield = SpartanEquipmentCompat.isShield(stack);
        boolean melee = HumanUtil.isMeleeWeapon(stack);
        int low = armor ? 34 : shield ? 24 : melee ? 38 : 34;
        int high = armor ? 44 : shield ? 32 : melee ? 46 : 44;
        stack = EnchantmentHelper.enchantItem(human.getRandom(), stack, human.getRandom().nextInt(low, high + 1), false);
        minimum(stack, Enchantments.UNBREAKING, 3);
        if (armor) {
            minimum(stack, Enchantments.ALL_DAMAGE_PROTECTION, human.getRandom().nextFloat() < .45F ? 4 : 3);
            if (human.getRandom().nextFloat() < .35F) minimum(stack, Enchantments.MENDING, 1);
        } else if (melee) {
            minimum(stack, Enchantments.SHARPNESS, human.getRandom().nextFloat() < .65F ? 5 : 4);
            minimum(stack, Enchantments.MENDING, 1);
        } else if (shield) minimum(stack, Enchantments.MENDING, 1);
        return stack;
    }
    private static void minimum(ItemStack stack, Enchantment enchantment, int level) {
        if (!enchantment.canEnchant(stack)) return;
        Map<Enchantment, Integer> values = EnchantmentHelper.getEnchantments(stack);
        values.put(enchantment, Math.max(level, values.getOrDefault(enchantment, 0)));
        EnchantmentHelper.setEnchantments(values, stack);
    }
    private static void applyEntryEnchantments(ItemStack stack, ConfiguredEquipmentLoadout.ItemEntry entry) {
        if (entry == null || stack.isEmpty()) return;
        Map<Enchantment, Integer> values = EnchantmentHelper.getEnchantments(stack);
        for (var e : entry.enchantments().entrySet()) {
            Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(e.getKey());
            if (enchantment != null && enchantment.canEnchant(stack)) values.put(enchantment, e.getValue());
            else warn(e.getKey().toString(), "Missing or inapplicable configured enchantment");
        }
        EnchantmentHelper.setEnchantments(values, stack);
    }
    private static void equip(Human human, EquipmentSlot slot, ItemStack stack) {
        if (stack.isEmpty()) return;
        human.setItemSlot(slot, stack);
        if (TierThreeHuman.isTierThree(human)) human.setDropChance(slot, 0);
    }
    private static Item fallbackMelee(Human human) {
        return TierThreeHuman.isTierThree(human) ? Items.NETHERITE_SWORD
                : TierAttributes.key(human).equals("tier2") ? Items.DIAMOND_SWORD : Items.IRON_SWORD;
    }
    private static boolean nonGunRanged(ItemStack stack) {
        return !GunSupport.get().isGun(stack)
                && (RangedWeaponCustody.isBowOrCrossbow(stack) || HumanUtil.isTrident(stack));
    }
    private static void warn(String key, String message) {
        if (WARNED.add(key)) HumanGunner.LOGGER.warn("{}: {}", message, key);
    }
}

package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** One final pass after native and optional-mod loadouts, before combat goals start. */
public final class NaturalEquipmentGrowth {
    private static final String DAY = "humangunner:equipment_spawn_day";
    private static final String APPLIED = "humangunner:equipment_growth_applied";
    private static final String GUN_MULTIPLIER = "humangunner:spawn_gun_chance_multiplier";
    private static final Set<String> KEPT_MARKERS = Set.of(HumanSpawnEquipment.BOUND_GEAR,
            "humangunner:durability_enchantment_rolled", "humangunner:primary_bow_owner");
    private static final Set<String> WARNED_IDS = ConcurrentHashMap.newKeySet();

    private NaturalEquipmentGrowth() { }

    static void excludeRequestedSpawn(Human human) {
        human.getPersistentData().remove(DAY);
        human.getPersistentData().remove(GUN_MULTIPLIER);
        human.getPersistentData().remove(APPLIED);
    }

    /** Called before loadout generation, and only for natural/automatic-battle spawns. */
    public static void markSpawn(Human human, ServerLevelAccessor level) {
        if (human.getPersistentData().contains(DAY)) return;
        long day = NaturalSpawnProgression.dayAt(Math.max(0L,
                PlayerProgressionData.timeNear(level, human.blockPosition())));
        human.getPersistentData().putLong(DAY, day);
        var settings = UnifiedConfig.get().equipmentGrowth();
        var profile = settings.tiers().get(TierAttributes.key(human));
        human.getPersistentData().putDouble(GUN_MULTIPLIER,
                settings.enabled() ? profile.grow(profile.earlyGunChanceMultiplier(), day) : 1.0D);
    }

    static double gunChanceMultiplier(Human human) {
        // Existing entities and requested summons have no growth snapshot.
        if (!UnifiedConfig.get().equipmentGrowth().enabled()
                || !human.getPersistentData().contains(GUN_MULTIPLIER)) return 1.0D;
        double value = human.getPersistentData().getDouble(GUN_MULTIPLIER);
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(1.0D, value)) : 1.0D;
    }

    static boolean isNaturalSpawn(Human human) {
        return human.getPersistentData().contains(DAY);
    }

    static void applyOnce(Human human) {
        var tag = human.getPersistentData();
        if (!tag.contains(DAY) || tag.getBoolean(APPLIED) || human.getData() == null) return;
        var settings = UnifiedConfig.get().equipmentGrowth();
        if (!settings.enabled() || human.hasOwner() || tag.hasUUID(HumanRelations.TEMP_OWNER)) {
            tag.putBoolean(APPLIED, true);
            return;
        }
        var profile = settings.tiers().get(TierAttributes.key(human));
        long day = tag.getLong(DAY);
        if (profile.progress(day) >= 1.0D) {
            tag.putBoolean(APPLIED, true);
            return; // Exact original equipment, no extra random rolls.
        }

        // One material-quality roll preserves coherent armor sets. Enchantments
        // are scaled separately on every physical equipped AND backpack stack.
        boolean baseline = human.getRandom().nextDouble() < profile.grow(profile.earlyBaselineChance(), day);
        EquipmentSlot[] slots = EquipmentSlot.values();
        ItemStack[] worn = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) {
            worn[i] = adjust(human, human.getItemBySlot(slots[i]), slots[i], profile, day, baseline);
        }
        var data = human.getData();
        ItemStack[] stored = new ItemStack[data.getInventoryItemsSize()];
        for (int i = 0; i < stored.length; i++) {
            ItemStack before = data.getInventoryItem(i);
            EquipmentSlot slot = before.getItem() instanceof ArmorItem armor ? armor.getEquipmentSlot() : null;
            stored[i] = adjust(human, before, slot, profile, day, baseline);
        }
        // Finish all registry/enchantment validation before committing any slot.
        for (int i = 0; i < slots.length; i++) {
            if (worn[i] != human.getItemBySlot(slots[i])) human.setItemSlot(slots[i], worn[i]);
        }
        for (int i = 0; i < stored.length; i++) {
            if (stored[i] != data.getInventoryItem(i)) data.setInventoryItem(i, stored[i]);
        }
        tag.putBoolean(APPLIED, true);
    }

    private static ItemStack adjust(Human human, ItemStack original, EquipmentSlot slot,
            EquipmentGrowthPolicy.Profile profile, long day, boolean baseline) {
        if (original.isEmpty() || GunSupport.get().isGun(original)) return original;
        boolean shield = SpartanEquipmentCompat.isShield(original);
        boolean ranged = RangedWeaponCustody.isBowOrCrossbow(original);
        boolean melee = HumanLootManager.isDedicatedMeleeWeapon(original);
        boolean armor = original.getItem() instanceof ArmorItem;
        boolean trident = original.getItem() instanceof TridentItem;
        if (original.is(Items.TOTEM_OF_UNDYING)) {
            return human.getRandom().nextDouble() < profile.grow(profile.earlyTotemKeepChance(), day)
                    ? original : ItemStack.EMPTY;
        }
        if (!shield && !ranged && !melee && !armor && !trident) return original;

        Item item = original.getItem();
        if (trident && human.getRandom().nextDouble() >= profile.grow(profile.earlyTridentKeepChance(), day)) {
            item = Items.BOW; // Keep the ranged capability, without an early trident.
        } else if (!baseline) {
            if (armor && slot != null) {
                String id = profile.earlyArmor().get(slot.getName());
                Item candidate = configuredItem(id, item);
                if (candidate instanceof ArmorItem replacement && replacement.getEquipmentSlot() == slot) item = candidate;
                else warn(id, "not armor for " + slot.getName());
            } else if (SpartanEquipmentCompat.isSpartanMeleeWeapon(original)
                    || SpartanEquipmentCompat.isSpartanRangedWeapon(original)
                    || (shield && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("spartanshields"))) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                String path = id.getPath();
                int separator = path.indexOf('_');
                Item fallback = shield ? Items.SHIELD : ranged
                        ? (RangedWeaponCustody.isCrossbowWeapon(original) ? Items.CROSSBOW : Items.BOW)
                        : configuredMelee(profile.earlyMelee().get("sword"), Items.STONE_SWORD);
                item = separator < 0 ? fallback : resolve(id.getNamespace() + ":"
                        + profile.spartanMaterial() + path.substring(separator), fallback);
            } else if (melee && !trident) {
                item = configuredMelee(profile.earlyMelee().get(original.getItem() instanceof AxeItem ? "axe" : "sword"), item);
            }
        }
        // Early substitutions must not upgrade an already weaker stock item.
        if (armor && item instanceof ArmorItem next && original.getItem() instanceof ArmorItem old
                && (next.getDefense() > old.getDefense() || next.getToughness() > old.getToughness())) {
            item = original.getItem();
        } else if (melee && !trident && meleeDamage(item) > meleeDamage(original.getItem())) {
            item = original.getItem();
        } else if (shield && item.getMaxDamage() > original.getMaxDamage()) {
            item = original.getItem();
        }

        ItemStack output = item == original.getItem() ? original.copy() : new ItemStack(item, original.getCount());
        if (item != original.getItem()) {
            if (original.isDamageableItem() && output.isDamageableItem()) {
                double wear = (double) original.getDamageValue() / original.getMaxDamage();
                output.setDamageValue(Math.min(output.getMaxDamage() - 1, (int) (wear * output.getMaxDamage())));
            }
            // Do not carry another item's custom attributes, ammunition or item-specific NBT.
            if (original.hasTag()) for (String key : KEPT_MARKERS) {
                if (original.getTag().contains(key)) output.getOrCreateTag().put(key, original.getTag().get(key).copy());
            }
        }
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        double keep = profile.grow(profile.earlyEnchantmentKeepChance(), day);
        double level = profile.grow(profile.earlyEnchantmentLevelMultiplier(), day);
        for (var entry : EnchantmentHelper.getEnchantments(original).entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (enchantment.isCurse()) {
                enchantments.put(enchantment, entry.getValue());
            } else if (enchantment.canEnchant(output) && human.getRandom().nextDouble() < keep) {
                int scaled = (int) Math.floor(entry.getValue() * level);
                if (scaled > 0) enchantments.put(enchantment, Math.min(scaled, entry.getValue()));
            }
        }
        EnchantmentHelper.setEnchantments(enchantments, output);
        if (RangedWeaponCustody.isBowOrCrossbow(output)) RangedWeaponCustody.registerPreferred(human, output);
        return output;
    }

    private static double meleeDamage(Item item) {
        return item.getDefaultInstance().getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE).stream().mapToDouble(modifier -> modifier.getAmount()).sum();
    }

    private static Item resolve(String id, Item fallback) {
        ResourceLocation key = id == null ? null : ResourceLocation.tryParse(id);
        if (key == null) return fallback;
        Item item = BuiltInRegistries.ITEM.getOptional(key).orElse(fallback);
        return item == Items.AIR ? fallback : item;
    }

    private static Item configuredItem(String id, Item fallback) {
        ResourceLocation key = id == null ? null : ResourceLocation.tryParse(id);
        Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
        if (item == Items.AIR) {
            warn(id, "missing, invalid or air item");
            return fallback;
        }
        return item;
    }

    private static Item configuredMelee(String id, Item fallback) {
        Item item = configuredItem(id, fallback);
        if (HumanLootManager.isDedicatedMeleeWeapon(item.getDefaultInstance())) return item;
        warn(id, "not a melee weapon");
        return fallback;
    }

    private static void warn(String id, String reason) {
        if (WARNED_IDS.add(String.valueOf(id))) HumanGunner.LOGGER.warn(
                "Ignoring equipment-growth item {} ({}); retaining fallback gear", id, reason);
    }
}

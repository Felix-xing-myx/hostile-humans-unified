package club.someoneice.humangunner;

import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.fml.ModList;

import java.util.List;
import java.util.Set;

/** Optional registry-only integration: no hard class dependency on either Spartan mod. */
public final class SpartanEquipmentCompat {
    private static final String GENERATED = HumanGunner.MOD_ID + ":spartan_loadout_generated";
    private static final TagKey<Item> BASIC_SHIELDS = itemTag("spartanshields", "basic_shields");
    private static final TagKey<Item> TOWER_SHIELDS = itemTag("spartanshields", "tower_shields");
    private static final Set<String> SAFE_MELEE_TYPES = Set.of(
            "dagger", "longsword", "katana", "saber", "rapier", "spear",
            "battleaxe", "flanged_mace", "warhammer", "glaive", "scythe"
    );
    private static final List<String> LIGHT_WEAPONS = List.of(
            "dagger", "longsword", "saber", "rapier", "spear", "flanged_mace"
    );
    private static final List<String> HEAVY_WEAPONS = List.of(
            "longsword", "katana", "saber", "rapier", "spear", "battleaxe",
            "flanged_mace", "warhammer", "glaive", "scythe"
    );

    private SpartanEquipmentCompat() {
    }

    public static void applyOnce(Human human) {
        boolean weaponryLoaded = ModList.get().isLoaded("spartanweaponry");
        boolean shieldsLoaded = ModList.get().isLoaded("spartanshields");
        if ((!weaponryLoaded && !shieldsLoaded)
                || human.getPersistentData().getBoolean(GENERATED)) {
            return;
        }
        human.getPersistentData().putBoolean(GENERATED, true);
        Profile profile = profileFor(human);
        if (weaponryLoaded && human.getRandom().nextFloat() < profile.weaponChance()) {
            createWeapon(human, profile);
        }
        if (weaponryLoaded && human.getRandom().nextFloat() < RangedSpawnChance.forHuman(human)) {
            createRangedWeapon(human, profile);
        }
        if (shieldsLoaded && human.getRandom().nextFloat() < profile.shieldChance()) {
            createShield(human, profile);
        }
    }

    public static boolean isShield(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof ShieldItem
                || stack.canPerformAction(ToolActions.SHIELD_BLOCK)
                || stack.is(BASIC_SHIELDS)
                || stack.is(TOWER_SHIELDS)) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("spartanshields") && id.getPath().endsWith("_shield");
    }

    public static boolean isSpartanMeleeWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getNamespace().equals("spartanweaponry")) {
            return false;
        }
        String path = id.getPath();
        return SAFE_MELEE_TYPES.stream().anyMatch(type -> path.endsWith("_" + type));
    }

    /** Registry-only recognition keeps Spartan Weaponry a fully optional dependency. */
    public static boolean isSpartanRangedWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("spartanweaponry")
                && (id.getPath().endsWith("_longbow")
                || id.getPath().endsWith("_heavy_crossbow"));
    }

    /**
     * Shields are strategic equipment rather than ordinary inventory filler.
     * The large base value keeps them above food, potions and normal weapons;
     * durability and enchantments still decide which shield is preferred.
     */
    public static double shieldValue(ItemStack shield) {
        if (!isShield(shield)) {
            return 0.0D;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(shield.getItem());
        double durabilityRatio = shield.isDamageableItem()
                ? Math.max(0.0D, 1.0D - (double) shield.getDamageValue() / shield.getMaxDamage())
                : 1.0D;
        int enchantmentLevels = EnchantmentHelper.getEnchantments(shield).values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        double score = 220.0D
                + durabilityRatio * 55.0D
                + Math.min(40.0D, shield.getMaxDamage() * 0.025D)
                + enchantmentLevels * 12.0D;
        if (id.getNamespace().equals("spartanshields")) {
            score += 70.0D;
            if (id.getPath().contains("tower_shield")) {
                score += 25.0D;
            }
        }
        return score;
    }

    private static void createWeapon(Human human, Profile profile) {
        List<String> types = profile.eliteTypes() ? HEAVY_WEAPONS : LIGHT_WEAPONS;
        String material = profile.materials().get(human.getRandom().nextInt(profile.materials().size()));
        String type = types.get(human.getRandom().nextInt(types.size()));
        Item item = resolve("spartanweaponry", material + "_" + type);
        if (item == Items.AIR) {
            return;
        }
        ItemStack weapon = new ItemStack(item);
        if (profile.enchantMaximum() > 0) {
            int level = human.getRandom().nextInt(profile.enchantMinimum(), profile.enchantMaximum() + 1);
            weapon = EnchantmentHelper.enchantItem(human.getRandom(), weapon, level, false);
        }
        markTierThree(human, weapon);

        ItemStack current = human.getMainHandItem();
        if (current.isEmpty() || HumanUtil.isMeleeWeapon(current)) {
            if (!current.isEmpty()) {
                human.putItemAway(current.copy());
            }
            human.setItemSlot(EquipmentSlot.MAINHAND, weapon);
            human.setDropChance(EquipmentSlot.MAINHAND, TierThreeHuman.isTierThree(human) ? 0.0F : 0.2F);
            return;
        }
        replaceStoredMeleeOrInsert(human, weapon);
    }

    private static void createShield(Human human, Profile profile) {
        ItemStack shield = createShieldFor(human);
        if (shield.isEmpty()) {
            return;
        }
        equipOrStoreShield(human, shield);
    }

    private static void createRangedWeapon(Human human, Profile profile) {
        String material = profile.materials().get(human.getRandom().nextInt(profile.materials().size()));
        if (material.equals("stone")) {
            material = "wooden";
        }
        String type = human.getRandom().nextBoolean() ? "longbow" : "heavy_crossbow";
        Item item = resolve("spartanweaponry", material + "_" + type);
        if (item == Items.AIR) {
            item = resolve("spartanweaponry", "wooden_" + type);
        }
        if (item == Items.AIR) {
            item = type.equals("longbow") ? Items.BOW : Items.CROSSBOW;
        }
        ItemStack ranged = new ItemStack(item);
        if (profile.enchantMaximum() > 0) {
            int level = human.getRandom().nextInt(profile.enchantMinimum(), profile.enchantMaximum() + 1);
            ranged = EnchantmentHelper.enchantItem(human.getRandom(), ranged, level, false);
        }
        markTierThree(human, ranged);
        RangedWeaponCustody.registerPreferred(human, ranged);
        HumanLootManager.storeWithEviction(human, ranged);
    }

    /** Creates the tier-appropriate shield, falling back to vanilla. */
    static ItemStack createShieldFor(Human human) {
        Profile profile = profileFor(human);
        Item item = Items.SHIELD;
        if (bothModsLoaded()) {
        String material = profile.materials().get(human.getRandom().nextInt(profile.materials().size()));
        boolean tower = human.getRandom().nextFloat() < profile.towerShieldChance();
            Item resolved = resolve("spartanshields", material + (tower ? "_tower_shield" : "_basic_shield"));
            if (resolved != Items.AIR) {
                item = resolved;
            }
        }
        ItemStack shield = new ItemStack(item);
        markTierThree(human, shield);
        return shield;
    }

    static void equipOrStoreShield(Human human, ItemStack shield) {
        ItemStack offhand = human.getOffhandItem();
        if (!isShield(offhand)) {
            if (!offhand.isEmpty()) {
                human.putItemAway(offhand.copy());
            }
            human.setItemSlot(EquipmentSlot.OFFHAND, shield);
            human.setDropChance(EquipmentSlot.OFFHAND, 1.0F);
        } else {
            HumanLootManager.storeWithEviction(human, shield.copy());
        }
    }

    private static void replaceStoredMeleeOrInsert(Human human, ItemStack weapon) {
        HumanData data = human.getData();
        if (data == null) {
            return;
        }
        int empty = -1;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stored = data.getInventoryItem(i);
            if (HumanUtil.isMeleeWeapon(stored)) {
                data.setInventoryItem(i, weapon);
                return;
            }
            if (empty < 0 && stored.isEmpty()) {
                empty = i;
            }
        }
        if (empty >= 0) {
            data.setInventoryItem(empty, weapon);
        }
    }

    private static Profile profileFor(Human human) {
        if (TierThreeHuman.isTierThree(human)) {
            return new Profile(1.0F, 0.85F, 0.65F,
                    List.of("diamond", "netherite", "netherite"), 34, 44, true);
        }
        return switch (human.getTier()) {
            case LEVEL2 -> new Profile(0.62F, 0.48F, 0.45F,
                    List.of("iron", "diamond", "diamond"), 18, 28, true);
            case LEVEL1 -> new Profile(0.42F, 0.30F, 0.25F,
                    List.of("stone", "copper", "iron", "iron"), 8, 16, false);
            case ROAMER -> new Profile(0.28F, 0.18F, 0.12F,
                    List.of("wooden", "stone", "copper", "copper"), 3, 9, false);
            default -> new Profile(0.0F, 0.0F, 0.0F, List.of("stone"), 0, 0, false);
        };
    }

    private static void markTierThree(Human human, ItemStack stack) {
        if (TierThreeHuman.isTierThree(human)) {
            stack.getOrCreateTag().putBoolean(TierThreeLoadout.BOUND_GEAR, true);
        }
    }

    private static Item resolve(String namespace, String path) {
        return BuiltInRegistries.ITEM.getOptional(new ResourceLocation(namespace, path)).orElse(Items.AIR);
    }

    private static boolean bothModsLoaded() {
        return ModList.get().isLoaded("spartanweaponry") && ModList.get().isLoaded("spartanshields");
    }

    private static TagKey<Item> itemTag(String namespace, String path) {
        return TagKey.create(Registries.ITEM, new ResourceLocation(namespace, path));
    }

    private record Profile(
            float weaponChance,
            float shieldChance,
            float towerShieldChance,
            List<String> materials,
            int enchantMinimum,
            int enchantMaximum,
            boolean eliteTypes
    ) {
    }
}

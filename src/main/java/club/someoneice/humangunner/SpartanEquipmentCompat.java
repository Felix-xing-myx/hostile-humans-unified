package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.ToolActions;
import java.util.Set;

/** Recognition and custody only; all spawn choices belong to configuration pools. */
public final class SpartanEquipmentCompat {
    private static final TagKey<Item> BASIC_SHIELDS = itemTag("basic_shields");
    private static final TagKey<Item> TOWER_SHIELDS = itemTag("tower_shields");
    private static final Set<String> MELEE_TYPES = Set.of("dagger", "longsword", "katana", "saber",
            "rapier", "spear", "battleaxe", "flanged_mace", "warhammer", "glaive", "scythe");
    private SpartanEquipmentCompat() { }

    public static boolean isShield(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof ShieldItem || stack.canPerformAction(ToolActions.SHIELD_BLOCK)
                || stack.is(BASIC_SHIELDS) || stack.is(TOWER_SHIELDS)) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("spartanshields") && id.getPath().endsWith("_shield");
    }

    public static boolean isSpartanMeleeWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("spartanweaponry")
                && MELEE_TYPES.stream().anyMatch(type -> id.getPath().endsWith("_" + type));
    }

    public static boolean isSpartanRangedWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("spartanweaponry")
                && (id.getPath().endsWith("_longbow") || id.getPath().endsWith("_heavy_crossbow"));
    }

    /** Stable equipment ordering: maximum durability only, never remaining durability or mod name. */
    public static long shieldEquipRank(ItemStack shield) {
        if (!isShield(shield)) return Long.MIN_VALUE;
        boolean canBlock = shield.getItem() instanceof ShieldItem
                || shield.canPerformAction(ToolActions.SHIELD_BLOCK)
                || shield.getUseAnimation() == net.minecraft.world.item.UseAnim.BLOCK;
        if (!shield.isDamageableItem() && canBlock) return Long.MAX_VALUE;
        return Math.max(0, shield.getMaxDamage());
    }

    public static boolean betterShield(ItemStack candidate, ItemStack current) {
        return isShield(candidate) && shieldEquipRank(candidate) > shieldEquipRank(current);
    }

    public static double shieldValue(ItemStack shield) {
        if (!isShield(shield)) return 0;
        int levels = EnchantmentHelper.getEnchantments(shield).values().stream().mapToInt(Integer::intValue).sum();
        return ShieldQualityPolicy.fullValue(shield.getMaxDamage(), levels,
                shieldEquipRank(shield) == Long.MAX_VALUE);
    }

    static void equipOrStoreShield(Human human, ItemStack shield) {
        if (shield.isEmpty()) return;
        ItemStack offhand = human.getOffhandItem();
        if (!isShield(offhand)) {
            if (!offhand.isEmpty() && !human.putItemAway(offhand.copy())) return;
            human.setItemSlot(EquipmentSlot.OFFHAND, shield);
            human.setDropChance(EquipmentSlot.OFFHAND, 1.0F);
        } else HumanLootManager.storeWithEviction(human, shield.copy());
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, new ResourceLocation("spartanshields", path));
    }
}

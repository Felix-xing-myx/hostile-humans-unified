package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/** Backpack arrows pay for a shot, never for a projectile query or interrupted draw. */
public final class HumanArrowCombat {
    private static final String NPC_LOAD = "hostile_humans_unified:NpcArrowLoad";
    private HumanArrowCombat() {}

    public static boolean canShoot(Human human, LivingEntity target) {
        return !human.level().isClientSide && human.isAlive()
                && human.level().getDifficulty() != Difficulty.PEACEFUL
                && target != null && target.isAlive() && !target.isRemoved()
                && target.level() == human.level() && HumanAmmo.hasArrows(human);
    }

    /** Native loading may shrink this temporary stack; it never touches the backpack. */
    public static ItemStack projectileFor(Human human) {
        return HumanAmmo.hasArrows(human) ? new ItemStack(Items.ARROW) : ItemStack.EMPTY;
    }

    public static void markNpcLoad(ItemStack crossbow) {
        crossbow.getOrCreateTag().putBoolean(NPC_LOAD, true);
        // Only retain the charged action state. Unpaid temporary ammunition
        // must not survive equipment transfer, death drops or item swapping.
        crossbow.getTag().remove("ChargedProjectiles");
        crossbow.getTag().remove("Projectile");
    }

    /** Inventory transfer clears only an NPC's empty charged action marker. */
    public static void clearNpcLoad(ItemStack crossbow) {
        if (!crossbow.hasTag() || !crossbow.getTag().getBoolean(NPC_LOAD)) return;
        CrossbowItem.setCharged(crossbow, false);
        crossbow.getTag().remove("ChargedProjectiles");
        crossbow.getTag().remove("Projectile");
        crossbow.getTag().remove(NPC_LOAD);
    }

    /** Saved/looted charged ammunition is also an NPC arrow volley, not a free rocket. */
    public static boolean prepareCrossbowVolley(ItemStack crossbow) {
        if (!(crossbow.getItem() instanceof CrossbowItem) || !CrossbowItem.isCharged(crossbow)) {
            return false;
        }
        ListTag arrows = new ListTag();
        int count = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, crossbow) > 0 ? 3 : 1;
        for (int i = 0; i < count; i++) arrows.add(new ItemStack(Items.ARROW).save(new CompoundTag()));
        crossbow.getOrCreateTag().put("ChargedProjectiles", arrows);
        return true;
    }
}

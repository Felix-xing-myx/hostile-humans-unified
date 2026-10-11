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
    private static final java.util.Map<Human, ArrowAttackCadence> CADENCE = new java.util.WeakHashMap<>();
    private HumanArrowCombat() {}

    private static ArrowAttackCadence cadence(Human human) {
        return CADENCE.computeIfAbsent(human, ignored -> new ArrowAttackCadence());
    }
    public static void configureBowInterval(Human human, int interval) {
        if (!human.level().isClientSide) cadence(human).bowInterval(interval);
    }
    public static boolean canDrawBow(Human human) {
        return !human.level().isClientSide && cadence(human).canDrawBow(human.tickCount);
    }
    public static void recordBowShot(Human human) {
        if (!human.level().isClientSide) cadence(human).bowShot(human.tickCount);
    }
    public static void beginCrossbowLoad(Human human, ItemStack weapon) {
        // A new unloaded cycle cannot reuse the deadline of a cancelled prior volley.
        if (!human.level().isClientSide && !CrossbowItem.isCharged(weapon)
                && cadence(human).hasLoadedCrossbow(weapon)) cadence(human).crossbowShot();
    }
    public static void crossbowLoaded(Human human, ItemStack weapon) {
        if (human.level().isClientSide || !CrossbowItem.isCharged(weapon) || cadence(human).hasLoadedCrossbow(weapon)) return;
        cadence(human).crossbowLoaded(weapon, human.tickCount,
                ArrowAttackCadence.CROSSBOW_DELAY_MIN + human.getRandom().nextInt(ArrowAttackCadence.CROSSBOW_DELAY_VARIANCE),
                SpartanRangedCompat.isHeavyCrossbow(weapon));
    }
    public static boolean crossbowReady(Human human, ItemStack weapon) {
        if (human.level().isClientSide || !CrossbowItem.isCharged(weapon)) return false;
        // A player-provided charged weapon also receives the normal loaded wait.
        if (!cadence(human).hasLoadedCrossbow(weapon)) crossbowLoaded(human, weapon);
        return cadence(human).canFireCrossbow(weapon, human.tickCount);
    }
    public static boolean crossbowWaitFinished(Human human, ItemStack weapon) {
        if (human.level().isClientSide || !CrossbowItem.isCharged(weapon)) return false;
        if (!cadence(human).hasLoadedCrossbow(weapon)) crossbowLoaded(human, weapon);
        return cadence(human).crossbowWaitFinished(weapon, human.tickCount);
    }
    public static void recordCrossbowShot(Human human) {
        if (!human.level().isClientSide) cadence(human).crossbowShot();
    }

    private static boolean cadenceAllowsShot(Human human) {
        ItemStack weapon = human.getMainHandItem();
        if (!(weapon.getItem() instanceof net.minecraft.world.item.BowItem || weapon.getItem() instanceof CrossbowItem))
            weapon = human.getOffhandItem();
        if (weapon.getItem() instanceof CrossbowItem) return crossbowReady(human, weapon);
        return !(weapon.getItem() instanceof net.minecraft.world.item.BowItem) || cadence(human).canFireBow(human.tickCount);
    }

    public static boolean canShoot(Human human, LivingEntity target) {
        return !human.level().isClientSide && human.isAlive()
                && human.level().getDifficulty() != Difficulty.PEACEFUL
                && target != null && target.isAlive() && !target.isRemoved()
                && target.level() == human.level() && HumanAmmo.hasArrows(human) && cadenceAllowsShot(human)
                && RangedShotClearance.canShoot(human, target);
    }

    /** Read the projectile's saved firing origin; the shooter may have switched weapons. */
    public static boolean isCrossbowProjectile(net.minecraft.world.entity.projectile.AbstractArrow arrow) {
        return arrow.shotFromCrossbow()
                || arrow.getPersistentData().getBoolean(HumanGunner.NPC_CROSSBOW_PROJECTILE)
                || arrow.getPersistentData().getBoolean(SpartanRangedCompat.NPC_BOLT);
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

package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeHooks;

/** Lets Hostile Humans reserve and consume totems without occupying either hand. */
public final class InventoryTotemProtection {
    private static final float RECOVERY_HEALTH_RATIO = 0.30F;
    private static final int RESISTANCE_TICKS = 60 * 20;

    private InventoryTotemProtection() {
    }

    public static void stowOffhandTotem(Human human) {
        if (!human.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            return;
        }
        HumanData data = human.getData();
        if (data == null) {
            return;
        }
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            if (!data.getInventoryItem(i).isEmpty()) {
                continue;
            }
            data.setInventoryItem(i, human.getOffhandItem().copy());
            human.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            return;
        }
    }

    public static boolean tryUseInventoryTotem(Human human, DamageSource source) {
        HumanData data = human.getData();
        if (data == null) {
            return false;
        }
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stack = data.getInventoryItem(i);
            if (!stack.is(Items.TOTEM_OF_UNDYING)) {
                continue;
            }
            ItemStack usedTotem = stack.copyWithCount(1);
            // Forge's event API has no inventory-slot variant. OFF_HAND is used
            // as the logical activation hand so existing event consumers stay
            // compatible, while the real stack remains in HumanData.
            if (!ForgeHooks.onLivingUseTotem(human, source, usedTotem, InteractionHand.OFF_HAND)) {
                return false;
            }
            ItemStack remaining = stack.copy();
            remaining.shrink(1);
            data.setInventoryItem(i, remaining);
            applyRecovery(human);
            human.level().broadcastEntityEvent(human, (byte) 35);
            return true;
        }
        return false;
    }

    /** Shared post-activation effect for real hand and inventory totems. */
    public static void applyRecovery(Human human) {
        if (human.level().isClientSide) return;
        human.removeAllEffects();
        human.setHealth(human.getMaxHealth() * RECOVERY_HEALTH_RATIO);
        human.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, RESISTANCE_TICKS, 0));
    }
}

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
            human.setHealth(1.0F);
            human.removeAllEffects();
            human.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
            human.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
            human.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
            human.level().broadcastEntityEvent(human, (byte) 35);
            return true;
        }
        return false;
    }
}

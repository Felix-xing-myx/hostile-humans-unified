package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;

/** Shared shield wear policy for vanilla attacks and TaCZ blocked bullets. */
public final class ShieldDurability {
    private ShieldDurability() { }

    /** Wild shields keep their old cost; hired shields use player-style durability. */
    public static void damageShield(Human human, float blockedDamage) {
        if (!Float.isFinite(blockedDamage) || blockedDamage <= 0.0F) {
            return;
        }
        InteractionHand hand = human.getUsedItemHand();
        ItemStack shield = human.getItemInHand(hand);
        if (shield.isEmpty()
                || !shield.canPerformAction(ToolActions.SHIELD_BLOCK)
                || !shield.isDamageableItem()) {
            return;
        }
        if (human.hasOwner()) {
            if (blockedDamage >= 3.0F) {
                EquipmentSlot slot = hand == InteractionHand.MAIN_HAND
                        ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                ItemStack shieldBeforeDamage = shield.copy();
                int cost = (int) Math.min(shield.getMaxDamage(), 1.0D + Math.floor(blockedDamage));
                shield.hurtAndBreak(cost, human,
                        broken -> {
                            EquipmentBreakSounds.playNow(
                                    human, slot, shieldBeforeDamage);
                            human.broadcastBreakEvent(slot);
                        });
                if (shield.isEmpty()) {
                    human.stopUsingItem();
                }
            }
            return;
        }
        double durabilityCost = 10.0D + Math.ceil(blockedDamage * 2.0D);
        int remaining = shield.getMaxDamage() - shield.getDamageValue();
        if (durabilityCost >= remaining) {
            EquipmentBreakSounds.playNow(human, hand == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, shield.copy());
            human.broadcastBreakEvent(hand);
            human.setItemSlot(
                    hand == InteractionHand.MAIN_HAND
                            ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND,
                    ItemStack.EMPTY
            );
            human.stopUsingItem();
            return;
        }
        shield.setDamageValue(shield.getDamageValue() + (int) durabilityCost);
    }


}


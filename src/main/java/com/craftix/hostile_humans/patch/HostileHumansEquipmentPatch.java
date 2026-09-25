package com.craftix.hostile_humans.patch;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;

import java.util.function.Predicate;

/**
 * Transactional equipment custody used by the patched Hostile Humans base.
 *
 * <p>The original implementation could clear a held item after checking only
 * slots 0-15, and its weapon switch stowed both hands even when only one hand
 * was being changed. These operations deliberately have one owner and never
 * overwrite the unrelated hand.</p>
 */
public final class HostileHumansEquipmentPatch {
    private HostileHumansEquipmentPatch() {
    }

    /** Store the complete supplied stack, or materialise its remainder in-world. */
    public static void stowOrDrop(Human human, ItemStack source) {
        if (source.isEmpty() || human.level().isClientSide) {
            return;
        }
        HumanData data = human.getData();
        if (data != null) {
            mergeAndFill(data, source, 0, data.getInventoryItemsSize());
        }
        if (source.isEmpty()) {
            return;
        }

        // A void base API cannot report a failed insertion to its caller. The
        // only lossless fallback is therefore a server-side world item.
        ItemStack remainder = source.copy();
        ItemEntity dropped = human.spawnAtLocation(remainder);
        if (dropped != null) {
            dropped.setPickUpDelay(80);
            source.setCount(0);
        }
    }

    /** Atomically exchange only the requested equipment slot. */
    public static boolean swapRequestedSlot(
            Human human,
            Predicate<ItemStack> predicate,
            EquipmentSlot requestedSlot
    ) {
        if (human.level().isClientSide) {
            return false;
        }
        HumanData data = human.getData();
        if (data == null) {
            return false;
        }
        // HumanData currently owns 30 backpack slots. Always trust the live
        // container size instead of the base mod's historical 0-15 combat
        // window, otherwise equipment in slots 16-29 exists and is saved but
        // can never be selected again.
        int candidateCount = data.getInventoryItemsSize();
        for (int index = 0; index < candidateCount; index++) {
            ItemStack candidate = data.getInventoryItem(index);
            if (candidate.isEmpty() || !predicate.test(candidate)) {
                continue;
            }

            ItemStack previous = human.getItemBySlot(requestedSlot).copy();
            ItemStack replacement = candidate.copy();
            // The inventory slot is the transaction's guaranteed destination;
            // no capacity check and no second hand mutation are involved.
            data.setInventoryItem(index, previous);
            human.setItemSlot(requestedSlot, replacement);
            return true;
        }
        return false;
    }

    /** Wild shields keep their old cost; hired shields use player-style durability. */
    public static void damageShield(Human human, float blockedDamage) {
        if (blockedDamage <= 0.0F) {
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
                shield.hurtAndBreak(1 + (int) blockedDamage, human,
                        broken -> human.broadcastBreakEvent(slot));
                if (shield.isEmpty()) {
                    human.stopUsingItem();
                }
            }
            return;
        }
        int durabilityCost = 10 + (int) Math.ceil(blockedDamage * 2.0F);
        int remaining = shield.getMaxDamage() - shield.getDamageValue();
        if (durabilityCost >= remaining) {
            human.broadcastBreakEvent(hand);
            human.setItemSlot(
                    hand == InteractionHand.MAIN_HAND
                            ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND,
                    ItemStack.EMPTY
            );
            human.stopUsingItem();
            return;
        }
        shield.setDamageValue(shield.getDamageValue() + durabilityCost);
    }

    /** All-or-nothing pickup storage across the complete live backpack. */
    public static boolean storePickupTransactionally(HumanData data, ItemStack incoming) {
        if (incoming.isEmpty()) {
            return false;
        }
        int end = data.getInventoryItemsSize();
        int start = 0;
        int capacity = 0;
        for (int index = start; index < end; index++) {
            ItemStack stored = data.getInventoryItem(index);
            if (stored.isEmpty()) {
                capacity += incoming.getMaxStackSize();
            } else if (ItemStack.isSameItemSameTags(stored, incoming)) {
                capacity += Math.max(0, stored.getMaxStackSize() - stored.getCount());
            }
            if (capacity >= incoming.getCount()) {
                break;
            }
        }
        if (capacity < incoming.getCount()) {
            return false;
        }

        ItemStack moving = incoming.copy();
        mergeAndFill(data, moving, start, end);
        return moving.isEmpty();
    }

    private static void mergeAndFill(HumanData data, ItemStack source, int start, int end) {
        for (int index = start; index < end && !source.isEmpty(); index++) {
            ItemStack stored = data.getInventoryItem(index);
            if (stored.isEmpty()
                    || !ItemStack.isSameItemSameTags(stored, source)
                    || stored.getCount() >= stored.getMaxStackSize()) {
                continue;
            }
            int moved = Math.min(source.getCount(), stored.getMaxStackSize() - stored.getCount());
            ItemStack merged = stored.copy();
            merged.grow(moved);
            source.shrink(moved);
            data.setInventoryItem(index, merged);
        }
        for (int index = start; index < end && !source.isEmpty(); index++) {
            if (!data.getInventoryItem(index).isEmpty()) {
                continue;
            }
            int moved = Math.min(source.getCount(), source.getMaxStackSize());
            data.setInventoryItem(index, source.copyWithCount(moved));
            source.shrink(moved);
        }
    }
}

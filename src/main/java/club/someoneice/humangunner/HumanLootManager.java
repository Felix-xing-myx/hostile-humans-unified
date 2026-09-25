package club.someoneice.humangunner;

import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.Comparator;

final class HumanLootManager {
    private static final double MIN_PICKUP_VALUE = 24.0D;

    private HumanLootManager() {
    }

    /**
     * Re-evaluates the complete live backpack, including slots 16-29, and
     * performs exact two-location upgrades. This remains active in combat.
     */
    static void optimizeOwnedEquipment(Human human) {
        HumanData data = human.getData();
        if (data == null) {
            return;
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET
        }) {
            if (!HumanManagedLoadout.isArmourLocked(human, slot)) {
                equipBestStored(human, data, slot, false);
            }
        }
        // Armour can be replaced while blocking, drawing a bow or consuming.
        // A main-hand exchange waits until item use and gun custody are idle,
        // preserving those transactional hand states without disabling the
        // optimizer for the rest of combat.
        if (!human.isUsingItem()
                && !GunCustody.isActive(human)
                && !RangedWeaponCustody.controlsMainHand(human)
                && !club.someoneice.humangunner.GunSupport.get().isGun(human.getMainHandItem())) {
            equipBestStored(human, data, EquipmentSlot.MAINHAND, true);
        }
    }

    private static void equipBestStored(
            Human human, HumanData data, EquipmentSlot slot, boolean weaponOnly
    ) {
        ItemStack current = human.getItemBySlot(slot);
        double bestScore = equipmentScore(current, slot);
        int bestSlot = -1;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack candidate = data.getInventoryItem(i);
            if (candidate.isEmpty() || preferredEquipmentSlot(candidate) != slot) {
                continue;
            }
            if (weaponOnly && !isNonGunWeapon(candidate)) {
                continue;
            }
            double score = equipmentScore(candidate, slot);
            if (score > bestScore + 0.25D) {
                bestScore = score;
                bestSlot = i;
            }
        }
        if (bestSlot < 0) {
            return;
        }
        ItemStack replacement = data.getInventoryItem(bestSlot).copy();
        data.setInventoryItem(bestSlot, current.copy());
        human.setItemSlot(slot, replacement);
        human.setDropChance(slot, 1.0F);
    }

    static boolean isNonGunWeapon(ItemStack stack) {
        if (stack.isEmpty() || club.someoneice.humangunner.GunSupport.get().isGun(stack)) {
            return false;
        }
        if (HumanUtil.isMeleeWeapon(stack)
                || SpartanEquipmentCompat.isSpartanMeleeWeapon(stack)
                || stack.getItem() instanceof TieredItem
                || stack.getItem() instanceof ProjectileWeaponItem
                || stack.getItem() instanceof TridentItem) {
            return true;
        }
        // Covers third-party weapons which do not subclass a vanilla weapon
        // class but expose their damage through the ordinary main-hand
        // attribute contract.
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE).stream()
                .anyMatch(modifier -> modifier.getAmount() > 0.0D);
    }

    static boolean isDedicatedMeleeWeapon(ItemStack stack) {
        if (stack.isEmpty()
                || club.someoneice.humangunner.GunSupport.get().isGun(stack)
                || RangedWeaponCustody.isBowOrCrossbow(stack)
                || stack.getItem() instanceof TridentItem) {
            return false;
        }
        if (HumanUtil.isMeleeWeapon(stack)
                || SpartanEquipmentCompat.isSpartanMeleeWeapon(stack)
                || stack.getItem() instanceof TieredItem) {
            return true;
        }
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE).stream()
                .anyMatch(modifier -> modifier.getAmount() > 0.0D);
    }

    static double mainHandWeaponScore(ItemStack stack) {
        return equipmentScore(stack, EquipmentSlot.MAINHAND);
    }

    static ItemEntity findBestNearby(Human human, double radius) {
        return human.level().getEntitiesOfClass(
                        ItemEntity.class,
                        human.getBoundingBox().inflate(radius, 4.0D, radius),
                        item -> item.isAlive()
                                && !item.getItem().isEmpty()
                                && benefit(human, item.getItem()) > 0.0D
                ).stream()
                .max(Comparator.comparingDouble(item ->
                        benefit(human, item.getItem()) * 4.0D - human.distanceTo(item)))
                .orElse(null);
    }

    static boolean collect(Human human, ItemEntity entity) {
        ItemStack ground = entity.getItem();
        if (ground.isEmpty() || !HumanGunAcceptance.accepts(ground)) {
            return false;
        }
        EquipmentSlot slot = preferredEquipmentSlot(ground);
        if (isEquipment(ground) && !HumanManagedLoadout.isArmourLocked(human, slot)
                && shouldEquip(human, ground, slot)) {
            ItemStack equipped = human.getItemBySlot(slot).copy();
            ItemStack replacement = ground.copyWithCount(1);
            if (SpartanEquipmentCompat.isShield(replacement)) {
                ShieldEnchantmentRoll.applyToShield(human, replacement);
            }
            if (!equipped.isEmpty() && !storeWithEviction(human, equipped)) {
                return false;
            }
            human.setItemSlot(slot, replacement);
            human.setDropChance(slot, TierThreeLoadout.isBoundGear(replacement) ? 0.0F : 0.2F);
            if (slot == EquipmentSlot.MAINHAND
                    && RangedWeaponCustody.isBowOrCrossbow(replacement)) {
                RangedWeaponCustody.registerPreferred(human, human.getMainHandItem());
            }
            ground.shrink(1);
        } else if (inventoryValue(human, ground) >= MIN_PICKUP_VALUE) {
            ItemStack moving = ground.copy();
            // Owner-managed means the AI must not rearrange explicitly chosen
            // equipment. It must not freeze the backpack's value policy: a
            // full inventory still needs to discard an ordinary lower-value
            // stack for a worthwhile pickup.
            boolean stored = storeWithEviction(human, moving);
            if (!stored) {
                return false;
            }
            ground.setCount(moving.getCount());
        } else {
            return false;
        }

        if (ground.isEmpty()) {
            entity.discard();
        } else {
            entity.setItem(ground);
        }
        return true;
    }

    private static double benefit(Human human, ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof IdentityBadgeItem) {
            return 0.0D;
        }
        EquipmentSlot slot = preferredEquipmentSlot(stack);
        if (isEquipment(stack) && shouldEquip(human, stack, slot)) {
            return 80.0D + Math.max(0.0D, equipmentScore(stack, slot)
                    - equipmentScore(human.getItemBySlot(slot), slot)) * 5.0D;
        }
        double value = inventoryValue(human, stack);
        return value >= MIN_PICKUP_VALUE && canStoreOrImprove(human, stack, value) ? value : 0.0D;
    }

    private static boolean shouldEquip(Human human, ItemStack incoming, EquipmentSlot slot) {
        if (HumanManagedLoadout.isArmourLocked(human, slot)) {
            return false;
        }
        ItemStack current = human.getItemBySlot(slot);
        if (slot == EquipmentSlot.MAINHAND
                && RangedWeaponCustody.isBowOrCrossbow(incoming)
                && !GunCustody.hasOwnedGun(human)) {
            // A non-gunner adopts a newly found bow/crossbow even when holding
            // a melee weapon or trident. The caller first stores that old hand
            // item and aborts without changing the ground stack if storage fails.
            return !RangedWeaponCustody.isBowOrCrossbow(current)
                    || equipmentScore(incoming, slot) > equipmentScore(current, slot) + 1.5D;
        }
        if (TierThreeLoadout.isBoundGear(current)) {
            return false;
        }
        if (slot == EquipmentSlot.OFFHAND && current.is(Items.TOTEM_OF_UNDYING)) {
            return false;
        }
        if (slot == EquipmentSlot.MAINHAND && !current.isEmpty()) {
            boolean incomingMelee = HumanUtil.isMeleeWeapon(incoming);
            boolean currentMelee = HumanUtil.isMeleeWeapon(current);
            boolean incomingRanged = HumanGunner.isRangedWeapon(incoming);
            boolean currentRanged = HumanGunner.isRangedWeapon(current);
            if ((incomingMelee != currentMelee) || (incomingRanged != currentRanged)) {
                return false;
            }
        }
        return current.isEmpty()
                || equipmentScore(incoming, slot) > equipmentScore(current, slot) + 1.5D;
    }

    private static boolean isEquipment(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem
                || SpartanEquipmentCompat.isShield(stack)
                || isNonGunWeapon(stack)
                || club.someoneice.humangunner.GunSupport.get().isGun(stack);
    }

    private static EquipmentSlot preferredEquipmentSlot(ItemStack stack) {
        // Vanilla's generic fallback is MAINHAND for non-armour items. Humans
        // should treat shields as off-hand equipment so picking one up does not
        // overwrite a melee weapon, bow or firearm.
        return SpartanEquipmentCompat.isShield(stack)
                ? EquipmentSlot.OFFHAND
                : Mob.getEquipmentSlotForItem(stack);
    }

    private static double equipmentScore(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        double score = 0.0D;
        score += attribute(stack, slot, Attributes.ARMOR) * 8.0D;
        score += attribute(stack, slot, Attributes.ARMOR_TOUGHNESS) * 5.0D;
        score += attribute(stack, slot, Attributes.KNOCKBACK_RESISTANCE) * 10.0D;
        score += attribute(stack, slot, Attributes.ATTACK_DAMAGE) * 5.0D;
        score += Math.max(0.0D, attribute(stack, slot, Attributes.ATTACK_SPEED)) * 1.5D;
        if (club.someoneice.humangunner.GunSupport.get().isGun(stack)) {
            score += 90.0D;
        } else if (stack.getItem() instanceof TridentItem) {
            score += 45.0D;
        } else if (stack.getItem() instanceof CrossbowItem) {
            score += 38.0D;
        } else if (stack.getItem() instanceof BowItem) {
            score += 34.0D;
        } else if (SpartanEquipmentCompat.isShield(stack)) {
            score += SpartanEquipmentCompat.shieldValue(stack);
        }
        score += EnchantmentHelper.getEnchantments(stack).values().stream()
                .mapToInt(Integer::intValue).sum() * 2.5D;
        if (stack.isDamageableItem()) {
            score *= 0.55D + 0.45D * (1.0D - (double) stack.getDamageValue() / stack.getMaxDamage());
        }
        return score;
    }

    private static double attribute(ItemStack stack, EquipmentSlot slot, net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        double total = 0.0D;
        for (AttributeModifier modifier : stack.getAttributeModifiers(slot).get(attribute)) {
            total += modifier.getAmount();
        }
        return total;
    }

    private static double inventoryValue(Human human, ItemStack stack) {
        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            return 150.0D;
        }
        if (stack.is(Items.GOLDEN_APPLE)) {
            return 120.0D;
        }
        if (stack.is(Items.TOTEM_OF_UNDYING)) {
            return 140.0D;
        }
        if (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
            var effects = PotionUtils.getMobEffects(stack);
            if (effects.stream().anyMatch(effect -> effect.getEffect() == MobEffects.HEAL)) {
                return 115.0D;
            }
            if (effects.stream().anyMatch(effect -> effect.getEffect() == MobEffects.REGENERATION)) {
                return 108.0D;
            }
            if (effects.stream().anyMatch(effect -> effect.getEffect() == MobEffects.DAMAGE_BOOST)) {
                return 96.0D;
            }
            if (effects.stream().anyMatch(effect -> effect.getEffect() == MobEffects.MOVEMENT_SPEED)) {
                return 88.0D;
            }
            if (stack.is(Items.SPLASH_POTION)
                    && effects.stream().anyMatch(effect -> effect.getEffect() == MobEffects.MOVEMENT_SLOWDOWN)) {
                return 92.0D;
            }
            if (stack.is(Items.SPLASH_POTION)
                    && effects.stream().anyMatch(effect -> effect.getEffect() == MobEffects.WEAKNESS)) {
                return 86.0D;
            }
            if (effects.stream().anyMatch(effect -> effect.getEffect().isBeneficial())) {
                return 70.0D;
            }
            return 20.0D;
        }
        if (RecoverySupplies.isRecoverySupply(human, stack)) {
            FoodProperties food = stack.getFoodProperties(human);
            return food == null
                    ? 90.0D
                    : 28.0D + food.getNutrition() * 4.0D + food.getSaturationModifier() * 10.0D;
        }
        if (isEquipment(stack)) {
            return 35.0D + equipmentScore(stack, preferredEquipmentSlot(stack));
        }
        return 0.0D;
    }

    private static boolean canStoreOrImprove(Human human, ItemStack incoming, double incomingValue) {
        HumanData data = human.getData();
        if (data == null) {
            return false;
        }
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stored = data.getInventoryItem(i);
            if (stored.isEmpty()
                    || (ItemStack.isSameItemSameTags(stored, incoming)
                    && stored.getCount() < stored.getMaxStackSize())) {
                return true;
            }
            if (!protectedFromEviction(stored)
                    && inventoryValue(human, stored) + 1.0D < incomingValue) {
                return true;
            }
        }
        return false;
    }

    /** Mutates {@code incoming}; success means at least one item was stored. */
    static boolean storeWithEviction(Human human, ItemStack incoming) {
        HumanData data = human.getData();
        if (data == null || incoming.isEmpty() || !HumanGunAcceptance.accepts(incoming)) {
            return false;
        }
        int originalCount = incoming.getCount();
        for (int i = 0; i < data.getInventoryItemsSize() && !incoming.isEmpty(); i++) {
            ItemStack stored = data.getInventoryItem(i);
            if (ItemStack.isSameItemSameTags(stored, incoming)
                    && stored.getCount() < stored.getMaxStackSize()) {
                int moved = Math.min(incoming.getCount(), stored.getMaxStackSize() - stored.getCount());
                stored.grow(moved);
                incoming.shrink(moved);
                data.setInventoryItem(i, stored);
            }
        }
        for (int i = 0; i < data.getInventoryItemsSize() && !incoming.isEmpty(); i++) {
            if (!data.getInventoryItem(i).isEmpty()) {
                continue;
            }
            int moved = Math.min(incoming.getCount(), incoming.getMaxStackSize());
            data.setInventoryItem(i, incoming.copyWithCount(moved));
            incoming.shrink(moved);
        }
        if (!incoming.isEmpty()) {
            int lowestSlot = -1;
            double lowestValue = Double.POSITIVE_INFINITY;
            for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                ItemStack stored = data.getInventoryItem(i);
                double value = inventoryValue(human, stored);
                if (!protectedFromEviction(stored) && value < lowestValue) {
                    lowestSlot = i;
                    lowestValue = value;
                }
            }
            double incomingValue = inventoryValue(human, incoming);
            if (lowestSlot >= 0 && incomingValue > lowestValue + 1.0D) {
                ItemStack evicted = data.getInventoryItem(lowestSlot).copy();
                ItemEntity dropped = new ItemEntity(
                        human.level(), human.getX(), human.getY() + 0.4D, human.getZ(), evicted
                );
                dropped.setPickUpDelay(60);
                human.level().addFreshEntity(dropped);
                int moved = Math.min(incoming.getCount(), incoming.getMaxStackSize());
                data.setInventoryItem(lowestSlot, incoming.copyWithCount(moved));
                incoming.shrink(moved);
            }
        }
        return incoming.getCount() < originalCount;
    }

    /** Shared valuation seam for mandatory off-hand custody operations. */
    static int lowestEvictableSlot(Human human) {
        HumanData data = human.getData();
        if (data == null) {
            return -1;
        }
        int lowestSlot = -1;
        double lowestValue = Double.POSITIVE_INFINITY;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stored = data.getInventoryItem(i);
            if (stored.isEmpty() || protectedFromEviction(stored)) {
                continue;
            }
            // Retaining a large stack is worth somewhat more than retaining a
            // single item, without allowing quantity to outweigh strategic
            // equipment categories completely.
            double value = inventoryValue(human, stored)
                    + Math.max(0, stored.getCount() - 1) * 0.5D;
            if (value < lowestValue) {
                lowestValue = value;
                lowestSlot = i;
            }
        }
        return lowestSlot;
    }

    /**
     * Finds a slot for a mandatory gunner melee fallback. Prefer ordinary
     * expendable contents, then permit lower-value strategic items only in the
     * pathological all-protected inventory case. Bound tier-three gear is
     * never displaced; a stored gun is the final fallback because the equipped
     * primary firearm remains under GunCustody ownership.
     */
    static int lowestMandatoryMeleeEvictionSlot(Human human) {
        HumanData data = human.getData();
        if (data == null) {
            return -1;
        }
        int ordinary = lowestEvictableSlot(human);
        if (ordinary >= 0) {
            return ordinary;
        }
        int bestSlot = -1;
        double bestValue = Double.POSITIVE_INFINITY;
        for (int pass = 0; pass < 2 && bestSlot < 0; pass++) {
            for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                ItemStack stack = data.getInventoryItem(i);
                if (stack.isEmpty() || TierThreeLoadout.isBoundGear(stack)) {
                    continue;
                }
                if (pass == 0 && club.someoneice.humangunner.GunSupport.get().isGun(stack)) {
                    continue;
                }
                double value = inventoryValue(human, stack)
                        + Math.max(0, stack.getCount() - 1) * 0.5D;
                if (value < bestValue) {
                    bestValue = value;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    private static boolean protectedFromEviction(ItemStack stack) {
        return TierThreeLoadout.isBoundGear(stack)
                || club.someoneice.humangunner.GunSupport.get().isGun(stack)
                || RangedWeaponCustody.isBowOrCrossbow(stack)
                || SpartanEquipmentCompat.isShield(stack)
                || stack.getItem() instanceof IdentityBadgeItem;
    }
}

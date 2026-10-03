package club.someoneice.humangunner;

import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.UUID;
import dev.felix.hostilehumans.core.PileFirstSelection;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.IntFunction;
import java.util.function.ToDoubleFunction;

public final class HumanLootManager {
    private static final double MIN_PICKUP_VALUE = 24.0D;
    private static final EquipmentSlot[] ARMOUR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

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
        // Classification can invoke compatibility hooks. Each backpack entry
        // needs classifying only once for this non-yielding optimization pass.
        EquipmentSlot[] storedSlots = new EquipmentSlot[data.getInventoryItemsSize()];
        for (int i = 0; i < storedSlots.length; i++) {
            ItemStack stack = data.getInventoryItem(i);
            if (!stack.isEmpty()) storedSlots[i] = preferredEquipmentSlot(stack);
        }
        for (EquipmentSlot slot : ARMOUR_SLOTS) {
            if (!HumanManagedLoadout.isArmourLocked(human, slot)) {
                equipBestStored(human, data, slot, false, storedSlots);
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
            equipBestStored(human, data, EquipmentSlot.MAINHAND, true, storedSlots);
        }
    }

    private static void equipBestStored(
            Human human, HumanData data, EquipmentSlot slot, boolean weaponOnly, EquipmentSlot[] storedSlots
    ) {
        ItemStack current = human.getItemBySlot(slot);
        double bestScore = equipmentScore(current, slot);
        int bestSlot = -1;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack candidate = data.getInventoryItem(i);
            if (candidate.isEmpty() || storedSlots[i] != slot) {
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
        storedSlots[bestSlot] = current.isEmpty() ? null : preferredEquipmentSlot(current);
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

    /** Existing tools are usable backups; never manufacture replacements. */
    public static boolean equipMeleeFallback(Human human) {
        return HumanManagedLoadout.equipStoredMeleeIfMainHandEmpty(human);
    }

    /** One complete nearby selection, with a bounded scoring batch and pile-first semantics. */
    static final class NearbySearch {
        private final Human human;
        private final double radius;
        private final Vec3 origin;
        private final Vec3 preferredPile;
        private final AABB searchArea;
        private final HumanData inventory;
        private final int inventoryRevision;
        private final PileFirstSelection<ItemEntity, UUID> selection;

        NearbySearch(Human human, double radius, Vec3 preferredPile) {
            this.human = human;
            this.radius = radius;
            this.origin = human.position();
            this.preferredPile = preferredPile;
            this.searchArea = human.getBoundingBox().inflate(radius, 4.0D, radius);
            this.inventory = human.getData();
            this.inventoryRevision = inventory == null ? 0 : inventory.getInventoryRevision();
            AABB pileArea = preferredPile == null ? null : new AABB(preferredPile, preferredPile).inflate(4.0D);
            boolean pileOnly = pileArea != null && searchArea.intersects(pileArea);
            selection = new PileFirstSelection<>(query(pileOnly ? searchArea.intersect(pileArea) : searchArea),
                    pileOnly, () -> query(searchArea), ItemEntity::getUUID,
                    item -> item.position().distanceToSqr(preferredPile) <= 16.0D);
        }

        boolean isCurrent(Human human) {
            HumanData current = human.getData();
            return this.human == human && origin.distanceToSqr(human.position()) <= 4.0D
                    && current == inventory && (current == null || current.getInventoryRevision() == inventoryRevision);
        }

        boolean isDone() { return selection.isDone(); }

        ItemEntity advance(int budget, Predicate<ItemEntity> skip, Consumer<ItemEntity> reject) {
            // The backpack can change between batches; values never cross a tick.
            HumanData current = human.getData();
            StorageSummary capacity = current == null ? null : new StorageSummary(
                    current.getInventoryItemsSize(), current::getInventoryItem,
                    HumanLootManager::protectedFromEviction, stack -> inventoryValue(human, stack));
            return selection.advance(budget, item -> {
                if (!item.isAlive() || item.getItem().isEmpty() || item.level() != human.level()
                        || !item.getBoundingBox().intersects(searchArea) || skip.test(item)) return Double.NEGATIVE_INFINITY;
                double candidateScore = score(item, capacity);
                if (candidateScore == Double.NEGATIVE_INFINITY) {
                    if (item.isAlive() && !item.getItem().isEmpty()) reject.accept(item);
                }
                return candidateScore;
            });
        }

        private Iterator<ItemEntity> query(AABB area) {
            return human.level().getEntitiesOfClass(ItemEntity.class, area,
                    item -> item.isAlive() && !item.getItem().isEmpty()).iterator();
        }

        private double score(ItemEntity item, StorageSummary capacity) {
            if (item == null || item.hasPickUpDelay() || !SoldierPickupPolicy.canCollectNow(human)
                    || !item.isAlive() || item.getItem().isEmpty()
                    || item.level() != human.level() || !item.getBoundingBox().intersects(searchArea)
                    || human.distanceToSqr(item) > (radius + 4.0D) * (radius + 4.0D)
                    || (selection.isPileOnly() && item.position().distanceToSqr(preferredPile) > 16.0D)
                    || !isReachableWithoutDiving(item)) return Double.NEGATIVE_INFINITY;
            double value = benefit(human, item.getItem(), capacity);
            return value <= 0.0D ? Double.NEGATIVE_INFINITY
                    : value * 0.25D - human.distanceTo(item) * 2.0D;
        }
    }

    static boolean isWorthCollecting(Human human, ItemEntity item) {
        return SoldierPickupPolicy.canCollectNow(human) && item.isAlive() && !item.getItem().isEmpty()
                && !item.hasPickUpDelay()
                && SoldierOrder.allowsLootPosition(human, item.getX(), item.getZ())
                && isReachableWithoutDiving(item)
                && benefit(human, item.getItem()) > 0.0D;
    }

    /** Keep the legacy nearby pickup ability on the same value policy as active searching. */
    public static boolean tryCollectNearby(Human human, ItemEntity item) {
        return SoldierPickupPolicy.canCollectNow(human)
                && human.distanceToSqr(item) <= 6.25D
                && isWorthCollecting(human, item)
                && collect(human, item);
    }

    static boolean isReachableWithoutDiving(ItemEntity item) {
        if (!item.isInWater()) {
            return true;
        }
        BlockPos itemBlock = item.blockPosition();
        // A surface-bound Human cannot collect an item at the bottom of a
        // deep column. Bound this probe to four loaded blocks per candidate.
        for (int offset = 1; offset <= 4; offset++) {
            BlockPos above = itemBlock.above(offset);
            if (!item.level().getFluidState(above).is(FluidTags.WATER)) {
                return ShoreSeekingPolicy.isSurfaceLootReachable(
                        above.getY(), item.getY());
            }
        }
        return false;
    }

    static boolean collect(Human human, ItemEntity entity) {
        if (!SoldierPickupPolicy.canCollectNow(human) || !entity.isAlive() || entity.hasPickUpDelay()
                || !SoldierOrder.allowsLootPosition(human, entity.getX(), entity.getZ())) return false;
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
            human.setDropChance(slot, HumanSpawnEquipment.isBoundGear(replacement) ? 0.0F : 0.2F);
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
        return benefit(human, stack, null);
    }

    private static double benefit(Human human, ItemStack stack, StorageSummary capacity) {
        if (stack.isEmpty() || stack.getItem() instanceof IdentityBadgeItem
                || !HumanGunAcceptance.accepts(stack)) {
            return 0.0D;
        }
        EquipmentSlot slot = preferredEquipmentSlot(stack);
        if (isEquipment(stack) && shouldEquip(human, stack, slot)) {
            return 80.0D + Math.max(0.0D, equipmentScore(stack, slot)
                    - equipmentScore(human.getItemBySlot(slot), slot)) * 5.0D;
        }
        double value = inventoryValue(human, stack);
        return value >= MIN_PICKUP_VALUE && (capacity == null ? canStoreOrImprove(human, stack, value)
                : capacity.canStore(stack, value)) ? value : 0.0D;
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
        if (HumanSpawnEquipment.isBoundGear(current)) {
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
        var modifiers = stack.getAttributeModifiers(slot);
        score += attribute(modifiers.get(Attributes.ARMOR)) * 8.0D;
        score += attribute(modifiers.get(Attributes.ARMOR_TOUGHNESS)) * 5.0D;
        score += attribute(modifiers.get(Attributes.KNOCKBACK_RESISTANCE)) * 10.0D;
        score += attribute(modifiers.get(Attributes.ATTACK_DAMAGE)) * 5.0D;
        score += Math.max(0.0D, attribute(modifiers.get(Attributes.ATTACK_SPEED))) * 1.5D;
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
        int enchantmentLevels = 0;
        for (int level : EnchantmentHelper.getEnchantments(stack).values()) enchantmentLevels += level;
        score += enchantmentLevels * 2.5D;
        if (stack.isDamageableItem()) {
            score *= 0.55D + 0.45D * (1.0D - (double) stack.getDamageValue() / stack.getMaxDamage());
        }
        return score;
    }

    private static double attribute(java.util.Collection<AttributeModifier> modifiers) {
        double total = 0.0D;
        for (AttributeModifier modifier : modifiers) {
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
            return potionValue(PotionUtils.getMobEffects(stack), stack.is(Items.SPLASH_POTION));
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

    static double potionValue(List<net.minecraft.world.effect.MobEffectInstance> effects, boolean splash) {
        int present = 0;
        for (var instance : effects) {
            var effect = instance.getEffect();
            if (effect == MobEffects.HEAL) return 115.0D;
            if (effect == MobEffects.REGENERATION) present |= 1;
            else if (effect == MobEffects.DAMAGE_BOOST) present |= 2;
            else if (effect == MobEffects.MOVEMENT_SPEED) present |= 4;
            else if (splash && effect == MobEffects.MOVEMENT_SLOWDOWN) present |= 8;
            else if (splash && effect == MobEffects.WEAKNESS) present |= 16;
            else if (effect.isBeneficial()) present |= 32;
        }
        // Priority is not numerical maximum: speed precedes splash slowness.
        if ((present & 1) != 0) return 108.0D;
        if ((present & 2) != 0) return 96.0D;
        if ((present & 4) != 0) return 88.0D;
        if ((present & 8) != 0) return 92.0D;
        if ((present & 16) != 0) return 86.0D;
        return (present & 32) != 0 ? 70.0D : 20.0D;
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
            if (!protectedFromEviction(stored)) {
                double storedValue = inventoryValue(human, stored);
                if (storedValue + 1.0D < incomingValue) return true;
            }
        }
        return false;
    }

    /** Lazy capacity/eviction summary; valid only during one non-mutating batch. */
    static final class StorageSummary {
        private final int size;
        private final IntFunction<ItemStack> items;
        private final Predicate<ItemStack> protectedItems;
        private final ToDoubleFunction<ItemStack> value;
        private boolean prepared;
        private boolean valuesPrepared;
        private boolean emptySlot;
        private List<ItemStack> stacks;
        private double lowestEvictable = Double.POSITIVE_INFINITY;
        private final Map<net.minecraft.world.item.Item, List<ItemStack>> mergeable = new IdentityHashMap<>();

        StorageSummary(int size, IntFunction<ItemStack> items, Predicate<ItemStack> protectedItems,
                       ToDoubleFunction<ItemStack> value) {
            this.size = size;
            this.items = items;
            this.protectedItems = protectedItems;
            this.value = value;
        }

        boolean canStore(ItemStack incoming, double incomingValue) {
            if (!prepared) prepare();
            if (emptySlot) return true;
            List<ItemStack> matches = mergeable.get(incoming.getItem());
            if (matches != null) for (ItemStack stored : matches) {
                if (ItemStack.isSameItemSameTags(stored, incoming)) return true;
            }
            if (!valuesPrepared) {
                valuesPrepared = true;
                for (ItemStack stored : stacks) {
                    if (!protectedItems.test(stored)) {
                        double storedValue = value.applyAsDouble(stored);
                        if (storedValue < lowestEvictable) lowestEvictable = storedValue;
                    }
                }
            }
            return lowestEvictable + 1.0D < incomingValue;
        }

        private void prepare() {
            prepared = true;
            stacks = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ItemStack stack = items.apply(i);
                if (stack.isEmpty()) {
                    emptySlot = true;
                    return;
                }
                stacks.add(stack);
            }
            for (ItemStack stored : stacks) {
                if (stored.getCount() < stored.getMaxStackSize()) {
                    mergeable.computeIfAbsent(stored.getItem(), ignored -> new ArrayList<>()).add(stored);
                }
            }
        }
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
                if (stack.isEmpty() || HumanSpawnEquipment.isBoundGear(stack)) {
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
        return HumanSpawnEquipment.isBoundGear(stack)
                || club.someoneice.humangunner.GunSupport.get().isGun(stack)
                || RangedWeaponCustody.isBowOrCrossbow(stack)
                || SpartanEquipmentCompat.isShield(stack)
                || stack.getItem() instanceof IdentityBadgeItem;
    }
}

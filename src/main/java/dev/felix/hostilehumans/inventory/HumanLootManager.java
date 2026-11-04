package dev.felix.hostilehumans.inventory;

import dev.felix.hostilehumans.faction.IdentityBadgeItem;
import dev.felix.hostilehumans.entity.HumanData;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.inventory.PileFirstSelection;

import dev.felix.hostilehumans.behavior.combat.CloseDefenseHold;
import dev.felix.hostilehumans.behavior.combat.defense.ShieldPickupPolicy;
import dev.felix.hostilehumans.behavior.combat.gun.GunCustody;
import dev.felix.hostilehumans.behavior.combat.gun.HumanGunAcceptance;
import dev.felix.hostilehumans.behavior.combat.ranged.AmmoWeaponSelection;
import dev.felix.hostilehumans.behavior.combat.ranged.HumanAmmo;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedWeaponCustody;
import dev.felix.hostilehumans.behavior.combat.recovery.MedicalSupplies;
import dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies;
import dev.felix.hostilehumans.behavior.combat.throwable.TacticalThrowables;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.equipment.ArmorSwapPolicy;
import dev.felix.hostilehumans.equipment.EquipmentCondition;
import dev.felix.hostilehumans.equipment.HumanSpawnEquipment;
import dev.felix.hostilehumans.navigation.ShoreSeekingPolicy;
import dev.felix.hostilehumans.perception.HumanLootVision;
import dev.felix.hostilehumans.perception.PerceptionBudget;
import dev.felix.hostilehumans.soldier.SoldierOrder;
import dev.felix.hostilehumans.soldier.SoldierRecallCharmItem;

import dev.felix.hostilehumans.entity.HumanUtil;
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
    public static void optimizeOwnedEquipment(Human human) {
        AmmoWeaponSelection.tick(human);
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
                && !CloseDefenseHold.shouldHold(human)
                && !GunCustody.isActive(human)
                && !RangedWeaponCustody.controlsMainHand(human)
                && !dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(human.getMainHandItem())) {
            equipBestStored(human, data, EquipmentSlot.MAINHAND, true, storedSlots);
        }
    }

    private static void equipBestStored(
            Human human, HumanData data, EquipmentSlot slot, boolean weaponOnly, EquipmentSlot[] storedSlots
    ) {
        ItemStack current = human.getItemBySlot(slot);
        double bestScore = equipmentScore(current, slot);
        int bestSlot = -1;
        boolean armor = ArmorSwapPolicy.isArmor(slot);
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            if (HumanInventoryCustody.isReservedForRecovery(data, i)) continue;
            ItemStack candidate = data.getInventoryItem(i);
            if (candidate.isEmpty() || storedSlots[i] != slot) {
                continue;
            }
            if (weaponOnly && (!isNonGunWeapon(candidate) || AmmoWeaponSelection.isExhausted(human, candidate)
                    || AmmoWeaponSelection.rank(human, candidate, HumanAmmo.hasArrows(human))
                    < AmmoWeaponSelection.rank(human, current, HumanAmmo.hasArrows(human)))) {
                continue;
            }
            double score = equipmentScore(candidate, slot);
            if (armor) {
                if (!ArmorSwapPolicy.allows(human, slot, candidate)) continue;
                double full = fullEquipmentScore(candidate, slot);
                ItemStack best = bestSlot < 0 ? ItemStack.EMPTY : data.getInventoryItem(bestSlot);
                if (bestSlot >= 0 && (full < fullEquipmentScore(best, slot)
                        || full == fullEquipmentScore(best, slot)
                        && EquipmentCondition.remainingFraction(candidate) <= EquipmentCondition.remainingFraction(best))) continue;
                bestSlot = i;
            } else if (score > bestScore + 0.25D) {
                bestScore = score;
                bestSlot = i;
            }
        }
        if (bestSlot < 0) {
            return;
        }
        ItemStack replacement = data.getInventoryItem(bestSlot);
        if (HumanEquipmentExchange.request(human, "optimize:" + slot.getName(), java.util.List.of(
                new HumanEquipmentExchange.Need(slot, stack -> stack == replacement)))
                != HumanEquipmentExchange.Result.SUCCESS) return;
        storedSlots[bestSlot] = current.isEmpty() ? null : preferredEquipmentSlot(current);
        ArmorSwapPolicy.equipped(human, slot);
        human.setDropChance(slot, 1.0F);
    }

    static boolean isNonGunWeapon(ItemStack stack) {
        if (stack.isEmpty() || dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(stack)) {
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

    public static boolean isDedicatedMeleeWeapon(ItemStack stack) {
        if (stack.isEmpty()
                || dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(stack)
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

    public static double mainHandWeaponScore(ItemStack stack) {
        return equipmentScore(stack, EquipmentSlot.MAINHAND);
    }

    /** Shared read-only query; callers still request actual layout changes from the exchange module. */
    public static ItemStack bestOwnedMeleeWeapon(Human human) {
        ItemStack best = ItemStack.EMPTY;
        double score = Double.NEGATIVE_INFINITY;
        for (ItemStack stack : ownedEquipment(human)) if (isDedicatedMeleeWeapon(stack)) {
            double value = mainHandWeaponScore(stack);
            if (value > score) { best = stack; score = value; }
        }
        return best;
    }

    /** Preserve the current offhand on ties; namespace and remaining wear do not affect equip order. */
    public static ItemStack bestOwnedShield(Human human) {
        ItemStack best = SpartanEquipmentCompat.isShield(human.getOffhandItem())
                ? human.getOffhandItem() : ItemStack.EMPTY;
        for (ItemStack stack : ownedEquipment(human))
            if (SpartanEquipmentCompat.betterShield(stack, best)) best = stack;
        return best;
    }

    private static java.util.List<ItemStack> ownedEquipment(Human human) {
        var actual = new java.util.ArrayList<ItemStack>();
        actual.add(human.getMainHandItem()); actual.add(human.getOffhandItem());
        if (human.getData() != null) for (int i = 0; i < human.getData().getInventoryItemsSize(); i++)
            if (!HumanInventoryCustody.isReservedForRecovery(human, i)) actual.add(human.getData().getInventoryItem(i));
        return actual;
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
        private PileFirstSelection<ItemEntity, UUID> selection;
        private final AABB pileArea;
        private final Vec3 selectionCenter;
        private final double selectionRadiusSqr;

        NearbySearch(Human human, double radius, Vec3 preferredPile) {
            this.human = human;
            this.radius = radius;
            this.origin = human.position();
            this.preferredPile = preferredPile;
            this.searchArea = human.getBoundingBox().inflate(radius, 4.0D, radius);
            this.inventory = human.getData();
            this.inventoryRevision = inventory == null ? 0 : inventory.getInventoryRevision();
            selectionCenter=preferredPile==null?origin:preferredPile;
            selectionRadiusSqr=preferredPile==null?64.0D:16.0D;
            AABB localPile = preferredPile == null ? new AABB(origin,origin).inflate(8.0D,1.5D,8.0D)
                    : new AABB(preferredPile, preferredPile).inflate(4.0D);
            this.pileArea = localPile != null && searchArea.intersects(localPile) ? searchArea.intersect(localPile) : null;
        }

        boolean isCurrent(Human human) {
            HumanData current = human.getData();
            return this.human == human && origin.distanceToSqr(human.position()) <= 4.0D
                    && current == inventory && (current == null || current.getInventoryRevision() == inventoryRevision);
        }

        boolean isDone() { return selection != null && selection.isDone(); }

        ItemEntity advance(int budget, Predicate<ItemEntity> skip, Consumer<ItemEntity> reject) {
            if (selection == null) {
                Iterator<ItemEntity> initial = query(pileArea == null ? searchArea : pileArea, skip, pileArea != null);
                if (initial == null) return null;
                selection = new PileFirstSelection<>(initial, pileArea != null, () -> query(searchArea, skip, false), ItemEntity::getUUID,
                        item -> item.position().distanceToSqr(selectionCenter) <= selectionRadiusSqr);
            }
            // The backpack can change between batches; values never cross a tick.
            HumanData current = human.getData();
            StorageSummary capacity = current == null ? null : new StorageSummary(human, current);
            return selection.advance(budget, item -> {
                if (!eligible(item) || skip.test(item)) return Double.NEGATIVE_INFINITY;
                HumanLootVision.Sight sight = HumanLootVision.inspect(human, item);
                if (sight == HumanLootVision.Sight.DEFERRED) return Double.NaN;
                if (sight != HumanLootVision.Sight.VISIBLE) return Double.NEGATIVE_INFINITY;
                double value = benefit(human, item.getItem(), capacity);
                if (value <= 0.0D) {
                    reject.accept(item);
                    return Double.NEGATIVE_INFINITY;
                }
                return value * 0.25D - human.distanceTo(item) * 2.0D;
            });
        }

        private Iterator<ItemEntity> query(AABB area, Predicate<ItemEntity> skip, boolean pileOnly) {
            if (!PerceptionBudget.claim(human, "queries", 1, ":loot-query")) return null;
            return nearbyCandidates(human, area, 64, item -> !skip.test(item)
                    && (!pileOnly || item.position().distanceToSqr(selectionCenter) <= selectionRadiusSqr)).iterator();
        }

        private boolean eligible(ItemEntity item) {
            return isEligibleForPickup(human, item) && item.getBoundingBox().intersects(searchArea)
                    && human.distanceToSqr(item) <= (radius + 4.0D) * (radius + 4.0D)
                    && (!selection.isPileOnly() || item.position().distanceToSqr(selectionCenter) <= selectionRadiusSqr);
        }
    }

    static boolean isWorthCollecting(Human human, ItemEntity item) {
        return isEligibleForPickup(human, item) && benefit(human, item.getItem()) > 0.0D;
    }

    static List<ItemEntity> nearbyCandidates(Human human, AABB area, int limit, Predicate<ItemEntity> filter) {
        if (!(human.level() instanceof net.minecraft.server.level.ServerLevel level)) return List.of();
        List<ItemEntity> items = new ArrayList<>();
        level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(ItemEntity.class), area,
                item -> item.isAlive() && !item.hasPickUpDelay() && !item.getItem().isEmpty()
                        && SoldierOrder.allowsLootPosition(human, item.getX(), item.getZ()) && filter.test(item),
                items, limit);
        return items;
    }

    static boolean isEligibleForPickup(Human human, ItemEntity item) {
        return item.level() == human.level() && SoldierPickupPolicy.canCollectNow(human)
                && item.isAlive() && !item.getItem().isEmpty()
                && !item.hasPickUpDelay()
                && SoldierOrder.allowsLootPosition(human, item.getX(), item.getZ())
                && isReachableWithoutDiving(item);
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

    /** Supply-only transaction: no direct equip, hand exchange, navigation or item-use changes. */
    static boolean collectCombatSupply(Human human, ItemEntity entity, int requested) {
        var config = UnifiedConfig.get().combatResupply();
        ItemStack ground = entity.getItem();
        if (requested <= 0 || !config.enabled() || !SoldierPickupPolicy.canCombatResupplyNow(human)
                || !CombatResupply.reachable(human, entity, config.radius())
                || !SoldierPickupPolicy.canCollectItem(human, ground)
                || !(HumanAmmo.isAmmo(ground) || RecoverySupplies.isHealingSupplyForResupply(human, ground))) return false;
        int taking = Math.min(requested, ground.getCount());
        ItemStack moving = ground.copyWithCount(taking);
        if (!storeWithEviction(human, moving)) return false;
        int moved = taking - moving.getCount();
        if (moved <= 0) return false;
        ItemStack remainder = ground.copy();
        remainder.shrink(moved);
        human.take(entity, moved);
        if (remainder.isEmpty()) entity.discard();
        else entity.setItem(remainder);
        return true;
    }

    static boolean collect(Human human, ItemEntity entity) {
        if (!isEligibleForPickup(human, entity)) return false;
        if (HumanLootVision.inspect(human, entity) != HumanLootVision.Sight.VISIBLE) return false;
        ItemStack ground = entity.getItem();
        if (ground.isEmpty() || !SoldierPickupPolicy.canCollectItem(human, ground)
                || !HumanGunAcceptance.accepts(ground)
                || isEquipment(ground) && !EquipmentCondition.canAutomaticallyCollect(ground)) {
            return false;
        }
        if (SpartanEquipmentCompat.isShield(ground)) return ShieldPickupPolicy.collect(human, entity);
        boolean equipment = isEquipment(ground);
        if (equipment && shouldEquip(human, ground, preferredEquipmentSlot(ground))
                || inventoryValue(human, ground) >= MIN_PICKUP_VALUE) {
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
            // Equipment is now a real backpack item. The same exchange entry used by combat
            // can equip it without partially storing and then deleting an old held stack.
            if (equipment) optimizeOwnedEquipment(human);
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
                || !SoldierPickupPolicy.canCollectItem(human, stack)
                || !HumanGunAcceptance.accepts(stack)
                || isEquipment(stack) && !EquipmentCondition.canAutomaticallyCollect(stack)) {
            return 0.0D;
        }
        if (SpartanEquipmentCompat.isShield(stack)) return ShieldPickupPolicy.benefit(human, stack, capacity);
        EquipmentSlot slot = preferredEquipmentSlot(stack);
        if (isEquipment(stack) && shouldEquip(human, stack, slot)) {
            if (!(capacity == null ? canStoreOrImprove(human, stack, inventoryValue(human, stack))
                    : capacity.canStore(stack, inventoryValue(human, stack)))) return 0.0D;
            return 80.0D + Math.max(0.0D, equipmentScore(stack, slot)
                    - equipmentScore(human.getItemBySlot(slot), slot)) * 5.0D;
        }
        double value = inventoryValue(human, stack);
        return value >= MIN_PICKUP_VALUE && (capacity == null ? canStoreOrImprove(human, stack, value)
                : capacity.canStore(stack, value)) ? value : 0.0D;
    }

    private static boolean shouldEquip(Human human, ItemStack incoming, EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND && CloseDefenseHold.shouldHold(human)) return false;
        if (HumanManagedLoadout.isArmourLocked(human, slot)) {
            return false;
        }
        if (slot == EquipmentSlot.MAINHAND && AmmoWeaponSelection.isExhausted(human, incoming)) {
            return false;
        }
        if (ArmorSwapPolicy.isArmor(slot)) return ArmorSwapPolicy.allows(human, slot, incoming);
        ItemStack current = human.getItemBySlot(slot);
        if (slot == EquipmentSlot.MAINHAND
                && RangedWeaponCustody.isBowOrCrossbow(incoming)
                && !RangedWeaponCustody.hasGunPriority(human)) {
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
            boolean incomingRanged = HumanRuntime.isRangedWeapon(incoming);
            boolean currentRanged = HumanRuntime.isRangedWeapon(current);
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
                || dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(stack);
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
        return EquipmentCondition.value(stack, fullEquipmentScore(stack, slot));
    }

    public static double fullEquipmentScore(ItemStack stack, EquipmentSlot slot) {
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
        if (dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(stack)) {
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
        // Shields already include their own deliberately lower enchantment weight.
        if (!SpartanEquipmentCompat.isShield(stack)) score += enchantmentLevels * 2.5D;
        return Math.max(0.0D, score);
    }

    private static double attribute(java.util.Collection<AttributeModifier> modifiers) {
        double total = 0.0D;
        for (AttributeModifier modifier : modifiers) {
            total += modifier.getAmount();
        }
        return total;
    }

    public static double inventoryValue(Human human, ItemStack stack) {
        if (stack.getItem() instanceof SoldierRecallCharmItem) return SoldierRecallCharmItem.INVENTORY_VALUE;
        if (MedicalSupplies.isSupply(stack)) return MedicalSupplies.inventoryValue(stack);
        if (TacticalThrowables.supported(stack)) return UnifiedConfig.get().scguns().throwableQuality();
        if (HumanAmmo.isAmmo(stack)) {
            HumanData data = human.getData();
            boolean gunner = GunCustody.hasOwnedGun(human);
            boolean archer = RangedWeaponCustody.isBowOrCrossbow(human.getMainHandItem())
                    || RangedWeaponCustody.isBowOrCrossbow(human.getOffhandItem())
                    || (data != null && data.hasStoredBowOrCrossbow(human.tickCount));
            return SupplyInventoryPolicy.ammoValue(HumanAmmo.isMagazine(stack), gunner, archer);
        }
        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            return foodInventoryValue(human, stack, 260.0D);
        }
        if (stack.is(Items.GOLDEN_APPLE)) {
            return foodInventoryValue(human, stack, 160.0D);
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
                    : foodInventoryValue(human, stack, foodValue(food));
        }
        if (isEquipment(stack)) {
            // Scale the storage premium too, without applying wear twice to attributes.
            return EquipmentCondition.value(stack, 35.0D)
                    + equipmentScore(stack, preferredEquipmentSlot(stack));
        }
        return 0.0D;
    }

    public static double foodValue(FoodProperties food) {
        return dev.felix.hostilehumans.equipment.FoodInventoryValue.quality(food.getNutrition(), food.getSaturationModifier());
    }

    private static double foodInventoryValue(Human human, ItemStack candidate, double quality) {
        int count = candidate.getCount();
        HumanData data = human.getData();
        // Same item and effective NBT form one quantity group, independent of stack splitting.
        // Bounded real backpack only: no world scan, with a hard comparison cap of 64.
        if (data != null) for (int i = 0; i < data.getInventoryItemsSize() && count < 64; i++) {
            ItemStack stored = data.getInventoryItem(i);
            if (stored != candidate && ItemStack.isSameItemSameTags(stored, candidate))
                count = Math.min(64, count + stored.getCount());
        }
        return dev.felix.hostilehumans.equipment.FoodInventoryValue.value(quality, count);
    }

    private static double quantityPremium(Human human, ItemStack stack) {
        return stack.getFoodProperties(human) != null && RecoverySupplies.isRecoverySupply(human, stack)
                ? 0 : Math.max(0, stack.getCount() - 1) * 0.5D;
    }

    public static double potionValue(List<net.minecraft.world.effect.MobEffectInstance> effects, boolean splash) {
        int present = 0;
        for (var instance : effects) {
            var effect = instance.getEffect();
            if (effect == MobEffects.HEAL) return 55.0D;
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
        return new StorageSummary(human, data).canStore(incoming, incomingValue);
    }

    private static SupplyInventoryPolicy.Slot supplySlot(Human human, ItemStack stack) {
        SupplyInventoryPolicy.Kind kind = SupplyInventoryPolicy.Kind.OTHER;
        if (stack.isEmpty()) kind = SupplyInventoryPolicy.Kind.EMPTY;
        else if (stack.getFoodProperties(human) != null && RecoverySupplies.isRecoverySupply(human, stack))
            kind = SupplyInventoryPolicy.Kind.FOOD;
        else if (MedicalSupplies.isMedicine(stack) || (stack.is(Items.POTION)
                && PotionUtils.getMobEffects(stack).stream().anyMatch(effect ->
                effect.getEffect() == MobEffects.HEAL || effect.getEffect() == MobEffects.REGENERATION)))
            kind = SupplyInventoryPolicy.Kind.MEDICINE;
        else if (stack.getItem() instanceof ArmorItem) kind = switch (preferredEquipmentSlot(stack)) {
            case HEAD -> SupplyInventoryPolicy.Kind.HEAD;
            case CHEST -> SupplyInventoryPolicy.Kind.CHEST;
            case LEGS -> SupplyInventoryPolicy.Kind.LEGS;
            case FEET -> SupplyInventoryPolicy.Kind.FEET;
            default -> SupplyInventoryPolicy.Kind.OTHER;
        };
        else if (isDedicatedMeleeWeapon(stack) || stack.getItem() instanceof TridentItem)
            kind = SupplyInventoryPolicy.Kind.MELEE;
        return new SupplyInventoryPolicy.Slot(kind, inventoryValue(human, stack),
                Math.min(stack.getCount(), stack.getMaxStackSize()), protectedFromEviction(stack));
    }

    private static SupplyInventoryPolicy supplyReservation(Human human, HumanData data) {
        List<SupplyInventoryPolicy.Slot> slots = new ArrayList<>(data.getInventoryItemsSize());
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            var slot = supplySlot(human, data.getInventoryItem(i));
            if (HumanInventoryCustody.isReservedForRecovery(human, i))
                slot = new SupplyInventoryPolicy.Slot(slot.kind(), slot.value(), slot.count(), true);
            slots.add(slot);
        }
        return new SupplyInventoryPolicy(slots);
    }

    /** Lazy capacity/eviction summary; valid only during one non-mutating batch. */
    public static final class StorageSummary {
        private final int size;
        private final IntFunction<ItemStack> items;
        private final Predicate<ItemStack> protectedItems;
        private final ToDoubleFunction<ItemStack> value;
        private Human human;
        private SupplyInventoryPolicy reservation;
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

        public StorageSummary(Human human, HumanData data) {
            this(data.getInventoryItemsSize(), data::getInventoryItem,
                    HumanLootManager::protectedFromEviction, stack -> inventoryValue(human, stack));
            this.human = human;
        }

        public boolean canStore(ItemStack incoming, double incomingValue) {
            if (!prepared) prepare();
            if (emptySlot && (reservation == null || reservation.availableEmptySlots(supplySlot(human, incoming)) > 0)) return true;
            List<ItemStack> matches = mergeable.get(incoming.getItem());
            if (matches != null) for (ItemStack stored : matches) {
                if (ItemStack.isSameItemSameTags(stored, incoming)) return true;
            }
            if (reservation != null) {
                SupplyInventoryPolicy.Slot candidate = supplySlot(human, incoming);
                for (int i = 0; i < stacks.size(); i++) if (reservation.canReplace(i, candidate)) return true;
                return false;
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
                    if (human == null) return;
                }
                stacks.add(stack);
            }
            if (human != null) {
                List<SupplyInventoryPolicy.Slot> slots = new ArrayList<>(size);
                for (ItemStack stack : stacks) slots.add(supplySlot(human, stack));
                reservation = new SupplyInventoryPolicy(slots);
            }
            for (ItemStack stored : stacks) {
                if (!stored.isEmpty() && stored.getCount() < stored.getMaxStackSize()) {
                    mergeable.computeIfAbsent(stored.getItem(), ignored -> new ArrayList<>()).add(stored);
                }
            }
        }
    }

    /** Mutates {@code incoming}; success means at least one item was stored. */
    public static boolean storeWithEviction(Human human, ItemStack incoming) {
        HumanData data = human.getData();
        if (data == null || incoming.isEmpty() || !HumanGunAcceptance.accepts(incoming)) {
            return false;
        }
        int originalCount = incoming.getCount();
        for (int i = 0; i < data.getInventoryItemsSize() && !incoming.isEmpty(); i++) {
            if (HumanInventoryCustody.isReservedForRecovery(human, i)) continue;
            ItemStack stored = data.getInventoryItem(i);
            if (ItemStack.isSameItemSameTags(stored, incoming)
                    && stored.getCount() < stored.getMaxStackSize()) {
                int moved = Math.min(incoming.getCount(), stored.getMaxStackSize() - stored.getCount());
                stored.grow(moved);
                incoming.shrink(moved);
                data.setInventoryItem(i, stored);
            }
        }
        int allowedEmptySlots = supplyReservation(human, data).availableEmptySlots(supplySlot(human, incoming));
        for (int i = 0; i < data.getInventoryItemsSize() && !incoming.isEmpty() && allowedEmptySlots > 0; i++) {
            if (HumanInventoryCustody.isReservedForRecovery(human, i)) continue;
            if (!data.getInventoryItem(i).isEmpty()) {
                continue;
            }
            int moved = Math.min(incoming.getCount(), incoming.getMaxStackSize());
            data.setInventoryItem(i, incoming.copyWithCount(moved));
            incoming.shrink(moved);
            allowedEmptySlots--;
        }
        if (!incoming.isEmpty()) {
            SupplyInventoryPolicy reservation = supplyReservation(human, data);
            SupplyInventoryPolicy.Slot candidate = supplySlot(human, incoming);
            int lowestSlot = -1;
            double lowestValue = Double.POSITIVE_INFINITY;
            for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                double value = reservation.replacementCost(i, candidate);
                if (reservation.canReplace(i, candidate) && value < lowestValue) {
                    lowestSlot = i;
                    lowestValue = value;
                }
            }
            if (lowestSlot >= 0) {
                ItemStack evicted = data.getInventoryItem(lowestSlot).copy();
                ItemEntity dropped = new ItemEntity(
                        human.level(), human.getX(), human.getY() + 0.4D, human.getZ(), evicted
                );
                dropped.setPickUpDelay(60);
                if (!human.level().addFreshEntity(dropped)) {
                    return incoming.getCount() < originalCount;
                }
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
        SupplyInventoryPolicy reservation = supplyReservation(human, data);
        int lowestSlot = -1;
        double lowestValue = Double.POSITIVE_INFINITY;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stored = data.getInventoryItem(i);
            if (stored.isEmpty() || protectedFromEviction(stored) || reservation.isReserved(i)
                    || HumanInventoryCustody.isReservedForRecovery(human, i)) {
                continue;
            }
            // Retaining a large stack is worth somewhat more than retaining a
            // single item, without allowing quantity to outweigh strategic
            // equipment categories completely.
            double value = inventoryValue(human, stored)
                    + quantityPremium(human, stored);
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
     * never displaced, nor are retained food/medicine/backup reserves; a stored gun is the final fallback because the equipped
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
        SupplyInventoryPolicy reservation = supplyReservation(human, data);
        int bestSlot = -1;
        double bestValue = Double.POSITIVE_INFINITY;
        for (int pass = 0; pass < 2 && bestSlot < 0; pass++) {
            for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                ItemStack stack = data.getInventoryItem(i);
                if (stack.isEmpty() || stack.getItem() instanceof SoldierRecallCharmItem || HumanSpawnEquipment.isBoundGear(stack) || reservation.isReserved(i)) {
                    continue;
                }
                if (pass == 0 && dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(stack)) {
                    continue;
                }
                double value = inventoryValue(human, stack)
                        + quantityPremium(human, stack);
                if (value < bestValue) {
                    bestValue = value;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    private static boolean protectedFromEviction(ItemStack stack) {
        return stack.getItem() instanceof SoldierRecallCharmItem || HumanSpawnEquipment.isBoundGear(stack)
                || dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(stack)
                || RangedWeaponCustody.isBowOrCrossbow(stack)
                || SpartanEquipmentCompat.isShield(stack)
                || stack.getItem() instanceof IdentityBadgeItem;
    }
}

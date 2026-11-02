package dev.felix.hostilehumans.behavior.combat.ranged;

import dev.felix.hostilehumans.entity.HumanData;
import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.behavior.combat.CloseDefenseHold;
import dev.felix.hostilehumans.behavior.combat.CombatPreference;
import dev.felix.hostilehumans.behavior.combat.gun.GunCustody;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.gun.HumanGunAcceptance;
import dev.felix.hostilehumans.behavior.combat.retreat.RetreatLastStand;
import dev.felix.hostilehumans.inventory.HumanEquipmentExchange;
import dev.felix.hostilehumans.inventory.HumanInventoryCustody;
import dev.felix.hostilehumans.inventory.HumanInventorySession;
import dev.felix.hostilehumans.inventory.HumanLootManager;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

/** Selects real owned weapons, independently of optional firearm implementation types. */
public final class AmmoWeaponSelection {
    private AmmoWeaponSelection() {}

    public static boolean usableGun(Human human, ItemStack stack) {
        return HumanGunAcceptance.accepts(stack) && (HumanAmmo.canUseGun(human, stack)
                || (GunSupport.get().isGun(stack) && GunSupport.get().isReloading(human, stack)));
    }

    public static boolean hasUsableGun(Human human) {
        if (usableGun(human, human.getMainHandItem()) || usableGun(human, human.getOffhandItem())) return true;
        HumanData data = human.getData();
        if (data != null) for (int slot = 0; slot < data.getInventoryItemsSize(); slot++) {
            if (usableGun(human, data.getInventoryItem(slot))) return true;
        }
        return false;
    }

    public static boolean isExhausted(Human human, ItemStack stack) {
        return (GunSupport.get().isGun(stack) && !usableGun(human, stack))
                || (RangedWeaponCustody.isBowOrCrossbow(stack) && !HumanAmmo.hasArrows(human));
    }

    static int rank(boolean gun, boolean usableGun, boolean bow, boolean arrows, boolean melee) {
        if (gun) return usableGun ? 3 : 0;
        if (bow) return arrows ? 2 : 0;
        return melee ? 1 : 0;
    }

    public static int rank(Human human, ItemStack stack, boolean arrows) {
        int fallback = rank(GunSupport.get().isGun(stack), usableGun(human, stack),
                RangedWeaponCustody.isBowOrCrossbow(stack), arrows,
                HumanLootManager.isDedicatedMeleeWeapon(stack) || stack.getItem() instanceof TridentItem);
        if (fallback == 0) return 0;
        boolean preferred = switch (CombatPreference.get(human)) {
            case DEFAULT -> false;
            case GUN -> GunSupport.get().isGun(stack);
            case BOW -> RangedWeaponCustody.isBowWeapon(stack);
            case CROSSBOW -> RangedWeaponCustody.isCrossbowWeapon(stack);
            case TRIDENT -> stack.getItem() instanceof TridentItem;
            case MELEE -> HumanLootManager.isDedicatedMeleeWeapon(stack);
        };
        return preferred ? fallback + 8 : fallback;
    }

    public static boolean mayRestore(Human human, ItemStack candidate) {
        boolean arrows = HumanAmmo.hasArrows(human);
        int candidateRank = rank(human, candidate, arrows);
        return candidateRank > 0 && candidateRank >= rank(human, human.getMainHandItem(), arrows);
    }
    static boolean gunHasPriority(Human human) {
        boolean arrows = HumanAmmo.hasArrows(human);
        int gun = 0, best = 0;
        java.util.List<ItemStack> actual = new java.util.ArrayList<>();
        actual.add(human.getMainHandItem()); actual.add(human.getOffhandItem());
        HumanData data = human.getData();
        if (data != null) for (int i = 0; i < data.getInventoryItemsSize(); i++) actual.add(data.getInventoryItem(i));
        for (ItemStack stack : actual) {
            int value = rank(human, stack, arrows);
            best = Math.max(best, value);
            if (GunSupport.get().isGun(stack)) gun = Math.max(gun, value);
        }
        return gun > 0 && gun >= best;
    }
    public static void preferenceChanged(Human human) {
        HumanBehaviorControl.cancel(human, "combat preference changed");
        GunCustody.adoptManualLoadout(human);
        RangedWeaponCustody.adoptManualLoadout(human);
    }

    public static void tick(Human human) {
        if (human.level().isClientSide || HumanInventorySession.isEditing(human)) return;
        HumanData data = human.getData();
        if (data == null) return;
        if (CloseDefenseHold.shouldHold(human) && !isExhausted(human, human.getMainHandItem())) return;
        // A stale draw must not keep an exhausted off-hand bow blocking melee.
        if (human.isUsingItem() && isExhausted(human, human.getUseItem())) human.stopUsingItem();
        if (human.isUsingItem() || (HumanGunAcceptance.accepts(human.getMainHandItem())
                && GunSupport.get().isReloading(human))) return;

        ItemStack current = human.getMainHandItem();
        boolean exhausted = isExhausted(human, current);
        boolean closeLease = GunCustody.isActive(human) || RangedWeaponCustody.isActive(human)
                || RetreatLastStand.permitsMelee(human);
        if (exhausted || !closeLease) {
            boolean arrows = HumanAmmo.hasArrows(human);
            int currentRank = rank(human, current, arrows);
            int bestRank = currentRank;
            double bestScore = HumanLootManager.mainHandWeaponScore(current);
            int bestSlot = -1;
            boolean useOffhand = false;
            ItemStack offhand = human.getOffhandItem();
            int offRank = rank(human, offhand, arrows);
            // Only promote ranged off-hand items; off-hand tools are explicit loadout choices.
            if (offRank >= 2 && offRank > bestRank) {
                bestRank = offRank;
                bestScore = HumanLootManager.mainHandWeaponScore(offhand);
                useOffhand = true;
            }
            for (int slot = 0; slot < data.getInventoryItemsSize(); slot++) {
                if (HumanInventoryCustody.isReservedForRecovery(data, slot)) continue;
                ItemStack candidate = data.getInventoryItem(slot);
                int candidateRank = rank(human, candidate, arrows);
                if (candidateRank == 0 || candidateRank < bestRank) continue;
                double score = HumanLootManager.mainHandWeaponScore(candidate);
                if (candidateRank > bestRank || ((bestSlot >= 0 || useOffhand) && candidateRank == bestRank
                        && score > bestScore + 1.5D)) {
                    bestRank = candidateRank;
                    bestScore = score;
                    bestSlot = slot;
                    useOffhand = false;
                }
            }
            if (useOffhand || bestSlot >= 0) {
                ItemStack selected = useOffhand ? offhand : data.getInventoryItem(bestSlot);
                if (HumanEquipmentExchange.request(human, "weapon-preference", java.util.List.of(
                        new HumanEquipmentExchange.Need(EquipmentSlot.MAINHAND, stack -> stack == selected)))
                        == HumanEquipmentExchange.Result.SUCCESS)
                    human.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
            } else if (exhausted) {
                stowExhausted(human, data, EquipmentSlot.MAINHAND);
            }
        }
        if (isExhausted(human, human.getOffhandItem())) {
            stowExhausted(human, data, EquipmentSlot.OFFHAND);
        }
    }

    private static void stowExhausted(Human human, HumanData data, EquipmentSlot hand) {
        HumanEquipmentExchange.stow(human, hand);
    }
}

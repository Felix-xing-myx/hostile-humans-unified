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
        return rank(human, stack, arrows, CombatPreference.get(human));
    }

    private static int rank(Human human, ItemStack stack, boolean arrows, CombatPreference preference) {
        if (stack.isEmpty()) return 0;
        boolean gun = GunSupport.get().isGun(stack);
        boolean bow = !gun && RangedWeaponCustody.isBowOrCrossbow(stack);
        boolean melee = !gun && !bow && HumanLootManager.isDedicatedMeleeWeapon(stack);
        if ((bow || gun && !GunSupport.get().hasLoadedAmmo(stack))
                && HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem())
                && dev.felix.hostilehumans.behavior.combat.HumanCombatDirector.mobileMeleeThreat(human)) return 0;
        int fallback = rank(gun, gun && usableGun(human, stack), bow, arrows,
                melee || stack.getItem() instanceof TridentItem);
        if (fallback == 0) return 0;
        boolean preferred = switch (preference) {
            case DEFAULT -> false;
            case GUN -> gun;
            case BOW -> RangedWeaponCustody.isBowWeapon(stack);
            case CROSSBOW -> RangedWeaponCustody.isCrossbowWeapon(stack);
            case TRIDENT -> stack.getItem() instanceof TridentItem;
            case MELEE -> melee;
        };
        return preferred ? fallback + 8 : fallback;
    }

    public static boolean mayRestore(Human human, ItemStack candidate) {
        boolean arrows = HumanAmmo.hasArrows(human);
        CombatPreference preference = CombatPreference.get(human);
        int candidateRank = rank(human, candidate, arrows, preference);
        return candidateRank > 0 && candidateRank >= rank(human, human.getMainHandItem(), arrows, preference);
    }
    static boolean gunHasPriority(Human human) {
        // A usable gun outranks all ordinary alternatives. Only a chosen non-gun preference can beat it.
        if (!hasUsableGun(human)) return false;
        CombatPreference preference = CombatPreference.get(human);
        if (preference == CombatPreference.DEFAULT || preference == CombatPreference.GUN) return true;
        boolean arrows = (preference == CombatPreference.BOW || preference == CombatPreference.CROSSBOW)
                && HumanAmmo.hasArrows(human);
        if (rank(human, human.getMainHandItem(), arrows, preference) > 3
                || rank(human, human.getOffhandItem(), arrows, preference) > 3) return false;
        HumanData data = human.getData();
        if (data != null) for (int slot = 0; slot < data.getInventoryItemsSize(); slot++) {
            if (rank(human, data.getInventoryItem(slot), arrows, preference) > 3) return false;
        }
        return true;
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
        // Stable frontline keeps using its current weapon between reviews. Exhaustion, empty hands
        // and the existing active custody/reload rules still take effect without waiting.
        boolean review = current.isEmpty() || !dev.felix.hostilehumans.perception.HumanPerception.steadyFrontline(human)
                || Math.floorMod(human.tickCount + (human.getId() & 7), 8) == 0;
        if (exhausted || !closeLease && review) {
            boolean arrows = HumanAmmo.hasArrows(human);
            CombatPreference preference = CombatPreference.get(human);
            int currentRank = rank(human, current, arrows, preference);
            int bestRank = currentRank;
            double bestScore = HumanLootManager.mainHandWeaponScore(current);
            int bestSlot = -1;
            boolean useOffhand = false;
            ItemStack offhand = human.getOffhandItem();
            int offRank = rank(human, offhand, arrows, preference);
            // Only promote ranged off-hand items; off-hand tools are explicit loadout choices.
            if (offRank >= 2 && offRank > bestRank) {
                bestRank = offRank;
                bestScore = HumanLootManager.mainHandWeaponScore(offhand);
                useOffhand = true;
            }
            for (int slot = 0; slot < data.getInventoryItemsSize(); slot++) {
                if (HumanInventoryCustody.isReservedForRecovery(data, slot)) continue;
                ItemStack candidate = data.getInventoryItem(slot);
                int candidateRank = rank(human, candidate, arrows, preference);
                if (candidateRank == 0 || candidateRank < bestRank) continue;
                if (candidateRank == bestRank && bestSlot < 0 && !useOffhand) continue;
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

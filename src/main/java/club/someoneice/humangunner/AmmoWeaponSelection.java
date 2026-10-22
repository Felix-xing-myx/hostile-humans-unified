package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

/** Selects real owned weapons, independently of optional firearm implementation types. */
final class AmmoWeaponSelection {
    private AmmoWeaponSelection() {}

    static boolean usableGun(Human human, ItemStack stack) {
        return HumanGunAcceptance.accepts(stack) && (HumanAmmo.canUseGun(human, stack)
                || (GunSupport.get().isGun(stack) && GunSupport.get().isReloading(human, stack)));
    }

    static boolean hasUsableGun(Human human) {
        if (usableGun(human, human.getMainHandItem()) || usableGun(human, human.getOffhandItem())) return true;
        HumanData data = human.getData();
        if (data != null) for (int slot = 0; slot < data.getInventoryItemsSize(); slot++) {
            if (usableGun(human, data.getInventoryItem(slot))) return true;
        }
        return false;
    }

    static boolean isExhausted(Human human, ItemStack stack) {
        return (GunSupport.get().isGun(stack) && !usableGun(human, stack))
                || (RangedWeaponCustody.isBowOrCrossbow(stack) && !HumanAmmo.hasArrows(human));
    }

    static int rank(boolean gun, boolean usableGun, boolean bow, boolean arrows, boolean melee) {
        if (gun) return usableGun ? 3 : 0;
        if (bow) return arrows ? 2 : 0;
        return melee ? 1 : 0;
    }

    static int rank(Human human, ItemStack stack, boolean arrows) {
        int fallback = rank(GunSupport.get().isGun(stack), usableGun(human, stack),
                RangedWeaponCustody.isBowOrCrossbow(stack), arrows,
                HumanLootManager.isDedicatedMeleeWeapon(stack) || stack.getItem() instanceof TridentItem);
        if (fallback == 0) return 0;
        boolean preferred = switch (CombatPreference.get(human)) {
            case DEFAULT -> false;
            case GUN -> GunSupport.get().isGun(stack);
            case BOW -> stack.getItem() instanceof net.minecraft.world.item.BowItem;
            case CROSSBOW -> stack.getItem() instanceof net.minecraft.world.item.CrossbowItem;
            case TRIDENT -> stack.getItem() instanceof TridentItem;
            case MELEE -> HumanLootManager.isDedicatedMeleeWeapon(stack);
        };
        return preferred ? fallback + 8 : fallback;
    }

    static void tick(Human human) {
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
        boolean closeLease = GunCustody.isActive(human) || RangedWeaponCustody.isActive(human);
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

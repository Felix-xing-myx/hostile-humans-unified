package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import net.minecraft.world.item.ItemStack;

/** Grants a finite, persistent one-to-three-shield loadout at spawn. */
final class GuaranteedShieldLoadout {
    private static final String GENERATED = "humangunner:guaranteed_shields_generated";
    private static final String TARGET_COUNT = "humangunner:spawn_shield_count";

    private GuaranteedShieldLoadout() {
    }

    static void applyOnce(Human human) {
        if (human.getPersistentData().getBoolean(GENERATED)) {
            return;
        }
        HumanData data = human.getData();
        if (data == null) {
            return;
        }
        int target = human.getPersistentData().getInt(TARGET_COUNT);
        if (target < 1 || target > 3) {
            target = human.getTier() == HumanTier.ROAMER
                    ? 1 : human.getRandom().nextInt(1, 4);
            human.getPersistentData().putInt(TARGET_COUNT, target);
        }

        int owned = countShields(human, data);
        while (owned < target) {
            ItemStack shield = SpartanEquipmentCompat.createShieldFor(human);
            if (shield.isEmpty()) {
                break;
            }
            if (!SpartanEquipmentCompat.isShield(human.getOffhandItem())) {
                SpartanEquipmentCompat.equipOrStoreShield(human, shield);
            } else if (!HumanLootManager.storeWithEviction(human, shield.copy())) {
                break;
            }
            owned = countShields(human, data);
        }
        // A failed insertion must remain retryable. Marking completion before
        // the transaction succeeded could permanently violate the guaranteed
        // spawn loadout after one transient/full-inventory failure.
        if (owned >= target) {
            human.getPersistentData().putBoolean(GENERATED, true);
        }
    }

    private static int countShields(Human human, HumanData data) {
        int count = SpartanEquipmentCompat.isShield(human.getMainHandItem())
                ? human.getMainHandItem().getCount() : 0;
        if (SpartanEquipmentCompat.isShield(human.getOffhandItem())) {
            count += human.getOffhandItem().getCount();
        }
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stack = data.getInventoryItem(i);
            if (SpartanEquipmentCompat.isShield(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}

package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.item.ItemStack;

/** Grants the configured finite, persistent zero-to-three-shield loadout at spawn. */
final class GuaranteedShieldLoadout {
    private static final String GENERATED = "hostile_humans_unified:guaranteed_shields_generated";
    private static final String TARGET_COUNT = "hostile_humans_unified:spawn_shield_count";

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
        int target;
        if (human.getPersistentData().contains(TARGET_COUNT)) target = human.getPersistentData().getInt(TARGET_COUNT);
        else {
            target = FactionEquipmentPolicy.choose(FactionEquipmentConfig.of(human).shieldCounts(),human.getRandom().nextDouble());
            if (target < 0) target=0;
            human.getPersistentData().putInt(TARGET_COUNT,target);
        }

        int owned = countShields(human, data);
        while (owned < target) {
            ItemStack shield = ConfiguredHumanEquipment.shield(human);
            if (shield.isEmpty()) {
                break;
            }
            if (!SpartanEquipmentCompat.isShield(human.getOffhandItem())) {
                ItemStack displaced=human.getOffhandItem().copy();
                if(!displaced.isEmpty() && !RecoverySupplies.insertInventory(data,displaced)) break;
                human.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,shield);
                human.setDropChance(net.minecraft.world.entity.EquipmentSlot.OFFHAND,1.0F);
            } else if (!RecoverySupplies.insertInventory(data, shield.copy())) {
                break;
            }
            int next=countShields(human,data);
            if(next<=owned) break;
            owned=next;
        }
        human.getPersistentData().putInt("hostile_humans_unified:spawn_shield_shortfall",Math.max(0,target-owned));
        // This is a once-only spawn grant; capacity must not turn into repeated future gifts.
        human.getPersistentData().putBoolean(GENERATED,true);
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

package dev.felix.hostilehumans.equipment;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One independent birth roll; never a hand-layout choice or a growth-quality roll. */
final class SpawnTotemSupply {
    private static final String ROLLED = HumanRuntime.MOD_ID + ":birth_totem_rolled";
    private SpawnTotemSupply() { }

    static double defaultChance(String rank) {
        return switch (rank) { case "tier2" -> .10; case "tier3" -> .20; default -> .05; };
    }

    static void grantOnce(Human human) {
        if (human.level().isClientSide || human.getData() == null) return;
        var tag = human.getPersistentData();
        if (tag.getBoolean(ROLLED)) return;
        // Capacity failure is still a settled birth roll, never a later free gift.
        tag.putBoolean(ROLLED, true);
        if (human.getRandom().nextDouble() < ConfiguredHumanEquipment.of(human).totemChance)
            RecoverySupplies.insertInventory(human.getData(), new ItemStack(Items.TOTEM_OF_UNDYING));
    }
}

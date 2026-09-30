package club.someoneice.humangunner;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Item-level regression only; this does not launch a Minecraft client/server. */
public final class LootScreeningTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        // Standalone JavaExec has no Forge event-bus bytecode transformation.
        // Initialize only item registries, not Bootstrap's network hook.
        var bootstrapFlag = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapFlag.setAccessible(true);
        bootstrapFlag.setBoolean(null, true);
        BuiltInRegistries.bootStrap();
        ItemStack equipped = new ItemStack(Items.IRON_CHESTPLATE);
        ItemStack snapshot = equipped.copy();
        check(ValuableItemPickupGoal.sameScreeningEquipment(snapshot, equipped), "unchanged equipment");
        equipped.setDamageValue(1);
        check(!ValuableItemPickupGoal.sameScreeningEquipment(snapshot, equipped), "in-place durability edit invalidates screening");
        snapshot = equipped.copy();
        equipped.enchant(Enchantments.UNBREAKING, 3);
        check(!ValuableItemPickupGoal.sameScreeningEquipment(snapshot, equipped), "in-place enchantment edit invalidates screening");
        check(!ValuableItemPickupGoal.sameScreeningEquipment(equipped.copy(), ItemStack.EMPTY), "broken/removed equipment invalidates screening");
        check(!ValuableItemPickupGoal.sameScreeningEquipment(null, ItemStack.EMPTY), "first selection has no stale snapshot");
        check(ValuableItemPickupGoal.sameScreeningEquipment(ItemStack.EMPTY, ItemStack.EMPTY), "stable empty slot");
        var types = List.of(MobEffects.HEAL, MobEffects.REGENERATION, MobEffects.DAMAGE_BOOST,
                MobEffects.MOVEMENT_SPEED, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS,
                MobEffects.FIRE_RESISTANCE);
        for (int mask = 0; mask < 128; mask++) {
            List<MobEffectInstance> effects = new ArrayList<>();
            for (int i = 0; i < types.size(); i++) if ((mask & (1 << i)) != 0) effects.add(new MobEffectInstance(types.get(i), 200));
            for (boolean splash : new boolean[]{false, true}) {
                double expected = (mask & 1) != 0 ? 115 : (mask & 2) != 0 ? 108 : (mask & 4) != 0 ? 96
                        : (mask & 8) != 0 ? 88 : splash && (mask & 16) != 0 ? 92
                        : splash && (mask & 32) != 0 ? 86 : (mask & 64) != 0 ? 70 : 20;
                check(HumanLootManager.potionValue(effects, splash) == expected, "single-pass value preserves original effect priority");
                Collections.reverse(effects);
                check(HumanLootManager.potionValue(effects, splash) == expected, "mixed-effect list order cannot change priority");
            }
        }
        Random random = new Random(84321);
        for (int trial = 0; trial < 200; trial++) {
            List<ItemStack> inventory = new ArrayList<>();
            Map<ItemStack, Double> values = new IdentityHashMap<>();
            for (int slot = 0; slot < 30; slot++) {
                ItemStack stack = new ItemStack(slot % 3 == 0 ? Items.DIRT : Items.COBBLESTONE,
                        random.nextInt(5) == 0 ? 32 : 64);
                if (random.nextInt(5) == 0) stack.getOrCreateTag().putInt("variant", random.nextInt(4));
                inventory.add(stack);
                values.put(stack, random.nextInt(100) * 1.0);
            }
            var summary = new HumanLootManager.StorageSummary(inventory.size(), inventory::get,
                    stack -> stack.getItem() == Items.DIRT, values::get);
            for (int candidate = 0; candidate < 64; candidate++) {
                ItemStack incoming = new ItemStack(candidate % 3 == 0 ? Items.DIRT : candidate % 3 == 1 ? Items.COBBLESTONE : Items.STONE);
                if (candidate % 5 == 0) incoming.getOrCreateTag().putInt("variant", random.nextInt(4));
                double incomingValue = random.nextInt(100);
                boolean expected = false;
                for (ItemStack stored : inventory) {
                    if (stored.isEmpty() || (ItemStack.isSameItemSameTags(stored, incoming)
                            && stored.getCount() < stored.getMaxStackSize())
                            || (stored.getItem() != Items.DIRT && values.get(stored) + 1 < incomingValue)) {
                        expected = true;
                        break;
                    }
                }
                check(summary.canStore(incoming, incomingValue) == expected, "batch capacity summary matches original full-slot decision");
            }
        }
        AtomicInteger reads = new AtomicInteger(), scores = new AtomicInteger();
        var full = new HumanLootManager.StorageSummary(30,
                i -> { reads.incrementAndGet(); return new ItemStack(Items.COBBLESTONE, 64); },
                stack -> false, stack -> { scores.incrementAndGet(); return 10; });
        for (int i = 0; i < 64; i++) check(full.canStore(new ItemStack(Items.STONE), 12), "cheaper item remains evictable");
        check(reads.get() == 30 && scores.get() == 30, "64 candidates read and score the backpack only once");
        var empty = new HumanLootManager.StorageSummary(30, i -> ItemStack.EMPTY,
                stack -> { throw new AssertionError("empty inventory protection lookup"); },
                stack -> { throw new AssertionError("empty inventory valuation"); });
        check(empty.canStore(new ItemStack(Items.STONE), 1), "empty slot needs no equipment valuation");
        var mergeOnly = new HumanLootManager.StorageSummary(1, i -> new ItemStack(Items.DIRT, 32),
                stack -> true, stack -> { throw new AssertionError("merge should not score protected item"); });
        check(mergeOnly.canStore(new ItemStack(Items.DIRT), 0), "merge into protected stack is still allowed");
        check(!mergeOnly.canStore(new ItemStack(Items.STONE), 1000), "protected item cannot be evicted");
        System.out.println("LootScreeningTest: 13386 checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

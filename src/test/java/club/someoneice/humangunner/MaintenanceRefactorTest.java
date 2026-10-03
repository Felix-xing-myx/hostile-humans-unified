package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.data.HumanHelper;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.zip.ZipFile;

/** Real inventory/codec and packaged-entrypoint checks; never starts a world or game. */
public final class MaintenanceRefactorTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        var flag = Bootstrap.class.getDeclaredField("isBootstrapped");
        flag.setAccessible(true);
        flag.setBoolean(null, true);
        BuiltInRegistries.bootStrap();
        Random random = new Random(3855);
        for (int trial = 0; trial < 500; trial++) {
            CompoundTag record = new CompoundTag();
            record.putUUID("UUID", new UUID(0, trial));
            HumanData data = new HumanData(record);
            int total = 0;
            int capacity = 0;
            List<ItemStack> before = new java.util.ArrayList<>();
            for (int slot = 0; slot < 30; slot++) {
                int n = random.nextInt(65);
                ItemStack stack = n == 0 ? ItemStack.EMPTY : new ItemStack(Items.COBBLESTONE, n);
                if (n > 0 && slot % 3 == 0) stack.getOrCreateTag().putInt("variant", 1);
                data.setInventoryItem(slot, stack);
                before.add(stack.copy());
                total += n;
                if (stack.isEmpty()) capacity += 64;
                else if (!stack.hasTag()) capacity += 64 - n;
            }
            ItemStack incoming = new ItemStack(Items.COBBLESTONE, 1 + random.nextInt(1024));
            int supplied = incoming.getCount();
            boolean accepted = HumanInventoryCustody.storePickupTransactionally(data, incoming);
            check(accepted == (capacity >= supplied), "full backpack capacity and NBT-aware merging");
            check(incoming.getCount() == supplied, "pickup source is owned by caller, never partly consumed");
            int after = 0;
            for (int slot = 0; slot < 30; slot++) {
                after += data.getInventoryItem(slot).getCount();
                if (!accepted) check(ItemStack.matches(before.get(slot), data.getInventoryItem(slot)),
                        "failed insertion leaves every slot unchanged");
            }
            check(after == total + (accepted ? supplied : 0), "item conservation");
        }
        for (String key : List.of("Armor", "Hand", "Inventory")) {
            int size = key.equals("Armor") ? 4 : key.equals("Hand") ? 2 : 30;
            NonNullList<ItemStack> slots = NonNullList.withSize(size, ItemStack.EMPTY);
            ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
            named.setDamageValue(9);
            named.getOrCreateTag().putString("test", "preserve NBT");
            slots.set(size - 1, named);
            CompoundTag saved = new CompoundTag();
            save(key, saved, slots);
            NonNullList<ItemStack> restored = NonNullList.withSize(size, ItemStack.EMPTY);
            load(key, saved, restored);
            check(ItemStack.matches(named, restored.get(size - 1)), "last slot survives codec: " + key);
            for (int slot = 0; slot < size; slot++) slots.set(slot, ItemStack.EMPTY);
            save(key, saved, slots);
            check(!saved.contains(key), "reused tag removes stale nonempty list: " + key);
            load(key, saved, restored);
            check(restored.stream().allMatch(ItemStack::isEmpty), "removed gear cannot resurrect: " + key);
            CompoundTag bad = new ItemStack(Items.DIRT).save(new CompoundTag());
            bad.putByte("Slot", (byte) 255);
            ListTag list = new ListTag();
            list.add(bad);
            saved.put(key, list);
            load(key, saved, restored);
            check(restored.stream().allMatch(ItemStack::isEmpty), "out-of-bounds unsigned slot ignored: " + key);
        }
        var config = new UnifiedConfig(new com.google.gson.JsonObject());
        int[] start = {3, 5, 10, 20}, finish = {40, 50, 70, 80};
        int rank = 0;
        for (String key : List.of("roamer", "tier1", "tier2", "tier3")) {
            var profile = config.equipmentGrowth().tiers().get(key);
            check(profile.startDay() == start[rank] && profile.fullQualityDay() == finish[rank],
                    "configured growth dates: " + key);
            check(profile.progress(Long.MIN_VALUE) == 0 && profile.progress(Long.MAX_VALUE) == 1,
                    "growth date bounds");
            double previous = 0;
            for (int day = 1; day <= finish[rank] + 10; day++) {
                double value = profile.grow(profile.earlyGunChanceMultiplier(), day);
                check(value >= previous && value >= 0 && value <= 1, "growth is monotonic and bounded");
                previous = value;
            }
            check(profile.grow(0.25, finish[rank]) == 1, "baseline reached exactly on full-quality day");
            rank++;
        }
        String human = "com/craftix/hostile_humans/entity/entities/Human";
        String gunner = "club/someoneice/humangunner/HumanGunner";
        check(calls(human, "equipWeapon", "swapRequestedSlot"), "equipment exchange uses formal inventory module");
        check(calls("club/someoneice/humangunner/MeleeSpacing", "control", "canStrike")
                        && calls("club/someoneice/humangunner/MeleeSpacing", "tryStep", "strafeMelee"),
                "spacing requires a hittable target and an explicit melee movement request");
        check(calls("com/craftix/hostile_humans/entity/ai/goal/MeleeAttackGoal", "canStrike", "canStrike")
                        && calls("club/someoneice/humangunner/MeleeCombatRange", "canStrike", "canStrike"),
                "pursuit/spacing and Better Combat share actual attack geometry");
        check(calls("club/someoneice/humangunner/HumanInventoryCustody", "stowOrDrop",
                "storePickupTransactionally"), "stowing cannot partly commit before a canceled world drop");
        check(calls("club/someoneice/humangunner/FirearmSpawnEquipment", "equip", "putItemAway")
                        && calls("club/someoneice/humangunner/SpartanEquipmentCompat", "equipOrStoreShield", "putItemAway"),
                "additional firearms and shields preserve existing equipment through custody");
        check(calls("club/someoneice/humangunner/GuaranteedShieldLoadout", "applyOnce", "shield")
                        && calls("club/someoneice/humangunner/HumanSpawnEquipment", "completeIfReady", "generateReserves"),
                "shield pools and bounded spawn reserves use the authoritative generation module");
        check(calls(human, "hurtCurrentlyUsedShield", "damageShield"), "vanilla blocks use shared shield policy");
        check(!calls(gunner, "initializeHumanIfReady", "ensureMeleeFallback")
                        && !calls(gunner, "onLivingTick", "ensureMeleeFallback"),
                "loaded/active soldiers never manufacture replacement melee weapons");
        check(calls("club/someoneice/humangunner/HumanSpawnEquipment", "completeIfReady", "ensureMeleeFallback"),
                "melee reserves are generated only by spawn completion");
        try (ZipFile jar = new ZipFile(args[0])) {
            for (String removed : List.of(
                    "com/craftix/hostile_humans/patch/HostileHumansEquipmentPatch.class",
                    "com/craftix/hostile_humans/entity/entities/HumanInventoryGenerator.class",
                    "com/craftix/hostile_humans/entity/ai/goal/FindWaterOnFireGoal.class",
                    "com/craftix/hostile_humans/entity/ai/goal/TridentAttackGoal.class",
                    "com/craftix/hostile_humans/entity/ai/goal/HumanGoal.class",
                    "com/craftix/hostile_humans/entity/ai/goal/LookForChestGoal.class",
                    "com/craftix/hostile_humans/entity/ai/control/HumanEntityWalkControl.class",
                    "com/craftix/hostile_humans/entity/loadout/HumanLoadoutManager.class",
                    "club/someoneice/humangunner/TierThreeLoadout.class",
                    "club/someoneice/humangunner/RangedSpawnChance.class",
                    "data/curios/slots/identity_badge.json",
                    "data/hostile_humans/human_loadouts/roamer.json"))
                check(jar.getEntry(removed) == null, "obsolete artifact absent: " + removed);
            for (String present : List.of("club/someoneice/humangunner/HumanSpawnEquipment.class",
                    "club/someoneice/humangunner/HumanInventoryCustody.class",
                    "club/someoneice/humangunner/ShieldDurability.class",
                    "data/humangunner/curios/slots/identity_badge.json",
                    "data/humangunner/curios/entities/identity_badge_players.json"))
                check(jar.getEntry(present) != null, "maintained module/resource packaged: " + present);
        }
        System.out.println("MaintenanceRefactorTest: " + checks + " inventory, codec, growth and architecture checks passed");
    }
    private static void save(String key, CompoundTag tag, NonNullList<ItemStack> items) {
        switch (key) {
            case "Armor" -> HumanHelper.saveArmorItems(tag, items);
            case "Hand" -> HumanHelper.saveHandItems(tag, items);
            default -> HumanHelper.saveInventoryItems(tag, items);
        }
    }
    private static void load(String key, CompoundTag tag, NonNullList<ItemStack> items) {
        switch (key) {
            case "Armor" -> HumanHelper.loadArmorItems(tag, items);
            case "Hand" -> HumanHelper.loadHandItems(tag, items);
            default -> HumanHelper.loadInventoryItems(tag, items);
        }
    }
    private static boolean calls(String owner, String name, String called) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = MaintenanceRefactorTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            if (stream == null) throw new AssertionError("Missing class: " + owner);
            new ClassReader(stream).accept(node, 0);
        }
        for (var method : node.methods) if (method.name.equals(name))
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && call.name.equals(called)) return true;
        return false;
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}

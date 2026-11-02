package dev.felix.hostilehumans.common;

import dev.felix.hostilehumans.testutil.ModuleTestSources;

import dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies;
import dev.felix.hostilehumans.item.VanillaPotionStacks;

import dev.felix.hostilehumans.entity.HumanData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Registry-only regression: production hook, real spawn insertion, overflow and NBT separation. */
public final class VanillaPotionStackingTest {
    public static void run() throws Exception {
        checkHook();
        check(!VanillaPotionStacks.applies(Items.GLASS_BOTTLE)
                && !VanillaPotionStacks.applies(Items.TIPPED_ARROW)
                && !VanillaPotionStacks.applies(Items.HONEY_BOTTLE),
                "unrelated bottles and tipped arrows retain their own limit");
        var field = Item.class.getDeclaredField("maxStackSize");
        field.setAccessible(true);
        for (Item item : new Item[]{Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION}) {
            check(VanillaPotionStacks.applies(item), "all three vanilla containers are covered");
            int original = field.getInt(item);
            // Standalone JavaExec does not apply Mixins: install only its verified return value,
            // then exercise the real Forge ItemStack and soldier inventory code below.
            try {
                field.setInt(item, VanillaPotionStacks.MAX_SIZE);
                ItemStack doses = PotionUtils.setPotion(new ItemStack(item, 9), Potions.HEALING);
                check(doses.getMaxStackSize() == 4 && doses.isStackable(), "native stack sees shared four-bottle limit");
                CompoundTag saved = new CompoundTag();
                saved.putUUID("UUID", java.util.UUID.randomUUID());
                HumanData data = new HumanData(saved);
                check(RecoverySupplies.insertInventory(data, doses) && doses.isEmpty(), "spawn insertion accepts nine doses");
                check(data.getInventoryItem(0).getCount() == 4 && data.getInventoryItem(1).getCount() == 4
                        && data.getInventoryItem(2).getCount() == 1, "overflow splits into 4/4/1");
                check(RecoverySupplies.insertInventory(data, PotionUtils.setPotion(new ItemStack(item, 3), Potions.HEALING))
                        && data.getInventoryItem(2).getCount() == 4, "same potion fills an existing partial stack");
                ItemStack stronger = PotionUtils.setPotion(new ItemStack(item), Potions.STRONG_HEALING);
                check(RecoverySupplies.insertInventory(data, stronger) && data.getInventoryItem(3).getCount() == 1,
                        "different potion strengths never merge");
                ItemStack custom = PotionUtils.setPotion(new ItemStack(item), Potions.HEALING);
                custom.getOrCreateTag().putString("variant", "custom");
                check(RecoverySupplies.insertInventory(data, custom) && data.getInventoryItem(4).getCount() == 1,
                        "custom potion data never merges with plain healing");
                ItemStack restored = ItemStack.of(data.getInventoryItem(0).save(new CompoundTag()));
                check(restored.getCount() == 4 && ItemStack.isSameItemSameTags(restored, data.getInventoryItem(0)),
                        "native NBT round trip preserves dose count and effect");
            } finally { field.setInt(item, original); }
        }
        System.out.println("VanillaPotionStackingTest passed; live mixing, drinking and throwing still require acceptance");
    }

    private static void checkHook() throws Exception {
        var item = read("net/minecraft/world/item/Item");
        check(item.methods.stream().anyMatch(m -> m.name.equals("getMaxStackSize") && m.desc.equals("()I")),
                "native no-argument Item size getter exists");
        check(calls(read("net/minecraft/world/item/ItemStack"), "getMaxStackSize", "()I",
                "getMaxStackSize", "(Lnet/minecraft/world/item/ItemStack;)I"), "Forge stack delegates to per-item size hook");
        check(calls(read("net/minecraftforge/common/extensions/IForgeItem"), "getMaxStackSize",
                "(Lnet/minecraft/world/item/ItemStack;)I", "getMaxStackSize", "()I"), "default Forge hook delegates to Item getter");
        int found = 0;
        for (var method : read("dev/felix/hostilehumans/mixin/VanillaPotionStackSizeMixin").methods) {
            var annotations = new java.util.ArrayList<org.objectweb.asm.tree.AnnotationNode>();
            if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
            if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
            for (var annotation : annotations) if (annotation.desc.endsWith("/Inject;")) {
                var values = new java.util.HashMap<String, Object>();
                for (int i = 0; i < annotation.values.size(); i += 2)
                    values.put((String) annotation.values.get(i), annotation.values.get(i + 1));
                check(((java.util.List<?>) values.get("method")).contains("m_41459_()I")
                        && Boolean.FALSE.equals(values.get("remap")) && Boolean.TRUE.equals(values.get("cancellable"))
                        && Integer.valueOf(1).equals(values.get("require")) && Integer.valueOf(1).equals(values.get("allow")),
                        "required production SRG potion hook is explicit and unique");
                found++;
            }
        }
        check(found == 1, "one shared size hook");
    }
    private static boolean calls(ClassNode node, String method, String descriptor, String callee, String targetDescriptor) {
        for (var m : node.methods) if (m.name.equals(method) && m.desc.equals(descriptor))
            for (var insn : m.instructions) if (insn instanceof MethodInsnNode call
                    && call.name.equals(callee) && call.desc.equals(targetDescriptor)) return true;
        return false;
    }
    private static ClassNode read(String name) throws Exception {
        var node = new ClassNode();
        try (var input = VanillaPotionStackingTest.class.getClassLoader().getResourceAsStream(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(name) + ".class")) {
            if (input == null) throw new AssertionError(name);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}

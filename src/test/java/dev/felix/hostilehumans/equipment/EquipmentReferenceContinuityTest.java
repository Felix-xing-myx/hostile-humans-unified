package dev.felix.hostilehumans.equipment;

import dev.felix.hostilehumans.entity.HumanData;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Executes the actual compiled synchronization methods against live-list aliases, without a game world. */
public final class EquipmentReferenceContinuityTest {
    public static class EquipmentState {
        final NonNullList<ItemStack> hands = NonNullList.withSize(2, ItemStack.EMPTY);
        final NonNullList<ItemStack> armor = NonNullList.withSize(4, ItemStack.EMPTY);
        final HumanData data;
        boolean substitute;
        int syncs;
        public EquipmentState() {
            CompoundTag saved = new CompoundTag();
            saved.putUUID(HumanData.UUID_TAG, UUID.randomUUID());
            data = new HumanData(saved);
        }
        public HumanData getData() { return data; }
        public void setDataSyncNeeded() { syncs++; }
        public ItemStack getItemBySlot(EquipmentSlot slot) {
            return (slot.getType() == EquipmentSlot.Type.ARMOR ? armor : hands).get(slot.getIndex());
        }
        public ItemStack getItemInHand(InteractionHand hand) {
            return getItemBySlot(hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
            // Models a native equipment callback substituting the installed stack.
            (slot.getType() == EquipmentSlot.Type.ARMOR ? armor : hands)
                    .set(slot.getIndex(), substitute && !stack.isEmpty() ? stack.copy() : stack);
        }
        public void setItemInHand(InteractionHand hand, ItemStack stack) {
            setItemSlot(hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, stack);
        }
    }

    private static EquipmentState fixture(boolean legacyCopies) throws Exception {
        String original = "dev/felix/hostilehumans/entity/HumanMobEntityData";
        String parent = EquipmentState.class.getName().replace('.', '/');
        String name = "dev/felix/hostilehumans/EquipmentReferenceFixture";
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, parent, null);
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(0, 0); constructor.visitEnd();
        for (var method : node.methods) {
            if (!method.name.equals("setItemSlot") && !method.name.equals("setItemInHand")) continue;
            for (var instruction : method.instructions.toArray()) if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(original))) call.owner = name;
                if (call.owner.equals(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(node.superName))) call.owner = parent;
                if (legacyCopies && call.owner.endsWith("/HumanData")
                        && (call.name.equals("setHandItem") || call.name.equals("setArmorItem")))
                    method.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                            "net/minecraft/world/item/ItemStack", "copy", "()Lnet/minecraft/world/item/ItemStack;", false));
            }
            method.accept(writer);
        }
        writer.visitEnd();
        class Loader extends ClassLoader {
            Loader() { super(EquipmentReferenceContinuityTest.class.getClassLoader()); }
            Class<?> define(byte[] bytes) { return defineClass(name.replace('/', '.'), bytes, 0, bytes.length); }
        }
        return (EquipmentState) new Loader().define(writer.toByteArray()).getConstructor().newInstance();
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        // JavaExec has no Forge event transformation; initialize item data without its network bootstrap.
        var bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true); bootstrapped.setBoolean(null, true);
        BuiltInRegistries.bootStrap();
        EquipmentState legacy = fixture(true);
        legacy.data.setHandItems(legacy.hands);
        ItemStack original = new ItemStack(Items.IRON_SWORD);
        legacy.setItemSlot(EquipmentSlot.MAINHAND, original);
        check(legacy.getItemBySlot(EquipmentSlot.MAINHAND) != original,
                "old copy write replaces the installed object through an aliased list");
        for (boolean aliased : new boolean[]{false, true}) {
            EquipmentState state = fixture(false);
            if (aliased) { state.data.setHandItems(state.hands); state.data.setArmorItems(state.armor); }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = new ItemStack(slot.getType() == EquipmentSlot.Type.ARMOR ? Items.IRON_HELMET : Items.IRON_SWORD);
                state.setItemSlot(slot, stack);
                ItemStack recorded = (slot.getType() == EquipmentSlot.Type.ARMOR
                        ? state.data.getArmorItems() : state.data.getHandItems()).get(slot.getIndex());
                check(state.getItemBySlot(slot) == stack && recorded == stack, "slot publication preserves real reference: " + slot);
                stack.setDamageValue(3);
                check(recorded.getDamageValue() == 3, "subsequent real wear stays visible to storage");
            }
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = new ItemStack(Items.SHIELD);
                state.setItemInHand(hand, stack);
                check(state.getItemInHand(hand) == stack, "hand setter does not copy the item");
            }
            state.substitute = true;
            ItemStack requested = new ItemStack(Items.IRON_SWORD);
            state.setItemInHand(InteractionHand.MAIN_HAND, requested);
            check(state.getItemInHand(InteractionHand.MAIN_HAND) != requested, "native substitution occurred");
            check(state.data.getHandItems().get(0) == state.getItemInHand(InteractionHand.MAIN_HAND),
                    "storage follows the actual native result, not the request parameter");
            check(state.syncs > 0, "server synchronization remains requested");
        }
        System.out.println("EquipmentReferenceContinuityTest passed; actual compiled setters, shared/separate storage and native substitution");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

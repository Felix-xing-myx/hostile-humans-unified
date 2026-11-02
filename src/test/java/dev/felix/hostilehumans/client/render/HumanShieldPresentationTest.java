package dev.felix.hostilehumans.client.render;

import dev.felix.hostilehumans.testutil.ModuleTestSources;

import net.minecraft.SharedConstants;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Actual shield selection and baked model arm writes; no world, window or OpenGL context. */
public final class HumanShieldPresentationTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        // Standalone JavaExec lacks Forge's event-bus transformation, like the existing registry tests.
        var bootstrapFlag = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapFlag.setAccessible(true);
        bootstrapFlag.setBoolean(null, true);
        net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        HumanSpectatorPresentationTest.main(args);
        ItemStack sword = new ItemStack(Items.IRON_SWORD), shield = new ItemStack(Items.SHIELD);
        check(HumanShieldPresentation.activeHand(0, true, sword, shield) == null, "holding a shield is not blocking");
        check(HumanShieldPresentation.activeHand(2, true, sword, shield) == InteractionHand.OFF_HAND,
                "server-confirmed offhand works without any local use timer");
        check(HumanShieldPresentation.activeHand(1, true, shield, sword) == InteractionHand.MAIN_HAND,
                "server-confirmed main hand works");
        check(HumanShieldPresentation.activeHand(2, true, sword, ItemStack.EMPTY) == null,
                "equipment not yet received cannot render a ghost shield");
        check(HumanShieldPresentation.activeHand(2, true, sword, new ItemStack(Items.BREAD)) == null,
                "old shield metadata cannot turn replacement food into a block");
        check(HumanShieldPresentation.activeHand(2, false, sword, shield) == null, "dead entity does not block");
        check(HumanShieldPresentation.activeHand(7, true, shield, shield) == null, "unknown hand fails closed");
        net.minecraft.client.renderer.item.ItemPropertyFunction original = (stack, level, entity, seed) -> .75F;
        check(HumanShieldPresentation.blockingProperty(original,
                new net.minecraft.resources.ResourceLocation("minecraft", "blocking"), shield, null) == original,
                "non-Human model contexts retain the exact original predicate");
        check(HumanShieldPresentation.blockingProperty(null,
                new net.minecraft.resources.ResourceLocation("minecraft", "blocking"), shield, null) == null,
                "missing native predicates are not invented");
        var root = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot();
        var model = new PlayerModel<LivingEntity>(root, false);
        model.rightArm.xRot = .73F;
        model.leftArm.xRot = 2; model.leftArm.yRot = 1; model.leftArm.zRot = 1;
        model.leftArm.y = 5;
        HumanShieldPresentation.applyArm(model, HumanoidArm.LEFT);
        near(model.leftArm.xRot, -.9424779F, "left shield arm raised after arbitrary weapon pose");
        near(model.leftArm.yRot, .5235988F, "left block yaw");
        near(model.leftArm.zRot, 0, "old twist cleared");
        near(model.rightArm.xRot, .73F, "other arm preserved");
        near(model.leftArm.y, 5, "crouch/shoulder pivot preserved");
        near(model.leftSleeve.xRot, model.leftArm.xRot, "sleeve follows block");
        HumanShieldPresentation.applyArm(model, HumanoidArm.LEFT);
        near(model.leftArm.xRot, -.9424779F, "repeated frame application cannot accumulate block angles");
        HumanShieldPresentation.applyArm(model, HumanoidArm.RIGHT);
        near(model.rightArm.yRot, -.5235988F, "right block mirrors left");
        near(model.rightSleeve.xRot, model.rightArm.xRot, "right sleeve follows block");
        verifyShieldFaceAlignment(model);
        check(calls("HumanShieldPresentation", "apply", "ShieldBlockPose", "resolve"),
                "production pose resolves the actual item model instead of fixed arm constants");
        check(calls("HumanPlayerModel", "setupAnim", "HumanShieldPresentation", "apply"), "final player model enforces shield arm");
        check(calls("BetterCombatNpcAnimator", "apply", "HumanShieldPresentation", "active")
                && calls("BetterCombatNpcAnimator", "hasAnimationStack", "HumanShieldPresentation", "active"),
                "BC model and held-item channels share shield exclusion");
        check(nativePropertyHookMatches(), "model-property injection matches an actual native invocation descriptor");
        System.out.println("HumanShieldPresentationTest passed; in-game animation acceptance remains unverified");
    }
    private static void verifyShieldFaceAlignment(PlayerModel<?> model) {
        for (HumanoidArm side : HumanoidArm.values()) {
            // Actual vanilla shield and Spartan tower shield blocking models share these rotations.
            var tower = new net.minecraft.client.renderer.block.model.ItemTransform(
                    new org.joml.Vector3f(45, 135, 0), new org.joml.Vector3f(), new org.joml.Vector3f(1));
            var aligned = ShieldBlockPose.resolve(tower, side);
            near(aligned.pitch(), -(float)Math.PI / 4, "standard shield pitch is model-derived");
            near(aligned.yaw(), (side == HumanoidArm.LEFT ? 1 : -1) * (float)Math.PI / 4,
                    "standard shield yaw mirrors between hands");
            var old = normal(ShieldBlockPose.vanilla(side), tower, side);
            check(Math.abs(old.x) > .1F || Math.abs(old.y) > .1F, "legacy fixed arm retains visible plane tilt");
            assertParallel(aligned, tower, side);
            model.leftArm.y = 5;
            HumanShieldPresentation.applyArm(model, side, aligned);
            HumanShieldPresentation.applyArm(model, side, aligned);
            near((side == HumanoidArm.LEFT ? model.leftArm : model.rightArm).xRot, aligned.pitch(),
                    "repeated final pose does not accumulate rotations");
            near(model.leftArm.y, 5, "alignment preserves the crouch shoulder pivot");
            // Spartan generated/basic shields have different native display transforms.
            var basic = new net.minecraft.client.renderer.block.model.ItemTransform(
                    new org.joml.Vector3f(40, 330, 0), new org.joml.Vector3f(), new org.joml.Vector3f(1));
            assertParallel(ShieldBlockPose.resolve(basic, side), basic, side);
            var forgeTransform = new net.minecraft.client.renderer.block.model.ItemTransform(
                    new org.joml.Vector3f(45, 135, 0), new org.joml.Vector3f(), new org.joml.Vector3f(1.2F, .9F, 1.1F));
            forgeTransform.rightRotation.set(5, 5, -5);
            assertParallel(ShieldBlockPose.resolve(forgeTransform, side), forgeTransform, side);
            var excessive = new net.minecraft.client.renderer.block.model.ItemTransform(
                    new org.joml.Vector3f(120, 135, 0), new org.joml.Vector3f(), new org.joml.Vector3f(1));
            check(ShieldBlockPose.resolve(excessive, side).equals(ShieldBlockPose.vanilla(side)),
                    "unnatural arm twist uses the native fallback");
            check(ShieldBlockPose.resolve(net.minecraft.client.renderer.block.model.ItemTransform.NO_TRANSFORM, side)
                    .equals(ShieldBlockPose.vanilla(side)), "missing transforms keep native fallback");
            var invalid = new net.minecraft.client.renderer.block.model.ItemTransform(
                    new org.joml.Vector3f(Float.NaN, 0, 0), new org.joml.Vector3f(), new org.joml.Vector3f(1));
            check(ShieldBlockPose.resolve(invalid, side).equals(ShieldBlockPose.vanilla(side)),
                    "invalid resource transform cannot corrupt the model pose");
        }
    }
    private static void assertParallel(ShieldBlockPose.ArmAngles angles,
            net.minecraft.client.renderer.block.model.ItemTransform transform, HumanoidArm side) {
        var normal = normal(angles, transform, side);
        check(Math.abs(normal.x) < .00001F && Math.abs(normal.y) < .00001F && Math.abs(normal.z) > .99999F,
                "shield face is parallel to the standing torso after the complete native transform");
    }
    private static org.joml.Vector3f normal(ShieldBlockPose.ArmAngles angles,
            net.minecraft.client.renderer.block.model.ItemTransform transform, HumanoidArm side) {
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        pose.mulPose(new org.joml.Quaternionf().rotationZYX(0, angles.yaw(), angles.pitch()));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
        transform.apply(side == HumanoidArm.LEFT, pose);
        return pose.last().normal().transform(new org.joml.Vector3f(0, 0, 1)).normalize();
    }
    private static boolean calls(String owner, String methodName, String target, String called) throws Exception {
        var node = new org.objectweb.asm.tree.ClassNode();
        String prefix = "dev/felix/hostilehumans/client/renderer/";
        try (var stream = HumanShieldPresentationTest.class.getClassLoader().getResourceAsStream(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(prefix + owner) + ".class")) {
            if (stream == null) throw new AssertionError("Missing " + owner);
            new org.objectweb.asm.ClassReader(stream).accept(node, 0);
        }
        for (var method : node.methods) if (method.name.equals(methodName))
            for (var instruction : method.instructions)
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && call.owner.equals(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(prefix + target)) && call.name.equals(called)) return true;
        return false;
    }
    private static org.objectweb.asm.tree.ClassNode read(String owner) throws Exception {
        var node = new org.objectweb.asm.tree.ClassNode();
        try (var stream = HumanShieldPresentationTest.class.getClassLoader().getResourceAsStream(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(owner) + ".class")) {
            if (stream == null) throw new AssertionError("Missing " + owner);
            new org.objectweb.asm.ClassReader(stream).accept(node, 0);
        }
        return node;
    }
    private static boolean nativePropertyHookMatches() throws Exception {
        // The built mod has no refmap. Verify production selectors as well as the actual
        // invocation in the mapped development bytecode; a Mojmap-only hook must fail this test.
        String resolveDesc = "(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;"
                + "Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/entity/LivingEntity;I)"
                + "Lnet/minecraft/client/resources/model/BakedModel;";
        String propertyDesc = "(Lnet/minecraft/world/item/Item;Lnet/minecraft/resources/ResourceLocation;)"
                + "Lnet/minecraft/client/renderer/item/ItemPropertyFunction;";
        String target = null;
        boolean productionMethod = false, explicitNamespace = false, required = false, unique = false;
        var mixin = read("dev/felix/hostilehumans/mixin/HumanShieldItemPropertyMixin");
        for (var method : mixin.methods) {
            var annotations = new java.util.ArrayList<org.objectweb.asm.tree.AnnotationNode>();
            if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
            if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
            for (var annotation : annotations) if (annotation.desc.endsWith("/Redirect;"))
                for (int i = 0; i < annotation.values.size(); i += 2) {
                    Object key = annotation.values.get(i), value = annotation.values.get(i + 1);
                    if (key.equals("method")) productionMethod = value instanceof java.util.List<?> methods
                            && methods.contains("m_173464_" + resolveDesc);
                    if (key.equals("remap")) explicitNamespace = Boolean.FALSE.equals(value);
                    if (key.equals("require")) required = Integer.valueOf(1).equals(value);
                    if (key.equals("allow")) unique = Integer.valueOf(1).equals(value);
                    if (key.equals("at")) {
                        var at = (org.objectweb.asm.tree.AnnotationNode) value;
                        for (int j = 0; j < at.values.size(); j += 2)
                            if (at.values.get(j).equals("target")) target = (String) at.values.get(j + 1);
                    }
                }
        }
        if (!productionMethod || !explicitNamespace || !required || !unique || !(
                "Lnet/minecraft/client/renderer/item/ItemProperties;m_117829_" + propertyDesc).equals(target)) return false;
        var nativeClass = read("net/minecraft/client/renderer/block/model/ItemOverrides");
        int invocations = 0;
        for (var method : nativeClass.methods) if (method.name.equals("resolve") && method.desc.equals(resolveDesc))
            for (var instruction : method.instructions)
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && call.owner.equals(dev.felix.hostilehumans.testutil.ModuleTestSources.owner("net/minecraft/client/renderer/item/ItemProperties"))
                        && call.name.equals("getProperty") && call.desc.equals(propertyDesc)) invocations++;
        return invocations == 1;
    }
    private static void near(float actual, float expected, String message) { check(Math.abs(actual - expected) < .00001F, message); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

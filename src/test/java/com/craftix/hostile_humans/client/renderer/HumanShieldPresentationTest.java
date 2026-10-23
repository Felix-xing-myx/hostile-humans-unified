package com.craftix.hostile_humans.client.renderer;

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
        Bootstrap.bootStrap();
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
        check(calls("HumanPlayerModel", "setupAnim", "HumanShieldPresentation", "apply"), "final player model enforces shield arm");
        check(calls("BetterCombatNpcAnimator", "apply", "HumanShieldPresentation", "active")
                && calls("BetterCombatNpcAnimator", "hasAnimationStack", "HumanShieldPresentation", "active"),
                "BC model and held-item channels share shield exclusion");
        check(nativePropertyHookMatches(), "model-property injection matches an actual native invocation descriptor");
        System.out.println("HumanShieldPresentationTest passed; in-game animation acceptance remains unverified");
    }
    private static boolean calls(String owner, String methodName, String target, String called) throws Exception {
        var node = new org.objectweb.asm.tree.ClassNode();
        String prefix = "com/craftix/hostile_humans/client/renderer/";
        try (var stream = HumanShieldPresentationTest.class.getClassLoader().getResourceAsStream(prefix + owner + ".class")) {
            if (stream == null) throw new AssertionError("Missing " + owner);
            new org.objectweb.asm.ClassReader(stream).accept(node, 0);
        }
        for (var method : node.methods) if (method.name.equals(methodName))
            for (var instruction : method.instructions)
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && call.owner.equals(prefix + target) && call.name.equals(called)) return true;
        return false;
    }
    private static org.objectweb.asm.tree.ClassNode read(String owner) throws Exception {
        var node = new org.objectweb.asm.tree.ClassNode();
        try (var stream = HumanShieldPresentationTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            if (stream == null) throw new AssertionError("Missing " + owner);
            new org.objectweb.asm.ClassReader(stream).accept(node, 0);
        }
        return node;
    }
    private static boolean nativePropertyHookMatches() throws Exception {
        String target = null;
        var mixin = read("club/someoneice/humangunner/mixin/HumanShieldItemPropertyMixin");
        for (var method : mixin.methods) {
            var annotations = new java.util.ArrayList<org.objectweb.asm.tree.AnnotationNode>();
            if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
            if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
            for (var annotation : annotations) if (annotation.desc.endsWith("/Redirect;"))
                for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals("at")) {
                    var at = (org.objectweb.asm.tree.AnnotationNode) annotation.values.get(i + 1);
                    for (int j = 0; j < at.values.size(); j += 2)
                        if (at.values.get(j).equals("target")) target = (String) at.values.get(j + 1);
                }
        }
        if (target == null) return false;
        var nativeClass = read("net/minecraft/client/renderer/block/model/ItemOverrides");
        for (var method : nativeClass.methods) if (method.name.equals("resolve"))
            for (var instruction : method.instructions)
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && target.equals("L" + call.owner + ";" + call.name + call.desc)) return true;
        return false;
    }
    private static void near(float actual, float expected, String message) { check(Math.abs(actual - expected) < .00001F, message); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

package com.craftix.hostile_humans.client.renderer;

import com.craftix.hostile_humans.entity.entities.Human;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import ttv.migami.jeg.client.handler.GunRenderingHandler;
import ttv.migami.jeg.common.GripType;
import ttv.migami.jeg.item.GunItem;
import ttv.migami.jeg.client.render.pose.MiniGunPose;
import ttv.migami.jeg.client.render.pose.AimPose;
import ttv.migami.jeg.client.render.pose.LimbPose;

/** Uses public LivingEntity renderer and grip metadata; no player animation callbacks. */
final class JegNativePose {
    private JegNativePose() {}
    // Protected native pose factories expose authored data without a fake Player or reflection.
    private static final class MachineGunPose extends MiniGunPose {
        final AimPose.Instance forward = getForwardPose().getIdle();
        final AimPose.Instance up = getUpPose().getIdle();
        final AimPose.Instance down = getDownPose().getIdle();
        AimPose.Instance target(float pitch) { return pitch > 0 ? down : up; }
    }
    private static final MachineGunPose MACHINE_GUN = new MachineGunPose();
    static boolean machineGun(ItemStack stack) {
        return ((GunItem) stack.getItem()).getModifiedGun(stack).getGeneral().getGripType() == GripType.MINI_GUN;
    }
    static float bodyOffset(Human human) {
        return machineGun(human.getMainHandItem()) ? MACHINE_GUN.forward.getRenderYawOffset() : 0;
    }
    static void apply(Human human, PlayerModel<Human> model) {
        var stack = human.getMainHandItem();
        var grip = ((GunItem) stack.getItem()).getModifiedGun(stack).getGeneral().getGripType();
        if (grip == GripType.MINI_GUN) {
            float pitch = model.head.xRot * Mth.RAD_TO_DEG;
            var target = MACHINE_GUN.target(pitch);
            float fraction = Mth.clamp(Math.abs(pitch) / 90, 0, 1);
            applyArm(model.rightArm, MACHINE_GUN.forward.getRightArm(), target.getRightArm(), fraction);
            applyArm(model.leftArm, MACHINE_GUN.forward.getLeftArm(), target.getLeftArm(), fraction);
            if (human.isCrouching()) {
                model.rightArm.y += 2;
                model.leftArm.y += 2;
            }
            // Body turns by the native shoulder offset; compensate only residual head aim.
            float residual = model.head.yRot + bodyOffset(human) * Mth.DEG_TO_RAD;
            model.rightArm.yRot += residual;
            model.leftArm.yRot += residual;
            return;
        }
        AnimationUtils.animateCrossbowHold(model.rightArm, model.leftArm, model.head, true);
        if (grip == GripType.ONE_HANDED) {
            model.rightArm.xRot = -(float) Math.PI / 2 + model.head.xRot;
            model.rightArm.yRot = model.head.yRot;
            model.leftArm.xRot = model.leftArm.yRot = model.leftArm.zRot = 0;
        }
    }
    private static float value(Float start, Float target, float fraction, float fallback) {
        return start == null ? fallback : target == null ? start : Mth.lerp(fraction, start, target);
    }
    private static void applyArm(ModelPart arm, LimbPose start, LimbPose target, float fraction) {
        arm.xRot = value(start.getRotationAngleX(), target.getRotationAngleX(), fraction, arm.xRot * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        arm.yRot = value(start.getRotationAngleY(), target.getRotationAngleY(), fraction, arm.yRot * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        arm.zRot = value(start.getRotationAngleZ(), target.getRotationAngleZ(), fraction, arm.zRot * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        arm.x = value(start.getRotationPointX(), target.getRotationPointX(), fraction, arm.x);
        arm.y = value(start.getRotationPointY(), target.getRotationPointY(), fraction, arm.y);
        arm.z = value(start.getRotationPointZ(), target.getRotationPointZ(), fraction, arm.z);
    }
    private static void applyMachineGunItem(Human human, PoseStack poses) {
        float pitch = Mth.lerp(Minecraft.getInstance().getFrameTime(), human.xRotO, human.getXRot());
        float fraction = Mth.clamp(Math.abs(pitch) / 90, 0, 1);
        var start = MACHINE_GUN.forward;
        var target = MACHINE_GUN.target(pitch);
        var translate = new org.joml.Vector3f(start.getItemTranslate()).lerp(target.getItemTranslate(), fraction);
        var rotate = new org.joml.Vector3f(start.getItemRotation()).lerp(target.getItemRotation(), fraction);
        poses.translate(translate.x() / 16, translate.y() / 16, .05 + translate.z() / 16);
        poses.mulPose(Axis.XP.rotationDegrees(rotate.x()));
        poses.mulPose(Axis.YP.rotationDegrees(rotate.y()));
        poses.mulPose(Axis.ZP.rotationDegrees(rotate.z()));
    }
    static boolean render(Human human, ItemStack stack, ItemDisplayContext context,
                          PoseStack poses, MultiBufferSource buffers, int light) {
        GunRenderingHandler renderer = GunRenderingHandler.get();
        if (machineGun(stack)) applyMachineGunItem(human, poses);
        renderer.applyWeaponScale(stack, poses);
        return renderer.renderWeapon(human, stack, context, poses, buffers, light, Minecraft.getInstance().getFrameTime());
    }
}

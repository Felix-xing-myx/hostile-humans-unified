package com.craftix.hostile_humans.client.renderer;

import com.craftix.hostile_humans.entity.entities.Human;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import top.ribs.scguns.common.GripType;
import top.ribs.scguns.item.GunItem;
import top.ribs.scguns.client.render.pose.*;
import java.util.Map;

/** Native authored pose data, adapted to a real NPC without Player/input callbacks or reflection. */
final class ScgunsMachineGunPose {
    private record Poses(AimPose forward, AimPose up, AimPose down) {
        AimPose.Instance target(float pitch) { return (pitch > 0 ? down : up).getIdle(); }
    }
    // Public extension points: keep each native grip's data separate, even if currently identical.
    private static final Map<GripType, Poses> POSES = Map.of(
            GripType.MINI_GUN, new MiniGunPose() {
                Poses snapshot() { return new Poses(getForwardPose(), getUpPose(), getDownPose()); }
            }.snapshot(),
            GripType.MINI_GUN_2, new MiniGun2Pose() {
                Poses snapshot() { return new Poses(getForwardPose(), getUpPose(), getDownPose()); }
            }.snapshot(),
            GripType.MINI_GUN_3, new MiniGun3Pose() {
                Poses snapshot() { return new Poses(getForwardPose(), getUpPose(), getDownPose()); }
            }.snapshot(),
            GripType.MINI_GUN_4, new MiniGun4Pose() {
                Poses snapshot() { return new Poses(getForwardPose(), getUpPose(), getDownPose()); }
            }.snapshot(),
            GripType.MINI_GUN_5, new MiniGun5Pose() {
                Poses snapshot() { return new Poses(getForwardPose(), getUpPose(), getDownPose()); }
            }.snapshot());

    private ScgunsMachineGunPose() {}
    private static boolean active(Human human) {
        return human.isAlive() && !human.isUsingItem() && !HumanShieldPresentation.active(human) && !human.isVisuallySwimming()
                && !human.isSleeping() && !human.isFallFlying();
    }
    private static Poses resolve(ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem gun)) return null;
        return POSES.get(gun.getModifiedGun(stack).getGeneral().getGripType(stack));
    }
    private static float fraction(float pitch) { return Mth.clamp(Math.abs(pitch) / 90, 0, 1); }
    private static float pitch(Human human, float partialTick) {
        return Mth.lerp(partialTick, human.xRotO, human.getXRot());
    }
    private static float yaw(Poses data, float pitch) {
        return Mth.lerp(fraction(pitch), data.forward().getIdle().getRenderYawOffset(),
                data.target(pitch).getRenderYawOffset());
    }
    static float bodyOffset(Human human, float partialTick) {
        if (!active(human)) return 0;
        Poses data = resolve(human.getMainHandItem());
        return data == null ? 0 : yaw(data, pitch(human, partialTick));
    }
    static boolean apply(Human human, PlayerModel<Human> model) {
        if (!active(human)) return false;
        Poses data = resolve(human.getMainHandItem());
        if (data == null) return false;
        float pitch = pitch(human, Minecraft.getInstance().getFrameTime());
        var start = data.forward().getIdle();
        var target = data.target(pitch);
        applyArm(model.rightArm, start.getRightArm(), target.getRightArm(), fraction(pitch));
        applyArm(model.leftArm, start.getLeftArm(), target.getLeftArm(), fraction(pitch));
        float residual = model.head.yRot + yaw(data, pitch) * Mth.DEG_TO_RAD;
        model.rightArm.yRot += residual;
        model.leftArm.yRot += residual;
        if (human.isCrouching()) {
            model.rightArm.y += 2;
            model.leftArm.y += 2;
        }
        return true;
    }
    private static float value(Float start, Float end, float progress, float fallback) {
        return start == null ? fallback : end == null ? start : Mth.lerp(progress, start, end);
    }
    private static void applyArm(ModelPart arm, LimbPose start, LimbPose end, float progress) {
        arm.xRot = value(start.getRotationAngleX(), end.getRotationAngleX(), progress, arm.xRot * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        arm.yRot = value(start.getRotationAngleY(), end.getRotationAngleY(), progress, arm.yRot * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        arm.zRot = value(start.getRotationAngleZ(), end.getRotationAngleZ(), progress, arm.zRot * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        arm.x = value(start.getRotationPointX(), end.getRotationPointX(), progress, arm.x);
        arm.y = value(start.getRotationPointY(), end.getRotationPointY(), progress, arm.y);
        arm.z = value(start.getRotationPointZ(), end.getRotationPointZ(), progress, arm.z);
    }
    static void applyItem(Human human, ItemStack stack, PoseStack poses) {
        if (!active(human)) return;
        Poses data = resolve(stack);
        if (data == null) return;
        float pitch = pitch(human, Minecraft.getInstance().getFrameTime());
        var start = data.forward().getIdle();
        var end = data.target(pitch);
        var translate = new org.joml.Vector3f(start.getItemTranslate()).lerp(end.getItemTranslate(), fraction(pitch));
        var rotation = new org.joml.Vector3f(start.getItemRotation()).lerp(end.getItemRotation(), fraction(pitch));
        poses.translate(translate.x() / 16, translate.y() / 16, .05 + translate.z() / 16);
        poses.mulPose(Axis.XP.rotationDegrees(rotation.x()));
        poses.mulPose(Axis.YP.rotationDegrees(rotation.y()));
        poses.mulPose(Axis.ZP.rotationDegrees(rotation.z()));
    }
}

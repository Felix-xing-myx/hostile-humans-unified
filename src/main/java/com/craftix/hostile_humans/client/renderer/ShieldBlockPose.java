package com.craftix.hostile_humans.client.renderer;

import com.craftix.hostile_humans.entity.entities.Human;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Align the common shield face to the torso using the resolved model's native hand transform.
 * Model identity changes on resource reload; weak keys do not retain discarded model generations. */
final class ShieldBlockPose {
    private static final Map<BakedModel, Map<HumanoidArm, ArmAngles>> CACHE = new WeakHashMap<>();
    record ArmAngles(float pitch, float yaw) { }
    private ShieldBlockPose() { }

    static ArmAngles vanilla(HumanoidArm side) {
        return new ArmAngles(-.9424779F, side == HumanoidArm.RIGHT ? -.5235988F : .5235988F);
    }
    static ArmAngles resolve(Human human, InteractionHand hand, HumanoidArm side) {
        try {
            BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(
                    human.getItemInHand(hand), human.level(), human, human.getId());
            return CACHE.computeIfAbsent(model, ignored -> new EnumMap<>(HumanoidArm.class))
                    .computeIfAbsent(side, ignored -> {
                        var context = HumanGunPresentation.context(side);
                        if (model.getTransforms().getTransform(context) == ItemTransform.NO_TRANSFORM) return vanilla(side);
                        PoseStack pose = handFrame();
                        // Forge's public hook also includes rightRotation and model-specific transforms.
                        model.applyTransform(context, pose, side == HumanoidArm.LEFT);
                        return fromNormal(pose.last().normal().transform(new Vector3f(0, 0, 1)), side);
                    });
        } catch (RuntimeException | LinkageError failure) {
            ClientAnimationCompatibility.warnOnce("ShieldBlockPose",
                    "Could not derive shield hand alignment; keeping the vanilla block pose", failure);
            return vanilla(side);
        }
    }
    /** Native transform fixture entrypoint, shared by the geometry regression. */
    static ArmAngles resolve(ItemTransform transform, HumanoidArm side) {
        if (transform == ItemTransform.NO_TRANSFORM) return vanilla(side);
        PoseStack pose = handFrame();
        transform.apply(side == HumanoidArm.LEFT, pose);
        return fromNormal(pose.last().normal().transform(new Vector3f(0, 0, 1)), side);
    }
    private static PoseStack handFrame() {
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        return pose;
    }
    private static ArmAngles fromNormal(Vector3f normal, HumanoidArm side) {
        ArmAngles fallback = vanilla(side);
        if (!Float.isFinite(normal.x) || !Float.isFinite(normal.y) || !Float.isFinite(normal.z)
                || !Float.isFinite(normal.lengthSquared()) || normal.lengthSquared() < 1e-10F
                || normal.y * normal.y + normal.z * normal.z < 1e-10F)
            return fallback;
        normal.normalize();
        // Solve yaw * pitch * itemNormal = +/- torsoZ. Choose the natural solution nearest BLOCK.
        float pitch = nearestPlaneAngle((float)Math.atan2(normal.y, normal.z), fallback.pitch());
        new Quaternionf().rotationX(pitch).transform(normal);
        float yaw = nearestPlaneAngle((float)Math.atan2(-normal.x, normal.z), fallback.yaw());
        if (pitch < -(float)Math.PI / 2 - 1e-5F || pitch > 1e-5F
                || Math.abs(yaw) > (float)Math.toRadians(75)) return fallback;
        return new ArmAngles(pitch, yaw);
    }
    private static float nearestPlaneAngle(float angle, float preferred) {
        float first = wrap(angle), second = wrap(angle + (float)Math.PI);
        return Math.abs(wrap(first - preferred)) <= Math.abs(wrap(second - preferred)) ? first : second;
    }
    private static float wrap(float value) { return (float)Math.atan2(Math.sin(value), Math.cos(value)); }
}

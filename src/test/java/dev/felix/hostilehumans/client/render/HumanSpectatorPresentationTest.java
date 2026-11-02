package dev.felix.hostilehumans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Camera-relative framing and item visibility boundaries without a window or graphics context. */
public final class HumanSpectatorPresentationTest {
    public static void main(String[] args) {
        SpectatorArmMaskTest.main(args);
        SpectatorMeleePresentationTest.main(args);
        check(HumanSpectatorPresentation.allowsNativeGunPose(false, false), "idle food/shield inventory does not replace native gun pose");
        check(!HumanSpectatorPresentation.allowsNativeGunPose(true, false), "real food/potion use owns arms");
        check(!HumanSpectatorPresentation.allowsNativeGunPose(false, true), "synced shield defense owns arms before use timer arrives");
        check(!HumanSpectatorPresentation.allowsNativeGunPose(true, true), "shield use remains authoritative");
        check(!HumanSpectatorPresentation.showOffhandItem(true, false, false), "idle ranged offhand hidden");
        check(HumanSpectatorPresentation.showOffhandItem(true, true, false), "synced shield visible before use timer arrives");
        check(HumanSpectatorPresentation.showOffhandItem(true, false, true), "actual offhand use visible");
        check(HumanSpectatorPresentation.showOffhandItem(true, true, true), "defense and use remain visible");
        for (boolean shield : new boolean[]{false, true}) for (boolean use : new boolean[]{false, true})
            check(HumanSpectatorPresentation.showOffhandItem(false, shield, use), "switching away from ranged restores item immediately");
        for (float pitch : new float[]{-80, 0, 80}) for (float yaw : new float[]{-180, 0, 90}) {
            PoseStack poses = new PoseStack();
            HumanSpectatorPresentation.frame(poses, pitch, yaw);
            Vector3f eye = poses.last().pose().transformPosition(new Vector3f(0, 1.53F, 0));
            Vec3 actual = new Vec3(eye.x, eye.y, eye.z);
            check(actual.dot(Vec3.directionFromRotation(pitch - 90, yaw)) < -.10,
                    "hand frame moves down on screen even at extreme pitch");
            check(actual.dot(Vec3.directionFromRotation(pitch, yaw)) > .30,
                    "frame moves forward from the old near-camera placement");
            float size = poses.last().pose().transformDirection(new Vector3f(1, 0, 0)).length();
            check(size > .5 && size < 1, "uniform modest scale keeps hands visible");
        }
        System.out.println("HumanSpectatorPresentationTest: visibility and nine camera orientations passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

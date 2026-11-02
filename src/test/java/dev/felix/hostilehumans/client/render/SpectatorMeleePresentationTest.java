package dev.felix.hostilehumans.client.render;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Presentation timing, finite bounds and shared grip translation; no combat or graphics context. */
public final class SpectatorMeleePresentationTest {
    public static void main(String[] args) {
        near(SpectatorMeleePresentation.attackWeight(0, 20, .4F), 0, "windup starts at rest");
        near(SpectatorMeleePresentation.attackWeight(8, 20, .4F), 1, "strike remains clear");
        near(SpectatorMeleePresentation.attackWeight(20, 20, .4F), .35, "nominal recovery relaxes");
        near(SpectatorMeleePresentation.attackWeight(40, 20, .4F), 0, "long recovery expires");
        near(SpectatorMeleePresentation.attackWeight(Double.NaN, 20, .4F), 0, "invalid clock ignored");
        near(SpectatorMeleePresentation.attackWeight(-1, 20, .4F), 0, "future action ignored");
        near(SpectatorMeleePresentation.attackWeight(8, 0, .4F), 0, "invalid duration ignored");
        for (double progress = 0; progress <= 3; progress += .01) {
            double slow = SpectatorMeleePresentation.attackWeight(progress * 30, 30, .5F);
            double fast = SpectatorMeleePresentation.attackWeight(progress * 10, 10, .5F);
            near(slow, fast, "same phase does not depend on attack speed");
            check(slow >= 0 && slow <= 1, "bounded presentation envelope");
        }
        var right = SpectatorMeleePresentation.fit(new Vec3(-.7, 1, .5), true, 1);
        var left = SpectatorMeleePresentation.fit(new Vec3(.7, 1, .5), false, 1);
        near(right.x(), -left.x(), "left and right handedness mirror");
        near(right.y(), left.y(), "same lift for either hand");
        check(right.y() < 0, "low hand moves upward in model coordinates");
        for (double x : new double[]{-100, 0, 100}) for (double y : new double[]{-100, 0, 100}) {
            var offset = SpectatorMeleePresentation.fit(new Vec3(x, y, 100), true, 1);
            check(Math.abs(offset.x()) <= .18 && offset.y() >= -.60 && offset.y() <= .14
                    && Math.abs(offset.z()) <= .12, "extreme custom poses have finite corrections");
        }
        check(SpectatorMeleePresentation.fit(new Vec3(Double.NaN, 0, 0), true, 1).nearZero(), "invalid pose ignored");
        var zero = SpectatorMeleePresentation.Offset.ZERO;
        var halfSteps = SpectatorMeleePresentation.blend(SpectatorMeleePresentation.blend(zero, right, .5), right, .5);
        var fullStep = SpectatorMeleePresentation.blend(zero, right, 1);
        near(halfSteps.y(), fullStep.y(), "smoothing is independent of render frequency");
        near(SpectatorMeleePresentation.blend(right, zero, 0).y(), right.y(), "repeated render cannot advance correction");
        var root = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot();
        var model = new PlayerModel<LivingEntity>(root, false);
        model.rightArm.xRot = -.7F;
        model.leftArm.xRot = -1.2F;
        float spacing = model.rightArm.x - model.leftArm.x;
        SpectatorMeleePresentation.translateArms(model, right);
        near(model.rightArm.x - model.leftArm.x, spacing, "shared translation preserves two-hand grip spacing");
        near(model.rightArm.xRot, -.7F, "native main swing rotation preserved");
        near(model.leftArm.xRot, -1.2F, "native support/offhand rotation preserved");
        near(model.rightSleeve.y, model.rightArm.y, "sleeve follows corrected arm");
        System.out.println("SpectatorMeleePresentationTest: phases, bounded poses, frame rate and grip passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < .00001, message);
    }
}

package dev.felix.hostilehumans.monitor.client;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/** Native voxel collision results flow through the exact bridge used by the prediction shell. */
public final class MonitorNativeMotionBridgeTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-6; }
    public static void main(String[] args) {
        for (int flags = 0; flags < 32; flags++) for (float forward : new float[]{-1, 0, 1}) {
            boolean active = (flags & 1) != 0, held = (flags & 2) != 0, sneak = (flags & 4) != 0;
            boolean attack = (flags & 8) != 0, use = (flags & 16) != 0;
            boolean expected = flags == 3 && forward > 0;
            check(MonitorPredictionIntent.sprinting(active, held, forward, sneak, attack, use) == expected,
                    "Prediction sprint matches active forward intent and suppresses all combat/sneak combinations");
        }
        AABB body = new AABB(0, 1, 0, .6, 2.8, .6);
        var wall = Shapes.box(1, 0, -10, 2, 4, 10);
        double dx = Shapes.collide(Direction.Axis.X, body, List.of(wall), 2);
        check(near(dx, .4), "Native collider stops before the wall at exact body width");
        var slide = MonitorPhysicalMotion.collision(new Vec3(2, 0, .2), new Vec3(dx, 0, .2), new Vec3(2, -.08, .2));
        check(slide.horizontal() && !slide.vertical() && near(slide.velocity().x, 0)
                && near(slide.velocity().z, .2), "Wall collision cancels blocked axis while preserving tangent motion");
        var floor = Shapes.box(-10, 0, -10, 10, 1, 10);
        double dy = Shapes.collide(Direction.Axis.Y, body, List.of(floor), -.3);
        var landing = MonitorPhysicalMotion.collision(new Vec3(0, -.3, 0), new Vec3(0, dy, 0), new Vec3(.2, -.3, 0));
        check(near(dy, 0) && landing.vertical() && landing.below() && near(landing.velocity().y, 0), "Native landing installs grounded flags and stops vertical velocity");
        var ceiling = Shapes.box(-10, 3, -10, 10, 4, 10);
        double up = Shapes.collide(Direction.Axis.Y, body, List.of(ceiling), .42);
        var headHit = MonitorPhysicalMotion.collision(new Vec3(0, .42, 0), new Vec3(0, up, 0), new Vec3(0, .42, 0));
        check(near(up, .2) && headHit.vertical() && !headHit.below(), "Hitting a ceiling never claims grounded state");
        var step = Shapes.box(.6, 1, 0, 2, 1.5, 1);
        double blocked = Shapes.collide(Direction.Axis.X, body, List.of(step), .5);
        double raised = Shapes.collide(Direction.Axis.X, body.move(0, .6, 0), List.of(step), .5);
        check(near(blocked, 0) && near(raised, .5), "Native step candidates distinguish an obstructed move from a raised half-slab path");
        Vec3 standing = MonitorPhysicalMotion.jump(new Vec3(.1, -.2, .3), .62F, false, 0);
        Vec3 sprint = MonitorPhysicalMotion.jump(new Vec3(.1, -.2, .3), .62F, true, 90);
        check(near(standing.y, .62) && near(standing.x, .1), "Native jump power, including effects/block factor, replaces only vertical motion");
        check(near(sprint.x, -.1) && near(sprint.y, .62) && near(sprint.z, .3), "Sprint jump uses native yaw-relative .2 impulse without another movement tick");
        System.out.println("MonitorNativeMotionBridgeTest passed");
    }
}

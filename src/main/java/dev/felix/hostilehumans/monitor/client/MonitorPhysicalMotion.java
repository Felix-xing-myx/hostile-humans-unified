package dev.felix.hostilehumans.monitor.client;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** The small side-effect-free bridge around native collision/jump, shared with sequence tests. */
final class MonitorPhysicalMotion {
    record Collision(boolean horizontal, boolean vertical, boolean below, Vec3 velocity) {}
    private MonitorPhysicalMotion() {}
    static Collision collision(Vec3 requested, Vec3 moved, Vec3 velocity) {
        boolean x = !Mth.equal(requested.x, moved.x), z = !Mth.equal(requested.z, moved.z);
        boolean vertical = requested.y != moved.y;
        return new Collision(x || z, vertical, vertical && requested.y < 0,
                new Vec3(x ? 0 : velocity.x, vertical ? 0 : velocity.y, z ? 0 : velocity.z));
    }
    static Vec3 jump(Vec3 before, float nativeJumpPower, boolean sprint, float yawDegrees) {
        Vec3 jumped = new Vec3(before.x, nativeJumpPower, before.z);
        if (!sprint) return jumped;
        float yaw = yawDegrees * ((float) Math.PI / 180F);
        return jumped.add(-Mth.sin(yaw) * .2F, 0, Mth.cos(yaw) * .2F);
    }
}

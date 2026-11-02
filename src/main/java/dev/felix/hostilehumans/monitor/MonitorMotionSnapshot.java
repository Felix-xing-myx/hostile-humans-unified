package dev.felix.hostilehumans.monitor;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/** Post-travel authority, addressed only to the controlling lease; never accepted from a client. */
public record MonitorMotionSnapshot(UUID token, UUID target, int entityId, long generation,
        long serverTick, long acknowledged, Vec3 position, Vec3 velocity, boolean onGround,
        int jumpCooldown, Physics physics) {
    public record Physics(float movementSpeed, boolean sprinting, float width, float height, float eyeHeight,
            float stepHeight, double gravity, double swimSpeed, boolean noGravity,
            int jumpBoost, int levitation, boolean slowFalling, boolean dolphinsGrace, int depthStrider, boolean supported) {
        public Physics {
            if (!Float.isFinite(movementSpeed) || movementSpeed < 0 || !Float.isFinite(width) || width <= 0
                    || !Float.isFinite(height) || height <= 0 || !Float.isFinite(eyeHeight) || eyeHeight < 0
                    || !Float.isFinite(stepHeight) || stepHeight < 0 || !Double.isFinite(gravity)
                    || !Double.isFinite(swimSpeed) || swimSpeed < 0
                    || jumpBoost < -1 || levitation < -1 || depthStrider < 0) throw new IllegalArgumentException("Invalid monitor physics");
        }
    }
    public MonitorMotionSnapshot {
        if (generation <= 0 || serverTick < 0 || acknowledged < -1 || jumpCooldown < 0 || jumpCooldown > 10
                || !finite(position) || !finite(velocity)) throw new IllegalArgumentException("Invalid monitor motion");
    }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    static void encode(MonitorMotionSnapshot s, FriendlyByteBuf b) {
        b.writeUUID(s.token); b.writeUUID(s.target); b.writeVarInt(s.entityId); b.writeVarLong(s.generation);
        b.writeVarLong(s.serverTick); b.writeLong(s.acknowledged);
        b.writeDouble(s.position.x); b.writeDouble(s.position.y); b.writeDouble(s.position.z);
        b.writeDouble(s.velocity.x); b.writeDouble(s.velocity.y); b.writeDouble(s.velocity.z);
        b.writeBoolean(s.onGround); b.writeByte(s.jumpCooldown);
        Physics p = s.physics;
        b.writeFloat(p.movementSpeed); b.writeBoolean(p.sprinting); b.writeFloat(p.width); b.writeFloat(p.height);
        b.writeFloat(p.eyeHeight); b.writeFloat(p.stepHeight); b.writeDouble(p.gravity); b.writeDouble(p.swimSpeed);
        b.writeBoolean(p.noGravity); b.writeInt(p.jumpBoost); b.writeInt(p.levitation);
        b.writeBoolean(p.slowFalling); b.writeBoolean(p.dolphinsGrace); b.writeVarInt(p.depthStrider); b.writeBoolean(p.supported);
    }
    static MonitorMotionSnapshot decode(FriendlyByteBuf b) {
        var s = new MonitorMotionSnapshot(b.readUUID(), b.readUUID(), b.readVarInt(), b.readVarLong(),
                b.readVarLong(), b.readLong(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readBoolean(), b.readUnsignedByte(),
                new Physics(b.readFloat(), b.readBoolean(), b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readFloat(), b.readDouble(), b.readDouble(), b.readBoolean(), b.readInt(), b.readInt(),
                    b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readBoolean()));
        if (b.isReadable()) throw new IllegalArgumentException("Trailing monitor motion");
        return s;
    }
}

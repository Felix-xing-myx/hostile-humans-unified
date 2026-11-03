package dev.felix.hostilehumans.monitor;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** Round trips and malformed network requests; never starts a client or changes a world. */
public final class HumanMonitorProtocolTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        UUID token = UUID.randomUUID(), target = UUID.randomUUID();
        for (boolean active : new boolean[]{false, true}) {
            var state = new HumanMonitorNetwork.State(token, target, 17, 27.5f, 80, "目标人类", true, 2, true, active, active);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                HumanMonitorNetwork.write(state, buffer);
                check(state.equals(HumanMonitorNetwork.read(buffer)), "authoritative identity, health, mode and flags survive a round trip");
                check(!buffer.isReadable(), "packet consumed completely");
            } finally { buffer.release(); }
        }
        for (int action = 0; action <= 7; action++) {
            var request = new HumanMonitorNetwork.Request(token, action, true, 2);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                HumanMonitorNetwork.Request.encode(request, buffer);
                check(buffer.readableBytes() <= 24, "request cannot contain target identity, coordinates or inventory stacks");
                check(request.equals(HumanMonitorNetwork.Request.decode(buffer)), "operation round trip");
            } finally { buffer.release(); }
        }
        for (int[] values : new int[][]{{-1, 0}, {8, 0}, {0, -1}, {0, 4}}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeUUID(token); buffer.writeVarInt(values[0]); buffer.writeBoolean(false); buffer.writeVarInt(values[1]);
                try { HumanMonitorNetwork.Request.decode(buffer); throw new AssertionError("unbounded operation accepted"); }
                catch (IllegalArgumentException expected) { }
            } finally { buffer.release(); }
        }
        var extra = new FriendlyByteBuf(Unpooled.buffer());
        try {
            HumanMonitorNetwork.Request.encode(new HumanMonitorNetwork.Request(token, 0, false, 0), extra); extra.writeByte(1);
            try { HumanMonitorNetwork.Request.decode(extra); throw new AssertionError("trailing payload accepted"); }
            catch (IllegalArgumentException expected) { }
        } finally { extra.release(); }
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                HumanMonitorNetwork.write(new HumanMonitorNetwork.State(token, target, 17, invalid, 80, "", false, 0, false, true, false), buffer);
                try { HumanMonitorNetwork.read(buffer); throw new AssertionError("nonfinite health accepted"); }
                catch (IllegalArgumentException expected) { }
            } finally { buffer.release(); }
        }
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, 2}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                HumanMonitorNetwork.Control.encode(new HumanMonitorNetwork.Control(token, bad, 0, 0, 0, false, false, false), buffer);
                try { HumanMonitorNetwork.Control.decode(buffer); throw new AssertionError("invalid movement accepted"); }
                catch (IllegalArgumentException expected) { }
            } finally { buffer.release(); }
        }
        var control = new HumanMonitorNetwork.Control(token, -1, 1, -179.5f, -90, true, false, true);
        for (int flags = 0; flags < 32; flags++) {
            var combat = new HumanMonitorNetwork.Control(token, 1, -1, 270, 0, false, false, false,
                    9, 721, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0, (flags & 16) != 0);
            var wire = new FriendlyByteBuf(Unpooled.buffer());
            try {
                HumanMonitorNetwork.Control.encode(combat, wire);
                check(wire.readableBytes() <= 48, "combat adds four intent bits, never a target/stack/damage");
                check(combat.equals(HumanMonitorNetwork.Control.decode(wire)), "all combat button combinations round trip");
            } finally { wire.release(); }
        }
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            HumanMonitorNetwork.Control.encode(control, buffer);
            check(control.equals(HumanMonitorNetwork.Control.decode(buffer)), "movement and view input round trip");
        } finally { buffer.release(); }
        for (float yaw : new float[]{180, 270, 360, -270, 721, -1080, Float.MAX_VALUE}) {
            var wrapped = new HumanMonitorNetwork.Control(token, 0, 0, yaw, 105, false, false, false);
            var angles = new FriendlyByteBuf(Unpooled.buffer());
            try {
                // Write raw wire angles too: accepting legacy/raw accumulated yaw is required.
                angles.writeUUID(token); angles.writeFloat(0); angles.writeFloat(0);
                angles.writeFloat(yaw); angles.writeFloat(105);
                angles.writeBoolean(false); angles.writeBoolean(false); angles.writeBoolean(false);
                angles.writeVarLong(0); angles.writeVarLong(0);
                angles.writeBoolean(true); angles.writeBoolean(false); angles.writeBoolean(false); angles.writeBoolean(false); angles.writeBoolean(false);
                var decoded = HumanMonitorNetwork.Control.decode(angles);
                check(wrapped.equals(decoded) && decoded.yaw() >= -180 && decoded.yaw() < 180
                        && decoded.pitch() == 90, "accumulated entity angles normalize without a disconnect");
            } finally { angles.release(); }
        }
        for (float[] invalid : new float[][]{{Float.NaN, 0}, {0, Float.POSITIVE_INFINITY}}) {
            var angles = new FriendlyByteBuf(Unpooled.buffer());
            try {
                HumanMonitorNetwork.Control.encode(new HumanMonitorNetwork.Control(token, 0, 0, invalid[0], invalid[1], false, false, false), angles);
                try { HumanMonitorNetwork.Control.decode(angles); throw new AssertionError("nonfinite rotation accepted"); }
                catch (IllegalArgumentException expected) { }
            } finally { angles.release(); }
        }
        var inbox = new dev.felix.hostilehumans.monitor.LatestTickInput<HumanMonitorNetwork.Control>(5);
        check(!inbox.fresh(0) && inbox.drain() == null, "uninitialized input never moves");
        var stop = new HumanMonitorNetwork.Control(token, 0, 0, 1, 2, false, false, false);
        inbox.offer(control, 10); inbox.offer(stop, 10);
        check(inbox.drain() == stop, "same-tick stop and latest look replace earlier movement");
        check(inbox.drain() == null, "at most one input consumed by a simulation tick");
        check(inbox.fresh(15) && !inbox.fresh(16) && !inbox.fresh(9), "held input expires at exact age boundary");
        inbox.offer(control, 17); check(inbox.drain() == control && inbox.fresh(17), "new input resumes after staleness");
        var sequenced = new HumanMonitorNetwork.Control(token, 1, 0, 270, 0, true, true, false, 9, 721);
        var sequencedBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            HumanMonitorNetwork.Control.encode(sequenced, sequencedBuffer);
            check(sequencedBuffer.readableBytes() <= 48, "control remains bounded and contains no client position");
            check(sequenced.equals(HumanMonitorNetwork.Control.decode(sequencedBuffer)), "generation and input sequence round trip");
        } finally { sequencedBuffer.release(); }
        var motion = new MonitorMotionSnapshot(token, target, 17, 9, 7000, 721,
                new net.minecraft.world.phys.Vec3(10, 64, -5), new net.minecraft.world.phys.Vec3(.2, -.08, 0), true, 4,
                new MonitorMotionSnapshot.Physics(.105F, true, .6F, 1.95F, 1.72F, .6F, .08, 1,
                        false, 2, -1, true, false, 3, true));
        var motionBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MonitorMotionSnapshot.encode(motion, motionBuffer);
            check(motionBuffer.readableBytes() <= 180, "owner-only snapshot has a fixed small physical payload");
            check(motion.equals(MonitorMotionSnapshot.decode(motionBuffer)), "post-travel physics, cooldown, generation and ack survive round trip");
        } finally { motionBuffer.release(); }
        var options = java.util.List.of(new MonitorLoadoutSnapshot.Option(MonitorLoadoutSnapshot.UNARMED, "", 0),
                new MonitorLoadoutSnapshot.Option(target, "Test gun", 4));
        var loadout = new MonitorLoadoutSnapshot(token, 9, 23, options, target);
        var loadoutBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MonitorLoadoutSnapshot.encode(loadout, loadoutBuffer);
            check(loadout.equals(MonitorLoadoutSnapshot.decode(loadoutBuffer)), "display menu and stable weapon identity round trip");
        } finally { loadoutBuffer.release(); }
        for (int fault = 0; fault < 3; fault++) {
            var invalidOptions = fault == 0 ? java.util.Collections.nCopies(34, options.get(0))
                    : fault == 1 ? java.util.List.of(options.get(1), options.get(1)) : options;
            var malformed = new MonitorLoadoutSnapshot(token, 9, 23, invalidOptions, fault == 2 ? UUID.randomUUID() : target);
            var bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                MonitorLoadoutSnapshot.encode(malformed, bytes);
                try { MonitorLoadoutSnapshot.decode(bytes); throw new AssertionError("invalid count/identity/selection accepted"); }
                catch (IllegalArgumentException expected) { }
            } finally { bytes.release(); }
        }
        var selection = new HumanMonitorNetwork.Selection(token, 9, 23, target);
        var selectionBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            HumanMonitorNetwork.Selection.encode(selection, selectionBuffer);
            check(selection.equals(HumanMonitorNetwork.Selection.decode(selectionBuffer)), "selection carries token, generation, revision and server-issued identity only");
            check(selectionBuffer.writerIndex() <= 52, "weapon selection cannot contain coordinates or item payload");
        } finally { selectionBuffer.release(); }
        System.out.println("HumanMonitorProtocolTest passed");
    }
}

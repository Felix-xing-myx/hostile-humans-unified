package dev.felix.hostilehumans.observer;

import dev.felix.hostilehumans.behavior.ActionProgressSnapshot;
import dev.felix.hostilehumans.behavior.BehaviorDebugSnapshot;
import dev.felix.hostilehumans.observer.NavigationDebugFrame;
import dev.felix.hostilehumans.observer.NavigationDebugFrameCodec;
import dev.felix.hostilehumans.observer.NavigationDebugSnapshot;
import dev.felix.hostilehumans.perception.PerceptionDebugSnapshot;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Isolated value/codec/read-only wiring checks; not executed until compilation is authorized. */
public final class BehaviorDebugTest {
    public static void main(String[] args) throws Exception {
        BehaviorDebugSnapshot sample = maximum();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            NavigationDebugFrameCodec.writeBehavior(sample, buffer);
            check(sample.equals(NavigationDebugFrameCodec.readBehavior(buffer)), "all action/lease/token/feedback/Goal/progress fields round trip");
            check(buffer.readableBytes() == 0, "behavior consumes exactly its payload");
        } finally { buffer.release(); }
        try {
            new BehaviorDebugSnapshot(0, false, 0, "unbound", Collections.nCopies(BehaviorDebugSnapshot.MAX_ACTIONS + 1, sample.actions().get(0)), List.of(), List.of(), List.of(), false);
            throw new AssertionError("oversized actions");
        } catch (IllegalArgumentException expected) { }
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -2}) {
            try { new ActionProgressSnapshot("food", "", "", "", 0, invalid, 1, 1, 0, 1, "");
                throw new AssertionError("invalid execution amount"); } catch (IllegalArgumentException expected) { }
        }
        check(ActionProgressSnapshot.remaining(.5, 1, .25) == 2, "remaining uses actual applied rate");
        check(ActionProgressSnapshot.remaining(.5, -1, .25) == -1, "native unknown total stays unknown");
        var empty = BehaviorDebugSnapshot.EMPTY;
        check(empty.bridge().equals("unbound") && !empty.authorityPresent(), "absence does not manufacture an active authority");
        try { sample.actions().clear(); throw new AssertionError("mutable snapshot"); }
        catch (UnsupportedOperationException expected) { }
        try { sample.actions().get(0).resources().clear(); throw new AssertionError("mutable resources"); }
        catch (UnsupportedOperationException expected) { }
        for (int invalid : new int[]{-1, BehaviorDebugSnapshot.MAX_ACTIONS + 1}) {
            var malformed = new FriendlyByteBuf(Unpooled.buffer());
            try {
                malformed.writeLong(0); malformed.writeBoolean(false); malformed.writeLong(0); malformed.writeUtf("unbound");
                malformed.writeBoolean(false); malformed.writeVarInt(invalid);
                try { NavigationDebugFrameCodec.readBehavior(malformed); throw new AssertionError("incoming count accepted"); }
                catch (IllegalArgumentException expected) { }
            } finally { malformed.release(); }
        }
        try {
            new BehaviorDebugSnapshot.Action(1, 1, "x".repeat(97), "RUNNING", List.of(), List.of(), 0, true, 1, false, 0, 0);
            throw new AssertionError("oversized token text");
        } catch (IllegalArgumentException expected) { }
        try {
            new BehaviorDebugSnapshot.Action(1, 1, "", "", Collections.nCopies(9, "BASE_MOVE"), List.of(), 0, true, 1, true, 40, 0);
            throw new AssertionError("oversized resources");
        } catch (IllegalArgumentException expected) { }
        try {
            new ActionProgressSnapshot("native_use", "", -2, 0, ""); throw new AssertionError("invalid progress");
        } catch (IllegalArgumentException expected) { }
        var rows = Collections.nCopies(128, new PerceptionDebugSnapshot.Row("组".repeat(32), "标".repeat(96), "值".repeat(256)));
        var markers = Collections.nCopies(64, new PerceptionDebugSnapshot.Marker("类".repeat(32), "点".repeat(96), Vec3.ZERO, Vec3.ZERO, 4096));
        var perception = new PerceptionDebugSnapshot(1, Vec3.ZERO, Vec3.ZERO, 4096, 0, 0, "警".repeat(64), "策".repeat(128), "因".repeat(256), rows, markers);
        List<NavigationDebugFrame.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 8; i++) entries.add(new NavigationDebugFrame.Entry(i,
                new NavigationDebugSnapshot(new ResourceLocation("minecraft", "overworld"), i, UUID.randomUUID(), "n", "observe", "", "", "", "", 20, 0, 0, 32,
                        Vec3.ZERO, null, null, null, Collections.nCopies(256, Vec3.ZERO), 0, 256, true, true, 0,
                        Collections.nCopies(48, new NavigationDebugSnapshot.Sample(Vec3.ZERO, "源".repeat(64), "果".repeat(64), 0)), perception, sample)));
        var frame = new NavigationDebugFrame(entries, entries.get(0).snapshot().entityUuid());
        buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            NavigationDebugFrameCodec.write(frame, buffer);
            check(buffer.readableBytes() <= NavigationDebugFrameCodec.MAX_FRAME_BYTES, "all maximal diagnostic sections fit the 900 KiB frame budget");
            var normalized = NavigationDebugFrameCodec.normalize(frame);
            check(normalized.equals(NavigationDebugFrameCodec.read(buffer)), "canonical frame keeps behavior and bounded perception independent");
            check(normalized.entries().size() == 8 && normalized.focused().equals(frame.focused()), "capacity policy retains every observed entity and focus");
            check(normalized.entries().stream().map(entry -> entry.snapshot().entityUuid()).toList()
                    .equals(frame.entries().stream().map(entry -> entry.snapshot().entityUuid()).toList()), "capacity policy retains identity and order");
            check(!normalized.entries().get(0).snapshot().capacityTruncated()
                    && normalized.entries().subList(1, 8).stream().allMatch(entry -> entry.snapshot().capacityTruncated()),
                    "focus keeps complete details; truncated background entities are explicit");
            check(normalized.entries().get(0).snapshot().perception().rows().size() == 128
                    && normalized.entries().get(0).snapshot().perception().markers().size() == 64,
                    "multiple observers cannot erase the focus details");
            check(normalized.entries().stream().mapToInt(entry -> entry.snapshot().perception().rows().size()).sum() <= 192
                    && normalized.entries().stream().mapToInt(entry -> entry.snapshot().perception().markers().size()).sum() <= 96,
                    "focused allocation still respects the same whole-frame limits");
            check(normalized.entries().stream().allMatch(entry -> entry.snapshot().behavior().equals(sample)), "authority details are retained");
            check(NavigationDebugFrameCodec.normalize(normalized).equals(normalized), "normalization is idempotent");
            check(frame.entries().get(0).snapshot().perception().rows().size() == 128, "representation normalization does not edit the source facts");
        } finally { buffer.release(); }
        var oversized = new FriendlyByteBuf(Unpooled.buffer());
        try {
            oversized.writeZero(NavigationDebugFrameCodec.MAX_FRAME_BYTES + 1);
            try { NavigationDebugFrameCodec.read(oversized); throw new AssertionError("oversized incoming frame accepted"); }
            catch (IllegalArgumentException expected) { }
        } finally { oversized.release(); }
        ClassNode node = new ClassNode();
        try (var stream = BehaviorDebugTest.class.getClassLoader().getResourceAsStream(dev.felix.hostilehumans.testutil.ModuleTestSources.resource("dev/felix/hostilehumans/behavior/BehaviorDebugSnapshot.class"))) {
            if (stream == null) throw new AssertionError("missing class"); new ClassReader(stream).accept(node, 0);
        }
        boolean peek = false;
        for (var field : node.fields) check(!field.desc.contains("entity/") && !field.desc.contains("level/") && !field.desc.contains("ItemStack"), "snapshot holds no world/item identity");
        for (var method : node.methods) for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
            check(!List.of("advance", "tick", "actions", "acquire", "request", "heartbeat", "locate", "isReloading", "getPersistentData").contains(call.name), "observation cannot execute or infer stale backend state: " + call.name);
            if (call.name.equals("peekActions")) peek = true;
        }
        check(peek, "capture reads the existing coordinator only");
        System.out.println("BehaviorDebugTest passed");
    }
    private static BehaviorDebugSnapshot maximum() {
        var action = new BehaviorDebugSnapshot.Action(Long.MAX_VALUE, Long.MAX_VALUE, "动".repeat(96), "态".repeat(16),
                Collections.nCopies(8, "权".repeat(24)), Collections.nCopies(8, "持".repeat(24)), Integer.MIN_VALUE, false, Long.MAX_VALUE, true, 1200, Long.MIN_VALUE);
        var feedback = new BehaviorDebugSnapshot.Feedback(1, 2, "动".repeat(96), "态".repeat(16), "因".repeat(160));
        var goal = new BehaviorDebugSnapshot.RunningGoal("源".repeat(24), "名".repeat(96), -12, Collections.nCopies(4, "旗".repeat(16)), true, false,
                "态".repeat(128), 1, 2);
        var progress = new ActionProgressSnapshot("类".repeat(24), "物".repeat(96), "位".repeat(32), "态".repeat(32), Long.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -1, Long.MAX_VALUE, "说".repeat(160));
        return new BehaviorDebugSnapshot(42, true, 1, "桥".repeat(128), Collections.nCopies(BehaviorDebugSnapshot.MAX_ACTIONS, action), Collections.nCopies(8, feedback),
                Collections.nCopies(BehaviorDebugSnapshot.MAX_GOALS, goal), Collections.nCopies(4, progress), true);
    }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}

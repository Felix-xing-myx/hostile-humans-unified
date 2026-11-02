package dev.felix.hostilehumans.observer;

import dev.felix.hostilehumans.behavior.ActionProgressSnapshot;
import dev.felix.hostilehumans.behavior.BehaviorDebugSnapshot;
import dev.felix.hostilehumans.perception.PerceptionDebugCodec;
import dev.felix.hostilehumans.perception.PerceptionDebugSnapshot;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/** Bounded data-only codec, separate from Forge channel registration for round-trip tests. */
final class NavigationDebugFrameCodec {
    static final int MAX_FRAME_BYTES = 900 * 1024;
    private static final int FRAME_ROWS = 192, FRAME_MARKERS = 96;
    private NavigationDebugFrameCodec() {}
    /** Representation-only quotas retain every entity, its slot and focus. Idempotent for network/codec callers. */
    static NavigationDebugFrame normalize(NavigationDebugFrame frame) {
        if (frame.entries().size() > NavigationDebugFrame.MAX_ENTITIES) throw new IllegalArgumentException("Too many observed entities");
        int entities = Math.max(1, frame.entries().size());
        boolean focusPresent = frame.entries().stream().anyMatch(entry -> entry.snapshot().entityUuid().equals(frame.focused()));
        int others = Math.max(1, entities - 1);
        var entries = new ArrayList<NavigationDebugFrame.Entry>(frame.entries().size());
        for (var entry : frame.entries()) {
            var s = entry.snapshot(); var p = s.perception();
            boolean focus = focusPresent && s.entityUuid().equals(frame.focused());
            int rows = Math.min(PerceptionDebugSnapshot.MAX_ROWS, focus ? FRAME_ROWS
                    : focusPresent ? (FRAME_ROWS - PerceptionDebugSnapshot.MAX_ROWS) / others : FRAME_ROWS / entities);
            int markers = Math.min(PerceptionDebugSnapshot.MAX_MARKERS, focus ? FRAME_MARKERS
                    : focusPresent ? (FRAME_MARKERS - PerceptionDebugSnapshot.MAX_MARKERS) / others : FRAME_MARKERS / entities);
            if (p.rows().size() > PerceptionDebugSnapshot.MAX_ROWS || p.markers().size() > PerceptionDebugSnapshot.MAX_MARKERS)
                throw new IllegalArgumentException("Oversized perception snapshot");
            boolean cut = p.rows().size() > rows || p.markers().size() > markers;
            var perception = new PerceptionDebugSnapshot(p.tick(), p.eye(), p.view(), p.range(), p.strangerHalfAngle(), p.familiarHalfAngle(),
                    p.awareness(), p.decision(), p.reason(), List.copyOf(p.rows().subList(0, Math.min(rows, p.rows().size()))),
                    List.copyOf(p.markers().subList(0, Math.min(markers, p.markers().size()))));
            entries.add(new NavigationDebugFrame.Entry(entry.slot(), new NavigationDebugSnapshot(s.dimension(), s.entityId(), s.entityUuid(),
                    s.name(), s.mode(), s.state(), s.navigator(), s.budget(), s.obstruction(), s.health(), s.fallBudget(), s.maxDrop(), s.followRange(),
                    s.position(), s.requested(), s.enemy(), s.endpoint(), s.path(), s.firstNode(), s.totalNodes(), s.complete(), s.truncated(),
                    s.searchMillis(), s.samples(), perception, s.behavior(), s.capacityTruncated() || cut)));
        }
        return new NavigationDebugFrame(List.copyOf(entries), frame.focused());
    }
    private static void vector(FriendlyByteBuf out, Vec3 value) { out.writeDouble(value.x); out.writeDouble(value.y); out.writeDouble(value.z); }
    private static Vec3 vector(FriendlyByteBuf in) {
        Vec3 value = new Vec3(in.readDouble(), in.readDouble(), in.readDouble());
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z))
            throw new IllegalArgumentException("Non-finite navigation geometry");
        return value;
    }
    private static int count(FriendlyByteBuf in, int maximum) {
        int count = in.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid navigation snapshot count");
        return count;
    }
    static void write(NavigationDebugFrame frame, FriendlyByteBuf out) {
        frame = normalize(frame);
        int start = out.writerIndex();
        out.writeVarInt(frame.entries().size());
        out.writeBoolean(frame.focused() != null); if (frame.focused() != null) out.writeUUID(frame.focused());
        for (var entry : frame.entries()) { out.writeVarInt(entry.slot()); writeSnapshot(entry.snapshot(), out); }
        if (out.writerIndex() - start > MAX_FRAME_BYTES) {
            out.writerIndex(start);
            throw new IllegalArgumentException("Navigation snapshot exceeds 900 KiB frame budget");
        }
    }
    private static void writeSnapshot(NavigationDebugSnapshot s, FriendlyByteBuf out) {
        if (s.path().size() > NavigationDebugSnapshot.MAX_PATH || s.samples().size() > NavigationDebugSnapshot.MAX_SAMPLES)
            throw new IllegalArgumentException("Oversized navigation geometry");
        out.writeUtf(s.dimension().toString(), NavigationDebugSnapshot.MAX_DIMENSION_ID); out.writeVarInt(s.entityId()); out.writeUUID(s.entityUuid());
        out.writeUtf(s.name(), 128); out.writeUtf(s.mode(), 32); out.writeUtf(s.state(), 64);
        out.writeUtf(s.navigator(), 64); out.writeUtf(s.budget(), 96); out.writeUtf(s.obstruction(), 64);
        out.writeFloat(s.health()); out.writeDouble(s.fallBudget()); out.writeVarInt(s.maxDrop()); out.writeDouble(s.followRange());
        vector(out, s.position());
        out.writeBoolean(s.requested() != null); if (s.requested() != null) out.writeBlockPos(s.requested());
        out.writeBoolean(s.enemy() != null); if (s.enemy() != null) vector(out, s.enemy());
        out.writeBoolean(s.endpoint() != null); if (s.endpoint() != null) vector(out, s.endpoint());
        out.writeVarInt(s.path().size()); for (Vec3 v : s.path()) vector(out, v);
        out.writeVarInt(s.firstNode()); out.writeVarInt(s.totalNodes()); out.writeBoolean(s.complete());
        out.writeBoolean(s.truncated()); out.writeDouble(s.searchMillis());
        out.writeVarInt(s.samples().size());
        for (var sample : s.samples()) {
            vector(out, sample.position()); out.writeUtf(sample.source(), 64);
            out.writeUtf(sample.result(), 64); out.writeDouble(sample.score());
        }
        PerceptionDebugCodec.write(s.perception(), out);
        writeBehavior(s.behavior(), out);
        out.writeBoolean(s.capacityTruncated());
    }
    static NavigationDebugFrame read(FriendlyByteBuf in) {
        if (in.readableBytes() > MAX_FRAME_BYTES) throw new IllegalArgumentException("Oversized navigation frame");
        int size = count(in, NavigationDebugFrame.MAX_ENTITIES);
        var focused = in.readBoolean() ? in.readUUID() : null;
        List<NavigationDebugFrame.Entry> entries = new ArrayList<>(size);
        Set<Integer> slots = new HashSet<>(); Set<UUID> humans = new HashSet<>();
        for (int i = 0; i < size; i++) {
            int slot = in.readVarInt();
            if (slot < 0 || slot >= NavigationDebugFrame.MAX_ENTITIES || !slots.add(slot))
                throw new IllegalArgumentException("Invalid or duplicate observer slot");
            var snapshot = readSnapshot(in);
            if (!humans.add(snapshot.entityUuid())) throw new IllegalArgumentException("Duplicate observer entity");
            entries.add(new NavigationDebugFrame.Entry(slot, snapshot));
        }
        return new NavigationDebugFrame(List.copyOf(entries), focused);
    }
    private static NavigationDebugSnapshot readSnapshot(FriendlyByteBuf in) {
        var dimension = new net.minecraft.resources.ResourceLocation(in.readUtf(NavigationDebugSnapshot.MAX_DIMENSION_ID)); int id = in.readVarInt(); var uuid = in.readUUID();
        String name = in.readUtf(128), mode = in.readUtf(32), state = in.readUtf(64),
                navigator = in.readUtf(64), budget = in.readUtf(96), obstruction = in.readUtf(64);
        float health = in.readFloat(); double fall = in.readDouble(); int drop = in.readVarInt(); double range = in.readDouble();
        var position = vector(in);
        var requested = in.readBoolean() ? in.readBlockPos() : null;
        var enemy = in.readBoolean() ? vector(in) : null;
        var endpoint = in.readBoolean() ? vector(in) : null;
        int nodes = count(in, NavigationDebugSnapshot.MAX_PATH); List<Vec3> path = new ArrayList<>(nodes);
        for (int i = 0; i < nodes; i++) path.add(vector(in));
        int first = in.readVarInt(), total = in.readVarInt(); boolean complete = in.readBoolean(), truncated = in.readBoolean();
        double millis = in.readDouble(); int samples = count(in, NavigationDebugSnapshot.MAX_SAMPLES);
        List<NavigationDebugSnapshot.Sample> points = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) points.add(new NavigationDebugSnapshot.Sample(vector(in), in.readUtf(64), in.readUtf(64), in.readDouble()));
        return new NavigationDebugSnapshot(dimension, id, uuid, name, mode, state, navigator, budget,
                obstruction, health, fall, drop, range, position, requested, enemy, endpoint, List.copyOf(path), first, total,
                complete, truncated, millis, List.copyOf(points), PerceptionDebugCodec.read(in), readBehavior(in), in.readBoolean());
    }
    static void writeBehavior(BehaviorDebugSnapshot s, FriendlyByteBuf out) {
        out.writeLong(s.tick()); out.writeBoolean(s.authorityPresent()); out.writeLong(s.session());
        out.writeUtf(s.bridge(), BehaviorDebugSnapshot.STATE_LENGTH); out.writeBoolean(s.truncated());
        out.writeVarInt(s.actions().size());
        for (var a : s.actions()) {
            out.writeLong(a.session()); out.writeLong(a.sequence()); out.writeUtf(a.name(), BehaviorDebugSnapshot.NAME_LENGTH);
            out.writeUtf(a.status(), 16); out.writeVarInt(a.resources().size()); for (var resource : a.resources()) out.writeUtf(resource, 24);
            out.writeVarInt(a.heldResources().size()); for (var resource : a.heldResources()) out.writeUtf(resource, 24);
            out.writeInt(a.priority()); out.writeBoolean(a.interruptible()); out.writeLong(a.remainingTicks());
            out.writeBoolean(a.lease()); out.writeLong(a.leaseTicks()); out.writeLong(a.lastHeartbeat());
        }
        out.writeVarInt(s.feedback().size());
        for (var f : s.feedback()) {
            out.writeLong(f.session()); out.writeLong(f.sequence()); out.writeUtf(f.name(), BehaviorDebugSnapshot.NAME_LENGTH);
            out.writeUtf(f.status(), 16); out.writeUtf(f.reason(), BehaviorDebugSnapshot.REASON_LENGTH);
        }
        out.writeVarInt(s.goals().size());
        for (var g : s.goals()) {
            out.writeUtf(g.source(), 24); out.writeUtf(g.name(), BehaviorDebugSnapshot.NAME_LENGTH); out.writeInt(g.priority());
            out.writeVarInt(g.flags().size()); for (var flag : g.flags()) out.writeUtf(flag, 16);
            out.writeBoolean(g.entered()); out.writeBoolean(g.executing()); out.writeUtf(g.state(), BehaviorDebugSnapshot.STATE_LENGTH);
            out.writeLong(g.tokenSession()); out.writeLong(g.tokenSequence());
        }
        out.writeVarInt(s.progress().size());
        for (var p : s.progress()) {
            out.writeUtf(p.kind(), 24); out.writeUtf(p.item(), BehaviorDebugSnapshot.NAME_LENGTH);
            out.writeUtf(p.location(), ActionProgressSnapshot.LOCATION_LENGTH); out.writeUtf(p.state(), ActionProgressSnapshot.STATE_LENGTH);
            out.writeLong(p.sampledTick()); out.writeDouble(p.work()); out.writeDouble(p.totalWork()); out.writeDouble(p.workPerTick());
            out.writeLong(p.elapsedTicks()); out.writeLong(p.remainingTicks()); out.writeUtf(p.detail(), ActionProgressSnapshot.DETAIL_LENGTH);
        }
    }
    static BehaviorDebugSnapshot readBehavior(FriendlyByteBuf in) {
        long tick = in.readLong(); boolean authority = in.readBoolean(); long session = in.readLong();
        String bridge = in.readUtf(BehaviorDebugSnapshot.STATE_LENGTH); boolean truncated = in.readBoolean();
        int size = count(in, BehaviorDebugSnapshot.MAX_ACTIONS);
        List<BehaviorDebugSnapshot.Action> actions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            long actionSession = in.readLong(), sequence = in.readLong(); String name = in.readUtf(BehaviorDebugSnapshot.NAME_LENGTH), status = in.readUtf(16);
            int resources = count(in, BehaviorDebugSnapshot.MAX_RESOURCES); List<String> names = new ArrayList<>(resources);
            for (int j = 0; j < resources; j++) names.add(in.readUtf(24));
            int held = count(in, BehaviorDebugSnapshot.MAX_RESOURCES); List<String> owners = new ArrayList<>(held);
            for (int j = 0; j < held; j++) owners.add(in.readUtf(24));
            actions.add(new BehaviorDebugSnapshot.Action(actionSession, sequence, name, status, names, owners, in.readInt(), in.readBoolean(),
                    in.readLong(), in.readBoolean(), in.readLong(), in.readLong()));
        }
        size = count(in, BehaviorDebugSnapshot.MAX_FEEDBACK); List<BehaviorDebugSnapshot.Feedback> feedback = new ArrayList<>(size);
        for (int i = 0; i < size; i++) feedback.add(new BehaviorDebugSnapshot.Feedback(in.readLong(), in.readLong(),
                in.readUtf(BehaviorDebugSnapshot.NAME_LENGTH), in.readUtf(16), in.readUtf(BehaviorDebugSnapshot.REASON_LENGTH)));
        size = count(in, BehaviorDebugSnapshot.MAX_GOALS); List<BehaviorDebugSnapshot.RunningGoal> goals = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String source = in.readUtf(24), name = in.readUtf(BehaviorDebugSnapshot.NAME_LENGTH); int priority = in.readInt();
            int flags = count(in, BehaviorDebugSnapshot.MAX_FLAGS); List<String> names = new ArrayList<>(flags);
            for (int j = 0; j < flags; j++) names.add(in.readUtf(16));
            goals.add(new BehaviorDebugSnapshot.RunningGoal(source, name, priority, names, in.readBoolean(), in.readBoolean(),
                    in.readUtf(BehaviorDebugSnapshot.STATE_LENGTH), in.readLong(), in.readLong()));
        }
        size = count(in, BehaviorDebugSnapshot.MAX_PROGRESS); List<ActionProgressSnapshot> progress = new ArrayList<>(size);
        for (int i = 0; i < size; i++) progress.add(new ActionProgressSnapshot(in.readUtf(24), in.readUtf(BehaviorDebugSnapshot.NAME_LENGTH),
                in.readUtf(ActionProgressSnapshot.LOCATION_LENGTH), in.readUtf(ActionProgressSnapshot.STATE_LENGTH),
                in.readLong(), in.readDouble(), in.readDouble(), in.readDouble(),
                in.readLong(), in.readLong(), in.readUtf(ActionProgressSnapshot.DETAIL_LENGTH)));
        return new BehaviorDebugSnapshot(tick, authority, session, bridge, actions, feedback, goals, progress, truncated);
    }

}

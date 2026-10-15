package club.someoneice.humangunner;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/** Bounded data-only codec, separate from Forge channel registration for round-trip tests. */
final class NavigationDebugFrameCodec {
    private NavigationDebugFrameCodec() {}
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
        if (frame.entries().size() > NavigationDebugFrame.MAX_ENTITIES) throw new IllegalArgumentException("Too many observed entities");
        out.writeVarInt(frame.entries().size());
        out.writeBoolean(frame.focused() != null); if (frame.focused() != null) out.writeUUID(frame.focused());
        for (var entry : frame.entries()) { out.writeVarInt(entry.slot()); writeSnapshot(entry.snapshot(), out); }
    }
    private static void writeSnapshot(NavigationDebugSnapshot s, FriendlyByteBuf out) {
        if (s.path().size() > NavigationDebugSnapshot.MAX_PATH || s.samples().size() > NavigationDebugSnapshot.MAX_SAMPLES)
            throw new IllegalArgumentException("Oversized navigation geometry");
        out.writeResourceLocation(s.dimension()); out.writeVarInt(s.entityId()); out.writeUUID(s.entityUuid());
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
    }
    static NavigationDebugFrame read(FriendlyByteBuf in) {
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
        var dimension = in.readResourceLocation(); int id = in.readVarInt(); var uuid = in.readUUID();
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
                complete, truncated, millis, List.copyOf(points));
    }
}

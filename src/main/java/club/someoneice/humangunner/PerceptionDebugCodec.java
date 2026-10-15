package club.someoneice.humangunner;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/** Data-only, symmetric bounds; diagnostics never resolve an entity or run a world query. */
final class PerceptionDebugCodec {
    private PerceptionDebugCodec() {}
    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite perception geometry");
        return value;
    }
    private static double radius(double value) {
        if (finite(value) < 0 || value > 4096) throw new IllegalArgumentException("Invalid perception radius");
        return value;
    }
    private static void vector(FriendlyByteBuf out, Vec3 p) {
        out.writeDouble(finite(p.x)); out.writeDouble(finite(p.y)); out.writeDouble(finite(p.z));
    }
    private static Vec3 vector(FriendlyByteBuf in) {
        return new Vec3(finite(in.readDouble()), finite(in.readDouble()), finite(in.readDouble()));
    }
    private static int count(int value, int max) {
        if (value < 0 || value > max) throw new IllegalArgumentException("Oversized perception snapshot");
        return value;
    }
    static void write(PerceptionDebugSnapshot s, FriendlyByteBuf out) {
        count(s.rows().size(), PerceptionDebugSnapshot.MAX_ROWS);
        count(s.markers().size(), PerceptionDebugSnapshot.MAX_MARKERS);
        out.writeLong(s.tick()); vector(out, s.eye()); vector(out, s.view());
        out.writeDouble(radius(s.range())); out.writeDouble(finite(s.strangerHalfAngle())); out.writeDouble(finite(s.familiarHalfAngle()));
        out.writeUtf(s.awareness(), 64); out.writeUtf(s.decision(), 128); out.writeUtf(s.reason(), 256);
        out.writeVarInt(s.rows().size());
        for (var row : s.rows()) { out.writeUtf(row.group(), 32); out.writeUtf(row.label(), 96); out.writeUtf(row.value(), 256); }
        out.writeVarInt(s.markers().size());
        for (var m : s.markers()) {
            out.writeUtf(m.kind(), 32); out.writeUtf(m.label(), 96); vector(out, m.position());
            out.writeBoolean(m.end() != null); if (m.end() != null) vector(out, m.end());
            out.writeDouble(radius(m.radius()));
        }
    }
    static PerceptionDebugSnapshot read(FriendlyByteBuf in) {
        long tick = in.readLong(); Vec3 eye = vector(in), view = vector(in);
        double range = radius(in.readDouble()), stranger = finite(in.readDouble()), familiar = finite(in.readDouble());
        String awareness = in.readUtf(64), decision = in.readUtf(128), reason = in.readUtf(256);
        int size = count(in.readVarInt(), PerceptionDebugSnapshot.MAX_ROWS);
        List<PerceptionDebugSnapshot.Row> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) rows.add(new PerceptionDebugSnapshot.Row(in.readUtf(32), in.readUtf(96), in.readUtf(256)));
        size = count(in.readVarInt(), PerceptionDebugSnapshot.MAX_MARKERS);
        List<PerceptionDebugSnapshot.Marker> markers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) markers.add(new PerceptionDebugSnapshot.Marker(in.readUtf(32), in.readUtf(96),
                vector(in), in.readBoolean() ? vector(in) : null, radius(in.readDouble())));
        return new PerceptionDebugSnapshot(tick, eye, view, range, stranger, familiar, awareness, decision, reason,
                List.copyOf(rows), List.copyOf(markers));
    }
}

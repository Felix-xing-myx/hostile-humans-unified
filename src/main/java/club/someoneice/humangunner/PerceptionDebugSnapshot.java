package club.someoneice.humangunner;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Server-authored perception geometry and explanations. Never carries entity references. */
public record PerceptionDebugSnapshot(long tick, Vec3 eye, Vec3 view, double range,
        double strangerHalfAngle, double familiarHalfAngle, String awareness,
        String decision, String reason, List<Row> rows, List<Marker> markers) {
    public static final int MAX_ROWS = 128;
    public static final int MAX_MARKERS = 64;
    public static final PerceptionDebugSnapshot EMPTY = new PerceptionDebugSnapshot(0,
            Vec3.ZERO, new Vec3(0, 0, 1), 0, 0, 0, "", "", "", List.of(), List.of());
    public record Row(String group, String label, String value) {}
    /** kind: visible,dynamic,last,recognition,sound,direction,beacon,los,blocked,vision_new_ring,vision_known_ring. */
    public record Marker(String kind, String label, Vec3 position, Vec3 end, double radius) {}
}

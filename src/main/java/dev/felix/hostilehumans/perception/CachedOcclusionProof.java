package dev.felix.hostilehumans.perception;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Conservative geometric proof using only previously observed, currently full collision cubes. */
public final class CachedOcclusionProof {
    public static final int MAX_BLOCKERS = 8;
    public static final int MAX_SAMPLES = BodyVisibilitySamples.MAX_POINTS + 1;
    private CachedOcclusionProof() {}
    public static boolean covers(long oldMask, long currentMask) {
        return currentMask != 0 && (currentMask & ~oldMask) == 0;
    }
    public static boolean covered(Vec3 eye, List<Vec3> samples, List<AABB> cubes) {
        if (!finite(eye) || samples.isEmpty() || samples.size() > MAX_SAMPLES
                || cubes.isEmpty() || cubes.size() > MAX_BLOCKERS) return false;
        for (Vec3 point : samples) {
            if (!finite(point)) return false;
            Vec3 toward = eye.subtract(point);
            // Match real LOS endpoints; merely touching a floor/wall surface is not proof.
            Vec3 end = toward.lengthSqr() > 1.0E-8 ? point.add(toward.normalize().scale(1.0E-4)) : point;
            boolean blocked = false;
            for (AABB cube : cubes) {
                AABB interior = cube.deflate(1.0E-6);
                if (!cube.contains(eye) && interior.clip(eye, end).isPresent()) { blocked = true; break; }
            }
            if (!blocked) return false;
        }
        return true;
    }
    private static boolean finite(Vec3 p) { return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
}

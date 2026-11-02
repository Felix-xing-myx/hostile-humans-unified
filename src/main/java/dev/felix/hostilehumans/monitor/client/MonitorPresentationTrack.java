package dev.felix.hostilehumans.monitor.client;

import net.minecraft.world.phys.Vec3;

/** Render trajectory is independent of authority packet arrival. */
final class MonitorPresentationTrack {
    private Vec3 previous, current, previousCorrection = Vec3.ZERO, correction = Vec3.ZERO;
    void clear() { previous = current = null; previousCorrection = correction = Vec3.ZERO; }
    Vec3 correctionFor(Vec3 corrected) {
        return current == null ? Vec3.ZERO : correction.subtract(corrected.subtract(current));
    }
    Vec3 previousCorrectionFor(Vec3 corrected) {
        return current == null ? Vec3.ZERO : previousCorrection.subtract(corrected.subtract(current));
    }
    void reconcile(Vec3 corrected, boolean preserveTrajectory) {
        if (current == null || !preserveTrajectory) {
            previous = current = corrected; previousCorrection = correction = Vec3.ZERO; return;
        }
        Vec3 delta = corrected.subtract(current);
        previous = previous.add(delta); current = corrected;
        previousCorrection = previousCorrection.subtract(delta); correction = correction.subtract(delta);
    }
    void advance(Vec3 next) {
        previous = current == null ? next : current;
        current = next;
        // A new physics tick starts at the previous displayed endpoint. Decay across the
        // rendered tick instead of subtracting half the offset at its first frame.
        previousCorrection = correction; correction = correction.scale(.5);
    }
    Vec3 at(float partialTick) {
        float fraction = Math.max(0, Math.min(1, partialTick));
        return current == null ? null : previous.lerp(current, fraction)
                .add(previousCorrection.lerp(correction, fraction));
    }
}

package dev.felix.hostilehumans.behavior.combat.defense;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Only actual impact reports enter this bounded directional memory. No world or target queries. */
final class PressureThreatDirections {
    static final int MEMORY_TICKS = 40, MAX_HITS = 64;
    static final double MINIMUM_SHARE = .02;
    private record Hit(UUID source, int tick, float yaw, double share) { }
    private final ArrayDeque<Hit> hits = new ArrayDeque<>();
    void record(UUID source, int now, float yaw, double damage, double maximumHealth) {
        if (source == null || !Float.isFinite(yaw) || !Double.isFinite(damage) || damage <= 0
                || !Double.isFinite(maximumHealth) || maximumHealth <= 0) return;
        expire(now);
        if (hits.size() == MAX_HITS) hits.removeFirst();
        hits.addLast(new Hit(source, now, ShieldDefensePolicy.wrap(yaw), damage / maximumHealth));
    }
    Float yaw(int now, float current) {
        expire(now);
        // Aggregate each source's recent damage, but retain its latest observed arrival direction.
        var sources = new LinkedHashMap<UUID, Hit>();
        for (Hit hit : hits) {
            Hit previous = sources.get(hit.source());
            sources.put(hit.source(), new Hit(hit.source(), hit.tick(), hit.yaw(),
                    hit.share() + (previous == null ? 0 : previous.share())));
        }
        var significant = sources.values().stream().filter(hit -> hit.share() >= MINIMUM_SHARE).toList();
        if (significant.isEmpty()) return null;
        if (significant.size() == 1) return significant.get(0).yaw();
        var options = new ArrayList<Float>();
        options.add(current);
        double x = 0, z = 0;
        for (Hit hit : significant) {
            options.add(hit.yaw()); options.add(ShieldDefensePolicy.wrap(hit.yaw()-45));
            options.add(ShieldDefensePolicy.wrap(hit.yaw()+45));
            x += Math.cos(Math.toRadians(hit.yaw())) * hit.share();
            z += Math.sin(Math.toRadians(hit.yaw())) * hit.share();
        }
        if (x*x+z*z > 1e-12) options.add((float)Math.toDegrees(Math.atan2(z,x)));
        float best = current;
        double most = -1, bestAlignment = -Double.MAX_VALUE, leastTurn = Double.MAX_VALUE;
        for (float candidate : options) {
            double covered = 0, alignment = 0;
            for (Hit hit : significant) {
                float delta = ShieldDefensePolicy.wrap(hit.yaw()-candidate);
                if (Math.abs(delta) <= 45.0001) covered += hit.share();
                alignment += Math.cos(Math.toRadians(delta))*hit.share();
            }
            double turn = Math.abs(ShieldDefensePolicy.wrap(candidate-current));
            if (covered > most+1e-8 || Math.abs(covered-most)<1e-8
                    && (alignment > bestAlignment+1e-8 || Math.abs(alignment-bestAlignment)<1e-8 && turn < leastTurn)) {
                best = candidate; most = covered; bestAlignment = alignment; leastTurn = turn;
            }
        }
        return best;
    }
    private void expire(int now) { while (!hits.isEmpty() && now-hits.peekFirst().tick() >= MEMORY_TICKS) hits.removeFirst(); }
    void clear() { hits.clear(); }
}

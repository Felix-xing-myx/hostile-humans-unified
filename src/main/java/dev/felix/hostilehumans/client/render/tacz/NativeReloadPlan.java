package dev.felix.hostilehumans.client.render.tacz;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Deterministic clip scheduling only. No entity, player, Lua, ammunition or wall-clock access. */
public final class NativeReloadPlan {
    public record Segment(String animation, double start, double duration) {}
    public record Sample(String animation, double seconds, int segment) {}
    public record Request(boolean empty, int magazineLevel, int missingRounds, boolean manual, String fireMode) {}
    /** Native work seconds, supplied by the server after the actual script tick; no data-file duration guesses. */
    public record Phase(int index, boolean finishing, double start, double end, int suppliedAtStart) {
        public boolean valid() { return index > 0 && Double.isFinite(start) && Double.isFinite(end) && end > start; }
    }
    private final List<Segment> segments;
    private final double duration;
    private final boolean cyclic;

    private NativeReloadPlan(List<Segment> segments, boolean cyclic) {
        this.segments = List.copyOf(segments);
        this.cyclic = cyclic;
        this.duration = segments.isEmpty() ? 0 : segments.get(segments.size() - 1).start()
                + segments.get(segments.size() - 1).duration();
    }
    public List<Segment> segments() { return segments; }
    public double duration() { return duration; }
    public boolean supported() { return !segments.isEmpty(); }
    public boolean cyclic() { return cyclic; }
    public double feedingDuration() { return cyclic && !segments.isEmpty() ? segments.get(segments.size()-1).start() : duration; }

    public static boolean hasCanonicalClips(Map<String, Double> clips) {
        if (first(clips, "reload", "reload_empty", "reload_tactical") != null) return true;
        return first(clips, "reload_intro", "reload_intro_empty", "reload_empty_intro", "reload_intro_semi") != null
                && first(clips, "reload_loop", "reload_loop_semi") != null
                && first(clips, "reload_end", "reload_end_semi") != null;
    }

    public static NativeReloadPlan create(Map<String, Double> clips, Request request) {
        List<Segment> result = new ArrayList<>();
        boolean semiVariant = "BURST".equals(request.fireMode()) && clips.containsKey("reload_loop_semi");
        String suffix = semiVariant ? "_semi" : "";
        String intro = request.empty() ? first(clips, "reload_intro_empty" + suffix,
                "reload_empty_intro" + suffix, "reload_intro_empty", "reload_empty_intro")
                : first(clips, "reload_intro" + suffix, "reload_intro");
        String loop = first(clips, "reload_loop" + suffix, "reload_loop");
        String end = first(clips, "reload_end" + suffix, "reload_end");
        // Scripted shotguns can declare reload.type=magazine. Only real clip capabilities decide cycling.
        boolean cyclic = intro != null && loop != null && end != null;
        if (cyclic) {
            append(result, clips, intro);
            int remaining = Math.max(1, Math.min(512, request.missingRounds()));
            while (remaining > 0) {
                boolean pair = remaining > 1 && clips.containsKey("reload_loop_2");
                append(result, clips, pair ? "reload_loop_2" : loop);
                remaining -= pair ? 2 : 1;
            }
            append(result, clips, end);
        } else {
            String base = request.empty() ? "reload_empty" : "reload_tactical";
            // Packs may supply pump-mode keyframes in addition to the normal magazine variant.
            if (request.empty() && "SEMI".equals(request.fireMode()) && clips.containsKey("reload_empty_pump"))
                base = "reload_empty_pump";
            String selected = variant(clips, base, request.magazineLevel());
            if (selected == null) selected = first(clips, "reload");
            // Single-shot weapons often provide only empty reload; do not use it for a partial magazine.
            if (selected != null) append(result, clips, selected);
        }
        return new NativeReloadPlan(result, cyclic);
    }

    public static String variant(Map<String, Double> clips, String base, int level) {
        if (level > 0) {
            String exact = base + "_xmag_" + level;
            if (clips.containsKey(exact)) return exact;
            if ((level == 2 || level == 3) && clips.containsKey(base + "_xmag_23")) return base + "_xmag_23";
            if (clips.containsKey(base + "_xmag")) return base + "_xmag";
            // Some authored packs deliberately use their level-2 animation for level 3.
            if (level == 3 && clips.containsKey(base + "_xmag_2")) return base + "_xmag_2";
        }
        return first(clips, base);
    }

    private static String first(Map<String, Double> clips, String... names) {
        for (String name : names) if (clips.containsKey(name) && clips.get(name) > 0) return name;
        return null;
    }
    private static void append(List<Segment> result, Map<String, Double> clips, String name) {
        double duration = clips.getOrDefault(name, 0D);
        if (!Double.isFinite(duration) || duration <= 0) return;
        double start = result.isEmpty() ? 0 : result.get(result.size() - 1).start() + result.get(result.size() - 1).duration();
        result.add(new Segment(name, start, duration));
    }

    /** Work seconds are server-authored native reload work, independent of NPC speed and client FPS. */
    public double presentationTime(double workSeconds, double nativeDurationSeconds) {
        if (!Double.isFinite(workSeconds) || workSeconds < 0 || !(nativeDurationSeconds > 0)) return 0;
        return Math.min(Math.max(0, duration - .0001), duration * Math.min(.99999, workSeconds / nativeDurationSeconds));
    }

    /** Feeding and finishing have independent authoritative deadlines, including early stop/partial supply. */
    public double presentationTime(double workSeconds, double fallbackSeconds, Phase phase) {
        if (!cyclic) return presentationTime(workSeconds, fallbackSeconds);
        if (phase == null || !phase.valid()) return Double.NaN; // Wait for the first real native script sample.
        if (phase.finishing() && workSeconds < phase.start()) {
            // A returned FINISHING state may reserve the last loop remainder before the ending begins.
            return Math.min(feedingDuration() - .0001,
                    feedingDuration() * Math.max(0, workSeconds / Math.max(.0001, phase.start())));
        }
        double start = phasePresentationStart(phase);
        double end = phase.finishing() ? duration : feedingDuration();
        double progress = Math.max(0, Math.min(.99999, (workSeconds - phase.start()) / (phase.end() - phase.start())));
        return Math.min(Math.max(0, end - .0001), start + Math.max(0, end - start) * progress);
    }

    public double phasePresentationStart(Phase phase) {
        if (!cyclic || phase == null) return 0;
        if (phase.finishing()) return feedingDuration();
        if (phase.index() <= 1 || phase.suppliedAtStart() <= 0) return 0;
        // A repeated feeding phase after a script's interruption resumes after already supplied loops.
        int supplied = phase.suppliedAtStart();
        double start = segments.get(0).duration();
        for (int i = 1; i < segments.size()-1 && supplied > 0; i++) {
            Segment segment = segments.get(i);
            supplied -= segment.animation().equals("reload_loop_2") ? 2 : 1;
            start = segment.start() + segment.duration();
        }
        return Math.min(feedingDuration() - .0001, start);
    }
    public Sample sample(double presentationSeconds) {
        if (segments.isEmpty()) return null;
        double time = Math.max(0, Math.min(duration - .0001, presentationSeconds));
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (time < segment.start() + segment.duration() || i == segments.size() - 1)
                return new Sample(segment.animation(), time - segment.start(), i);
        }
        throw new IllegalStateException("Unreachable clip interval");
    }

    public static double checkpointWork(double work, double rate, long checkpoint, double gameTime, boolean paused) {
        if (!Double.isFinite(work) || work < 0 || !Double.isFinite(rate) || rate <= 0) return 0;
        return work + (paused ? 0 : Math.min(10, Math.max(0, gameTime - checkpoint))) * rate;
    }
}

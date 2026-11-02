package dev.felix.hostilehumans.compat.tacz;

import dev.felix.hostilehumans.entity.Human;

import net.minecraft.nbt.CompoundTag;

/** Server-authored visual timing only. Never completes a reload or modifies ammo.
 * Native countdown is a phase deadline, not necessarily the whole reload deadline. */
public final class TaczReloadVisualTimeline {
    private TaczReloadVisualTimeline() { }

    public static void begin(Human human, String gunId, int estimatedTicks, int finishingTicks, int feedingTicks) {
        if (human.level().isClientSide) return;
        long now = human.level().getGameTime();
        CompoundTag tag = new CompoundTag();
        tag.putString("Gun", gunId);
        tag.putLong("Start", now);
        tag.putLong("End", now + Math.max(1, Math.min(72000, estimatedTicks)));
        tag.putInt("Tail", Math.max(0, Math.min(72000, finishingTicks)));
        tag.putInt("Feed", Math.max(0, Math.min(72000, feedingTicks)));
        human.setTaczReloadVisual(tag);
    }

    public static void update(Human human, long remainingMillis, boolean finishing) {
        if (human.level().isClientSide || remainingMillis < 0) return;
        CompoundTag tag = human.getTaczReloadVisual();
        if (tag.isEmpty()) return;
        long phaseEnd = human.level().getGameTime() + Math.max(1, (Math.min(3600000, remainingMillis) + 49) / 50);
        long previousEnd = tag.getLong("End");
        boolean firstFeed = !finishing && !tag.getBoolean("SeenFeed");
        long estimatedEnd = previousEnd;
        if (firstFeed) {
            // Actual first-phase duration also captures pack scripts and attachment timing.
            // Correct a faster reload early instead of waiting until the final few ticks.
            int feed = tag.getInt("Feed");
            if (feed > 0) {
                double ratio = Math.max(.01, Math.min(100, (double) (phaseEnd - tag.getLong("Start")) / feed));
                estimatedEnd = tag.getLong("Start") + Math.max(1, Math.min(72000,
                        (long) Math.ceil((previousEnd - tag.getLong("Start")) * ratio)));
                tag.putInt("Tail", (int) Math.min(72000, Math.ceil(tag.getInt("Tail") * ratio)));
            }
            tag.putBoolean("SeenFeed", true);
        }
        // Feeding may repeat for manual guns. Only finishing is authoritative for the whole cycle.
        long end = finishing ? phaseEnd : Math.max(estimatedEnd, phaseEnd + tag.getInt("Tail"));
        boolean phaseChanged = tag.getBoolean("Finishing") != finishing;
        if (firstFeed || phaseChanged || Math.abs(end - previousEnd) > 2) {
            tag.putLong("End", end);
            tag.putBoolean("Finishing", finishing);
            human.setTaczReloadVisual(tag);
        }
    }

    public static void clear(Human human) {
        if (!human.getTaczReloadVisual().isEmpty()) human.setTaczReloadVisual(new CompoundTag());
    }

    /** Last-session receipt lets audio distinguish natural completion from cancellation. Replaced on next begin. */
    public static void complete(Human human, double work) {
        if (human.level().isClientSide) return;
        CompoundTag tag = human.getTaczReloadVisual();
        if (tag.isEmpty()) return;
        tag.putBoolean("Complete", true);
        tag.putDouble("Work", Math.max(0, work));
        tag.putLong("WorkTick", human.level().getGameTime());
        human.setTaczReloadVisual(tag);
    }

    /** Begin-time presentation facts survive ammunition changes and a viewer joining mid-reload. */
    public static void describe(Human human, boolean empty, int missing, int magazineLevel,
                                boolean manual, String fireMode, int nativeTicks, long session) {
        if (human.level().isClientSide) return;
        CompoundTag tag = human.getTaczReloadVisual();
        if (tag.isEmpty()) return;
        tag.putBoolean("Empty", empty);
        tag.putInt("Missing", Math.max(1, Math.min(512, missing)));
        tag.putInt("MagazineLevel", Math.max(0, Math.min(3, magazineLevel)));
        tag.putBoolean("Manual", manual);
        tag.putString("FireMode", fireMode);
        tag.putInt("NativeTicks", Math.max(1, Math.min(72000, nativeTicks)));
        tag.putLong("Session", session);
        human.setTaczReloadVisual(tag);
    }

    /** Snapshot the returned native script state, in the same work units as the admitted reload clock.
     * FEEDING may represent an entire scripted loop sequence; it must never be inferred from FeedType.
     * This method reads presentation facts only and cannot advance or complete ammunition work. */
    public static void phase(Human human, String phase, boolean finishing, double work,
                             long remainingMillis, int supplied) {
        if (human.level().isClientSide || !Double.isFinite(work) || work < 0 || remainingMillis < 0) return;
        CompoundTag tag = human.getTaczReloadVisual();
        if (tag.isEmpty()) return;
        long now = human.level().getGameTime();
        double end = work + Math.min(3600000, remainingMillis) / 50D;
        boolean first = !tag.contains("PhaseIndex");
        boolean changed = first || !phase.equals(tag.getString("NativePhase"))
                || end > tag.getDouble("PhaseEndWork") + 2;
        boolean suppliedChanged = supplied != tag.getInt("Supplied");
        if (!changed && !suppliedChanged && now - tag.getLong("PhaseTick") < 10
                && Math.abs(end-tag.getDouble("PhaseEndWork")) <= 2) return;
        if (changed) {
            tag.putInt("PhaseIndex", first ? 1 : tag.getInt("PhaseIndex") + 1);
            // First native tick can arrive after several admitted work units; the first phase began at zero.
            double start = first && !finishing ? 0 : work;
            // Some scripts return FINISHING on the last feed but include the remainder of its loop
            // in the countdown. Preserve the actual preceding feeding deadline when it fits inside
            // this endpoint. A genuine early interruption ends before that deadline and uses now.
            double feedingEnd = tag.getDouble("PhaseEndWork");
            if (finishing && !first && !tag.getBoolean("PhaseFinishing") && feedingEnd < end)
                start = feedingEnd;
            tag.putDouble("PhaseStartWork", Math.max(0, start));
            tag.putInt("PhaseSuppliedStart", Math.max(0, supplied));
        }
        tag.putString("NativePhase", phase);
        tag.putBoolean("PhaseFinishing", finishing);
        tag.putDouble("PhaseEndWork", Math.max(tag.getDouble("PhaseStartWork") + .001, end));
        tag.putLong("PhaseRemaining", remainingMillis);
        tag.putDouble("PhaseWork", work);
        tag.putLong("PhaseTick", now);
        tag.putInt("Supplied", Math.max(0, supplied));
        // Phase boundaries and actual feed events are immediate checkpoints for viewers joining mid-action.
        tag.putDouble("Work", work);
        tag.putLong("WorkTick", now);
        human.setTaczReloadVisual(tag);
    }

    /** Native reload work and its admitted rate are authored by the server, not client configuration. */
    public static void work(Human human, double work, double rate) {
        if (human.level().isClientSide || !Double.isFinite(work) || work < 0
                || !Double.isFinite(rate) || rate <= 0) return;
        CompoundTag tag = human.getTaczReloadVisual();
        if (tag.isEmpty()) return;
        long now = human.level().getGameTime();
        boolean changedRate = !tag.contains("WorkRate") || Math.abs(tag.getDouble("WorkRate") - rate) > 1e-8;
        // A periodic checkpoint bounds display drift; rate transitions are immediate.
        if (!changedRate && now - tag.getLong("WorkTick") < 10) return;
        tag.putDouble("Work", work);
        tag.putDouble("WorkRate", rate);
        tag.putLong("WorkTick", now);
        human.setTaczReloadVisual(tag);
    }
}

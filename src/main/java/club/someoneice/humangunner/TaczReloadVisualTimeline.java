package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
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
}

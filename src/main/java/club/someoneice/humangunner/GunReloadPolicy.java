package club.someoneice.humangunner;

/** Minimum wait before an NPC may recover a gun whose native reload stalled. */
final class GunReloadPolicy {
    private GunReloadPolicy() {}

    static int capacityCheckedRounds(int magazineRounds, boolean chambered, boolean inventoryFeed, boolean openBolt) {
        return Math.max(0, magazineRounds) + (inventoryFeed && chambered && !openBolt ? 1 : 0);
    }

    static int inventoryFeedDurationTicks(double feedSeconds, double cooldownSeconds) {
        double feed = Double.isFinite(feedSeconds) ? Math.max(0.0D, feedSeconds) : 0.0D;
        double cooldown = Double.isFinite(cooldownSeconds) ? Math.max(0.0D, cooldownSeconds) : 0.0D;
        return (int) Math.max(1L, Math.min(72000L, (long) Math.ceil((feed + cooldown) * 20.0D)));
    }

    static boolean shouldIdleTopOff(int loaded, int capacity, boolean inCombat,
                                    boolean fleeing, boolean usingItem, boolean reloading) {
        return reloadThresholdSatisfied(loaded, capacity, false)
                && !inCombat && !fleeing && !usingItem && !reloading;
    }

    static boolean reloadThresholdSatisfied(int loaded, int capacity, boolean inCombat) {
        return capacity > 0 && loaded >= 0 && (inCombat ? loaded == 0 : (long) loaded * 4L < capacity);
    }

    static int recoveryDelayTicks(boolean manualFeed, int missingRounds,
                                  double feedSeconds, double cooldownSeconds) {
        double feed = Double.isFinite(feedSeconds) ? Math.max(0.0D, feedSeconds) : 0.0D;
        double cooldown = Double.isFinite(cooldownSeconds) ? Math.max(0.0D, cooldownSeconds) : 0.0D;
        double totalSeconds = feed * (manualFeed ? Math.max(1, missingRounds) : 1)
                + cooldown;
        return (int) Math.max(60L, Math.min(72000L,
                (long) Math.ceil(totalSeconds * 20.0D) + 20L));
    }

    static int hardLimitTicks(int recoveryDelayTicks) {
        return Math.max(600, Math.min(216000, recoveryDelayTicks * 3));
    }

    static int boltRecoveryDelayTicks(double boltSeconds, double feedSeconds) {
        return (int) Math.max(40L, Math.min(400L,
                (long) Math.ceil((Math.max(0.0D, boltSeconds)
                        + Math.max(0.0D, feedSeconds)) * 20.0D) + 20L));
    }
}

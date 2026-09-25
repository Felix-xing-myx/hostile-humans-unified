package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.LivingEntity;

/** Distance and pressure gates shared by retreat potions and retreat food. */
public final class RetreatRecoveryPolicy {
    public static final double FORCED_RETREAT_HEALTH_RATIO = 0.25D;
    private static final int QUIET_TICKS_BEFORE_RECOVERY = 10;
    private static final double OPEN_RECOVERY_DISTANCE_SQR = 12.0D * 12.0D;
    private static final double COVER_RECOVERY_DISTANCE_SQR = 9.0D * 9.0D;
    private static final double SAFE_RETURN_DISTANCE_SQR = 24.0D * 24.0D;

    private RetreatRecoveryPolicy() {
    }

    public static boolean shouldForceRetreat(double healthRatio) {
        return healthRatio <= FORCED_RETREAT_HEALTH_RATIO;
    }

    static boolean safeToReturn(double distanceSqr, int quietTicks, int retreatTicks) {
        return distanceSqr >= SAFE_RETURN_DISTANCE_SQR
                && quietTicks >= 40
                && retreatTicks >= 40;
    }

    static boolean canStart(Human human, LivingEntity threat) {
        if (threat == null || !threat.isAlive()) {
            return false;
        }
        int ticksSincePressure = Integer.MAX_VALUE;
        if (human.getLastHurtByMob() != null) {
            ticksSincePressure = Math.max(0, human.tickCount - human.getLastHurtByMobTimestamp());
        }
        int gunfireUntil = HumanGunner.recentGunfireUntil(human);
        if (gunfireUntil >= human.tickCount) {
            ticksSincePressure = Math.min(ticksSincePressure,
                    Math.max(0, human.tickCount + CombatPressurePolicy.GUNFIRE_MEMORY_TICKS - gunfireUntil));
        }
        return canStart(human.distanceToSqr(threat),
                !human.getSensing().hasLineOfSight(threat), ticksSincePressure);
    }

    static boolean canStart(double distanceSqr, boolean behindCover, int ticksSincePressure) {
        return ticksSincePressure > QUIET_TICKS_BEFORE_RECOVERY
                && distanceSqr >= (behindCover
                ? COVER_RECOVERY_DISTANCE_SQR : OPEN_RECOVERY_DISTANCE_SQR);
    }

    static boolean shouldBreakOffUse(double distanceSqr, boolean hasLineOfSight, int remainingUseTicks) {
        return hasLineOfSight && distanceSqr < COVER_RECOVERY_DISTANCE_SQR
                && remainingUseTicks > 3;
    }

    public static boolean isUsefulRetreatDestination(double currentDistanceSqr, double candidateDistanceSqr) {
        return Math.sqrt(candidateDistanceSqr) >= Math.sqrt(currentDistanceSqr) + 3.0D;
    }
}

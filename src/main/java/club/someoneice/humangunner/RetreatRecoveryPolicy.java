package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.LivingEntity;

/** Distance and pressure gates shared by retreat potions and retreat food. */
public final class RetreatRecoveryPolicy {
    private static final int QUIET_TICKS_BEFORE_RECOVERY = 10;
    private static final double OPEN_RECOVERY_DISTANCE_SQR = 12.0D * 12.0D;
    private static final double COVER_RECOVERY_DISTANCE_SQR = 9.0D * 9.0D;
    private static final double SAFE_RETURN_DISTANCE_SQR = 24.0D * 24.0D;

    private RetreatRecoveryPolicy() {
    }

    public static boolean shouldForceRetreat(Human human) {
        return human.getHealth() / Math.max(1.0F, human.getMaxHealth())
                < CombatAiConfig.forHuman(human).retreatHealthRatio();
    }

    private static final class EscapeFood { final int since; boolean separated; EscapeFood(int now) { since=now; } }
    private static final java.util.Map<Human, EscapeFood> FOOD_ESCAPE = new java.util.WeakHashMap<>();
    static void observeEscape(Human human, boolean fleeing) {
        if (!fleeing) FOOD_ESCAPE.remove(human);
        else FOOD_ESCAPE.computeIfAbsent(human, ignored -> new EscapeFood(human.tickCount));
    }
    static void clearEscape(Human human) { FOOD_ESCAPE.remove(human); }
    static boolean canStartFood(Human human, LivingEntity threat) {
        if (!human.isFleeing) { clearEscape(human); return true; }
        if (threat == null || !threat.isAlive()) return true;
        observeEscape(human, true);
        EscapeFood state = FOOD_ESCAPE.get(human);
        double distance = human.distanceToSqr(threat);
        if (distance >= 9) state.separated = true;
        double eventual = RecoverySupplies.projectedHealth(human);
        var regeneration = human.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
        if (regeneration != null) eventual += regeneration.getDuration() / 20.0
                * (regeneration.getAmplifier() >= 1 ? 2 : 1);
        boolean sufficient = eventual > human.getMaxHealth() * CombatAiConfig.forHuman(human).resumeHealthRatio();
        return foodOpportunity(distance, sufficient, human.tickCount-state.since, state.separated);
    }
    static boolean foodOpportunity(double distanceSqr, boolean sufficient, int elapsed, boolean separated) {
        return sufficient ? distanceSqr > 64 : distanceSqr >= 9 || !separated && elapsed >= 60;
    }

    static boolean safeToReturn(double distanceSqr, int quietTicks, int retreatTicks) {
        return distanceSqr >= SAFE_RETURN_DISTANCE_SQR
                && quietTicks >= 40
                && retreatTicks >= 40;
    }

    /** Actual healing may end a retreat even when a pursuer prevents a 24-block gap. */
    static boolean canResumeAfterHealing(double healthRatio, double resumeRatio,
            int retreatTicks) {
        return healthRatio >= resumeRatio && retreatTicks >= 40;
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

package dev.felix.hostilehumans.behavior.combat.retreat;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.CombatAiConfig;
import dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies;

import net.minecraft.world.entity.LivingEntity;

/** Food timing and movement exit rules. Non-food recovery uses the shared restorative executor. */
public final class RetreatRecoveryPolicy {
    private static final double SAFE_RETURN_DISTANCE_SQR = 24.0D * 24.0D;

    private RetreatRecoveryPolicy() {
    }

    public static boolean shouldForceRetreat(Human human) {
        return human.getHealth() / Math.max(1.0F, human.getMaxHealth())
                < CombatAiConfig.forHuman(human).retreatHealthRatio();
    }

    private static final class EscapeFood { final int since; boolean separated; EscapeFood(int now) { since=now; } }
    private static final java.util.Map<Human, EscapeFood> FOOD_ESCAPE = new java.util.WeakHashMap<>();
    public static void observeEscape(Human human, boolean fleeing) {
        if (!fleeing) FOOD_ESCAPE.remove(human);
        else FOOD_ESCAPE.computeIfAbsent(human, ignored -> new EscapeFood(human.tickCount));
    }
    public static void clearEscape(Human human) { FOOD_ESCAPE.remove(human); }
    public static boolean canStartFood(Human human, LivingEntity threat) {
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
    public static boolean foodOpportunity(double distanceSqr, boolean sufficient, int elapsed, boolean separated) {
        return sufficient ? distanceSqr > 64 : distanceSqr >= 9 || !separated && elapsed >= 60;
    }

    public static boolean safeToReturn(double distanceSqr, int quietTicks, int retreatTicks) {
        return distanceSqr >= SAFE_RETURN_DISTANCE_SQR
                && quietTicks >= 40
                && retreatTicks >= 40;
    }

    /** Actual healing may end a retreat even when a pursuer prevents a 24-block gap. */
    public static boolean canResumeAfterHealing(double healthRatio, double resumeRatio,
            int retreatTicks) {
        return healthRatio >= resumeRatio && retreatTicks >= 40;
    }


    public static boolean isUsefulRetreatDestination(double currentDistanceSqr, double candidateDistanceSqr) {
        return Math.sqrt(candidateDistanceSqr) >= Math.sqrt(currentDistanceSqr) + 3.0D;
    }
}

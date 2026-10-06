package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.WeakHashMap;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Shared by native escape, target acquisition and adaptive retreat. Server thread only. */
public final class LowHealthCombatController {
    private record Sample(int tick, float healed, float damaged, UUID attacker) { }
    private static final class State {
        final LowHealthCombatPolicy.State decision = new LowHealthCombatPolicy.State();
        final ArrayDeque<Sample> samples = new ArrayDeque<>();
        int scanTick = Integer.MIN_VALUE;
        boolean alone;
        UUID scannedTarget;
    }
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private LowHealthCombatController() { }

    public static void recordHealing(Human human, float actual) { record(human, actual, 0.0F, null); }
    public static void recordDamage(Human human, float actual, LivingEntity attacker) {
        record(human, 0.0F, actual, attacker == null ? null : attacker.getUUID());
    }

    private static void record(Human human, float healed, float damaged, UUID attacker) {
        if (human.level().isClientSide || !Float.isFinite(healed) || !Float.isFinite(damaged)
                || (healed <= 0.0F && damaged <= 0.0F)) return;
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        state.samples.addLast(new Sample(human.tickCount, Math.max(0, healed), Math.max(0, damaged), attacker));
        prune(state, human.tickCount, 400);
    }

    public static boolean shouldHoldGround(Human human, LivingEntity requestedThreat) {
        if (human.level().isClientSide || !human.isAlive()) return false;
        CombatAiConfig ai = CombatAiConfig.forHuman(human);
        if (!ai.enabled()) return false;
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        double ratio = human.getHealth() / Math.max(1.0F, human.getMaxHealth());
        if (ratio >= ai.resumeHealthRatio()) {
            state.decision.reset();
            return false;
        }
        LivingEntity threat = requestedThreat != null ? requestedThreat
                : human.getTarget() != null ? human.getTarget() : human.toAvoid;
        if (threat == null || !threat.isAlive() || threat.level() != human.level() || !human.canAttack(threat))
            return false;
        // No scans or trial decisions are needed outside the ordinary low-health band.
        if (ratio >= Math.max(ai.retreatHealthRatio(), ai.legacyFleeHealthRatio())) return false;
        if (human.tickCount >= state.scanTick || !Objects.equals(state.scannedTarget, threat.getUUID())) {
            state.scanTick = human.tickCount + 10;
            state.scannedTarget = threat.getUUID();
            state.alone = human.level().getEntitiesOfClass(LivingEntity.class,
                    human.getBoundingBox().inflate(16.0D, 8.0D, 16.0D), candidate ->
                            candidate != human && candidate != threat && candidate.isAlive()
                            && human.canAttack(candidate) && human.getSensing().hasLineOfSight(candidate)
                            && (candidate instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == human
                            || candidate == human.getLastHurtByMob()
                            || candidate instanceof net.minecraft.world.entity.monster.Monster
                            || candidate instanceof net.minecraft.world.entity.player.Player
                            || candidate instanceof Human)).isEmpty();
            if (state.alone && human.level() instanceof net.minecraft.server.level.ServerLevel server) {
                state.alone = state.samples.stream().noneMatch(sample -> {
                    if (sample.attacker() == null || sample.attacker().equals(threat.getUUID())
                            || human.tickCount - sample.tick() > 80) return false;
                    var entity = server.getEntity(sample.attacker());
                    return entity instanceof LivingEntity attacker && attacker.isAlive() && human.canAttack(attacker);
                });
            }
        }
        LowHealthCombatConfig config = ai.lowHealthCombat();
        prune(state, human.tickCount, Math.max(80, config.observationTicks()));
        float healed = 0, damaged = 0, lastStrike = 0;
        int since = Math.max(state.decision.observationStart(), human.tickCount - config.observationTicks());
        for (Sample sample : state.samples) {
            if (sample.tick() >= since) { healed += sample.healed(); damaged += sample.damaged(); }
            if (sample.damaged() > 0) lastStrike = sample.damaged();
        }
        boolean recovery = ai.itemRecoveryEnabled() && (RecoverySupplies.hasPendingGradualHealing(human)
                || RecoverySupplies.hasOrdinaryFood(human) || CombatFoodRecoveryGoal.isActive(human))
                || human.hasEffect(MobEffects.REGENERATION);
        return state.decision.allow(new LowHealthCombatPolicy.Input(human.tickCount, human.getHealth(),
                human.getMaxHealth(), threat.getUUID(), state.alone
                && LowHealthCombatPolicy.weakTarget(threat.getHealth(), threat.getMaxHealth()),
                recovery, ai.itemRecoveryEnabled() && (RecoverySupplies.hasUrgentInstantHealing(human)
                || RecoverySupplies.hasInstantHealingInProgress(human)),
                healed, damaged, lastStrike), config, human.getRandom()::nextDouble);
    }

    private static void prune(State state, int tick, int window) {
        while (!state.samples.isEmpty() && tick - state.samples.peekFirst().tick() > window)
            state.samples.removeFirst();
        // Merge-heavy modpacks cannot retain an unbounded event queue.
        while (state.samples.size() > 1024) state.samples.removeFirst();
    }
}

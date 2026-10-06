package club.someoneice.humangunner;

import java.util.Objects;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/** A bounded permission to fight while low, never a new damage-triggered escape. */
final class LowHealthCombatPolicy {
    enum Mode { RETREAT, FINISH, TRIAL, SUSTAIN }
    record Input(int tick, float health, float maximumHealth, UUID target,
                 boolean loneWeakTarget, boolean recoveryAvailable, boolean instantHealing,
                 float healed, float damaged, float lastStrike) { }

    static boolean critical(double health, double maximumHealth, LowHealthCombatConfig config) {
        return health / Math.max(1.0D, maximumHealth) < config.criticalHealthRatio()
                && health < config.criticalAbsoluteHealth();
    }

    static boolean weakTarget(double health, double maximumHealth) {
        return health < 30.0D && health / Math.max(1.0D, maximumHealth) < 0.30D;
    }

    static boolean healingCovers(float healed, float damaged, double margin) {
        // Quiet healing while running away is not evidence about an opponent's damage.
        return damaged > 0.0F && healed >= damaged * margin;
    }

    static final class State {
        private UUID target;
        private Mode mode = Mode.RETREAT;
        private boolean finishRolled;
        private boolean finishApproved;
        private int trialStarted;
        private int holdUntil;
        private int retryAfter;
        private float startHealth;

        void reset() {
            target = null;
            mode = Mode.RETREAT;
            finishRolled = false;
            finishApproved = false;
            retryAfter = 0;
            trialStarted = 0;
        }

        int observationStart() { return trialStarted; }
        Mode mode() { return mode; }

        boolean allow(Input input, LowHealthCombatConfig config, DoubleSupplier random) {
            if (!Objects.equals(target, input.target())) {
                reset();
                target = input.target();
            }
            if (input.target() == null || critical(input.health(), input.maximumHealth(), config)
                    || input.instantHealing()) return retreat(input.tick(), config);
            // The previous real hit must not be enough to kill during an experiment.
            if (input.lastStrike() >= input.health()) return retreat(input.tick(), config);
            if (input.loneWeakTarget() && !finishRolled) {
                finishRolled = true;
                finishApproved = random.getAsDouble() < config.finishChance();
            }
            if (input.loneWeakTarget() && finishApproved) {
                mode = Mode.FINISH;
                return true;
            }
            if (mode == Mode.SUSTAIN && !input.recoveryAvailable() && input.healed() <= 0)
                return retreat(input.tick(), config);
            if (mode == Mode.FINISH) return retreat(input.tick(), config);
            if (mode == Mode.TRIAL || mode == Mode.SUSTAIN) {
                // Do not wait for the hard floor after a clearly losing exchange.
                if (input.health() < startHealth - Math.max(2.0F, startHealth * 0.25F))
                    return retreat(input.tick(), config);
                if (mode == Mode.TRIAL && input.tick() < trialStarted + config.trialTicks()) return true;
                if (mode == Mode.SUSTAIN && input.tick() < holdUntil) return true;
                if (!healingCovers(input.healed(), input.damaged(), config.healingSafetyMultiplier()))
                    return retreat(input.tick(), config);
                mode = Mode.SUSTAIN;
                holdUntil = input.tick() + config.decisionHoldTicks();
                startHealth = input.health();
                return true;
            }
            if (input.tick() < retryAfter) return false;
            mode = Mode.TRIAL;
            trialStarted = input.tick();
            startHealth = input.health();
            return true;
        }

        private boolean retreat(int tick, LowHealthCombatConfig config) {
            if (mode != Mode.RETREAT) retryAfter = tick + config.retryDelayTicks();
            mode = Mode.RETREAT;
            return false;
        }
    }
}

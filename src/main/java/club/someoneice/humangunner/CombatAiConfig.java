package club.someoneice.humangunner;

import com.google.gson.JsonObject;


/** Immutable per-tier AI settings; the enabled master switch belongs to global.json. */
public record CombatAiConfig(
        boolean enabled,
        double retreatHealthRatio,
        double criticalHealthRatio,
        double resumeHealthRatio,
        int coverSearchRadius,
        int coverSearchAttempts,
        double recoveryDamageToleranceRatio,
        int recoveryCommitTicks,
        int gradualHealIntervalTicks,
        float foodRecoveryMinHealth,
        float foodRecoveryMaxHealth,
        int shieldBlockDurationMinTicks,
        int shieldBlockDurationMaxTicks,
        int shieldBlockCooldownTicks,
        int tacticalRepositionInterval,
        double allySpacingRadius,
        int combatJumpIntervalMin,
        int combatJumpIntervalMax,
        double combatJumpChance,
        boolean itemRecoveryEnabled,
        double shieldUseSpeedMultiplier,
        double legacyFleeHealthRatio,
        double runAwayMiddleFightChance,
        double midBattleBuffInsteadOfRunChance,
        double greetChance,
        LowHealthCombatConfig lowHealthCombat
) {
    public static CombatAiConfig forHuman(com.craftix.hostile_humans.entity.entities.Human human) {
        return UnifiedConfig.get().combatAi(TierAttributes.key(human));
    }

    static CombatAiConfig parse(JsonObject root) {
        CombatAiConfig defaults = defaults();
        return new CombatAiConfig(
                    bool(root, "enabled", defaults.enabled),
                    bounded(root, "retreat_health_ratio", defaults.retreatHealthRatio, 0.1D, 0.9D),
                    bounded(root, "critical_health_ratio", defaults.criticalHealthRatio, 0.05D, 0.75D),
                    bounded(root, "resume_health_ratio", defaults.resumeHealthRatio, 0.2D, 1.0D),
                    integer(root, "cover_search_radius", defaults.coverSearchRadius, 6, 32),
                    integer(root, "cover_search_attempts", defaults.coverSearchAttempts, 2, 24),
                    recoveryDamageToleranceRatio(root, defaults.recoveryDamageToleranceRatio),
                    integer(root, "recovery_commit_ticks", defaults.recoveryCommitTicks, 0, 40),
                    integer(root, "gradual_heal_interval_ticks", defaults.gradualHealIntervalTicks, 2, 40),
                    (float) bounded(root, "food_recovery_min_health", defaults.foodRecoveryMinHealth, 0.0D, 20.0D),
                    (float) bounded(root, "food_recovery_max_health", defaults.foodRecoveryMaxHealth, 1.0D, 30.0D),
                    integer(root, "shield_block_duration_min_ticks", defaults.shieldBlockDurationMinTicks, 3, 30),
                    integer(root, "shield_block_duration_max_ticks", defaults.shieldBlockDurationMaxTicks, 4, 160),
                    integer(root, "shield_block_cooldown_ticks", defaults.shieldBlockCooldownTicks, 5, 120),
                    integer(root, "tactical_reposition_interval", defaults.tacticalRepositionInterval, 8, 100),
                    bounded(root, "ally_spacing_radius", defaults.allySpacingRadius, 0.0D, 10.0D),
                    integer(root, "combat_jump_interval_min", defaults.combatJumpIntervalMin, 4, 40),
                    integer(root, "combat_jump_interval_max", defaults.combatJumpIntervalMax, 5, 60),
                    bounded(root, "combat_jump_chance", defaults.combatJumpChance, 0.0D, 1.0D),
                    bool(root, "item_recovery_enabled", defaults.itemRecoveryEnabled),
                    bounded(root, "shield_use_speed_multiplier", defaults.shieldUseSpeedMultiplier, 0.1D, 1.0D),
                    bounded(root, "flee_hp", defaults.legacyFleeHealthRatio, 0, 1),
                    bounded(root, "run_away_middle_fight_chance", defaults.runAwayMiddleFightChance, 0, 1),
                    bounded(root, "mid_battle_buff_instead_of_run_chance", defaults.midBattleBuffInsteadOfRunChance, 0, 1),
                    bounded(root, "greet_chance", defaults.greetChance, 0, 1),
                    LowHealthCombatConfig.parse(UnifiedConfig.object(root, "low_health_combat"),
                            bounded(root, "retreat_health_ratio", defaults.retreatHealthRatio, 0.1D, 0.9D))
        ).normalized();
    }

    private CombatAiConfig normalized() {
        double critical = Math.min(criticalHealthRatio, retreatHealthRatio);
        double resume = Math.max(resumeHealthRatio, retreatHealthRatio + 0.05D);
        float foodMinimum = Math.min(foodRecoveryMinHealth, foodRecoveryMaxHealth);
        float foodMaximum = Math.max(foodRecoveryMinHealth, foodRecoveryMaxHealth);
        return new CombatAiConfig(
                enabled,
                retreatHealthRatio,
                critical,
                Math.min(1.0D, resume),
                coverSearchRadius,
                coverSearchAttempts,
                recoveryDamageToleranceRatio,
                recoveryCommitTicks,
                gradualHealIntervalTicks,
                foodMinimum,
                foodMaximum,
                shieldBlockDurationMinTicks,
                Math.max(shieldBlockDurationMinTicks + 1, shieldBlockDurationMaxTicks),
                shieldBlockCooldownTicks,
                tacticalRepositionInterval,
                allySpacingRadius,
                combatJumpIntervalMin,
                Math.max(combatJumpIntervalMin + 1, combatJumpIntervalMax),
                combatJumpChance,
                itemRecoveryEnabled,
                shieldUseSpeedMultiplier, legacyFleeHealthRatio, runAwayMiddleFightChance, midBattleBuffInsteadOfRunChance, greetChance, lowHealthCombat
        );
    }

    private static CombatAiConfig defaults() {
        return new CombatAiConfig(
                true,
                0.30D,
                0.12D,
                0.65D,
                18,
                10,
                1.0D / 6.0D,
                14,
                10,
                2.0F,
                12.0F,
                6,
                160,
                5,
                26,
                3.5D,
                12,
                22,
                0.60D,
                true,
                0.2D, 0.35D, 0.5D, 0.1D, 0.05D, LowHealthCombatConfig.defaults()
        );
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        return UnifiedConfig.bool(root, key, fallback);
    }

    private static double bounded(JsonObject root, String key, double fallback, double min, double max) {
        return UnifiedConfig.number(root, key, fallback, min, max);
    }

    private static int integer(JsonObject root, String key, int fallback, int min, int max) {
        return (int) UnifiedConfig.number(root, key, fallback, min, max);
    }

    private static double recoveryDamageToleranceRatio(JsonObject root, double fallback) {
        return UnifiedConfig.number(root, "recovery_damage_tolerance_ratio", fallback, 0, 1);
    }
}

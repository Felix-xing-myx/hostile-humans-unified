package club.someoneice.humangunner;

import com.google.gson.JsonObject;


/** General AI settings from the unified file; movement bases belong to each tier. */
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
        double shieldBlockChanceAfterHit,
        int tacticalRepositionInterval,
        double allySpacingRadius,
        int combatJumpIntervalMin,
        int combatJumpIntervalMax,
        double combatJumpChance,
        boolean itemRecoveryEnabled
) {
    private static final String PATH = "hostile_humans_unified.json: ai";
    private static volatile CombatAiConfig instance;

    public static CombatAiConfig get() {
        CombatAiConfig value = instance;
        if (value == null) {
            synchronized (CombatAiConfig.class) {
                value = instance;
                if (value == null) {
                    instance = value = load();
                }
            }
        }
        return value;
    }

    private static CombatAiConfig load() {
        CombatAiConfig defaults = defaults();
        try {
            JsonObject root = UnifiedConfig.get().ai();
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
                    bounded(root, "shield_block_chance_after_hit", defaults.shieldBlockChanceAfterHit, 0.0D, 1.0D),
                    integer(root, "tactical_reposition_interval", defaults.tacticalRepositionInterval, 8, 100),
                    bounded(root, "ally_spacing_radius", defaults.allySpacingRadius, 0.0D, 10.0D),
                    integer(root, "combat_jump_interval_min", defaults.combatJumpIntervalMin, 4, 40),
                    integer(root, "combat_jump_interval_max", defaults.combatJumpIntervalMax, 5, 60),
                    bounded(root, "combat_jump_chance", defaults.combatJumpChance, 0.0D, 1.0D),
                    bool(root, "item_recovery_enabled", defaults.itemRecoveryEnabled)
            ).normalized();
        } catch (Exception exception) {
            HumanGunner.LOGGER.error("Could not read {}; using defaults", PATH, exception);
            return defaults;
        }
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
                shieldBlockChanceAfterHit,
                tacticalRepositionInterval,
                allySpacingRadius,
                combatJumpIntervalMin,
                Math.max(combatJumpIntervalMin + 1, combatJumpIntervalMax),
                combatJumpChance,
                itemRecoveryEnabled
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
                0.65D,
                26,
                3.5D,
                12,
                22,
                0.60D,
                true
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
        if (root.has("recovery_damage_tolerance_ratio")) {
            return UnifiedConfig.number(root, "recovery_damage_tolerance_ratio", fallback, 0, 1);
        }
        // One-time compatibility for older configs where this was an absolute
        // 10-health threshold based on the former uniform 60-health entities.
        if (root.has("recovery_damage_tolerance")) {
            return Math.max(0.0D, Math.min(1.0D,
                    root.get("recovery_damage_tolerance").getAsDouble() / 60.0D));
        }
        return fallback;
    }
}

package dev.felix.hostilehumans.behavior.combat.defense;

import dev.felix.hostilehumans.behavior.combat.gun.GunAimPoint;
import dev.felix.hostilehumans.behavior.combat.gun.GunRangePolicy;
import dev.felix.hostilehumans.behavior.combat.gun.GunReloadPolicy;
import dev.felix.hostilehumans.behavior.combat.ranged.ArrowAttackCadence;
import dev.felix.hostilehumans.behavior.combat.ranged.BowRangePolicy;
import dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies;
import dev.felix.hostilehumans.behavior.combat.retreat.LowHealthCombatConfig;
import dev.felix.hostilehumans.behavior.combat.retreat.LowHealthCombatPolicy;
import dev.felix.hostilehumans.behavior.combat.retreat.RetreatPathSafetyPolicy;
import dev.felix.hostilehumans.behavior.combat.retreat.RetreatRecoveryPolicy;
import dev.felix.hostilehumans.behavior.combat.throwable.ThrowableBallistics;
import dev.felix.hostilehumans.navigation.ShoreProgressTracker;
import dev.felix.hostilehumans.navigation.ShoreSeekingPolicy;
import dev.felix.hostilehumans.progression.MaxHealthCompatibilityPolicy;

public final class CombatPressurePolicyTest {
    private static int checks;

    public static void main(String[] args) {
        check(!RetreatRecoveryPolicy.foodOpportunity(8.99, false, 59, false), "insufficient healing waits for three blocks before timeout");
        check(RetreatRecoveryPolicy.foodOpportunity(9, false, 1, true), "insufficient healing permits food at three blocks");
        check(RetreatRecoveryPolicy.foodOpportunity(1, false, 60, false), "three second failed separation permits emergency food");
        check(!RetreatRecoveryPolicy.foodOpportunity(64, true, 100, false), "sufficient pending recovery keeps making space through eight blocks");
        check(RetreatRecoveryPolicy.foodOpportunity(64.01, true, 1, true), "sufficient pending recovery permits food beyond eight blocks");

        checkThrowableSpeed();
        ArrowAttackCadence shots = new ArrowAttackCadence();
        shots.bowShot(100);
        check(!shots.canDrawBow(115) && shots.canDrawBow(116), "shield use cannot erase bow recovery interval");
        check(!shots.canFireBow(122) && shots.canFireBow(123), "background draw cannot overlap cooldown to accelerate shots");
        check(ArrowAttackCadence.crossbowWaitTicks(0, 72, false) == 16
                && ArrowAttackCadence.crossbowWaitTicks(0, 103, false) == 22, "normal light crossbow loaded wait preserved");
        check(ArrowAttackCadence.crossbowWaitTicks(0, 73, true) == 31
                && ArrowAttackCadence.crossbowWaitTicks(1, 73, true) == 30, "heavy crossbow retains alternating tick pacing");
        Object crossbow = new Object();
        shots.crossbowLoaded(crossbow, 0, 103, false);
        shots.crossbowLoaded(crossbow, 10, 1, false);
        check(!shots.canFireCrossbow(crossbow, 21) && shots.canFireCrossbow(crossbow, 22),
                "guard/restart cannot resample the loaded wait or instantly shoot");
        check(shots.crossbowWaitFinished(crossbow, 21), "ready transition precedes actual shot by one tick");
        shots.crossbowShot();
        check(!shots.hasLoadedCrossbow(crossbow), "a volley spends one loaded timing state");
        shots.crossbowLoaded(crossbow, 23, 72, false);
        check(!shots.canFireCrossbow(crossbow, 23), "previous charged state cannot grant a second instant shot");

        PressureGuardFacing facing = new PressureGuardFacing();
        facing.record(40);
        check(facing.step(0, 0) == 12, "front-side pressure turns toward the source immediately");
        float yaw = 12;
        for (int tick = 1; tick <= 3; tick++) yaw = facing.step(tick, yaw);
        check(yaw == 40, "a single source is faced fully rather than stopping at a sixty-degree offset");
        facing.record(55);
        check(facing.step(4, yaw) == 52, "nearby changes follow a moving single pressure direction");
        yaw = facing.step(5, 52);
        facing.record(-160);
        check(facing.step(6, yaw) == yaw && facing.step(11, yaw) == yaw,
                "a different side cannot immediately reverse the current shield direction");
        check(facing.step(12, yaw) == 67, "new pressure side turns smoothly once switch interval expires");
        facing.clear();
        check(facing.step(100, 65) == 65, "unknown direction retains existing facing");
        facing.record(105);
        check(facing.step(101, 65) == 77, "first real direction does not wait behind an empty hold");
        facing.clear(); facing.record(-170);
        check(facing.step(0, 170) == -178, "wraparound uses the shortest bounded turn");
        check(facing.step(1, -178) == -170, "wraparound finishes facing the source");
        facing.clear(); facing.record(Float.NaN);
        check(facing.step(0, 15) == 15, "invalid input cannot invent a source direction");
        facing.clear(); yaw = 0;
        for (int tick = 0; tick < 120; tick++) {
            facing.record(tick % 2 == 0 ? 90 : -90);
            float next = facing.step(tick, yaw);
            float delta = ((next - yaw + 540) % 360) - 180;
            check(Math.abs(delta) <= PressureGuardFacing.TURN_STEP, "alternating sides never snap shield yaw");
            yaw = next;
        }
        ShieldPressureConfig pressure = new ShieldPressureConfig(true, 10, .22, 20, 120, 70);
        check(Math.abs(pressure.damageMultiplier(true) - .8) < 1e-9, "active pressure guard reduces final health damage twenty percent");
        check(pressure.damageMultiplier(false) == 1, "requests and other guards do not grant mitigation");
        check(new ShieldPressureConfig(false, 10, .22, 20, 120, 70).damageMultiplier(true) == 1, "disabled pressure grants no reduction");
        check(new ShieldPressureConfig(true, 10, .22, 20, 120, 70, 0).damageMultiplier(true) == 1, "zero disables only the bonus");
        check(Math.abs(new ShieldPressureConfig(true, 10, .22, 20, 120, 70, .35).damageMultiplier(true) - .65) < 1e-9, "configured bonus controls one independent multiplier");
        PressureDefenseCycle cycle = new PressureDefenseCycle();
        cycle.guarded(0);
        check(!cycle.update(119, true, pressure), "guard waits six seconds");
        check(cycle.update(120, true, pressure) && cycle.attempting(), "max duration starts cooldown");
        cycle.damage(121, 100, 100, pressure);
        check(cycle.attempting(), "fresh damage cannot break cooldown");
        cycle.update(189, true, pressure);
        check(cycle.attempting(), "cooldown lasts seventy ticks");
        cycle.update(190, true, pressure);
        check(!cycle.attempting(), "cooldown ends at exact boundary");
        cycle.guarded(200);
        check(cycle.update(201, false, pressure) && cycle.attempting(), "quiet exit also starts cooldown");
        cycle.clear();
        check(!cycle.update(500, true, pressure) && !cycle.attempting(), "waiting drink has no guard deadline");
        DamagePressureTracker burst = new DamagePressureTracker();
        burst.record(0, 6, 100, pressure);
        check(!burst.active(0, pressure), "one small hit cannot exceed the DPS threshold");
        burst.record(1, 6, 100, pressure);
        check(burst.active(1, pressure), "rapid burst triggers on its damage event without waiting");
        burst.blocked(20, pressure);
        check(burst.active(39, pressure) && !burst.active(40, pressure), "block extends exact quiet timer");
        burst.blocked(41, pressure);
        check(!burst.active(41, pressure), "blocks alone cannot create pressure");
        DamagePressureTracker single = new DamagePressureTracker();
        single.record(0, 12, 100, pressure);
        check(single.active(0, pressure), "high immediate DPS needs neither a second hit nor settlement");
        DamagePressureTracker boundary = new DamagePressureTracker();
        boundary.record(0, 11, 100, pressure);
        check(boundary.active(0, pressure), "equal DPS threshold is inclusive");
        check(Math.abs(single.damageRatioPerSecond(10, pressure) - .24 / Math.E) < 1e-10,
                "uninterrupted rate decays exponentially after half a second");
        DamagePressureTracker slow = new DamagePressureTracker();
        for (int i = 0; i < 100; i++) slow.record(i * 20, 2, 100, pressure);
        check(!slow.active(1980, pressure), "occasional weak hits never accumulate an unbounded debt");
        DamagePressureTracker frequent = new DamagePressureTracker();
        for (int i = 0; i < 10; i++) frequent.record(i, 2, 100, pressure);
        check(frequent.active(9, pressure), "sustained high incoming DPS triggers without hit-count gating");
        DamagePressureTracker merged = new DamagePressureTracker(), pellets = new DamagePressureTracker();
        merged.record(0, 12, 100, pressure);
        for (int i = 0; i < 12; i++) pellets.record(0, 1, 100, pressure);
        check(Math.abs(merged.damageRatioPerSecond(0, pressure) - pellets.damageRatioPerSecond(0, pressure)) < 1e-12,
                "same-tick pellets and combined damage have the same rate");
        DamagePressureTracker invalid = new DamagePressureTracker();
        invalid.record(0, Float.NaN, 100, pressure); invalid.record(0, 10, Float.POSITIVE_INFINITY, pressure);
        invalid.record(0, 0, 100, pressure); invalid.record(0, -1, 100, pressure);
        check(invalid.damageRatioPerSecond(0, pressure) == 0, "invalid health/damage never poisons rate");
        single.damageRatioPerSecond(-1, pressure);
        check(single.damageRatioPerSecond(-1, pressure) == 0, "clock rewind clears old rate");
        DamagePressureTracker deferred = new DamagePressureTracker();
        deferred.executingEvent(200, pressure); deferred.blocked(219, pressure);
        check(deferred.active(238, pressure), "executing guard after a drink accepts blocked events");
        ShieldPressureConfig disabled = new ShieldPressureConfig(false, 10, .22, 20, 120, 70);
        check(!frequent.active(9, disabled) && !frequent.active(9, pressure), "disable clears pressure history");
        check(frequent.damageRatioPerSecond(9, pressure) == 0, "disable clears rate as well as request");

        check(RetreatPathSafetyPolicy.isSafe(10, 0, 0, 0,
                        new double[][] {{9, 0}, {9, 3}, {12, 3}, {16, 0}}, true),
                "safe whole retreat permits a short first step toward a wall-side threat");
        check(RetreatPathSafetyPolicy.isSafe(10, 0, 0, 0,
                        new double[][] {{9, 0}, {9, 4}, {11, 4}, {11, -4}, {16, -4}}, true),
                "bounded U-shaped retreat is not rejected by first-node dot product");
        check(!RetreatPathSafetyPolicy.isSafe(10, 0, 0, 0,
                        new double[][] {{6, 0}, {18, 0}}, true)
                        && !RetreatPathSafetyPolicy.isSafe(2, 0, 0, 0,
                        new double[][] {{1.9, 0}, {6, 0}}, true),
                "detours cannot dip beyond the approach allowance or closer inside melee clearance");
        check(!RetreatPathSafetyPolicy.isSafe(10, 0, 0, 0,
                        new double[][] {{-15, 0}}, true),
                "segments cannot cross near the enemy even when endpoints are distant");
        check(!RetreatPathSafetyPolicy.isSafe(10, 0, 0, 0,
                        new double[][] {{9, 0}, {9, 6}, {8, 4}, {16, 4}}, true),
                "initial approach must finish within eight travelled blocks");
        check(!RetreatPathSafetyPolicy.isSafe(10, 0, 0, 0,
                        new double[][] {{10, 30}, {18, 0}}, true),
                "extra route distance is bounded relative to the direct escape");
        check(RetreatPathSafetyPolicy.isSafe(10, 0, 0, 0,
                        new double[][] {{9, 0}, {10, 2}}, false)
                        && !RetreatPathSafetyPolicy.isSafe(10, 0, 0, 0,
                        new double[][] {{9, 0}, {10, 2}}, true),
                "continuing a safe remaining route does not require a fresh three-block gain");
        check(!RetreatPathSafetyPolicy.isSafe(10, 0, 0, 0, new double[0][2], true)
                        && !RetreatPathSafetyPolicy.isSafe(10, 0, 0, 0,
                        new double[RetreatPathSafetyPolicy.MAX_NODES + 1][2], true)
                        && !RetreatPathSafetyPolicy.isSafe(10, 0, 0, 0,
                        new double[][] {{Double.NaN, 0}}, true),
                "whole-route inspection is finite and rejects missing or oversized paths");
        check(MaxHealthCompatibilityPolicy.initialOwnership(60.0D, 75, 60.0D)
                        && MaxHealthCompatibilityPolicy.initialOwnership(75.0D, 75, 60.0D)
                        && !MaxHealthCompatibilityPolicy.initialOwnership(500.0D, 75, 60.0D)
                        && !MaxHealthCompatibilityPolicy.initialOwnership(500.0D, 0, 60.0D)
                        && MaxHealthCompatibilityPolicy.retainsOwnership(true, 50, 90, 50)
                        && !MaxHealthCompatibilityPolicy.retainsOwnership(false, 40, 40, 60),
                "growth preserves explicit external base ownership even when values coincide");
        ShoreProgressTracker shoreProgress = new ShoreProgressTracker();
        shoreProgress.reset(0);
        for (int tick = 0; tick <= 60; tick++) shoreProgress.observe(tick, 0, 8.0D + Math.sin(tick) * 0.05D);
        check(shoreProgress.isStalled(60), "bobbing/circling without approaching the waypoint expires shore route");
        shoreProgress.reset(100);
        shoreProgress.observe(100, 0, 10.0D);
        shoreProgress.observe(140, 0, 9.5D);
        check(!shoreProgress.isStalled(199) && shoreProgress.isStalled(200), "meaningful approach resets the stall deadline");
        shoreProgress.observe(200, 1, 12.0D);
        check(!shoreProgress.isStalled(259), "advancing to another waypoint counts on detour routes");
        shoreProgress.reset(300);
        check(shoreProgress.isStalled(360), "missing fallback destination still permits bounded retry");

        check(ShoreSeekingPolicy.shouldAttemptShore(true, true, true, false, false, false, false)
                        && !ShoreSeekingPolicy.shouldAttemptShore(false, true, true, false, false, false, false)
                        && !ShoreSeekingPolicy.shouldAttemptShore(true, false, true, false, false, false, false)
                        && !ShoreSeekingPolicy.shouldAttemptShore(true, true, false, false, false, false, false)
                        && !ShoreSeekingPolicy.shouldAttemptShore(true, true, true, true, false, false, false)
                        && !ShoreSeekingPolicy.shouldAttemptShore(true, true, true, false, true, false, false)
                        && !ShoreSeekingPolicy.shouldAttemptShore(true, true, true, false, false, true, false)
                        && !ShoreSeekingPolicy.shouldAttemptShore(true, true, true, false, false, false, true),
                "shore assistance runs only while idle, not during combat, retreat or water-loot pursuit");
        check(ShoreSeekingPolicy.shouldAttemptShore(true, true, true, false, false, false, false)
                        && !ShoreSeekingPolicy.shouldSearchForShorePath(9, 10)
                        && ShoreSeekingPolicy.shouldSearchForShorePath(10, 10),
                "path-search staggering limits work without interrupting the combat movement owner");
        check(ShoreSeekingPolicy.MAX_SHORE_BLOCK_PROBES_PER_SEARCH == 64
                        && ShoreSeekingPolicy.MAX_SHORE_PATH_PROBES_PER_SEARCH == 2
                        && ShoreSeekingPolicy.MAX_SHORE_PATH_DISTANCE <= 40,
                "shore searches have strict per-search world-scan and A-star budgets");
        Object server = new Object();
        check(ShoreSeekingPolicy.reserveShorePathSearch(server, 11)
                        && !ShoreSeekingPolicy.reserveShorePathSearch(server, 11)
                        && ShoreSeekingPolicy.reserveShorePathSearch(server, 12),
                "shore searches have a global per-server-tick budget");
        check(ShoreSeekingPolicy.shouldRiseTowardSurface(true, 0.2D)
                        && ShoreSeekingPolicy.shouldRiseTowardSurface(false, 1.2D)
                        && ShoreSeekingPolicy.shouldRiseTowardSurface(false, 0.65D)
                        && !ShoreSeekingPolicy.shouldRiseTowardSurface(false, 0.50D)
                        && !ShoreSeekingPolicy.shouldRiseTowardSurface(false, 0.2D)
                        && !ShoreSeekingPolicy.shouldRiseTowardSurface(false, -0.2D)
                        && ShoreSeekingPolicy.surfaceAscentLimit(0.50D, 0.08D) == 0.0D
                        && ShoreSeekingPolicy.surfaceAscentLimit(0.65D, 0.08D) < 0.08D
                        && ShoreSeekingPolicy.isNearRealSurfaceForShorePop(0.5D)
                        && !ShoreSeekingPolicy.isNearRealSurfaceForShorePop(1.5D)
                        && !ShoreSeekingPolicy.isNearRealSurfaceForShorePop(-0.5D)
                        && ShoreSeekingPolicy.oneBlockShoreLandingLimit(63.2D) == 65.0D
                        && ShoreSeekingPolicy.oneBlockShoreLandingLimit(64.0D) == 65.0D,
                "surface ascent and shore pop use the real waterline relative to the feet");
        check(ShoreSeekingPolicy.shouldContinueSeekingShore(true, false, false, false, false)
                        && !ShoreSeekingPolicy.shouldContinueSeekingShore(false, false, false, false, false)
                        && !ShoreSeekingPolicy.shouldContinueSeekingShore(true, true, false, false, false)
                        && !ShoreSeekingPolicy.shouldContinueSeekingShore(true, false, true, false, false)
                        && !ShoreSeekingPolicy.shouldContinueSeekingShore(true, false, false, true, false)
                        && !ShoreSeekingPolicy.shouldContinueSeekingShore(true, false, false, false, true),
                "shore assistance yields as soon as combat, retreat or water-loot pursuit begins");
        check(ShoreSeekingPolicy.isSurfaceLootReachable(65.0D, 64.0D)
                        && ShoreSeekingPolicy.isSurfaceLootReachable(65.0D, 63.5D)
                        && !ShoreSeekingPolicy.isSurfaceLootReachable(65.0D, 63.0D),
                "surface-bound Humans only pursue shallow water loot they can physically reach");
        check(ShoreSeekingPolicy.COMBAT_GOAL_PRIORITY
                        < ShoreSeekingPolicy.WATER_LOOT_GOAL_PRIORITY
                        && ShoreSeekingPolicy.WATER_LOOT_GOAL_PRIORITY
                        < ShoreSeekingPolicy.GOAL_PRIORITY
                        && ShoreSeekingPolicy.GOAL_PRIORITY > -8
                        && ShoreSeekingPolicy.GOAL_PRIORITY < -6
                        && ShoreSeekingPolicy.GOAL_PRIORITY < 8,
                "combat outranks water loot, which outranks idle shore seeking and ordinary orders");
        check(ShoreSeekingPolicy.isShoreTransitionActive(true, false)
                        && ShoreSeekingPolicy.isShoreTransitionActive(false, true)
                        && !ShoreSeekingPolicy.isShoreTransitionActive(false, false),
                "ranged attacks retain their active state while shore seeking suppresses competing combat movement");
        check(!ShoreSeekingPolicy.shouldMoveTowardCombatTarget(true)
                        && ShoreSeekingPolicy.shouldMoveTowardCombatTarget(false)
                        && ShoreSeekingPolicy.shouldSteerTowardFallback(true, true, true)
                        && !ShoreSeekingPolicy.shouldSteerTowardFallback(true, false, true)
                        && !ShoreSeekingPolicy.shouldSteerTowardFallback(false, true, true)
                        && !ShoreSeekingPolicy.shouldSteerTowardFallback(true, true, false),
                "combat pursuit yields to shore movement and unreachable routes keep steering toward a dry fallback");
        check(ShoreSeekingPolicy.waterPathMalus(true, true, false, false)
                        > ShoreSeekingPolicy.waterPathMalus(true, false, false, false)
                        && ShoreSeekingPolicy.waterPathMalus(true, false, false, false) > 0.0F
                        && ShoreSeekingPolicy.waterPathMalus(true, false, true, false) == 0.0F
                        && ShoreSeekingPolicy.waterPathMalus(false, false, true, false) == 0.0F
                        && ShoreSeekingPolicy.waterPathMalus(false, false, false, true) == 0.0F
                        && ShoreSeekingPolicy.waterPathMalus(true, false, false, true) == 0.0F
                        && ShoreSeekingPolicy.waterPathMalus(true, true, true, false) == 0.0F
                        && ShoreSeekingPolicy.waterPathMalus(true, true, false, true) == 0.0F
                        && ShoreSeekingPolicy.waterPathMalus(false, false, false, false) < 0.0F,
                "combat and worthwhile loot can enter water, while idle and active-shore paths prefer dry land");
        check(ShoreSeekingPolicy.shouldPreferSaferShorePath(12, 18, true)
                        && !ShoreSeekingPolicy.shouldPreferSaferShorePath(12, 19, true)
                        && !ShoreSeekingPolicy.shouldPreferSaferShorePath(12, 13, false),
                "retreats choose a safer bank only when the shore detour remains bounded");
        check(ShoreSeekingPolicy.shouldClearFleeingAfterCombatStop(true)
                        && !ShoreSeekingPolicy.shouldClearFleeingAfterCombatStop(false)
                        && !ShoreSeekingPolicy.shouldReturnToOwnerAfterCombatStop(true, true)
                        && ShoreSeekingPolicy.shouldReturnToOwnerAfterCombatStop(true, false),
                "a shore handoff releases the stopped combat owner's flee flag without starting owner return");
        check(ProjectileShieldPolicy.shouldGuardRangedUser(0.5D)
                        && ProjectileShieldPolicy.shouldGuardRangedUser(10.0D)
                        && !ProjectileShieldPolicy.shouldGuardRangedUser(10.01D)
                        && !ProjectileShieldPolicy.shouldGuardRangedUser(Double.NaN),
                "ranged defense anticipates impact with enough time to establish a shield");
        check(ProjectileShieldPolicy.guardWindowTicks(true, 2.5D) == 6
                        && ProjectileShieldPolicy.guardWindowTicks(true, 10.0D) == 12
                        && ProjectileShieldPolicy.guardWindowTicks(false, 12.0D) == 14
                        && ProjectileShieldPolicy.guardWindowTicks(false, 20.0D) == 0,
                "all valid projectile holds include the five-tick shield establishment budget");
        check(!CombatPressurePolicy.shouldCounterAfterGuard(5, true, false), "a block cannot bypass startup");
        check(CombatPressurePolicy.shouldCounterAfterGuard(6, true, false), "observed block opens a counter");
        check(!CombatPressurePolicy.shouldCounterAfterGuard(19, false, false), "nearby target alone does not release guard");
        check(CombatPressurePolicy.shouldCounterAfterGuard(20, false, false), "one-second wait breaks a standoff");
        check(!CombatPressurePolicy.shouldCounterAfterGuard(10, true, true), "imminent projectile preserves guard");
        check(CombatPressurePolicy.predictsIncomingMelee(8, false, true, true, 0),
                "stationary melee intent is predicted before the swing/hit tick");
        check(CombatPressurePolicy.isRecentHit(106, 100)
                        && !CombatPressurePolicy.isRecentHit(107, 100),
                "a distant hit opens an immediate shield reaction, not a delayed chase-time block");
        check(CombatPressurePolicy.isRecentGunfire(106, 140)
                        && !CombatPressurePolicy.isRecentGunfire(107, 140),
                "gunfire pressure expires as a shield trigger after the short reaction window");
        check(!CombatPressurePolicy.predictsIncomingMelee(9.0D, true, true, true, -0.08D),
                "a fleeing opponent's swing animation is not an incoming attack");
        check(!CombatPressurePolicy.predictsIncomingMelee(9.0D, true, true, false, 0.0D),
                "a swing facing away does not trigger a shield block");
        check(CombatPressurePolicy.predictsIncomingMelee(9.0D, true, true, true, 0.0D)
                        && CombatPressurePolicy.predictsIncomingMelee(8.0D, false, true, true, 0.06D),
                "close committed swings and advancing melee threats still trigger defense");
        check(CombatPressurePolicy.shouldReleaseForPursuit(6, 4, true, 25.0D, 0.0D)
                        && CombatPressurePolicy.shouldReleaseForPursuit(6, 4, true, 9.0D, -0.08D),
                "an escaping melee target releases the shield after the effective block window");
        check(!CombatPressurePolicy.shouldReleaseForPursuit(5, 4, true, 25.0D, 0.0D)
                        && !CombatPressurePolicy.shouldReleaseForPursuit(6, 3, true, 25.0D, 0.0D)
                        && !CombatPressurePolicy.shouldReleaseForPursuit(6, 4, false, 25.0D, 0.0D),
                "pursuit release does not cancel a fresh block or sustained ranged defense");

        LowHealthCombatConfig lowConfig = LowHealthCombatConfig.defaults();
        check(LowHealthCombatPolicy.critical(14, 100, lowConfig)
                        && !LowHealthCombatPolicy.critical(15, 100, lowConfig)
                        && !LowHealthCombatPolicy.critical(25, 1000, lowConfig)
                        && !LowHealthCombatPolicy.critical(100, 1000, lowConfig)
                        && !LowHealthCombatPolicy.critical(24, 100, lowConfig),
                "hard escape needs both strictly low ratio and low absolute health");
        check(LowHealthCombatPolicy.weakTarget(20, 100)
                        && !LowHealthCombatPolicy.weakTarget(30, 1000)
                        && !LowHealthCombatPolicy.weakTarget(20, 50),
                "finishing target needs both under thirty percent and thirty health");
        checkLowHealthDecisions();
        check(CombatPressurePolicy.shouldUseRetreatCounterfire(5.0D * 5.0D, 12, 2.0D, 64.0D, 12)
                        && CombatPressurePolicy.shouldUseRetreatCounterfire(5.0D * 5.0D, 12, 2.0D, 36.0D, 12)
                        && !CombatPressurePolicy.shouldUseRetreatCounterfire(1.5D * 1.5D, 12, 2.0D, 64.0D, 12)
                        && !CombatPressurePolicy.shouldUseRetreatCounterfire(5.0D * 5.0D, 11, 2.0D, 64.0D, 12),
                "gunners and trident users counterattack during retreat once outside melee range, including the former close-range dead zone");
        check(!RetreatRecoveryPolicy.safeToReturn(20.0D * 20.0D, 80, 80)
                        && !RetreatRecoveryPolicy.safeToReturn(24.0D * 24.0D, 39, 80)
                        && !RetreatRecoveryPolicy.safeToReturn(24.0D * 24.0D, 80, 39)
                        && RetreatRecoveryPolicy.safeToReturn(24.0D * 24.0D, 40, 40),
                "retreat does not yield to attack or anchor return until distance, quiet and commitment gates pass");
        check(RetreatRecoveryPolicy.canResumeAfterHealing(1.0D, 0.75D, 40)
                        && RetreatRecoveryPolicy.canResumeAfterHealing(0.75D, 0.75D, 40)
                        && !RetreatRecoveryPolicy.canResumeAfterHealing(0.74D, 0.75D, 100)
                        && !RetreatRecoveryPolicy.canResumeAfterHealing(1.0D, 0.75D, 39),
                "real healing ends prolonged retreat without a distance gate, retaining minimum retreat commitment");



        check(RetreatRecoveryPolicy.isUsefulRetreatDestination(9.0D * 9.0D, 12.0D * 12.0D)
                        && !RetreatRecoveryPolicy.isUsefulRetreatDestination(9.0D * 9.0D, 9.0D * 9.0D),
                "native escape paths are judged by increased enemy distance, not travel distance");
        check(GunRangePolicy.forType("sniper").minimum() > GunRangePolicy.forType("rifle").maximum()
                        && GunRangePolicy.forType("shotgun").maximum()
                        < GunRangePolicy.forType("rifle").maximum()
                        && GunRangePolicy.forType("snipers_rifle").maximum()
                        == GunRangePolicy.forType("sniper").maximum(),
                "snipers stand farther back and shotguns engage closer than general rifles");
        check(GunRangePolicy.forType("sniper").minimum() == 48.0D
                        && GunRangePolicy.forType("rifle").minimum() == 24.0D
                        && GunRangePolicy.forType("rifle").maximum() == 36.0D
                        && GunRangePolicy.forType("mg").minimum() == 20.0D
                        && GunRangePolicy.forType("mg").maximum() == 32.0D
                        && GunRangePolicy.forType("smg").minimum() == 12.0D
                        && GunRangePolicy.forType("smg").maximum() == 20.0D
                        && GunRangePolicy.forType("pistol").minimum() == 8.0D
                        && GunRangePolicy.forType("pistol").maximum() == 12.0D
                        && GunRangePolicy.forType("shotgun").minimum() == 3.0D
                        && GunRangePolicy.forType("shotgun").maximum() == 8.0D
                        && GunRangePolicy.forType("shotgun").retreatResume() == 5.5D,
                "each gun family uses the confirmed spacing band");
        check(Math.abs(GunAimPoint.height(0.0D, 2.0D, 1.8D) - 1.4D) < 0.00001D
                        && GunAimPoint.height(0.0D, 2.0D, 1.0D) == 1.0D
                        && Math.abs(GunAimPoint.height(10.0D, 12.0D, 11.8D) - 11.4D) < 0.00001D,
                "gun aim lowers high eyes to 70 percent of the hitbox without raising low eyes");
        check(GunRangePolicy.forType("shotgun").fireRange() == 24.0D
                        && GunRangePolicy.forType("rifle").fireRange() == 80.0D
                        && GunRangePolicy.forType("sniper").fireRange() == 96.0D,
                "gunfire can begin well outside the desired holding distance");
        check(GunReloadPolicy.recoveryDelayTicks(false, 30, 2.25D, 2.6D) >= 117
                        && GunReloadPolicy.recoveryDelayTicks(true, 5, 1.2D, 0.8D) >= 156,
                "stalled reload recovery waits at least the gun's real feed and cooldown time");
        check(GunReloadPolicy.hardLimitTicks(120) >= 600,
                "a normally active reload is never interrupted by the short fallback timer");
        check(GunReloadPolicy.boltRecoveryDelayTicks(2.0D, 0.5D) >= 70,
                "bolt-action guns retain their own cycling delay before emergency recovery");
        check(GunReloadPolicy.shouldIdleTopOff(7, 30, false, false, false, false)
                        && GunReloadPolicy.shouldIdleTopOff(0, 30, false, false, false, false),
                "idle gunners top off both tactical and empty magazines");
        check(!GunReloadPolicy.shouldIdleTopOff(30, 30, false, false, false, false)
                        && !GunReloadPolicy.shouldIdleTopOff(12, 30, true, false, false, false)
                        && !GunReloadPolicy.shouldIdleTopOff(12, 30, false, true, false, false)
                        && !GunReloadPolicy.shouldIdleTopOff(12, 30, false, false, true, false)
                        && !GunReloadPolicy.shouldIdleTopOff(12, 30, false, false, false, true),
                "full magazines and active combat, escape, item use or reload do not restart idle reload");

        int counters = 0;
        int cooldown = 0;
        int held = 0;
        boolean defending = false;
        for (int tick = 0; tick < 40; tick++) {
            if (!defending && tick >= cooldown) {
                defending = true;
                held = 0;
            }
            if (defending && CombatPressurePolicy.shouldCounterAfterGuard(++held, true, false)) {
                counters++;
                defending = false;
                cooldown = tick + 5;
            }
        }
        check(counters >= 4,
                "continuous pressure alternates block and counter instead of extending one block forever");

        check(CombatPressurePolicy.shouldPrioritizeCloserThreat(20.0D * 20.0D,
                        3.0D * 3.0D, 2.0F, 60.0F)
                        && CombatPressurePolicy.shouldPrioritizeCloserThreat(5.0D * 5.0D,
                        2.0D * 2.0D, 0.0F, 60.0F),
                "an unhurt nearby enemy can displace a low-pressure distant target");
        check(!CombatPressurePolicy.shouldPrioritizeCloserThreat(20.0D * 20.0D,
                        3.0D * 3.0D, 10.0F, 60.0F)
                        && !CombatPressurePolicy.shouldPrioritizeCloserThreat(20.0D * 20.0D,
                        7.0D * 7.0D, 0.0F, 60.0F)
                        && !CombatPressurePolicy.shouldPrioritizeCloserThreat(8.0D * 8.0D,
                        7.0D * 7.0D, 0.0F, 60.0F)
                        && !CombatPressurePolicy.shouldPrioritizeCloserThreat(3.0D * 3.0D,
                        2.0D * 2.0D, 0.0F, 60.0F),
                "serious recent damage, out-of-range and marginal proximity do not cause target thrashing");
        check(CombatPressurePolicy.shouldUseCrowdCounterfire(true, true, true, 1.0D),
                "surrounded gunner may fire at point-blank range");
        check(!CombatPressurePolicy.shouldUseCrowdCounterfire(true, true, false, 1.0D),
                "counterfire never ignores line of sight");
        check(!CombatPressurePolicy.shouldUseCrowdCounterfire(true, false, true, 1.0D),
                "point-blank override is limited to an active crowd-pressure window");

        check(RecoverySupplies.acceleratedUseDuration(32, true) == 23,
                "32-tick food use is rounded safely to 70 percent");
        check(RecoverySupplies.acceleratedUseDuration(16, true) == 12,
                "short food use is also scaled to 70 percent");
        check(RecoverySupplies.acceleratedUseDuration(32, false) == 32,
                "drink and non-food use duration stays unchanged");
        check(RecoverySupplies.shouldContinueCombatEating(0.40D, 2.0D / 3.0D, true),
                "actual low health keeps the combat meal chain active");
        check(!RecoverySupplies.shouldContinueCombatEating(0.70D, 2.0D / 3.0D, true),
                "actual recovered health ends the combat meal chain");
        check(!RecoverySupplies.shouldContinueCombatEating(0.40D, 2.0D / 3.0D, false),
                "an empty food supply ends the combat meal chain safely");
        check(RecoverySupplies.shouldContinueCombatEating(0.99D, 1.0D, true),
                "the meal chain continues when projected food healing still leaves missing health");
        check(!RecoverySupplies.shouldContinueCombatEating(1.0D, 1.0D, true),
                "the meal chain stops as soon as projected nutrition and saturation can fill health");

        check(BowRangePolicy.shouldPathRetreat(13.99D * 13.99D),
                "bow users path away before entering the twelve-block danger zone");
        check(!BowRangePolicy.shouldPathRetreat(14.0D * 14.0D),
                "fourteen blocks starts the buffered orbit band");
        check(BowRangePolicy.orbitForwardInput(15.0D * 15.0D) < 0.0F,
                "near orbiting retains a radial retreat component");
        check(BowRangePolicy.orbitForwardInput(18.0D * 18.0D) == 0.0F,
                "safe orbiting is purely lateral");
        check(BowRangePolicy.orbitForwardInput(25.0D * 25.0D, 16, 24) > 0.0F,
                "very distant bow users may close slowly");
        check(!BowRangePolicy.usefulRetreatDestination(13.0D * 13.0D, 15.0D * 15.0D),
                "retreat paths that remain inside the orbit buffer are rejected");
        check(BowRangePolicy.usefulRetreatDestination(13.0D * 13.0D, 16.0D * 16.0D),
                "retreat paths must establish the full sixteen-block buffer");
        check(!BowRangePolicy.shouldPursue(20.0D * 20.0D, 36.0D * 36.0D),
                "an archer with a shot from twenty blocks must not sprint toward the target");
        check(BowRangePolicy.shouldPursue(40.0D * 40.0D, 36.0D * 36.0D),
                "an archer can approach from beyond bow range");

        System.out.println("CombatPressurePolicyTest: " + checks + " behavior checks passed");
    }

    private static LowHealthCombatPolicy.Input lowInput(int tick, float health, boolean weak,
            boolean recovery, boolean instant, float healed, float damaged, float strike) {
        return new LowHealthCombatPolicy.Input(tick, health, 100,
                new java.util.UUID(0, 1), weak, recovery, instant, healed, damaged, strike);
    }

    private static void checkLowHealthDecisions() {
        LowHealthCombatConfig config = LowHealthCombatConfig.defaults();
        LowHealthCombatPolicy.State state = new LowHealthCombatPolicy.State();
        java.util.concurrent.atomic.AtomicInteger rolls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.DoubleSupplier chooseFinish = () -> { rolls.incrementAndGet(); return 0.4D; };
        check(state.allow(lowInput(0, 20, true, false, false, 0, 0, 0), config, chooseFinish),
                "approved lone weak opponent permits finishing even without recovery items");
        for (int tick = 1; tick < 80; tick++)
            check(state.allow(lowInput(tick, 20, true, false, false, 0, 0, 0), config, chooseFinish),
                    "finishing permission stays stable throughout an encounter");
        check(rolls.get() == 1, "probability is not rerolled by repeated AI callers");
        check(!state.allow(lowInput(80, 14, true, true, false, 10, 1, 0), config, chooseFinish),
                "hard dual health floor overrides finishing and good healing");
        state.reset();
        check(!state.allow(lowInput(0, 20, true, true, true, 0, 0, 0), config, chooseFinish),
                "an available or in-progress instant potion keeps healing priority");
        state.reset();
        check(state.allow(lowInput(0, 28, false, false, false, 0, 0, 0), config, () -> 0.9D),
                "no supplies still allows a bounded damage trial");
        check(!state.allow(lowInput(40, 26, false, false, false, 0, 2, 2), config, () -> 0.9D),
                "zero regeneration cannot sustain incoming damage");
        check(!state.allow(lowInput(41, 27, false, true, false, 4, 2, 2), config, () -> 0.9D),
                "failed trials have a cooldown rather than bouncing back next tick");
        state.reset();
        check(state.allow(lowInput(0, 28, false, true, false, 0, 0, 0), config, () -> 0.9D)
                        && state.allow(lowInput(40, 30, false, true, false, 4, 2, 2), config, () -> 0.9D)
                        && state.mode() == LowHealthCombatPolicy.Mode.SUSTAIN,
                "actual healing exceeding tested damage grants sustained retaliation");
        check(!state.allow(lowInput(50, 21, false, true, false, 4, 8, 4), config, () -> 0.9D),
                "losing exchange revokes permission before hard emergency health");
        state.reset();
        check(state.allow(lowInput(0, 28, false, true, false, 0, 0, 0), config, () -> 0.9D)
                        && !state.allow(lowInput(40, 32, false, true, false, 4, 0, 0), config, () -> 0.9D),
                "quiet healing without an enemy hit cannot prove damage is sustainable");
        check(!LowHealthCombatPolicy.healingCovers(2, 2, 1.1D)
                        && LowHealthCombatPolicy.healingCovers(3, 2, 1.1D),
                "sustained fighting needs the configured healing safety margin");
    }

    private static void checkThrowableSpeed() {
        for (double distance : new double[] {.5, 2, 6, 12, 24, 32})
            for (double speed : new double[] {1.6, 2.0, 2.4})
                for (double gravity : new double[] {(double) .025F, (double) .03F, (double) .04F, (double) .05F}) {
                    var shot = ThrowableBallistics.solve(distance, -1.5, 0, gravity, speed, 60);
                    check(shot != null, "ordinary grenade range remains reachable");
                    double actual = Math.sqrt(shot.x() * shot.x() + shot.y() * shot.y() + shot.z() * shot.z());
                    check(Math.abs(actual - speed) < 1.0E-8, "short landing/bounce aim cannot reduce launch speed");
                    double x = 0, y = 0, vx = shot.x(), vy = shot.y();
                    int whole = (int) shot.flightTicks();
                    for (int tick = 0; tick < whole; tick++) {
                        x += vx; y += vy;
                        vx *= ThrowableBallistics.DRAG;
                        vy = vy * ThrowableBallistics.DRAG - gravity;
                    }
                    double fraction = shot.flightTicks() - whole;
                    x += vx * fraction; y += vy * fraction;
                    check(Math.abs(x - distance) < 1.0E-8 && Math.abs(y + 1.5) < 1.0E-8,
                            "fixed speed preserves native discrete trajectory endpoint");
                }
        check(ThrowableBallistics.solve(32, -1.5, 0, .03, 1.6, 60) != null,
                "long throw remains reachable at default speed");
        check(ThrowableBallistics.solve(1000, 0, 0, .03, 1.6, 60) == null,
                "unreachable target does not bypass configured speed");
        check(ThrowableBallistics.solve(10, 0, 0, .03, 0, 60) == null,
                "invalid speed rejected");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

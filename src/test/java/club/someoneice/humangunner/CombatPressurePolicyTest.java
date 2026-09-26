package club.someoneice.humangunner;

public final class CombatPressurePolicyTest {
    private static int checks;

    public static void main(String[] args) {
        check(ShoreSeekingPolicy.shouldAttemptShore(true, true, true, false)
                        && !ShoreSeekingPolicy.shouldAttemptShore(false, true, true, false)
                        && !ShoreSeekingPolicy.shouldAttemptShore(true, false, true, false)
                        && !ShoreSeekingPolicy.shouldAttemptShore(true, true, false, false)
                        && !ShoreSeekingPolicy.shouldAttemptShore(true, true, true, true),
                "shore seeking is active for every server-side water state, regardless of combat or orders");
        check(ShoreSeekingPolicy.shouldContinueSeekingShore(true, false)
                        && !ShoreSeekingPolicy.shouldContinueSeekingShore(false, false)
                        && !ShoreSeekingPolicy.shouldContinueSeekingShore(true, true),
                "shore movement retains priority across failed path searches until water is exited or lava is entered");
        check(ShoreSeekingPolicy.shouldPursueLowerWaterTarget(false, true, true, false, false)
                        && !ShoreSeekingPolicy.shouldPursueLowerWaterTarget(true, true, true, false, false)
                        && !ShoreSeekingPolicy.shouldPursueLowerWaterTarget(false, true, true, true, false)
                        && !ShoreSeekingPolicy.shouldPursueLowerWaterTarget(false, true, true, false, true),
                "shore seeking and survival priorities prevent combat from pulling Humans deeper after a submerged target");
        check(ShoreSeekingPolicy.GOAL_PRIORITY < -30
                        && ShoreSeekingPolicy.GOAL_PRIORITY < -10
                        && ShoreSeekingPolicy.GOAL_PRIORITY < -8,
                "shore exit pre-empts chest, hazard, and combat movement goals");
        check(ShoreSeekingPolicy.isShoreTransitionActive(true, false)
                        && ShoreSeekingPolicy.isShoreTransitionActive(false, true)
                        && !ShoreSeekingPolicy.isShoreTransitionActive(false, false),
                "ranged attacks retain their active state while shore seeking suppresses competing combat movement");
        check(ShoreSeekingPolicy.waterPathMalus(true, true)
                        > ShoreSeekingPolicy.waterPathMalus(true, false)
                        && ShoreSeekingPolicy.waterPathMalus(true, false) > 0.0F
                        && ShoreSeekingPolicy.waterPathMalus(false, false) < 0.0F,
                "water remains traversable when unavoidable while normal and active-shore paths strongly prefer land");
        check(ShoreSeekingPolicy.shouldPreferSaferShorePath(12, 18, true)
                        && !ShoreSeekingPolicy.shouldPreferSaferShorePath(12, 19, true)
                        && !ShoreSeekingPolicy.shouldPreferSaferShorePath(12, 13, false),
                "retreats choose a safer bank only when the shore detour remains bounded");
        check(!ShoreSeekingPolicy.shouldClearFleeingAfterCombatStop(true, true)
                        && ShoreSeekingPolicy.shouldClearFleeingAfterCombatStop(true, false)
                        && !ShoreSeekingPolicy.shouldReturnToOwnerAfterCombatStop(true, true)
                        && ShoreSeekingPolicy.shouldReturnToOwnerAfterCombatStop(true, false),
                "a shore handoff does not cancel retreat state or prematurely start owner-return behavior");
        check(ProjectileShieldPolicy.shouldGuardRangedUser(0.5D)
                        && ProjectileShieldPolicy.shouldGuardRangedUser(2.5D)
                        && !ProjectileShieldPolicy.shouldGuardRangedUser(2.51D)
                        && !ProjectileShieldPolicy.shouldGuardRangedUser(8.0D),
                "ranged projectile defense waits until an arrow is within the immediate impact window");
        check(ProjectileShieldPolicy.guardWindowTicks(true, 2.5D) == 3
                        && ProjectileShieldPolicy.guardWindowTicks(true, 0.5D) == 2
                        && ProjectileShieldPolicy.guardWindowTicks(false, 20.0D) == 32,
                "ranged shield interruption lasts only through the imminent shot while other defenders retain their window");
        check(!CombatPressurePolicy.shouldOpenCounterWindow(5, 0, true),
                "shield remains up through the effective block wind-up");
        check(CombatPressurePolicy.shouldOpenCounterWindow(6, 0, true),
                "melee/ranged counter opens immediately after the minimum block");
        check(CombatPressurePolicy.shouldOpenCounterWindow(6, 10, false),
                "a quiet defense releases even when the target is currently out of range");
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
        check(!RetreatRecoveryPolicy.canStart(8.0D * 8.0D, true, 20)
                        && !RetreatRecoveryPolicy.canStart(11.0D * 11.0D, false, 20),
                "cover alone and a short open-field gap cannot start recovery");
        check(RetreatRecoveryPolicy.shouldForceRetreat(0.25D)
                        && !RetreatRecoveryPolicy.shouldForceRetreat(0.2501D),
                "one-quarter health always triggers escape without a chance reroll");
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
        check(RetreatRecoveryPolicy.canStart(9.0D * 9.0D, true, 11)
                        && RetreatRecoveryPolicy.canStart(12.0D * 12.0D, false, 11),
                "retreat recovery starts at nine blocks behind cover or twelve in the open");
        check(!RetreatRecoveryPolicy.canStart(15.0D * 15.0D, false, 10),
                "even a distant human waits for a quiet moment before using supplies");
        check(RetreatRecoveryPolicy.shouldBreakOffUse(8.0D * 8.0D, true, 10)
                        && !RetreatRecoveryPolicy.shouldBreakOffUse(8.0D * 8.0D, false, 10)
                        && !RetreatRecoveryPolicy.shouldBreakOffUse(8.0D * 8.0D, true, 3),
                "exposed close pressure interrupts an early drink but not a protected or nearly finished use");
        check(RetreatRecoveryPolicy.isUsefulRetreatDestination(9.0D * 9.0D, 12.0D * 12.0D)
                        && !RetreatRecoveryPolicy.isUsefulRetreatDestination(9.0D * 9.0D, 9.0D * 9.0D),
                "native escape paths are judged by increased enemy distance, not travel distance");
        check(GunRangePolicy.forType("sniper").minimum() > GunRangePolicy.forType("rifle").maximum()
                        && GunRangePolicy.forType("shotgun").maximum()
                        < GunRangePolicy.forType("rifle").maximum()
                        && GunRangePolicy.forType("snipers_rifle").maximum()
                        == GunRangePolicy.forType("sniper").maximum(),
                "snipers stand farther back and shotguns engage closer than general rifles");
        check(GunRangePolicy.forType("sniper").minimum() == 32.0D
                        && GunRangePolicy.forType("rifle").minimum() == 16.0D
                        && GunRangePolicy.forType("rifle").maximum() == 24.0D
                        && GunRangePolicy.forType("mg").minimum() == 16.0D
                        && GunRangePolicy.forType("mg").maximum() == 24.0D
                        && GunRangePolicy.forType("smg").minimum() == 8.0D
                        && GunRangePolicy.forType("smg").maximum() == 16.0D
                        && GunRangePolicy.forType("pistol").minimum() == 6.0D
                        && GunRangePolicy.forType("pistol").maximum() == 12.0D
                        && GunRangePolicy.forType("shotgun").minimum() == 3.0D
                        && GunRangePolicy.forType("shotgun").maximum() == 8.0D,
                "each gun family uses the requested spacing band");
        check(GunRangePolicy.forType("shotgun").fireRange() >= 40.0D
                        && GunRangePolicy.forType("rifle").fireRange() >= 40.0D
                        && GunRangePolicy.forType("sniper").fireRange() >= 128.0D,
                "gunfire can begin well outside the desired holding distance");
        check(GunReloadPolicy.recoveryDelayTicks(false, 30, 2.25D, 2.6D) >= 117
                        && GunReloadPolicy.recoveryDelayTicks(true, 5, 1.2D, 0.8D) >= 156,
                "stalled reload recovery waits at least the gun's real feed and cooldown time");
        check(GunReloadPolicy.hardLimitTicks(120) >= 600,
                "a normally active reload is never interrupted by the short fallback timer");
        check(GunReloadPolicy.boltRecoveryDelayTicks(2.0D, 0.5D) >= 70,
                "bolt-action guns retain their own cycling delay before emergency recovery");
        check(GunReloadPolicy.shouldIdleTopOff(12, 30, false, false, false, false)
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
            if (defending && CombatPressurePolicy.shouldOpenCounterWindow(++held, 0, true)) {
                counters++;
                defending = false;
                cooldown = tick + 5;
            }
        }
        check(counters >= 4,
                "continuous pressure alternates block and counter instead of extending one block forever");

        check(!CombatPressurePolicy.shouldPrioritizeGunnerRetreat(true, 2),
                "two nearby threats do not force the crowd override");
        check(CombatPressurePolicy.shouldPrioritizeGunnerRetreat(true, 3),
                "three nearby threats force an armed gunner to retreat");
        check(!CombatPressurePolicy.shouldPrioritizeGunnerRetreat(false, 6),
                "ordinary melee humans keep their normal pressure behavior");
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
        check(BowRangePolicy.orbitForwardInput(23.0D * 23.0D) > 0.0F,
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

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

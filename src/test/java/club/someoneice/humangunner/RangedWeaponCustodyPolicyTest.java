package club.someoneice.humangunner;

public final class RangedWeaponCustodyPolicyTest {
    public static void main(String[] args) {
        for (int delay : new int[]{20, 40}) {
            check(RangedFiringPosition.retryDelay(true, false, delay) == 1,
                    "partial firing-lane search continues next tick instead of taking failure cooldown");
            check(RangedFiringPosition.retryDelay(false, false, delay) == delay,
                    "exhausted firing-lane search retains failure cooldown");
            check(RangedFiringPosition.retryDelay(false, true, delay) == 10,
                    "completed firing-lane search retains normal cooldown");
        }
        check(RangedWeaponCustody.shouldReleaseReturnShield(true, false, 3, 160),
                "stale shield releases when the bow or crossbow should return");
        check(!RangedWeaponCustody.shouldReleaseReturnShield(false, false, 200, 160),
                "close melee defense keeps the shield");
        check(!RangedWeaponCustody.shouldReleaseReturnShield(true, true, 30, 160),
                "an active defense window may finish normally");
        check(RangedWeaponCustody.shouldReleaseReturnShield(true, true, 181, 160),
                "an abnormally long defense cannot trap the ranged weapon");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

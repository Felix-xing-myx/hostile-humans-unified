package club.someoneice.humangunner;

public final class RangedWeaponCustodyPolicyTest {
    public static void main(String[] args) {
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

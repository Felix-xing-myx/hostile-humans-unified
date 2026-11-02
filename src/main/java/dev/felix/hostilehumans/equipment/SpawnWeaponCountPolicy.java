package dev.felix.hostilehumans.equipment;

/** Conditional rolls make larger spawn-only allocations progressively rarer. */
public final class SpawnWeaponCountPolicy {
    private SpawnWeaponCountPolicy() { }
    public static int roll(int maximum, double secondChance, double thirdChance, double secondRoll, double thirdRoll) {
        if (maximum < 2 || secondRoll >= secondChance) return 1;
        return maximum >= 3 && thirdRoll < thirdChance ? 3 : 2;
    }
}

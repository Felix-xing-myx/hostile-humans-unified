package club.someoneice.humangunner;

/** Conditional rolls make larger spawn-only allocations progressively rarer. */
final class SpawnWeaponCountPolicy {
    private SpawnWeaponCountPolicy() { }
    static int roll(int maximum, double secondChance, double thirdChance, double secondRoll, double thirdRoll) {
        if (maximum < 2 || secondRoll >= secondChance) return 1;
        return maximum >= 3 && thirdRoll < thirdChance ? 3 : 2;
    }
}

package club.someoneice.humangunner;

/** Optional faction/rank spawn preference; it never raises the existing total weapon cap. */
record SpawnMeleeReservePolicy(double secondChance, double thirdChance, boolean preferOneHanded) {
    int target(int maximum, double secondRoll, double thirdRoll) {
        return SpawnWeaponCountPolicy.roll(maximum, secondChance, thirdChance, secondRoll, thirdRoll);
    }

    int additional(int target, int ownedMelee, int totalWeapons, int maximum) {
        return Math.max(0, Math.min(target - ownedMelee, maximum - totalWeapons));
    }
}

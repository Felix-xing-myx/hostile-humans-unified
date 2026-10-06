package club.someoneice.humangunner;

/** Spawn budget and gun-pack type compatibility without a running game. */
public final class HumanAmmoPolicyTest {
    public static void main(String[] args) {
        String[] types = {"pistol", "smg", "rifle", "shotgun", "sniper", "mg", "rpg"};
        int[] costs = {1, 1, 2, 2, 3, 4, 5};
        for (int i = 0; i < types.length; i++) {
            check(HumanAmmoPolicy.magazineCost(types[i]) == costs[i], types[i]);
        }
        check(HumanAmmoPolicy.magazineCost("SNIPER") == 3, "case-insensitive gun packs");
        check(HumanAmmoPolicy.magazineCost(null) > 0, "missing gun type cannot give free reloads");
        check(HumanAmmoPolicy.magazineCost("custom") > 0, "unknown gun type cannot give free reloads");
        int[][] budgets = {{32, 128, 6, 12}, {64, 128, 12, 24},
                {128, 192, 32, 48}, {192, 192, 48, 72}};
        for (int tier = 0; tier < budgets.length; tier++) {
            check(HumanAmmoPolicy.arrowMinimum(tier) == budgets[tier][0], "arrow minimum tier " + tier);
            check(HumanAmmoPolicy.arrowMaximum(tier) == budgets[tier][1], "arrow maximum tier " + tier);
            check(HumanAmmoPolicy.magazineMinimum(tier) == budgets[tier][2], "magazine minimum tier " + tier);
            check(HumanAmmoPolicy.magazineMaximum(tier) == budgets[tier][3], "magazine maximum tier " + tier);
        }
        System.out.println("PASS: human ammo spawn budgets and reload costs");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

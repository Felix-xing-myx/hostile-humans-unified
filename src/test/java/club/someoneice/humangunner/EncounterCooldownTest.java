package club.someoneice.humangunner;

import com.google.gson.JsonObject;

/** Executable regression test, requiring only Java 17 (no downloaded test framework). */
public final class EncounterCooldownTest {
    public static void main(String[] args) {
        for (int badge = -1; badge <= 4; badge++) {
            for (int rank = 0; rank <= 3; rank++) {
                BadgePolicy.Relation expected = badge < rank ? BadgePolicy.Relation.HOSTILE
                        : badge == rank ? BadgePolicy.Relation.NEUTRAL : BadgePolicy.Relation.FRIENDLY;
                check(BadgePolicy.relation(badge, rank) == expected, "badge relation matrix");
            }
        }
        System.out.println("PASS: identity badge hostile/neutral/friendly matrix");
        int[] costs = {8, 24, 72, 216}, limits = {12, 10, 6, 3}, waves = {5, 4, 3, 2};
        UnifiedConfig defaultConfig = new UnifiedConfig(new JsonObject());
        for (int tier = 0; tier < 4; tier++) {
            check(RecruitmentPolicy.cost(tier) == costs[tier], "recruitment cost");
            check(RecruitmentPolicy.limit(tier, defaultConfig) == limits[tier], "follower cap");
            check(RecruitmentPolicy.waveSize(tier) == waves[tier], "wave size");
        }
        check(!RecruitmentPolicy.betrayed(25.01F, 100.0F), "above betrayal threshold");
        check(RecruitmentPolicy.betrayed(25.0F, 100.0F), "exact betrayal threshold");
        System.out.println("PASS: recruitment costs/caps, wave sizes and 25% betrayal boundary");
        for (int tier = 0; tier < 4; tier++) {
            for (int other = 0; other < 4; other++) {
                if (tier != other) {
                    check(!SignalPolicy.cooldownKey(false, tier).equals(SignalPolicy.cooldownKey(false, other)),
                            "support cooldown keys must be tier-specific");
                    check(!SignalPolicy.cooldownKey(true, tier).equals(SignalPolicy.cooldownKey(true, other)),
                            "hostile cooldown keys must be tier-specific");
                }
            }
            check(!SignalPolicy.cooldownKey(false, tier).equals(SignalPolicy.cooldownKey(true, tier)),
                    "support and hostile cooldowns must be separate");
        }
        check(SignalPolicy.cooldownTicks(false) == 9600L, "support cooldown");
        check(SignalPolicy.cooldownTicks(true) == 40L, "hostile cooldown");
        check(SignalPolicy.SUPPORT_LIFETIME_TICKS == 4800L, "support lifetime");
        check(SignalPolicy.minimumDistance(false) == 2 && SignalPolicy.maximumDistance(false) == 6,
                "support spawns beside owner");
        check(SignalPolicy.minimumDistance(true) == 48 && SignalPolicy.maximumDistance(true) == 60,
                "hostile signal distance");
        check(SoldierOrder.values().length == 4, "exactly four soldier orders");
        System.out.println("PASS: per-tier signal cooldowns, support lifetime/distances and soldier orders");
        check(!SpawnSafety.farEnough(96, 0), "96 block boundary excluded");
        check(!SpawnSafety.farEnough(-96, 0), "negative boundary excluded");
        check(SpawnSafety.farEnough(96.001, 0), "strictly beyond 96 allowed");
        check(!SpawnSafety.farEnough(60, 60), "diagonal within radius excluded");
        check(SpawnSafety.farEnough(70, 70), "diagonal beyond radius allowed");
        check(SpawnSafety.unlitBuffer((x,y,z) -> false), "fully dark buffer allowed");
        int[][] cells = {{0,0,0},{16,0,0},{-16,0,0},{0,16,0},{0,-16,0},{0,0,16},{0,0,-16},{9,9,9}};
        for (int[] cell : cells) {
            check(!SpawnSafety.unlitBuffer((x,y,z) -> x == cell[0] && y == cell[1] && z == cell[2]),
                    "lit cell within buffer must deny spawn");
        }
        check(SpawnSafety.unlitBuffer((x,y,z) -> x == 16 && y == 1 && z == 0), "outside spherical boundary");
        check(SpawnSafety.unlitBuffer((x,y,z) -> x == 17 && y == 0 && z == 0), "17 block dark gap");
        System.out.println("PASS: strict 96-block horizontal distance and 16-block spherical light buffer");
        EncounterCooldown clock = new EncounterCooldown();
        Object first = new Object(), second = new Object();
        check(!clock.cooling(100), "failed/no attempts must not start cooldown");
        for (int i = 0; i < 5; i++) check(clock.join(100, first, 5), "same squad member " + i);
        check(!clock.join(100, first, 5), "sixth member must be denied");
        check(!clock.join(100, second, 5), "second squad in same tick must be denied");
        check(!clock.join(101, first, 5), "token reuse on later tick must be denied");
        check(!clock.join(2499, second, 5), "cooldown must last full 2400 ticks");
        check(clock.join(2500, second, 5), "new squad at exact cooldown expiry");
        EncounterCooldown battle = new EncounterCooldown();
        for (int i = 0; i < 36; i++) check(battle.join(200, first, Integer.MAX_VALUE), "battle member");
        check(!battle.join(200, second, 5), "battle must suppress normal squad");
        check(!clock.join(2500, first, Integer.MAX_VALUE), "normal squad must suppress battle");
        EncounterCooldown roamer = new EncounterCooldown();
        check(roamer.join(0, first, 1), "first roamer");
        check(!roamer.join(0, first, 1), "roamer entry remains solitary");
        check(!roamer.join(2400, null, 1), "missing token rejected");
        check(roamer.join(2400, second, 1), "rejection must not consume next interval");
        System.out.println("PASS: squad size, cross-entry cooldown, battle, roamer, expiry, failed admission");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

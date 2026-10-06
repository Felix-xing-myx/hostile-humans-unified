package club.someoneice.humangunner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Follow hysteresis, independent speed overrides and offline order eligibility. */
public final class SoldierFollowPolicyTest {
    private static int checks;
    public static void main(String[] args) {
        check(!SoldierFollowNavigation.shouldChase(false, 16), "start boundary");
        check(SoldierFollowNavigation.shouldChase(false, 16.01), "start beyond four blocks");
        check(SoldierFollowNavigation.shouldChase(true, 9), "keep chasing inside start radius");
        check(!SoldierFollowNavigation.shouldChase(false, 9), "idle inside start radius");
        check(!SoldierFollowNavigation.shouldChase(true, 6.25), "stop at two and a half blocks");
        check(SoldierFollowNavigation.shouldChase(true, 6.26), "continue above stop radius");
        UnifiedConfig defaults = new UnifiedConfig(new JsonObject());
        for (String rank : new String[]{"roamer", "tier1", "tier2", "tier3"}) {
            check(defaults.followSpeed(rank, false) == 1.35, "normal default " + rank);
            check(defaults.followSpeed(rank, true) == 1.6, "catch-up default " + rank);
        }
        UnifiedConfig custom = new UnifiedConfig(JsonParser.parseString("""
                {"tiers":{"tier1":{"movement":{"follow_speed_multiplier":1.7,
                "follow_catch_up_multiplier":1.2}},"tier2":{"movement":{
                "follow_speed_multiplier":99,"follow_catch_up_multiplier":-1}}}}
                """).getAsJsonObject());
        check(custom.followSpeed("tier1", false) == 1.7, "rank override");
        check(custom.followSpeed("tier1", true) == 1.7, "catch-up cannot slow normal follow");
        check(custom.followSpeed("tier3", false) == 1.35, "rank isolation");
        check(custom.followSpeed("tier2", false) == 3, "upper bound");
        check(custom.followSpeed("tier2", true) == 3, "bounded catch-up floor");
        for (SoldierOrder order : SoldierOrder.values()) {
            check(OwnerOfflinePolicy.shouldGuard(true, true, order) == (order == SoldierOrder.FOLLOW),
                    "only followers convert " + order);
            check(!OwnerOfflinePolicy.shouldGuard(false, true, order), "wild orders untouched " + order);
            check(!OwnerOfflinePolicy.shouldGuard(true, false, order), "dead orders untouched " + order);
        }
        System.out.println("SoldierFollowPolicyTest: " + checks + " checks passed");
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}

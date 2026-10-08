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
        check(!SoldierFollowNavigation.rescueDue(199, 100), "short stalls cannot teleport");
        check(SoldierFollowNavigation.rescueDue(200, 100), "long stalls may rescue nearby soldiers");
        check(!SoldierFollowNavigation.rescueDue(300, 6.25), "no rescue inside follow spacing");
        check(SoldierFollowNavigation.retryDelay(1) == 10, "failed follow searches back off");
        check(SoldierFollowNavigation.retryDelay(100) == 40, "retry delay stays bounded");
        Object server = new Object();
        java.util.UUID first = new java.util.UUID(0, 1), second = new java.util.UUID(0, 2);
        check(ShoreSeekingPolicy.reserveShorePathSearch(server, 1, first), "first swimmer gets this tick");
        ShoreSeekingPolicy.cancelShorePathSearch(server, first);
        check(!ShoreSeekingPolicy.reserveShorePathSearch(server, 1, second), "one shore search per tick");
        check(!ShoreSeekingPolicy.reserveShorePathSearch(server, 2, first), "first swimmer cannot starve queued peer");
        check(ShoreSeekingPolicy.reserveShorePathSearch(server, 2, second), "queued swimmer gets next tick");
        var batches = new dev.felix.hostilehumans.core.FairWorkBudget<Integer>(32, 4, 40);
        boolean[] served = new boolean[11];
        for (int tick = 1; tick <= 3; tick++) {
            for (int id = 0; id < served.length; id++) {
                int grant = batches.claimFull(tick, id, 3);
                check(grant == 0 || grant == 3, "a three-search operation never consumes a partial grant");
                served[id] |= grant == 3;
            }
        }
        for (boolean value : served) check(value, "all callers receive full capacity despite a two-slot remainder");
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

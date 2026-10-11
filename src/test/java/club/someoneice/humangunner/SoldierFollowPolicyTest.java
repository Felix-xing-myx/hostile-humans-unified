package club.someoneice.humangunner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Follow hysteresis, independent speed overrides and offline order eligibility. */
public final class SoldierFollowPolicyTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        check(WaterCombatPolicy.closingProgress(8, 7.5), "half block water approach counts");
        check(!WaterCombatPolicy.closingProgress(8, 8), "circling without approach is not progress");
        check(!WaterCombatPolicy.closingProgress(8, 7.8), "water jitter does not reset watchdog");
        check(!WaterCombatPolicy.closingProgress(8, Double.NaN), "invalid progress is rejected");
        check(WaterCombatPolicy.directApproach(true, 64, 1.5, true), "bounded visible surface pursuit");
        check(!WaterCombatPolicy.directApproach(true, 64.01, 0, true), "distant combat keeps path planning");
        check(!WaterCombatPolicy.directApproach(true, 4, 2, true), "deep target does not force diving");
        check(!WaterCombatPolicy.directApproach(true, 4, 0, false), "wall occlusion prevents eager direct pursuit");
        check(WaterCombatPolicy.chasingMovingTarget(1, .6, .1), "equally fast fleeing target still permits progress");
        check(!WaterCombatPolicy.chasingMovingTarget(0, .6, .1), "static target requires closing distance");
        check(!WaterCombatPolicy.chasingMovingTarget(1, .6, 1), "lateral circling is not forward pursuit");
        check(!WaterCombatPolicy.chasingMovingTarget(1, -.6, 0), "moving away is not pursuit");
        check(!SoldierFollowNavigation.shouldChase(false, 16), "start boundary");
        check(SoldierFollowNavigation.shouldChase(false, 16.01), "start beyond four blocks");
        check(SoldierFollowNavigation.shouldChase(true, 9), "keep chasing inside start radius");
        check(!SoldierFollowNavigation.shouldChase(false, 9), "idle inside start radius");
        check(!SoldierFollowNavigation.shouldChase(true, 6.25), "stop at two and a half blocks");
        check(SoldierFollowNavigation.shouldChase(true, 6.26), "continue above stop radius");
        check(!SoldierFollowNavigation.rescueDue(199, 100), "short stalls cannot teleport");
        check(SoldierFollowNavigation.rescueDue(200, 100), "long stalls may rescue nearby soldiers");
        check(!SoldierFollowNavigation.rescueDue(300, 6.25), "no rescue inside follow spacing");
        check(!SoldierFollowNavigation.meaningfulApproach(10, 10, 2.5), "node changes without physical approach do not defer rescue");
        check(!SoldierFollowNavigation.meaningfulApproach(9.75, 10, 2.5), "small obstacle jitter does not defer rescue");
        check(SoldierFollowNavigation.meaningfulApproach(9.49, 10, 2.5), "real approach refreshes rescue timer");
        check(SoldierFollowNavigation.meaningfulApproach(2.5, 2.5, 2.5), "arrival ends rescue pressure");
        check(SoldierFollowNavigation.rescueDue(200, 400), "twenty-block obstruction is eligible without the long-distance teleport gate");
        check(SoldierFollowNavigation.retryDelay(1) == 10, "failed follow searches back off");
        check(SoldierFollowNavigation.retryDelay(100) == 40, "retry delay stays bounded");
        for (SoldierOrder order : SoldierOrder.values()) {
            check(SoldierStuckRecovery.eligibleOrder(order) == (order != SoldierOrder.HOLD_POSITION),
                    "rescue applies only to requested mobile orders: " + order);
        }
        check(!SoldierStuckRecovery.due(199, 100, true), "combat rescue waits ten seconds");
        check(SoldierStuckRecovery.due(200, 100, true), "target presence cannot disable combat rescue deadline");
        check(!SoldierStuckRecovery.due(200, 6.25, true), "follow spacing excluded");
        check(SoldierStuckRecovery.due(200, 6.26, true), "follow displacement beyond spacing");
        check(!SoldierStuckRecovery.due(200, 2.25, false), "standing at guard or patrol destination excluded");
        check(SoldierStuckRecovery.due(200, 2.26, false), "guard and patrol use tighter arrival spacing");
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
        for (boolean water : new boolean[]{false, true}) {
            for (boolean shore : new boolean[]{false, true}) {
                for (boolean waterShore : new boolean[]{false, true}) {
                    check(!ShoreSeekingPolicy.shouldUseWaterNavigator(true, water, true, shore, waterShore),
                            "surface following keeps walking navigation across land, water and stale shore state");
                }
            }
        }
        check(ShoreSeekingPolicy.shouldUseWaterNavigator(true, true, false, false, false),
                "ordinary water navigation remains available outside owner-follow routing");
        check(!ShoreSeekingPolicy.shouldUseWaterNavigator(true, true, false, true, false),
                "ground shore route retains its navigator");
        check(!ShoreSeekingPolicy.shouldUseWaterNavigator(false, true, false, false, false),
                "disabled AI stays on ground navigation");
        check(updateSwimmingUsesSurfacePolicy(), "compiled navigator selection uses the surface-follow policy");
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
    private static boolean updateSwimmingUsesSurfacePolicy() throws Exception {
        var node = new org.objectweb.asm.tree.ClassNode();
        try (var stream = SoldierFollowPolicyTest.class.getClassLoader().getResourceAsStream(
                "com/craftix/hostile_humans/entity/entities/Human.class")) {
            if (stream == null) throw new AssertionError("Human class missing");
            new org.objectweb.asm.ClassReader(stream).accept(node, 0);
        }
        var method = node.methods.stream().filter(m -> m.name.equals("updateSwimming"))
                .findFirst().orElseThrow();
        for (var instruction : method.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                    && call.owner.equals("club/someoneice/humangunner/ShoreSeekingPolicy")
                    && call.name.equals("shouldUseWaterNavigator")) return true;
        }
        return false;
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}

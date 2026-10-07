package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.ai.InteractionSafetyPolicy;

/** Standalone pure policy checks; no world, navigator or optional mod dependencies. */
public final class InteractionSafetyPolicyTest {
    private static int checks;
    public static void main(String[] args) {
        check(InteractionSafetyPolicy.firstNearbyNode(1000) == 999, "never rescan route history");
        check(InteractionSafetyPolicy.firstNearbyNode(0) == 0, "first node cannot be negative");
        check(InteractionSafetyPolicy.nearbyNodeLimit(1000, 2000) == 1002, "only two forward nodes");
        check(InteractionSafetyPolicy.nearbyNodeLimit(1, 2) == 2, "node bounds honor short paths");
        check(InteractionSafetyPolicy.nearPassage(2.25), "local passage boundary included");
        check(!InteractionSafetyPolicy.nearPassage(2.26), "remote trapdoor excluded");
        check(InteractionSafetyPolicy.escapeTnt(true, 80.99), "inside explosion clearance escapes");
        check(!InteractionSafetyPolicy.escapeTnt(true, 81), "safe clearance ends escape");
        check(!InteractionSafetyPolicy.escapeTnt(false, 0), "removed or exploded TNT ends escape");
        check(!InteractionSafetyPolicy.retryTnt(9, 10, 0), "search retries are throttled");
        check(InteractionSafetyPolicy.retryTnt(10, 10, 3), "last bounded retry allowed");
        check(!InteractionSafetyPolicy.retryTnt(100, 10, 4), "search limit never exceeded");
        check(!InteractionSafetyPolicy.closePassage(19, false, false), "opening grace period");
        check(InteractionSafetyPolicy.closePassage(20, false, false), "clear unused passage closes");
        check(!InteractionSafetyPolicy.closePassage(100, true, false), "never close on an entity");
        check(!InteractionSafetyPolicy.closePassage(100, false, true), "queued NPC passage stays open");
        check(!InteractionSafetyPolicy.abandonClose(99), "lease lasts through bounded wait");
        check(InteractionSafetyPolicy.abandonClose(100), "occupied leases eventually relinquish ownership");
        System.out.println("InteractionSafetyPolicyTest: " + checks + " checks passed");
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

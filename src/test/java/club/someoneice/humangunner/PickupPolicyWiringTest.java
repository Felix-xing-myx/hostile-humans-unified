package club.someoneice.humangunner;

import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Policy and compiled-entrypoint audit. Does not simulate a live world's pathfinding. */
public final class PickupPolicyWiringTest {
    private static int assertions;
    private static final String POLICY = "club/someoneice/humangunner/SoldierPickupPolicy";
    private static final String LOOT = "club/someoneice/humangunner/HumanLootManager";
    private static final String GOAL = "club/someoneice/humangunner/ValuableItemPickupGoal";
    private static final String HUMAN = "com/craftix/hostile_humans/entity/entities/Human";

    public static void main(String[] args) throws Exception {
        for (SoldierOrder order : SoldierOrder.values()) {
            for (SoldierCombatMode mode : SoldierCombatMode.values()) {
                check(!SoldierPickupPolicy.enabled(true, false), order + "/" + mode + " disabled means forbidden");
                check(SoldierPickupPolicy.enabled(true, true), order + "/" + mode + " owner can enable pickup");
            }
        }
        check(SoldierPickupPolicy.enabled(false, false) && SoldierPickupPolicy.enabled(false, true),
                "wild humans retain autonomous loot permission");
        Object pickup = new Object();
        Object combat = new Object();
        Object guard = new Object();
        check(ValuableItemPickupGoal.ownsPath(pickup, pickup), "collector can release its own route");
        check(!ValuableItemPickupGoal.ownsPath(combat, pickup), "collector cannot cancel a replacement combat route");
        check(!ValuableItemPickupGoal.ownsPath(guard, pickup), "collector cannot cancel a replacement guard route");
        check(!ValuableItemPickupGoal.ownsPath(null, null), "no path does not grant navigation ownership");
        check(!ValuableItemPickupGoal.ownsPath(pickup, null), "never assigned a route means no ownership");

        for (String method : List.of("tryCollectNearby", "collect", "isWorthCollecting"))
            check(calls(LOOT, method, POLICY, "canCollectNow"), "manager entrypoint gated: " + method);
        check(callIndex(LOOT, "collect", POLICY, "canCollectNow")
                        < callIndex(LOOT, "collect", "net/minecraft/world/entity/item/ItemEntity", "getItem"),
                "permission precedes ground-item/equipment/inventory mutation");
        check(calls("com/craftix/hostile_humans/entity/type/human/PickUpLoot", "tick", POLICY, "canCollectNow"),
                "legacy proximity scanner cannot bypass the switch");
        check(calls(HUMAN, "canPickUpLoot", POLICY, "canCollectNow"), "vanilla pickup permission gated");
        check(calls(HUMAN, "pickUpItem", LOOT, "tryCollectNearby")
                && !calls(HUMAN, "pickUpItem", "net/minecraft/world/entity/Mob", "pickUpItem"),
                "vanilla collection routed through the shared transaction instead of super pickup");
        for (String method : List.of("canUse", "canContinueToUse"))
            check(calls(GOAL, method, POLICY, "canCollectNow"), "active search respects policy: " + method);
        check(calls(GOAL, "tick", GOAL, "canContinueToUse"), "mid-tick permission changes rechecked before movement");
        check(calls(GOAL, "stop", GOAL, "ownsPath"), "interruption releases only the collector's path");
        check(!calls(POLICY, "set", "net/minecraft/world/entity/ai/navigation/PathNavigation", "stop"),
                "switching pickup off never blindly cancels a combat route");
        for (String method : List.of("canCollectNow", "isEnabled")) {
            check(!callsOwner(POLICY, method, "club/someoneice/humangunner/SoldierCombatMode"),
                    "no combat mode is an exemption from pickup permission");
        }
        for (String method : List.of("getTarget", "isUsingItem", "isSleeping", "isInLava", "isOnFire", "isNoAi"))
            check(callsName(POLICY, "canCollectNow", method), "idle/safety gate includes " + method);
        check(calls(POLICY, "canCollectNow", "club/someoneice/humangunner/SoldierOrder", "isReturningFromRetreat"),
                "returning from retreat cannot race a new pickup route");
        check(calls(POLICY, "canCollectNow", "club/someoneice/humangunner/RecoverySupplies", "hasHandCustody"),
                "pickup cannot modify equipment during deferred recovery hand restoration");
        check(calls(POLICY, "canCollectNow", "club/someoneice/humangunner/HumanGunner", "isManualInventoryOpen"),
                "manual inventory editing excludes ground pickup");
        check(!calls(POLICY, "canCollectNow", "club/someoneice/humangunner/OwnerOfflinePolicy", "isOwnerOffline"),
                "owner logout does not disable configured pickup");
        check(calls(POLICY, "canCollectNow", "club/someoneice/humangunner/SoldierOrder", "allowsLootPosition"),
                "area boundary is enforced in all pickup entrypoints");
        check(calls("club/someoneice/humangunner/SoldierOrder", "tick", POLICY, "canCollectNow"),
                "follow/patrol/guard lifecycle yields to an authorized active pickup");
        check(calls("club/someoneice/humangunner/RecruitmentContractItem", "interactLivingEntity",
                "club/someoneice/humangunner/RecruitmentConfigNetwork", "serverPrice")
                && calls("club/someoneice/humangunner/RecruitmentContractItem", "interactLivingEntity",
                "club/someoneice/humangunner/RecruitmentConfigNetwork", "pay"),
                "actual hiring uses the tested authoritative terms and item transaction");
        check(calls("club/someoneice/humangunner/RecruitmentContractItem", "appendHoverText",
                "club/someoneice/humangunner/RecruitmentConfigClient", "price")
                && !calls("club/someoneice/humangunner/RecruitmentContractItem", "appendHoverText",
                "club/someoneice/humangunner/UnifiedConfig", "get"),
                "contract hover reads the synchronized view, not client-local payment settings");
        System.out.println("PickupPolicyWiringTest: " + assertions + " permission, navigation-ownership and entrypoint checks passed");
    }

    private static MethodNode method(String owner, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = PickupPolicyWiringTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            if (stream == null) throw new AssertionError("Missing compiled class " + owner);
            new ClassReader(stream).accept(node, 0);
        }
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static int callIndex(String type, String method, String owner, String name) throws Exception {
        int index = 0;
        for (var instruction : method(type, method).instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return index;
            index++;
        }
        return Integer.MAX_VALUE;
    }
    private static boolean calls(String type, String method, String owner, String name) throws Exception {
        return callIndex(type, method, owner, name) != Integer.MAX_VALUE;
    }
    private static boolean callsName(String type, String method, String name) throws Exception {
        for (var instruction : method(type, method).instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals(name)) return true;
        return false;
    }
    private static boolean callsOwner(String type, String method, String owner) throws Exception {
        for (var instruction : method(type, method).instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner)) return true;
        return false;
    }
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}

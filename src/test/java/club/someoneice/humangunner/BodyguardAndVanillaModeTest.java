package club.someoneice.humangunner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Protection boundaries, permit isolation, saved-mode defaults and health transitions. */
public final class BodyguardAndVanillaModeTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        UnifiedConfig defaults = new UnifiedConfig(new JsonObject());
        check(!defaults.vanillaMode(), "old configs default vanilla mode off");
        check(new UnifiedConfig(JsonParser.parseString("{\"vanilla_mode\":true}").getAsJsonObject()).vanillaMode(), "global mode can enable");
        check(!new UnifiedConfig(JsonParser.parseString("{\"vanilla_mode\":\"true\"}").getAsJsonObject()).vanillaMode(), "wrong type cannot enable");
        JsonObject global = TierConfigProjection.global(UnifiedConfig.defaults());
        check(global.has("vanilla_mode") && !global.get("vanilla_mode").getAsBoolean(), "mode belongs in global projection");
        for (String rank : TierConfigProjection.RANKS)
            check(!TierConfigProjection.tier(UnifiedConfig.defaults(), rank).has("vanilla_mode"), "no duplicate rank override " + rank);
        check(VanillaModePolicy.attackBase(true, 100) == 1, "high rank base cannot add vanilla-mode damage");
        check(VanillaModePolicy.attackBase(false, 100) == 100, "disabled mode preserves configured base");
        check(VanillaModePolicy.transitionedHealth(35, 70, 20) == 10, "entering preserves injury percentage");
        check(VanillaModePolicy.transitionedHealth(15, 30, 80) == 40, "external health modifiers included in ratio");
        check(VanillaModePolicy.transitionedHealth(10, 20, 70) == 35, "leaving restores injury percentage");
        check(VanillaModePolicy.transitionedHealth(0, 70, 20) == 0, "mode changes never revive");
        check(VanillaModePolicy.transitionedHealth(100, 70, 20) == 20, "overfull old health clamps");
        check(VanillaModePolicy.restoredBase(true, 60, 0, 50) == 50, "fresh vanilla human changing tier restores current original before rolling");
        check(VanillaModePolicy.restoredBase(true, 70, 70, 50) == 70, "saved roll remains recognizable by tier migration");
        check(VanillaModePolicy.restoredBase(false, 123, 70, 50) == 123, "external base remains external after mode exit");
        check(SoldierOrder.BODYGUARD.ordinal() == 4 && SoldierOrder.PATROL.ordinal() == 3, "old movement ordinals preserved");
        check(SoldierCombatMode.AUTHORIZED.ordinal() == 3 && SoldierCombatMode.SELF_DEFENSE.ordinal() == 2, "old combat ordinals preserved");
        check(!SoldierCombatMode.autonomous(SoldierCombatMode.AUTHORIZED), "permission is not autonomous aggression");
        for (String cause : new String[] {"SELF", "OWNER_DEFENSE", "OWNER_ASSAULT"})
            check(SoldierCombatMode.permits(SoldierCombatMode.AUTHORIZED, cause), "permission source " + cause);
        check(!SoldierCombatMode.permits(SoldierCombatMode.AUTHORIZED, "INVALID"), "invalid permission denied");
        check(!SoldierCombatMode.permits(SoldierCombatMode.PASSIVE_PROTECTION, "OWNER_ASSAULT"), "passive cannot join an owner assault");
        check(SoldierCombatMode.permits(SoldierCombatMode.PASSIVE_PROTECTION, "SELF"), "passive may defend itself");
        check(BodyguardPolicy.attackInRange(144, 144, false, false), "twelve block chase boundary");
        check(!BodyguardPolicy.attackInRange(144.01, 1, true, true), "protector must return beyond twelve blocks");
        check(!BodyguardPolicy.attackInRange(9, 145, false, true), "melee never chases a distant shooter");
        check(BodyguardPolicy.attackInRange(9, 2304, true, true), "ranged may retaliate from protection position");
        check(!BodyguardPolicy.attackInRange(9, 2304.01, true, true), "bounded remote detection");
        check(!BodyguardPolicy.attackInRange(9, 200, true, false), "no remote unrelated target acquisition");
        check(BodyguardPolicy.priority(9, false, false) < BodyguardPolicy.priority(100, true, true), "near owner threat wins before owner is hurt");
        check(BodyguardPolicy.priority(25, false, false) < BodyguardPolicy.priority(144, true, true), "near threat interrupts maximum chase");
        check(BodyguardPolicy.priority(16, false, true) < BodyguardPolicy.priority(16, false, false), "small tie hysteresis avoids oscillation");
        check(!BodyguardPolicy.needsRangedPosition(6.25, 4), "safe floor height difference cannot permanently block firing");
        check(!BodyguardPolicy.needsRangedPosition(16, 0), "existing protection position does not need an unreachable exact intercept point");
        check(BodyguardPolicy.needsRangedPosition(16.01, 0), "ranged user returns when outside protection position");
        check(BodyguardPolicy.interceptSpacing(20) == 2.5, "normal intercept remains close");
        check(BodyguardPolicy.interceptSpacing(1) < 1, "do not walk behind a threat already beside the owner");
        String prefix = "club/someoneice/humangunner/";
        check(calls(prefix + "HumanGunner", "configureSoldierOrders", prefix + "BodyguardTargetGoal", "<init>"), "single target controller installed");
        check(calls(prefix + "HumanGunner", "configureSoldierOrders", prefix + "BodyguardMovementGoal", "<init>"), "protection MOVE goal installed");
        check(calls(prefix + "BodyguardProtection", "update", prefix + "BodyguardPolicy", "priority"), "runtime uses owner-centered priority");
        check(calls(prefix + "SoldierOrder", "allowsTarget", prefix + "BodyguardProtection", "allowsTarget"), "all attack goals share leash gate");
        check(!calls(prefix + "BodyguardTargetGoal", "tick", null, "forgetSoldierTarget"), "switching priority does not erase supplies/self-attacker or blacklist target");
        check(calls(prefix + "HumanGunner", "onLivingHurt", prefix + "UnifiedConfig", "vanillaMode"), "native melee multiplier bypass installed");
        check(calls(prefix + "TierAttributes", "apply", prefix + "VanillaModePolicy", "attackBase"), "weapon-only base installed");
        check(calls(prefix + "RandomizedHumanHealth", "changeBase", prefix + "VanillaModePolicy", "transitionedHealth"), "actual max with modifiers used at transition");
        check(calls(prefix + "HumanGunner", "onLivingDamage", prefix + "HumanRelations", "defendAfterDamage"), "passive protection waits for actual owner damage");
        check(calls(prefix + "SoldierCombatMode", "refreshAuthorizedFight", prefix + "SoldierCombatMode", "isExplicitlyAuthorizedAgainst"), "NPC damage cannot create permission for an unrelated target");
        System.out.println("BodyguardAndVanillaModeTest: " + checks + " checks passed");
    }
    private static boolean calls(String owner, String method, String target, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = BodyguardAndVanillaModeTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            if (stream == null) throw new AssertionError("Missing compiled " + owner);
            new ClassReader(stream).accept(node, 0);
        }
        for (var entry : node.methods) if (entry.name.equals(method)) for (var instruction : entry.instructions)
            if (instruction instanceof MethodInsnNode call && (target == null || target.equals(call.owner)) && name.equals(call.name)) return true;
        return false;
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

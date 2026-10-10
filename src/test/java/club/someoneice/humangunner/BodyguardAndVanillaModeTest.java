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
        check(VanillaModePolicy.clampedHealth(35, 20) == 20, "lower maximum clamps current health");
        check(VanillaModePolicy.clampedHealth(15, 80) == 15, "higher maximum grants no free healing");
        check(VanillaModePolicy.clampedHealth(10, 70) == 10, "leaving vanilla grants no free healing");
        check(VanillaModePolicy.clampedHealth(0, 20) == 0, "mode changes never revive");
        check(VanillaModePolicy.clampedHealth(100, 20) == 20, "overfull old health clamps");
        check(!MaxHealthCompatibilityPolicy.retainsOwnership(false, 40, 40, 60), "matching an external value never adopts ownership");
        check(SoldierOrder.BODYGUARD.ordinal() == 4 && SoldierOrder.PATROL.ordinal() == 3, "old movement ordinals preserved");
        check(SoldierCombatMode.AUTHORIZED.ordinal() == 3 && SoldierCombatMode.SELF_DEFENSE.ordinal() == 2, "old combat ordinals preserved");
        check(!SoldierCombatMode.autonomous(SoldierCombatMode.AUTHORIZED), "permission is not autonomous aggression");
        for (String cause : new String[] {"SELF", "OWNER_DEFENSE", "OWNER_ASSAULT"})
            check(SoldierCombatMode.permits(SoldierCombatMode.AUTHORIZED, cause), "permission source " + cause);
        check(!SoldierCombatMode.permits(SoldierCombatMode.AUTHORIZED, "INVALID"), "invalid permission denied");
        check(!SoldierCombatMode.permits(SoldierCombatMode.PASSIVE_PROTECTION, "OWNER_ASSAULT"), "passive cannot join an owner assault");
        check(SoldierCombatMode.permits(SoldierCombatMode.PASSIVE_PROTECTION, "SELF"), "passive may defend itself");
        check(BodyguardPolicy.attackInRange(144, 144, false, false, false, 144, 0), "twelve block chase boundary");
        check(!BodyguardPolicy.attackInRange(144.01, 1, true, true, true, 1, 16384), "protector must return beyond twelve blocks");
        check(!BodyguardPolicy.attackInRange(9, 145, false, true, true, 145, 0), "melee never chases a distant shooter");
        check(BodyguardPolicy.attackInRange(9, 2304, true, true, false, 2304, 2304), "ranged may retaliate from protection position");
        check(!BodyguardPolicy.attackInRange(9, 2304.01, true, true, false, 2304.01, 16384), "bounded remote detection");
        check(!BodyguardPolicy.attackInRange(9, 200, true, false, false, 200, 1296), "no remote unrelated target acquisition");
        check(BodyguardPolicy.attackInRange(9, 10000, true, false, true, 9409, 16384), "active sniper acquires distant untouched enemy");
        check(!BodyguardPolicy.attackInRange(9, 10000, true, false, false, 9409, 16384), "passive sniper needs real authorization");
        check(!BodyguardPolicy.attackInRange(9, 4000, true, false, true, 4096.01, 4096), "active gun still obeys actual firing reach");
        check(BodyguardPolicy.attackInRange(9, 1600, true, false, true, 1296, 1296), "bow range is measured from soldier rather than owner");
        check(GunRangePolicy.firingReach(GunRangePolicy.forType("sniper"), 160) == 160, "sniper follow-range extension shared with bodyguard");
        check(GunRangePolicy.firingReach(GunRangePolicy.forType("pistol"), 160) == 64, "normal gun cannot inherit sniper firing reach");
        check(BodyguardPolicy.priority(9, false, false) < BodyguardPolicy.priority(100, true, true), "near owner threat wins before owner is hurt");
        check(BodyguardPolicy.priority(25, false, false) < BodyguardPolicy.priority(144, true, true), "near threat interrupts maximum chase");
        check(BodyguardPolicy.priority(16, false, true) < BodyguardPolicy.priority(16, false, false), "small tie hysteresis avoids oscillation");
        check(BodyguardPolicy.priority(9, false, false) < BodyguardPolicy.priority(10, true, true), "self attacker cannot outweigh a nearer owner threat");
        check(BodyguardPolicy.priority(9, false, false) == BodyguardPolicy.priority(9, true, false), "retaliation supplies permission but not priority");
        check(CreeperSafetyPolicy.escape(true, false, 15, 1, 4, false, false), "advancing fuse triggers escape");
        check(!CreeperSafetyPolicy.escape(true, false, 15, -1, 4, false, false), "cooling fuse immediately ends escape despite residual swell");
        check(!CreeperSafetyPolicy.escape(true, false, 0, -1, 4, false, false), "idle creeper does not force avoidance");
        check(CreeperSafetyPolicy.escape(true, true, 0, -1, 4, false, false), "flint ignition counts before first swell tick");
        check(!CreeperSafetyPolicy.escape(false, true, 20, 1, 4, false, false), "dead creeper ends route");
        check(!CreeperSafetyPolicy.escape(true, true, 20, 1, 36, false, false), "ordinary escape ends at six blocks");
        check(CreeperSafetyPolicy.escape(true, true, 20, 1, 36, true, false), "powered creeper needs wider safety margin");
        check(!CreeperSafetyPolicy.escape(true, true, 20, 1, 81, true, false), "powered escape ends at nine blocks");
        check(!CreeperSafetyPolicy.escape(true, true, 20, 1, 4, false, true), "owner rescue overrides escape");
        check(CreeperSafetyPolicy.rescue(true, false, 1, 1, 36, false), "live fuse beside owner triggers all-mode rescue");
        check(!CreeperSafetyPolicy.rescue(true, false, 20, -1, 4, false), "rescue exception ends when fuse cools");
        check(!CreeperSafetyPolicy.rescue(true, true, 20, 1, 36.01, false), "remote explosion cannot grant rescue permission");
        check(!CreeperSafetyPolicy.rescue(false, true, 20, 1, 4, false), "other orders retain ordinary permissions");
        check(!BodyguardPolicy.needsRangedPosition(6.25, 4), "safe floor height difference cannot permanently block firing");
        check(!BodyguardPolicy.needsRangedPosition(16, 0), "existing protection position does not need an unreachable exact intercept point");
        check(BodyguardPolicy.needsRangedPosition(16.01, 0), "ranged user returns when outside protection position");
        check(BodyguardPolicy.interceptSpacing(20) == 2.5, "normal intercept remains close");
        check(BodyguardPolicy.interceptSpacing(1) < 1, "do not walk behind a threat already beside the owner");
        String prefix = "club/someoneice/humangunner/";
        String humanClass = "com/craftix/hostile_humans/entity/entities/Human";
        String meleeGoal = "com/craftix/hostile_humans/entity/ai/goal/MeleeAttackGoal";
        check(calls(humanClass, "setTarget", prefix + "BodyguardProtection", "preferredAttack"), "all direct target writers share protector selection");
        check(calls(prefix + "AdaptiveCombatGoal", "resolveThreat", prefix + "BodyguardProtection", "attack"), "adaptive retaliation cannot steal protector focus");
        check(calls(humanClass, "startUsingItem", humanClass, "humanGunner$hasPendingMeleeAttack"), "shield cannot interrupt pending melee");
        check(calls(meleeGoal, "checkAndPerformAttack", humanClass, "humanGunner$hasPendingMeleeAttack"), "ordinary melee cannot spend cooldown during pending attack");
        check(calls(meleeGoal, "humanGunner$tryImmediateCounterattack", humanClass, "humanGunner$hasPendingMeleeAttack"), "counter cannot fake another swing during pending attack");
        check(calls("com/craftix/hostile_humans/client/renderer/BetterCombatNpcAnimator", "start", null, "startIdle"), "empty cancel animation resets idle instead of short fallback attack");
        check(calls(prefix + "SoldierCombatMode", "allowsTarget", prefix + "BodyguardProtection", "protectingFromExplosion"), "all modes share narrow rescue exception");
        check(calls(prefix + "SoldierOrder", "allowsTarget", prefix + "BodyguardProtection", "protectingFromExplosion"), "rescue bypasses ignored-target memory and return flag only while fuse is live");
        check(calls("com/craftix/hostile_humans/entity/ai/goal/AvoidCreeperGoal", "canContinueToUse", null, "shouldEscape"), "escape rechecks fuse and owner rescue");
        check(calls(prefix + "HumanGunner", "configureSoldierOrders", prefix + "BodyguardTargetGoal", "<init>"), "single target controller installed");
        check(calls(prefix + "HumanGunner", "configureSoldierOrders", prefix + "BodyguardMovementGoal", "<init>"), "protection MOVE goal installed");
        check(calls(prefix + "BodyguardProtection", "update", prefix + "BodyguardPolicy", "priority"), "runtime uses owner-centered priority");
        check(calls(prefix + "SoldierOrder", "allowsTarget", prefix + "BodyguardProtection", "allowsTarget"), "all attack goals share leash gate");
        check(!calls(prefix + "BodyguardTargetGoal", "tick", null, "forgetSoldierTarget"), "switching priority does not erase supplies/self-attacker or blacklist target");
        check(calls(prefix + "HumanGunner", "onLivingHurt", prefix + "HumanDamageScaling", "outgoing")
                && calls(prefix + "HumanDamageScaling", "outgoing", prefix + "UnifiedConfig", "vanillaMode"), "native melee multiplier bypass installed");
        check(calls(prefix + "TierAttributes", "apply", prefix + "TierAttributes", "applyCombatBase"), "initial attributes install weapon-only base");
        check(calls(prefix + "HumanGrowth", "tick", prefix + "TierAttributes", "applyCombatBase"), "server config reload refreshes weapon-only base");
        check(calls(prefix + "TierAttributes", "applyCombatBase", prefix + "VanillaModePolicy", "attackBase"), "shared weapon-only base installed");
        check(calls(prefix + "GrowthHealth", "change", null, "getMaxHealth"), "growth transition clamps to actual max without free healing");
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

package club.someoneice.humangunner;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Badge matrix, custody precedence and actual optional compatibility entrypoints. */
public final class MaidHumanRelationTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(!MaidHumanRelationPolicy.allowsWildTarget(true, true, false), "wild humans never initiate against maids");
        check(MaidHumanRelationPolicy.allowsWildTarget(true, true, true), "wild humans retain actual self-defense");
        check(MaidHumanRelationPolicy.allowsWildTarget(false, true, false), "hired/temporary combat rules remain separate");
        check(MaidHumanRelationPolicy.allowsWildTarget(true, false, false), "other target classifications are unchanged");
        for (int rank = 0; rank <= 3; rank++) {
            for (int clearance = -1; clearance <= 4; clearance++) {
                check(MaidHumanRelationPolicy.allowsAttack(true, false, clearance, rank, false, false)
                        == (clearance < rank), "wild badge relation " + clearance + "/" + rank);
                check(MaidHumanRelationPolicy.allowsAttack(true, false, clearance, rank, true, false),
                        "beacon threat overrides badge");
                check(MaidHumanRelationPolicy.allowsAttack(true, false, clearance, rank, false, true),
                        "actual attack permits self/owner defense");
                for (boolean forced : new boolean[]{false, true}) {
                    for (boolean defense : new boolean[]{false, true}) {
                        check(!MaidHumanRelationPolicy.allowsAttack(true, true, clearance, rank, forced, defense),
                                "hired/temporary custody always wins");
                        check(!MaidHumanRelationPolicy.allowsAttack(false, false, clearance, rank, forced, defense),
                                "unresolved owner never invents hostility");
                    }
                }
            }
        }
        // A clearance change must change the decision without a stored faction classification.
        check(MaidHumanRelationPolicy.allowsAttack(true, false, 0, 3, false, false)
                && !MaidHumanRelationPolicy.allowsAttack(true, false, 3, 3, false, false)
                && !MaidHumanRelationPolicy.allowsAttack(true, false, 4, 3, false, false),
                "switching badges removes hostility for neutral as well as friendly humans");
        String compat = "club/someoneice/humangunner/TouhouMaidCompat";
        String mixin = "club/someoneice/humangunner/mixin/TouhouMaidHumanTargetMixin";
        String targeting = "club/someoneice/humangunner/HumanTargeting";
        String human = "com/craftix/hostile_humans/entity/entities/Human";
        check(calls(human, "canAttack", targeting, "allowsWildHumanTarget"), "all weapon damage/attack gates honor maid neutrality");
        check(calls(human, "setTarget", targeting, "allowsWildHumanTarget"), "direct target assignment honors neutrality");
        check(calls(targeting, "isAutonomousPlayerEnemy", targeting, "allowsWildHumanTarget"), "autonomous search excludes neutral maids");
        check(calls("club/someoneice/humangunner/HumanGunner", "onLivingTick", targeting, "allowsWildHumanTarget"),
                "already selected neutral maid targets are cleared");
        check(calls("club/someoneice/humangunner/HumanGunner", "onLivingChangeTarget", targeting, "allowsWildHumanTarget"),
                "external target events honor neutrality");
        check(calls(targeting, "allowsWildHumanTarget", "club/someoneice/humangunner/HumanRelations", "isRecentHumanAttacker"),
                "self-defense requires a real recent attacker rather than anger alone");
        check(calls(mixin, "humanGunner$recognizeHumans", compat, "humanAttackDecision"), "maid canAttack hook");
        check(calls(compat, "humanAttackDecision", "club/someoneice/humangunner/MaidTargetRules", "permits"),
                "upstream explicit exclusions remain separate from badge/faction decisions");
        var upstream = new ClassNode();
        upstream.methods.add(new org.objectweb.asm.tree.MethodNode(org.objectweb.asm.Opcodes.ACC_STATIC, "generate",
                "(Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/item/ItemStack;", null, null));
        check(club.someoneice.humangunner.compat.OptionalMixinPlugin.hasMethod(upstream,
                club.someoneice.humangunner.compat.OptionalMixinPlugin.SPARTAN_RANDOMIZER, true), "known optional fix contract accepted");
        check(!club.someoneice.humangunner.compat.OptionalMixinPlugin.hasMethod(upstream,
                club.someoneice.humangunner.compat.OptionalMixinPlugin.SPARTAN_RANDOMIZER, false), "static/instance contract changes rejected");
        upstream.methods.clear();
        upstream.methods.add(new org.objectweb.asm.tree.MethodNode(0, "generate", "()V", null, null));
        check(!club.someoneice.humangunner.compat.OptionalMixinPlugin.hasMethod(upstream,
                club.someoneice.humangunner.compat.OptionalMixinPlugin.SPARTAN_RANDOMIZER, true), "changed overload must not receive the fix");
        // Model ModLauncher's supported provider mode and its real failure condition.
        int[] providerReads = {0};
        var loaderProvider = new org.spongepowered.asm.service.IClassBytecodeProvider() {
            @Override public ClassNode getClassNode(String name) {
                return getClassNode(name, true);
            }
            @Override public ClassNode getClassNode(String name, boolean runTransformers) {
                if (!runTransformers) throw new IllegalArgumentException(
                        "ModLauncher service does not currently support retrieval of untransformed bytecode");
                providerReads[0]++;
                return upstream;
            }
        };
        check(club.someoneice.humangunner.compat.OptionalMixinPlugin.readTarget(loaderProvider, "fixture.Target") == upstream
                && providerReads[0] == 1, "optional probe uses ModLauncher-supported bytecode mode");
        boolean rejectsRawMode = false;
        try { loaderProvider.getClassNode("fixture.Target", false); }
        catch (IllegalArgumentException expected) { rejectsRawMode = true; }
        check(rejectsRawMode, "fixture preserves actual Forge unsupported-raw-bytecode failure");
        check(calls("club/someoneice/humangunner/compat/OptionalMixinPlugin", "shouldApplyMixin",
                "club/someoneice/humangunner/compat/OptionalMixinPlugin", "readTarget"), "actual plugin uses loader boundary");
        check(calls(compat, "relationAllowsAttack", "club/someoneice/humangunner/IdentityBadgeAccess", "highestClearance"),
                "owner inventory and Curios badge access");
        check(calls(compat, "relationAllowsAttack", "club/someoneice/humangunner/HumanRelations", "effectiveOwner"),
                "hired and temporary owner gate");
        check(calls(compat, "onMaidTick", compat, "humanAttackDecision"), "brain target revalidation");
        check(calls(compat, "onMaidTick", "net/minecraft/world/entity/ai/Brain", "eraseMemory"), "stale brain memory removed");
        check(calls(compat, "onMaidHumanAttack", compat, "relationAllowsAttack"), "in-flight projectile relationship guard");
        System.out.println("MaidHumanRelationTest: " + checks + " checks passed; maid brain gameplay acceptance remains required");
    }

    private static boolean calls(String owner, String method, String target, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = MaidHumanRelationTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            if (stream == null) throw new AssertionError("Missing compiled " + owner);
            new ClassReader(stream).accept(node, 0);
        }
        for (var entry : node.methods) if (entry.name.equals(method)) {
            for (var instruction : entry.instructions)
                if (instruction instanceof MethodInsnNode call && call.owner.equals(target) && call.name.equals(name)) return true;
        }
        return false;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

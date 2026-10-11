package club.someoneice.humangunner;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Scope matrix plus the actual Forge cooldown-query bytecode contract. */
public final class ProjectileCooldownPolicyTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(ProjectileCooldownPolicy.allows(false, false, false, true, true, false),
                "human archers can independently damage an ordinary mob");
        check(ProjectileCooldownPolicy.allows(false, false, true, false, true, false),
                "a human can receive successive skeleton/player projectiles");
        check(ProjectileCooldownPolicy.allows(false, false, true, true, true, false),
                "hostile human factions can exchange successive projectiles");
        for (boolean victimHuman : new boolean[]{false, true}) {
            for (boolean shooterHuman : new boolean[]{false, true}) {
                check(!ProjectileCooldownPolicy.allows(true, false, victimHuman, shooterHuman, true, false),
                        "vanilla mode preserves original cooldown handling");
                check(!ProjectileCooldownPolicy.allows(false, true, victimHuman, shooterHuman, true, false),
                        "never extend bypass to player victims, including hired PvP");
                check(!ProjectileCooldownPolicy.allows(false, false, victimHuman, shooterHuman, false, false),
                        "melee, environmental and status-effect damage are unaffected");
                check(!ProjectileCooldownPolicy.allows(false, false, victimHuman, shooterHuman, true, true),
                        "projectile-attributed explosions are unaffected");
            }
        }
        check(!ProjectileCooldownPolicy.allows(false, false, false, false, true, false),
                "unrelated mob combat remains unchanged");

        check(HumanAttackCooldownPolicy.allowsMelee(false, true, true, true, false, false),
                "human direct melee ignores hurt cooldown at its existing attack rate, including player victims");
        check(!HumanAttackCooldownPolicy.allowsMelee(true, true, true, true, false, false),
                "vanilla mode retains melee cooldown");
        check(!HumanAttackCooldownPolicy.allowsMelee(false, false, true, true, false, false),
                "ordinary mobs attacking humans keep their melee cooldown");
        check(!HumanAttackCooldownPolicy.allowsMelee(false, true, false, true, false, false),
                "indirect human damage cannot qualify as melee");
        check(!HumanAttackCooldownPolicy.allowsMelee(false, true, true, false, false, false),
                "thorns, fire and other damage types are not direct attacks");
        check(!HumanAttackCooldownPolicy.allowsMelee(false, true, true, true, true, false)
                && !HumanAttackCooldownPolicy.allowsMelee(false, true, true, true, false, true),
                "projectiles and explosions retain their own policy");
        check(CreeperFinishPolicy.canFinish(20, 10, 10, 1, 4, 10, true)
                && !CreeperFinishPolicy.canFinish(20, 10, 10, 1, 4, 10, false),
                "creeper forecast recognizes actual fast melee hits without speeding up attacks");

        ClassNode living = read("net/minecraft/world/entity/LivingEntity");
        var hurt = living.methods.stream().filter(m -> (m.name.equals("hurt") || m.name.equals("m_6469_"))
                && m.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
                .findFirst().orElseThrow();
        boolean inside = false;
        int starts = 0, ends = 0, queries = 0;
        for (var instruction : hurt.instructions) {
            if (instruction instanceof FieldInsnNode field) {
                if (field.owner.equals("net/minecraft/tags/DamageTypeTags")
                        && (field.name.equals("BYPASSES_COOLDOWN") || field.name.equals("f_273918_"))) {
                    inside = true;
                    starts++;
                }
                if (inside && field.owner.equals("net/minecraft/world/entity/LivingEntity")
                        && (field.name.equals("lastHurt") || field.name.equals("f_20898_"))) {
                    inside = false;
                    ends++;
                }
            }
            if (inside && instruction instanceof MethodInsnNode call
                    && call.owner.equals("net/minecraft/world/damagesource/DamageSource")
                    && (call.name.equals("is") || call.name.equals("m_269533_"))
                    && call.desc.equals("(Lnet/minecraft/tags/TagKey;)Z")) queries++;
        }
        check(starts == 1 && ends == 1 && queries == 1,
                "the real Forge slice contains exactly one cooldown tag query");
        for (String name : new String[]{"HumanAttackCooldownPolicy", "ProjectileCooldownPolicy", "mixin/LivingEntityProjectileCooldownMixin"}) {
            for (var method : read("club/someoneice/humangunner/" + name).methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode field)
                        check(field.getOpcode() != Opcodes.PUTFIELD || !field.name.equals("invulnerableTime")
                                && !field.name.equals("f_19802_"), "never reset the shared cooldown timer");
                    if (instruction instanceof MethodInsnNode call)
                        check(!call.name.equals("hurt") && !call.name.equals("m_6469_")
                                && !call.name.equals("setHealth") && !call.name.equals("m_21153_"),
                                "never synthesize hits or write health outside the normal damage pipeline");
                }
            }
        }
        System.out.println("ProjectileCooldownPolicyTest: " + checks + " checks passed; startup and gameplay require acceptance");
    }

    private static ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = ProjectileCooldownPolicyTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing class " + name);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

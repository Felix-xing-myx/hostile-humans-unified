package club.someoneice.humangunner;

import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Verify custody asymmetry and the real collision candidate paths, not just cancelled damage. */
public final class HumanProjectileRelationsTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var owner = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
        var other = java.util.UUID.fromString("00000000-0000-0000-0000-000000000002");
        for (boolean pvp : new boolean[]{false, true})
            check(HumanRelationshipPolicy.ownedHumanAlliedToPlayer(owner, owner, pvp), "owner is protected in both PvP modes");
        check(HumanRelationshipPolicy.ownedHumanAlliedToPlayer(owner, other, false), "PvP off: other players are skipped");
        check(!HumanRelationshipPolicy.ownedHumanAlliedToPlayer(owner, other, true), "PvP on: other players can be hit accidentally");
        check(!HumanRelationshipPolicy.ownedHumanAlliedToPlayer(null, other, false), "wild humans do not inherit hired player immunity");
        check(HumanRelationshipPolicy.blockHumanDamage(false, false, true), "same-faction wild humans are protected");
        check(!HumanRelationshipPolicy.blockHumanDamage(false, false, false), "wild enemies remain hittable");
        check(!HumanRelationshipPolicy.blockHumanDamage(false, true, true), "wild can hit same-tier hired human before retaliation");
        check(HumanRelationshipPolicy.blockHumanDamage(true, false, true), "reverse permission alone cannot establish mutual friendship");
        check(HumanRelationshipPolicy.blockHumanDamage(true, true, true), "allied hired humans are protected");
        check(!HumanRelationshipPolicy.blockHumanDamage(true, true, false), "authorized hired PvP remains hittable");
        String root = "club/someoneice/humangunner/";
        ClassNode relation = read(root + "HumanProjectileRelations");
        check(calls(relation, "protectsPlayer", root + "HumanRelationshipPolicy", "ownedHumanAlliedToPlayer") == 1,
                "player immunity is applied before collision using owner/PvP rule");
        check(calls(relation, "passesThrough", root + "HumanProjectileRelations", "protectsPlayer") == 1,
                "collision uses the shared owner/PvP player protection");
        check(calls(relation, "onProtectedPlayerAttack", root + "HumanProjectileRelations", "protectsPlayer") == 1,
                "early damage protection uses the same rule");
        check(calls(relation, "onProjectileExplosion", "java/util/List", "removeIf") == 1,
                "explosion removes protected entities before damage and knockback");
        check(calls(relation, "passesThrough", root + "HumanGunner", "areCombatAllies") == 2,
                "collision gate checks both directions, preserving hired/wild combat");
        check(calls(read(root + "mixin/HumanProjectileCollisionMixin"), "humanGunner$skipMutualAllies",
                root + "HumanProjectileRelations", "passesThrough") == 1, "vanilla predicate filters before hit side effects");
        ClassNode arrow = read("net/minecraft/world/entity/projectile/AbstractArrow");
        check(calls(arrow, "canHitEntity", "net/minecraft/world/entity/projectile/Projectile", "canHitEntity") == 1,
                "arrow delegates to shared projectile predicate");
        ClassNode trident = read("net/minecraft/world/entity/projectile/ThrownTrident");
        check(calls(trident, "findHitEntity", "net/minecraft/world/entity/projectile/AbstractArrow", "findHitEntity") == 1,
                "trident uses filtered arrow entity search");
        ClassNode taczMixin = read(root + "mixin/TaczHumanProjectileCollisionMixin");
        var redirect = taczMixin.methods.stream().filter(m -> m.name.equals("humanGunner$filterBulletCandidates"))
                .findFirst().orElseThrow();
        var annotations = new java.util.ArrayList<org.objectweb.asm.tree.AnnotationNode>();
        if (redirect.visibleAnnotations != null) annotations.addAll(redirect.visibleAnnotations);
        if (redirect.invisibleAnnotations != null) annotations.addAll(redirect.invisibleAnnotations);
        var injection = annotations.stream().filter(a -> a.desc.endsWith("/Redirect;")).findFirst().orElseThrow();
        List<?> paths = (List<?>) value(injection, "method");
        check(paths.containsAll(List.of("findEntityOnPath", "findEntitiesOnPath")), "TaCZ ordinary and piercing searches both intercepted");
        check(Integer.valueOf(2).equals(value(injection, "require")), "both TaCZ candidate filters must inject");
        try (ZipFile jar = new ZipFile(args[0]); var input = jar.getInputStream(jar.getEntry("com/tacz/guns/util/EntityUtil.class"))) {
            ClassNode util = new ClassNode();
            new ClassReader(input).accept(util, 0);
            for (String method : List.of("findEntityOnPath", "findEntitiesOnPath"))
                check(calls(util, method, "net/minecraft/world/level/Level", "m_6249_") == 1,
                        "installed TaCZ candidate query supported: " + method);
        }
        System.out.println("HumanProjectileRelationsTest: " + checks + " checks passed; hit/fire/piercing gameplay remains to be tested");
    }
    private static Object value(org.objectweb.asm.tree.AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
    private static ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = HumanProjectileRelationsTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing class " + name);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }
    private static int calls(ClassNode node, String method, String owner, String name) {
        int count = 0;
        for (var entry : node.methods) if (entry.name.equals(method))
            for (var instruction : entry.instructions)
                if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++;
        return count;
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

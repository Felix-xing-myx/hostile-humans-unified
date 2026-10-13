package club.someoneice.humangunner;

import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;

/** Run with the exact TaCZ JAR path as the sole argument; never loads TaCZ classes. */
public final class TaczAmmoMixinContractTest {
    private static final List<String> RELOAD_CALLS = List.of(
            TaczAmmoMixinContract.RELOAD_INVENTORY_AMMO,
            TaczAmmoMixinContract.START_RELOAD, TaczAmmoMixinContract.RELOAD_NOTIFICATION);

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected TaCZ JAR path");
        try (ZipFile jar = new ZipFile(args[0])) {
            ClassNode reload = read(jar, "com/tacz/guns/entity/shooter/LivingEntityReload");
            checkReload(reload);
            var selector = MemberInfo.parse(TaczAmmoMixinContract.RELOAD_BODY, null).validate();
            check(selector.getName() == null && selector.getMaxMatchCount() == Integer.MAX_VALUE,
                    "reload signature must select all matching bodies without a synthetic name");
            MethodNode body = reload.methods.stream()
                    .filter(m -> selector.matches(reload.name, m.name, m.desc).isExactMatch())
                    .findFirst().orElseThrow();
            body.name = "upstreamRenamedReloadBody";
            checkReload(reload);

            // An unrelated method with the same signature cannot hide the actual body.
            MethodNode unrelated = new MethodNode(Opcodes.ACC_PRIVATE, "unrelated", body.desc, null, null);
            reload.methods.add(0, unrelated);
            checkReload(reload);
            // Moving the same calls into reload() must still preserve all boundaries.
            MethodNode entry = reload.methods.stream().filter(m -> m.name.equals("reload")).findFirst().orElseThrow();
            entry.instructions.clear();
            entry.instructions.add(body.instructions);
            reload.methods.remove(body);
            checkReload(reload);
            // A duplicate payment call must no longer be accepted as exactly one hook.
            var call = MemberInfo.parse(TaczAmmoMixinContract.START_RELOAD, null).validate();
            unrelated.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                    call.getOwner(), call.getName(), call.getDesc(), false));
            check(count(reload, List.of(TaczAmmoMixinContract.RELOAD, TaczAmmoMixinContract.RELOAD_BODY),
                    TaczAmmoMixinContract.START_RELOAD) == 2, "duplicate reload payment invocation detected");

            for (String shootAlternativeContract : List.of(TaczAmmoMixinContract.SHOOT, TaczAmmoMixinContract.SHOOT_LEGACY)) {
                check(MemberInfo.parse(shootAlternativeContract, null).validate().getMinMatchCount() == 0,
                        "alternative shoot selector must allow an absent version-specific overload");
            }
            ClassNode shoot = read(jar, "com/tacz/guns/entity/shooter/LivingEntityShoot");
            check(count(shoot, List.of(TaczAmmoMixinContract.SHOOT, TaczAmmoMixinContract.SHOOT_LEGACY), TaczAmmoMixinContract.SHOOT_INVENTORY_AMMO) == 1,
                    "installed shoot inventory boundary must be unique");
            ClassNode script = read(jar, "com/tacz/guns/item/ModernKineticGunScriptAPI");
            check(methods(script, TaczAmmoMixinContract.SCRIPT_INVENTORY_AMMO) == 1,
                    "installed inventory script signature");
            check(methods(script, TaczAmmoMixinContract.SCRIPT_SHOOT_ONCE) == 1,
                    "installed consume flag script signature");
        }
        verifyRedirectAnnotations();
        System.out.println("TaczAmmoMixinContractTest passed; startup and reload gameplay still require acceptance");
    }

    private static void checkReload(ClassNode node) {
        for (String target : RELOAD_CALLS) check(count(node,
                List.of(TaczAmmoMixinContract.RELOAD, TaczAmmoMixinContract.RELOAD_BODY), target) == 1,
                "reload requires exactly one invocation: " + target);
        check(count(node, List.of(TaczAmmoMixinContract.TICK_RELOAD), TaczAmmoMixinContract.GUN_TICK_RELOAD) == 1,
                "installed reload tick boundary must be unique");
        check(methods(node, TaczAmmoMixinContract.CANCEL_RELOAD) == 1, "installed cancel signature");
    }

    private static int methods(ClassNode node, String selector) {
        var parsed = MemberInfo.parse(selector, null).validate();
        return (int) node.methods.stream().filter(m -> parsed.matches(node.name, m.name, m.desc).isExactMatch()).count();
    }

    private static int count(ClassNode node, List<String> selectors, String target) {
        var parsed = selectors.stream().map(s -> MemberInfo.parse(s, null).validate()).toList();
        var invocation = MemberInfo.parse(target, null).validate();
        int count = 0;
        for (var method : node.methods) {
            if (parsed.stream().noneMatch(s -> s.matches(node.name, method.name, method.desc).isExactMatch())) continue;
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call
                        && invocation.matches(call.owner, call.name, call.desc).isExactMatch()) count++;
        }
        return count;
    }

    private static void verifyRedirectAnnotations() throws Exception {
        for (String name : List.of("TaczHumanReloadMixin", "TaczHumanShootMixin")) {
            ClassNode node = new ClassNode();
            try (var input = TaczAmmoMixinContractTest.class.getClassLoader().getResourceAsStream(
                    "club/someoneice/humangunner/mixin/" + name + ".class")) {
                if (input == null) throw new AssertionError("Missing compiled mixin " + name);
                new ClassReader(input).accept(node, 0);
            }
            int redirects = 0;
            for (var method : node.methods) {
                if (method.visibleAnnotations == null) continue;
                for (var annotation : method.visibleAnnotations) {
                    if (!annotation.desc.endsWith("/Redirect;")) continue;
                    redirects++;
                    check(Integer.valueOf(1).equals(value(annotation, "require")), "required ammo hook: " + method.name);
                    check(Integer.valueOf(1).equals(value(annotation, "allow")), "unique ammo hook: " + method.name);
                    if (name.equals("TaczHumanReloadMixin") && !method.name.equals("humanGunner$paidTick"))
                        check(value(annotation, "method").equals(List.of(
                                TaczAmmoMixinContract.RELOAD, TaczAmmoMixinContract.RELOAD_BODY)),
                                "reload redirects use the reviewed signature selectors");
                }
            }
            check(redirects == (name.equals("TaczHumanReloadMixin") ? 4 : 1), "all ammo redirects reviewed");
        }
    }

    private static Object value(org.objectweb.asm.tree.AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }

    private static ClassNode read(ZipFile jar, String name) throws Exception {
        var entry = jar.getEntry(name + ".class");
        if (entry == null) throw new AssertionError("Missing TaCZ boundary class " + name);
        ClassNode node = new ClassNode();
        try (var input = jar.getInputStream(entry)) { new ClassReader(input).accept(node, 0); }
        return node;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

package dev.felix.hostilehumans.monitor;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/**
 * Actual installed Scguns/JEG API + NPC commit timing. Does not load optional native/client
 * classes.
 */
public final class NativeNpcRecoilContractTest {
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    private static ClassNode node(byte[] bytes) {
        var c = new ClassNode();
        new ClassReader(bytes).accept(c, 0);
        return c;
    }

    private static ClassNode nativeClass(ZipFile jar, String owner) throws Exception {
        var e = jar.getEntry(owner + ".class");
        check(e != null, "Missing native class " + owner);
        try (var in = jar.getInputStream(e)) {
            return node(in.readAllBytes());
        }
    }

    private static MethodNode method(ClassNode c, String name) {
        return c.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static boolean calls(MethodNode m, String owner, String name) {
        for (var i : m.instructions)
            if (i instanceof MethodInsnNode x && x.owner.equals(owner) && x.name.equals(name))
                return true;
        return false;
    }

    private static int callIndex(MethodNode m, String owner, String name) {
        for (int i = 0; i < m.instructions.size(); i++)
            if (m.instructions.get(i) instanceof MethodInsnNode x
                    && x.owner.equals(owner)
                    && x.name.equals(name)) return i;
        return -1;
    }

    private static void api(ZipFile jar, String owner, String name, String desc) throws Exception {
        check(
                nativeClass(jar, owner).methods.stream()
                        .anyMatch(
                                m ->
                                        m.name.equals(name)
                                                && m.desc.equals(desc)
                                                && (m.access & Opcodes.ACC_PUBLIC) != 0),
                "Missing public actual API " + owner + "." + name + desc);
    }

    private static void nativeContract(ZipFile jar, String prefix, boolean sc) throws Exception {
        var event = nativeClass(jar, prefix + "/event/GunFireEvent");
        check(
                event.superName.equals("net/minecraftforge/event/entity/player/PlayerEvent"),
                "Native fire event requires Player; never fabricate one for Human");
        api(
                jar,
                prefix + "/event/GunFireEvent$Post",
                "<init>",
                "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V");
        var recoil =
                method(nativeClass(jar, prefix + "/client/handler/GunRecoilHandler"), "onGunFire");
        check(
                recoil.desc.equals("(L" + prefix + "/event/GunFireEvent$Post;)V"),
                "Native camera is post-shot, not pre-trigger");
        check(
                calls(recoil, prefix + "/util/GunModifierHelper", "getRecoilModifier"),
                "Actual attachment recoil helper");
        check(
                calls(
                        recoil,
                        prefix + (sc ? "/common/Gun$Projectile" : "/common/Gun$General"),
                        "getRecoilAngle"),
                "Use correct actual recoil angle field for this backend");
        var frame =
                method(
                        nativeClass(jar, prefix + "/client/handler/GunRecoilHandler"),
                        "onRenderTick");
        var constants = new HashSet<Float>();
        for (var i : frame.instructions)
            if (i instanceof LdcInsnNode l && l.cst instanceof Float f) constants.add(f);
        check(
                constants.contains(.15f) && constants.contains(.2f) && constants.contains(.8f),
                "Actual camera timing is .15/tick with .2 kick and .8 recovery");
        var aim =
                method(
                        nativeClass(jar, prefix + "/client/handler/AimingHandler$AimTracker"),
                        "handleAiming");
        check(
                calls(aim, prefix + "/util/GunEnchantmentHelper", "getAimDownSightSpeed")
                        && calls(
                                aim,
                                prefix + "/util/GunModifierHelper",
                                "getModifiedAimDownSightSpeed"),
                "Native ADS speed includes gun enchantments and attachments");
        check(
                java.util.stream.StreamSupport.stream(aim.instructions.spliterator(), false)
                        .anyMatch(
                                i -> i instanceof LdcInsnNode l && Double.valueOf(5).equals(l.cst)),
                "Actual native maximum aim progress is 5");
        var npc = method(nativeClass(jar, prefix + "/entity/ai/AIGunEvent"), "performGunAttack");
        int returns = 0;
        for (var i : npc.instructions) {
            if (i.getOpcode() == Opcodes.RETURN) returns++;
            check(
                    !(i instanceof TypeInsnNode t
                            && t.getOpcode() == Opcodes.NEW
                            && t.desc.startsWith(prefix + "/event/GunFireEvent")),
                    "NPC path does not publish Player fire events");
        }
        check(
                returns == (sc ? 2 : 3),
                "Known native NPC early returns: client only; JEG additionally harvesting item");
        int create = callIndex(npc, prefix + "/interfaces/IProjectileFactory", "create"),
                publish =
                        callIndex(
                                npc,
                                "com/mrcrayfish/framework/api/network/FrameworkNetwork",
                                "sendToNearbyPlayers");
        check(
                create >= 0 && publish > create,
                "Native NPC path constructs projectiles before publishing trails");
        api(
                jar,
                prefix + "/util/GunModifierHelper",
                "getRecoilModifier",
                "(Lnet/minecraft/world/item/ItemStack;)F");
        api(
                jar,
                prefix + "/util/GunModifierHelper",
                "getModifiedAimDownSightSpeed",
                "(Lnet/minecraft/world/item/ItemStack;D)D");
        api(
                jar,
                prefix + "/util/GunEnchantmentHelper",
                "getAimDownSightSpeed",
                "(Lnet/minecraft/world/item/ItemStack;)D");
        if (sc)
            api(
                    jar,
                    prefix + "/util/GunEnchantmentHelper",
                    "getRecoilModifier",
                    "(Lnet/minecraft/world/item/ItemStack;)F");
    }

    private static void links(ZipFile jar, Path root, String relative, String prefix)
            throws Exception {
        var c = node(Files.readAllBytes(root.resolve(relative + ".class")));
        for (var method : c.methods)
            for (var i : method.instructions) {
                if (i instanceof MethodInsnNode m && m.owner.startsWith(prefix)) {
                    var nativeOwner = nativeClass(jar, m.owner);
                    check(
                            nativeOwner.methods.stream()
                                    .anyMatch(x -> x.name.equals(m.name) && x.desc.equals(m.desc)),
                            "Native method linkage " + m.owner + "." + m.name + m.desc);
                }
                if (i instanceof FieldInsnNode f && f.owner.startsWith(prefix)) {
                    var nativeOwner = nativeClass(jar, f.owner);
                    check(
                            nativeOwner.fields.stream()
                                    .anyMatch(x -> x.name.equals(f.name) && x.desc.equals(f.desc)),
                            "Native field linkage " + f.owner + "." + f.name);
                }
                check(
                        !(i instanceof MethodInsnNode m && m.owner.endsWith("/GunRecoilHandler")),
                        "HHU must never call global native recoil handler");
                check(
                        !(i instanceof FieldInsnNode f
                                && f.owner.equals("net/minecraft/client/Minecraft")
                                && f.name.equals("player")),
                        "No real LocalPlayer camera mutation");
            }
    }

    private static void commit(Path root, String backend, String prefix, String title)
            throws Exception {
        var directory = root.resolve("dev/felix/hostilehumans/compat/" + backend);
        int commits = 0;
        try (var entries = Files.list(directory)) {
            for (var path :
                    entries.filter(
                                    p ->
                                            p.getFileName()
                                                            .toString()
                                                            .startsWith(title + "Integration")
                                                    && p.toString().endsWith(".class"))
                            .toList())
                for (var m : node(Files.readAllBytes(path)).methods) {
                    int nativeShot =
                            callIndex(m, prefix + "/entity/ai/AIGunEvent", "performGunAttack");
                    if (nativeShot < 0) continue;
                    int adapter =
                            callIndex(
                                    m,
                                    "dev/felix/hostilehumans/compat/"
                                            + backend
                                            + "/"
                                            + title
                                            + "MonitorRecoilAdapter",
                                    "committed");
                    int hearing =
                            callIndex(
                                    m,
                                    "dev/felix/hostilehumans/perception/HumanAwareness",
                                    "gunshot");
                    check(
                            adapter > hearing && hearing > nativeShot,
                            "Native shot completes before existing accepted shot commit and recoil"
                                + " callback");
                    int n = 0;
                    for (var i : m.instructions)
                        if (i instanceof MethodInsnNode x
                                && x.name.equals("committed")
                                && x.owner.endsWith("MonitorRecoilAdapter")) n++;
                    check(
                            n == 1,
                            "Exactly one recoil per NPC paid shot, independent of projectile pellet"
                                + " count");
                    commits++;
                }
        }
        check(commits == 1, "Exactly one accepted NPC shot seam for " + backend);
    }

    public static void main(String[] args) throws Exception {
        check(
                args.length == 3,
                "Pass actual Scguns jar, actual JEG jar, compiled HHU classes root");
        var root = Path.of(args[2]);
        try (var sc = new ZipFile(args[0]);
                var jeg = new ZipFile(args[1])) {
            nativeContract(sc, "top/ribs/scguns", true);
            nativeContract(jeg, "ttv/migami/jeg", false);
            links(
                    sc,
                    root,
                    "dev/felix/hostilehumans/compat/scguns/ScgunsMonitorRecoilAdapter",
                    "top/ribs/scguns");
            links(
                    jeg,
                    root,
                    "dev/felix/hostilehumans/compat/jeg/JegMonitorRecoilAdapter",
                    "ttv/migami/jeg");
            commit(root, "scguns", "top/ribs/scguns", "Scguns");
            commit(root, "jeg", "ttv/migami/jeg", "Jeg");
            var identity =
                    node(
                            Files.readAllBytes(
                                    root.resolve(
                                            "dev/felix/hostilehumans/compat/jeg/JegGunIdentity.class")));
            check(
                    identity.methods.stream()
                            .flatMap(
                                    m ->
                                            java.util.stream.StreamSupport.stream(
                                                    m.instructions.spliterator(), false))
                            .anyMatch(
                                    i ->
                                            i instanceof TypeInsnNode t
                                                    && t.getOpcode() == Opcodes.INSTANCEOF
                                                    && t.desc.equals(
                                                            "ttv/migami/jeg/item/HarvestingGunItem")),
                    "HHU identity rejects native JEG harvesting early return");
        }
        System.out.println("Actual native NPC discharge/recoil/ADS/API isolation contracts passed");
    }
}

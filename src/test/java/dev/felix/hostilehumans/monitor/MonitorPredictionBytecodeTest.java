package dev.felix.hostilehumans.monitor;

import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Checks real Forge injection targets and compiled, subtype-scoped construction isolation. */
public final class MonitorPredictionBytecodeTest {
    private static ZipFile artifact;
    private static final String ENTITY = "net/minecraft/world/entity/Entity";
    private static final String BODY = "dev/felix/hostilehumans/monitor/client/MonitorPredictionBody";
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static ClassNode read(ZipFile jar, String owner) throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(jar.getInputStream(jar.getEntry(owner + ".class"))).accept(node, 0); return node;
    }
    private static MethodNode method(ClassNode node, String... names) {
        return node.methods.stream().filter(m -> Set.of(names).contains(m.name)).findFirst().orElseThrow();
    }
    private static long calls(MethodNode method, String owner, String name) {
        return java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(i -> i instanceof MethodInsnNode m && (owner == null || m.owner.equals(owner)) && m.name.equals(name)).count();
    }
    private static ClassNode own(String owner) throws Exception {
        if (artifact != null) return read(artifact, owner);
        ClassNode node = new ClassNode();
        try (var in = MonitorPredictionBytecodeTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            check(in != null, "compiled implementation available: " + owner); new ClassReader(in).accept(node, 0);
        }
        return node;
    }
    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        throw new AssertionError("Missing annotation value " + key);
    }
    private static String target(MethodNode method) {
        AnnotationNode redirect = method.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Redirect;")).findFirst().orElseThrow();
        return (String) value((AnnotationNode) value(redirect, "at"), "target");
    }
    public static void main(String[] args) throws Exception {
        check(args.length == 1 || args.length == 2, "pass mapped Forge Minecraft jar, optionally the reobfuscated mod jar");
        if (args.length == 2) artifact = new ZipFile(Path.of(args[1]).toFile());
        try (var jar = new ZipFile(Path.of(args[0]).toFile())) {
            ClassNode entity = read(jar, ENTITY);
            MethodNode ctor = method(entity, "<init>");
            check(calls(ctor, "net/minecraftforge/event/ForgeEventFactory", "getEntitySizeForge") == 1,
                    "constructor has exactly one size event call to redirect");
            check(calls(ctor, "net/minecraftforge/eventbus/api/IEventBus", "post") == 1,
                    "constructor has exactly one direct construction event publication");
            check(calls(ctor, ENTITY, "gatherCapabilities") == 1,
                    "constructor capabilities redirect owner/descriptor remains Entity");
            MethodNode collide = method(entity, "collide", "m_20272_");
            check((collide.access & Opcodes.ACC_PRIVATE) != 0 && collide.desc.equals("(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"),
                    "native private collision invoker descriptor matches");
            check(calls(collide, ENTITY, "collideBoundingBox") >= 4 || calls(collide, ENTITY, "m_198894_") >= 4,
                    "native collision still evaluates alternate step candidates rather than a flat sweep");
            method(entity, "updateFluidOnEyes", "m_20323_");
            ClassNode living = read(jar, "net/minecraft/world/entity/LivingEntity");
            check(living.fields.stream().anyMatch(f -> Set.of("noJumpDelay", "f_20954_").contains(f.name) && f.desc.equals("I")),
                    "native jump cooldown field is an int");
            MethodNode jump = method(living, "jumpFromGround", "m_6135_");
            check(calls(jump, "net/minecraftforge/common/ForgeHooks", "onLivingJump") == 1,
                    "native jump event remains the side effect intentionally omitted by the motion bridge");
        }
        ClassNode isolation = own("dev/felix/hostilehumans/mixin/MonitorPredictionConstructionMixin");
        for (String handler : new String[]{"hostileHumans$predictionSize", "hostileHumans$predictionConstruction", "hostileHumans$predictionCapabilities"}) {
            MethodNode m = method(isolation, handler);
            check(java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false)
                    .anyMatch(i -> i instanceof TypeInsnNode t && t.getOpcode() == Opcodes.INSTANCEOF && t.desc.equals(BODY)),
                    "construction isolation is guarded by exact prediction subtype: " + handler);
        }
        check(calls(method(isolation, "hostileHumans$predictionConstruction"), "net/minecraftforge/eventbus/api/IEventBus", "post") == 1,
                "real entity construction events retain their original bus call");
        check(calls(method(isolation, "hostileHumans$predictionSize"), "net/minecraftforge/event/ForgeEventFactory", "getEntitySizeForge") == 1,
                "real entity size modifications retain their original factory call");
        check(calls(method(isolation, "hostileHumans$predictionCapabilities"), null, "gatherCapabilities") == 1,
                "real entity capability attachment retains its original call");
        check(target(method(isolation, "hostileHumans$predictionSize")).equals(
                "Lnet/minecraftforge/event/ForgeEventFactory;getEntitySizeForge(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Pose;Lnet/minecraft/world/entity/EntityDimensions;F)Lnet/minecraftforge/event/entity/EntityEvent$Size;"),
                "packaged constructor size redirect retains exact production descriptor");
        check(target(method(isolation, "hostileHumans$predictionConstruction")).equals(
                "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"),
                "packaged construction event redirect retains exact production descriptor");
        check(target(method(isolation, "hostileHumans$predictionCapabilities")).equals(
                "Lnet/minecraft/world/entity/Entity;gatherCapabilities()V"),
                "packaged capability redirect retains exact production owner/descriptor");
        ClassNode access = own("dev/felix/hostilehumans/mixin/MonitorPredictionEntityAccess");
        for (String[] contract : new String[][]{{"hostileHumans$collide", "m_20272_"},
                {"hostileHumans$updateFluidEyes", "m_20323_"}, {"hostileHumans$dimensions", "f_19815_"},
                {"hostileHumans$eyeHeight", "f_19816_"}})
            check(value(method(access, contract[0]).visibleAnnotations.get(0), "value").equals(contract[1]),
                    "packaged access bridge retains production SRG target " + contract[0]);
        ClassNode body = own(BODY);
        check(calls(method(body,"step"),null,artifact==null?"setJumping":"m_6862_")==1,
                "climbing receives the same held-jump flag as server travel, including the production artifact");
        MethodNode predictiveStep = method(body, "step");
        String intent = "dev/felix/hostilehumans/monitor/client/MonitorPredictionIntent";
        check(calls(predictiveStep, intent, "sprinting") == 1,
                "real prediction step delegates sprint eligibility to the exhaustively tested intent rule");
        for (String button : new String[]{"active", "sprint", "forward", "sneak", "attack", "use"})
            check(calls(predictiveStep, "dev/felix/hostilehumans/monitor/HumanMonitorNetwork$Control", button) >= 1,
                    "compiled prediction consumes actual authoritative intent component: " + button);
        ClassNode prediction = own("dev/felix/hostilehumans/monitor/client/MonitorMovementPrediction");
        check(calls(method(prediction, "acceptSafely"), "dev/felix/hostilehumans/monitor/MonitorInputHistory", "discard") == 1,
                "failed replay discards causally invalid pending input instead of resuming a partial replay");
        for (MethodNode m : body.methods) for (var i : m.instructions) if (i instanceof MethodInsnNode call) {
            check(!call.name.equals("addFreshEntity") && !call.name.equals("m_7967_"), "prediction shell never enters a level");
            check(!call.owner.equals("net/minecraftforge/common/ForgeHooks"), "prediction shell emits no Forge gameplay hook");
        }
        if (artifact != null) artifact.close();
        System.out.println("MonitorPredictionBytecodeTest passed" + (args.length == 2 ? " (reobfuscated artifact)" : " (compiled classes)"));
    }
}

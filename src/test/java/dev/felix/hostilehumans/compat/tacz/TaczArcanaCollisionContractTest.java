package dev.felix.hostilehumans.compat.tacz;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Real installed-JAR composition audit. Models Arcana's actual Redirect replacement, then verifies
 * the HHU STORE boundary and stack. This is not a full Mixin engine/server startup test. Arguments:
 * TaCZ jar, Arcana loader-hybrid jar.
 */
public final class TaczArcanaCollisionContractTest {
    private static int checks;

    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("TaCZ jar and Arcana jar required");
        ClassNode nativeUtil;
        try (ZipFile jar = new ZipFile(args[0])) {
            nativeUtil =
                    read(
                            jar.getInputStream(jar.getEntry("com/tacz/guns/util/EntityUtil.class"))
                                    .readAllBytes());
        }
        byte[] nested;
        try (ZipFile jar = new ZipFile(args[1])) {
            nested = jar.getInputStream(jar.getEntry("META-INF/mod.jar")).readAllBytes();
        }
        ClassNode arcana = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(nested))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                if (entry.getName()
                        .equals("group/taczexpands/server/mixin/MixinEntityUtil.class")) {
                    arcana = read(zip.readAllBytes());
                    break;
                }
        }
        check(arcana != null, "read actual nested Arcana EntityUtil mixin");
        check(
                arcana.fields.stream()
                        .anyMatch(f -> f.name.equals("taczexpands$prevProximityFuzeRange")),
                "Arcana proximity-fuze state remains present");
        boolean returnHook = false;
        int redirects = 0;
        for (MethodNode handler : arcana.methods) {
            for (AnnotationNode annotation : annotations(handler)) {
                if (annotation.desc.endsWith("/Inject;")
                        && ((List<?>) value(annotation, "method")).contains("getHitResult"))
                    returnHook = true;
                if (!annotation.desc.endsWith("/Redirect;")) continue;
                AnnotationNode at = (AnnotationNode) value(annotation, "at");
                check(
                        String.valueOf(value(at, "target")).contains("Level;getEntities("),
                        "Arcana redirects the broad-phase query");
                for (Object selected : (List<?>) value(annotation, "method")) {
                    String method = (String) selected;
                    check(
                            Map.of("findEntityOnPath", 6, "findEntitiesOnPath", 4)
                                    .containsKey(method),
                            "known native collision method");
                    MethodNode original =
                            nativeUtil.methods.stream()
                                    .filter(m -> m.name.equals(method))
                                    .findFirst()
                                    .orElseThrow();
                    MethodNode transformed =
                            new MethodNode(
                                    original.access,
                                    original.name,
                                    original.desc,
                                    original.signature,
                                    original.exceptions.toArray(String[]::new));
                    original.accept(transformed);
                    int replaced = 0;
                    for (AbstractInsnNode instruction : transformed.instructions.toArray()) {
                        if (instruction instanceof MethodInsnNode call
                                && call.owner.equals("net/minecraft/world/level/Level")
                                && call.name.equals("m_6249_")) {
                            String descriptor = "(L" + call.owner + ";" + call.desc.substring(1);
                            check(
                                    descriptor.equals(handler.desc),
                                    "actual Arcana handler consumes exactly the query"
                                        + " receiver/arguments");
                            transformed.instructions.set(
                                    call,
                                    new MethodInsnNode(
                                            Opcodes.INVOKESTATIC,
                                            nativeUtil.name,
                                            handler.name,
                                            handler.desc,
                                            false));
                            replaced++;
                        }
                    }
                    check(replaced == 1, "apply actual Arcana redirect exactly once");
                    new Analyzer<>(new BasicInterpreter()).analyze(nativeUtil.name, transformed);
                    int slot = Map.of("findEntityOnPath", 6, "findEntitiesOnPath", 4).get(method);
                    int stores = 0, storeAt = -1, iteratorAt = -1, hitAt = -1;
                    for (int i = 0; i < transformed.instructions.size(); i++) {
                        AbstractInsnNode instruction = transformed.instructions.get(i);
                        if (instruction instanceof VarInsnNode variable
                                && variable.getOpcode() == Opcodes.ASTORE
                                && variable.var == slot) {
                            stores++;
                            storeAt = i;
                        }
                        if (instruction instanceof MethodInsnNode call) {
                            if (call.name.equals("iterator") && iteratorAt < 0) iteratorAt = i;
                            if (call.name.equals("getHitResult") && hitAt < 0) hitAt = i;
                        }
                    }
                    check(stores == 1, "HHU candidate STORE remains unique after Arcana redirect");
                    check(
                            storeAt >= 0 && iteratorAt > storeAt && hitAt > iteratorAt,
                            "HHU filters before candidate iteration, hit side effects and"
                                + " piercing");
                    redirects++;
                }
            }
        }
        verifySoundBroadcastOrdering(nested);
        check(redirects == 2, "ordinary and piercing queries both composed");
        check(returnHook, "Arcana getHitResult return-time proximity compensation is preserved");
        System.out.println(
                "TaczArcanaCollisionContractTest: "
                        + checks
                        + " actual-JAR bytecode checks passed; full Mixin startup remains"
                        + " separate");
    }

    /** A cancellable upstream HEAD must not bypass the monitor audience supplement. */
    private static void verifySoundBroadcastOrdering(byte[] nested) throws Exception {
        ClassNode upstream = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(nested))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                if (entry.getName().equals("group/taczexpands/common/mixin/MixinSoundManager.class")) {
                    upstream = read(zip.readAllBytes());
                    break;
                }
        }
        check(upstream != null, "actual Arcana sound replacement exists");
        boolean cancelsAtHead = false;
        for (MethodNode method : upstream.methods) for (AnnotationNode annotation : annotations(method)) {
            if (!annotation.desc.endsWith("/Inject;") || !Boolean.TRUE.equals(value(annotation, "cancellable"))) continue;
            Object atValue = value(annotation, "at");
            List<?> ats = atValue instanceof List<?> list ? list : List.of(atValue);
            for (Object at : ats) if (at instanceof AnnotationNode a && "HEAD".equals(value(a, "value")))
                cancelsAtHead |= java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                        .anyMatch(i -> i instanceof MethodInsnNode call && call.name.equals("cancel")
                                && call.owner.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfo"));
        }
        check(cancelsAtHead, "fixture reproduces a real cancelling HEAD, bypassing native TAIL");
        ClassNode own;
        try (var stream = TaczArcanaCollisionContractTest.class.getClassLoader().getResourceAsStream(
                "dev/felix/hostilehumans/mixin/TaczMonitorSoundBroadcastMixin.class")) {
            check(stream != null, "compiled monitor sound mixin exists");
            own = read(stream.readAllBytes());
        }
        AnnotationNode ownMixin = own.invisibleAnnotations.stream()
                .filter(a -> a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
        AnnotationNode upstreamMixin = upstream.invisibleAnnotations.stream()
                .filter(a -> a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
        int upstreamPriority = value(upstreamMixin, "priority") instanceof Integer i ? i : 1000;
        check(value(ownMixin, "priority") instanceof Integer priority && priority > upstreamPriority,
                "monitor supplements before actual upstream cancellation");
        boolean head = false;
        for (MethodNode method : own.methods) for (AnnotationNode annotation : annotations(method)) {
            if (!annotation.desc.endsWith("/Inject;")) continue;
            check(!Boolean.TRUE.equals(value(annotation, "cancellable")), "monitor never cancels native broadcast");
            Object atValue = value(annotation, "at");
            List<?> ats = atValue instanceof List<?> list ? list : List.of(atValue);
            for (Object at : ats) if (at instanceof AnnotationNode a) head |= "HEAD".equals(value(a, "value"));
        }
        check(head, "monitor does not depend on cancelled return path");
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode n = new ClassNode();
        new ClassReader(bytes).accept(n, 0);
        return n;
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> result = new ArrayList<>();
        if (method.visibleAnnotations != null) result.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) result.addAll(method.invisibleAnnotations);
        return result;
    }

    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
}

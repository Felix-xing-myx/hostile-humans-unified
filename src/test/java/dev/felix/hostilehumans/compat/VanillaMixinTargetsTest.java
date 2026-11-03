package dev.felix.hostilehumans.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;
import org.spongepowered.asm.mixin.refmap.ReferenceMapper;

/** Checks the actual release config, refmap and selectors against SRG Minecraft, without a game. */
public final class VanillaMixinTargetsTest {
    private static int checked;
    private static String text(ZipFile jar, String name) throws Exception {
        var entry = jar.getEntry(name);
        if (entry == null) throw new AssertionError("Missing packaged resource " + name);
        try (var in = jar.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private static ClassNode node(ZipFile jar, String name) throws Exception {
        var entry = jar.getEntry(name + ".class");
        if (entry == null) throw new AssertionError("Missing runtime class " + name);
        ClassNode result = new ClassNode();
        try (var in = jar.getInputStream(entry)) { new ClassReader(in).accept(result, 0); }
        return result;
    }
    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2)
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> result = new ArrayList<>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }
    private static List<String> strings(Object value) {
        if (value instanceof String string) return List.of(string);
        if (value instanceof List<?> list) return list.stream().map(Object::toString).toList();
        return List.of();
    }
    private static boolean matches(ClassNode target, String selector) {
        var member = MemberInfo.parse(selector, null).validate();
        return target.methods.stream().anyMatch(m -> member.matches(target.name, m.name, m.desc).isExactMatch());
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("SRG Forge Minecraft jar and reobfuscated HHU jar required");
        int settlementHandlers = 0;
        try (ZipFile nativeJar = new ZipFile(args[0]); ZipFile release = new ZipFile(args[1])) {
            for (String configName : List.of("hostile_humans_unified.mixins.json", "hostile_humans_unified.world.mixins.json")) {
                JsonObject config = JsonParser.parseString(text(release, configName)).getAsJsonObject();
                String mapText = config.has("refmap") ? text(release, config.get("refmap").getAsString()) : "{}";
                ReferenceMapper mapper = ReferenceMapper.read(new StringReader(mapText), configName);
                for (String context : new String[]{null, "searge"}) {
                    mapper.setContext(context);
                    for (String group : List.of("mixins", "client", "server")) {
                        if (!config.has(group)) continue;
                        for (var entry : config.getAsJsonArray(group)) {
                            String owner = (config.get("package").getAsString() + "." + entry.getAsString()).replace('.', '/');
                            ClassNode mixin = node(release, owner);
                            AnnotationNode mix = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations).stream()
                                    .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
                            List<String> targets = new ArrayList<>(strings(value(mix, "targets")));
                            if (value(mix, "value") instanceof List<?> types)
                                for (Object type : types) targets.add(((Type) type).getInternalName());
                            for (String targetName : targets) {
                                targetName = targetName.replace('.', '/');
                                if (!targetName.startsWith("net/minecraft/")) continue;
                                ClassNode target = node(nativeJar, targetName);
                                for (var handler : mixin.methods) for (var injection : annotations(handler.visibleAnnotations, handler.invisibleAnnotations)) {
                                    if (!injection.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/")) continue;
                                    List<String> selectors = strings(value(injection, "method"));
                                    if (selectors.isEmpty() || Integer.valueOf(0).equals(value(injection, "require"))) continue;
                                    boolean remap = !Boolean.FALSE.equals(value(mix, "remap")) && !Boolean.FALSE.equals(value(injection, "remap"));
                                    boolean found = false;
                                    for (String selector : selectors)
                                        found |= matches(target, remap ? mapper.remap(owner, selector) : selector);
                                    if (!found) throw new AssertionError(configName + ": " + owner + "." + handler.name
                                            + " misses SRG " + targetName + " selectors=" + selectors + " context=" + context);
                                    checked++;
                                    if (owner.endsWith("/SettlementStructureStartMixin") || owner.endsWith("/SettlementTemplatePlacementMixin"))
                                        settlementHandlers++;
                                }
                            }
                        }
                    }
                }
            }
        }
        if (settlementHandlers != 12) throw new AssertionError("All six settlement handlers must resolve in default and searge contexts");
        System.out.println("VanillaMixinTargetsTest: " + checked + " packaged vanilla handler/context targets passed; full game startup remains separate");
    }
}

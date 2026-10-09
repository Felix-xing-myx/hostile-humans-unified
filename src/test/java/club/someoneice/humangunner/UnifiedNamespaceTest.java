package club.someoneice.humangunner;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Checks the shipped identity and initialization wiring without loading mods. */
public final class UnifiedNamespaceTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        try (var jar = new ZipFile(args[0])) {
            var metadata = text(jar, "META-INF/mods.toml");
            var mods = Pattern.compile("(?s)\\[\\[mods\\]\\]\\s*(.*?)(?=\\[\\[|\\z)").matcher(metadata);
            int identities = 0;
            while (mods.find()) {
                identities++;
                check(mods.group(1).contains("modId=\"hostile_humans_unified\""), "only unified mod ID");
                check(mods.group(1).contains("displayName=\"Hostile Humans Unified\""), "unified display name");
            }
            check(identities == 1, "one Forge mod identity");
            check(!metadata.contains("dependencies.humangunner]") && !metadata.contains("dependencies.hostile_humans]"),
                    "dependencies belong to the unified container");
            var base = read(jar, "com/craftix/hostile_humans/HostileHumans");
            var combat = read(jar, "club/someoneice/humangunner/HumanGunner");
            check(isMod(base) && !isMod(combat), "only the primary module is a Forge entrypoint");
            long calls = base.methods.stream().filter(method -> method.name.equals("<init>"))
                    .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                    .filter(instruction -> instruction instanceof MethodInsnNode call
                            && call.owner.equals("club/someoneice/humangunner/HumanGunner") && call.name.equals("<init>"))
                    .count();
            check(calls == 1, "primary entrypoint initializes the combat module exactly once");
            check(base.methods.stream().noneMatch(method -> method.name.equals("remapUnifiedTierThreeIds")),
                    "no old registry migration handler");
            for (String config : List.of("hostile_humans_unified.mixins.json", "hostile_humans_unified.world.mixins.json")) {
                var json = JsonParser.parseString(text(jar, config)).getAsJsonObject();
                if (json.has("refmap")) check(jar.getEntry(json.get("refmap").getAsString()) != null, "refmap packaged");
            }
            for (var entries = jar.entries(); entries.hasMoreElements();) {
                var name = entries.nextElement().getName();
                if (!name.startsWith("assets/") && !name.startsWith("data/") && !name.startsWith("datapacks/")) continue;
                check(!Pattern.compile("/(hostile_humans|humangunner)/").matcher(name).find(), "no legacy resource namespace: " + name);
                if (name.endsWith(".json")) check(!Pattern.compile("(?:hostile_humans|humangunner):")
                        .matcher(text(jar, name)).find(), "no legacy JSON registry reference: " + name);
            }
        }
        System.out.println("UnifiedNamespaceTest: " + checks + " identity/resource checks passed; new-world gameplay still requires acceptance");
    }
    private static boolean isMod(ClassNode node) {
        return node.visibleAnnotations != null && node.visibleAnnotations.stream()
                .anyMatch(annotation -> annotation.desc.equals("Lnet/minecraftforge/fml/common/Mod;"));
    }
    private static ClassNode read(ZipFile jar, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = jar.getInputStream(jar.getEntry(name + ".class"))) { new ClassReader(stream).accept(node, 0); }
        return node;
    }
    private static String text(ZipFile jar, String name) throws Exception {
        var entry = jar.getEntry(name);
        if (entry == null) throw new AssertionError("Missing packaged resource " + name);
        try (var stream = jar.getInputStream(entry)) { return new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

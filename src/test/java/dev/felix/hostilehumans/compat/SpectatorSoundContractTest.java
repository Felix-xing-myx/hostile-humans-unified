package dev.felix.hostilehumans.compat;

import dev.felix.hostilehumans.compat.SpectatorSoundContract;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Read real upstream jars without loading Minecraft clients, native audio or optional classes. */
public final class SpectatorSoundContractTest {
    private static final List<String> TARGETS = List.of("com/tacz/guns/client/sound/GunSoundInstance",
            SpectatorSoundContract.ANIMATION, "top/ribs/scguns/client/audio/GunShotSound", "ttv/migami/jeg/client/audio/GunShotSound");

    public static void main(String[] args) throws Exception {
        int checked = 0;
        for (String path : args) try (ZipFile jar = new ZipFile(path)) {
            var playback = jar.getEntry("com/tacz/guns/client/sound/SoundPlayManager.class");
            if (playback != null) {
                ClassNode manager = new ClassNode();
                new ClassReader(jar.getInputStream(playback)).accept(manager, 0);
                require(SpectatorSoundContract.supportsShotPlayback(manager), "native message/playback public contract: " + path);
                var method = manager.methods.stream().filter(m -> m.name.equals("playMessageSound")).findFirst().orElseThrow();
                method.desc = "()V";
                require(!SpectatorSoundContract.supportsShotPlayback(manager), "changed message contract must retain native playback");
            }
            for (String target : TARGETS) {
                var entry = jar.getEntry(target + ".class");
                if (entry == null) continue;
                ClassNode node = new ClassNode();
                new ClassReader(jar.getInputStream(entry)).accept(node, 0);
                boolean animation = target.equals(SpectatorSoundContract.ANIMATION);
                if (!animation) verifyConstructorSelector(node);
                require(SpectatorSoundContract.supports(node, animation), "native contract: " + path + " / " + target);
                MethodNode body = node.methods.stream().filter(m -> animation
                        ? (m.name + m.desc).equals(SpectatorSoundContract.ANIMATION_METHOD) : m.name.equals("<init>"))
                        .filter(m -> java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false)
                                .anyMatch(i -> i instanceof MethodInsnNode call && call.owner.equals(dev.felix.hostilehumans.testutil.ModuleTestSources.owner("java/lang/Math"))
                                        && call.name.equals("sqrt")))
                        .findFirst().orElseThrow();
                MethodInsnNode sqrt = null;
                for (var instruction : body.instructions) if (instruction instanceof MethodInsnNode call
                        && call.owner.equals(dev.felix.hostilehumans.testutil.ModuleTestSources.owner("java/lang/Math")) && call.name.equals("sqrt")) sqrt = call;
                require(sqrt != null, "one distance root exists");
                sqrt.name = "cos";
                require(!SpectatorSoundContract.supports(node, animation), "changed calculation must fail closed");
                sqrt.name = "sqrt";
                body.instructions.add(new MethodInsnNode(sqrt.getOpcode(), sqrt.owner, sqrt.name, sqrt.desc, false));
                require(!SpectatorSoundContract.supports(node, animation), "ambiguous injection must fail closed");
                checked++;
            }
        }
        require(checked >= 4, "provide TaCZ, Scorched Guns and JEG jars");
        System.out.println("SpectatorSoundContractTest: " + checked + " upstream methods and incompatible-body guards passed");
    }

    /** Exercise Mixin's own selector quantifiers, including the delegating 1.1.8 overload. */
    private static void verifyConstructorSelector(ClassNode target) throws Exception {
        ClassNode mixin = new ClassNode();
        try (var stream = SpectatorSoundContractTest.class.getClassLoader().getResourceAsStream(
                "dev/felix/hostilehumans/mixin/SpectatorGunSoundDistanceMixin.class")) {
            if (stream == null) throw new AssertionError("Missing compiled sound mixin");
            new ClassReader(stream).accept(mixin, 0);
        }
        var handler = mixin.methods.stream().filter(m -> m.name.equals("hostileHumans$listenerDistance"))
                .findFirst().orElseThrow();
        var annotation = handler.visibleAnnotations.stream()
                .filter(a -> a.desc.endsWith("/ModifyArg;")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        var selectors = (List<String>) annotation.values.get(annotation.values.indexOf("method") + 1);
        int calls = 0;
        for (String text : selectors) calls += selectedDistanceCalls(target, text);
        require(calls == 1, "actual Mixin selector reaches exactly one distance call: " + target.name);
        if (target.name.equals("com/tacz/guns/client/sound/GunSoundInstance")
                && target.methods.stream().filter(m -> m.name.equals("<init>")).count() > 1) {
            require(selectedDistanceCalls(target, "<init>") == 0,
                    "regression fixture reproduces old selector missing the delegated body");
        }
    }

    private static int selectedDistanceCalls(ClassNode target, String text) {
        var selector = org.spongepowered.asm.mixin.injection.struct.MemberInfo.parse(text, null)
                .configure(org.spongepowered.asm.mixin.injection.selectors.ITargetSelector.Configure.SELECT_MEMBER);
        int selected = 0, calls = 0;
        for (MethodNode method : target.methods) {
            if (!selector.match(org.spongepowered.asm.mixin.injection.selectors.ElementNode.of(target, method)).isExactMatch()) continue;
            if (selected++ >= selector.getMaxMatchCount()) break;
            for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("java/lang/Math") && call.name.equals("sqrt") && call.desc.equals("(D)D")) calls++;
        }
        return calls;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

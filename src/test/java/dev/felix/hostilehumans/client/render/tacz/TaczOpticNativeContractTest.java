package dev.felix.hostilehumans.client.render.tacz;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Audits the exact native optic seams against both real upstream jars without loading the game. */
public final class TaczOpticNativeContractTest {
    private static ClassNode read(ZipFile jar, String name) throws Exception {
        try (var in = jar.getInputStream(jar.getEntry("com/tacz/guns/" + name + ".class"))) {
            var node = new ClassNode(); new ClassReader(in).accept(node, 0); return node;
        }
    }
    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing seam " + node.name + "." + name + descriptor));
    }
    private static int calls(MethodNode node, String owner, String name, String descriptor) {
        int count = 0;
        for (var ins : node.instructions) if (ins instanceof MethodInsnNode call && call.owner.equals(owner)
                && call.name.equals(name) && call.desc.equals(descriptor)) count++;
        return count;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static boolean resolves(ZipFile jar, String owner, String name, String descriptor, boolean field, Set<String> seen) throws Exception {
        if (owner == null || !seen.add(owner)) return false;
        ClassNode type;
        if (owner.startsWith("com/tacz/guns/")) type = read(jar, owner.substring("com/tacz/guns/".length()));
        else {
            try (var in = TaczOpticNativeContractTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
                if (in == null) return false;
                type = new ClassNode(); new ClassReader(in).accept(type, 0);
            }
        }
        if (field ? type.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(descriptor) && (f.access & Opcodes.ACC_PUBLIC) != 0)
                : type.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor) && (m.access & Opcodes.ACC_PUBLIC) != 0)) return true;
        if (name.equals("<init>")) return false;
        if (resolves(jar, type.superName, name, descriptor, field, seen)) return true;
        for (String parent : type.interfaces) if (resolves(jar, parent, name, descriptor, field, seen)) return true;
        return false;
    }
    private static void compiledReferences(ZipFile jar, Path root) throws Exception {
        var cameraMixin = new ClassNode();
        new ClassReader(Files.readAllBytes(root.resolve("dev/felix/hostilehumans/mixin/TaczScopeCameraMixin.class"))).accept(cameraMixin, 0);
        var guard = method(cameraMixin, "hostileHumans$cameraFov", "(Lnet/minecraftforge/client/event/ViewportEvent$ComputeFov;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        check(calls(guard, "dev/felix/hostilehumans/client/render/HumanCameraView", "attached",
                "(Ldev/felix/hostilehumans/entity/Human;)Z") == 1, "Native FOV isolation must cover every authorized NPC camera");
        check(calls(guard, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "cancel", "()V") == 1,
                "Native player FOV must be cancelled for an attached NPC camera");
        for (var ins : guard.instructions) if (ins instanceof MethodInsnNode call)
            check(!call.name.equals("ownsCameraFov") && !call.name.equals("isUsingItem") && !call.name.equals("getMainHandItem"),
                    "Native player FOV isolation must not depend on NPC gun/medicine state");
        int checked = 0;
        try (var paths = Files.walk(root.resolve("dev/felix/hostilehumans/client/render/tacz"))) {
            for (var path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                var node = new ClassNode(); new ClassReader(Files.readAllBytes(path)).accept(node, 0);
                for (var method : node.methods) for (var ins : method.instructions) {
                    String owner = null, name = null, descriptor = null; boolean field = false;
                    if (ins instanceof MethodInsnNode call) { owner = call.owner; name = call.name; descriptor = call.desc; }
                    else if (ins instanceof FieldInsnNode access) { owner = access.owner; name = access.name; descriptor = access.desc; field = true; }
                    if (owner != null && owner.startsWith("com/tacz/guns/")) {
                        check(resolves(jar, owner, name, descriptor, field, new HashSet<>()), "Native public ABI changed: " + owner + "." + name + descriptor);
                        checked++;
                    }
                }
            }
        }
        check(checked >= 20, "Compiled native adapter references were not inspected");
        System.out.println("Resolved " + checked + " compiled native adapter references");
    }
    public static void main(String[] args) throws Exception {
        check(args.length >= 2, "Both runtime and compile dependency jars are required");
        for (String arg : args) try (var jar = new ZipFile(Path.of(arg).toFile())) {
            var model = read(jar, "client/model/BedrockAttachmentModel");
            var ocular = method(model, "renderOcularAndDivision", "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIZ)V");
            check(calls(ocular, "com/tacz/guns/api/client/gameplay/IClientPlayerGunOperator", "getClientAimingProgress", "(F)F") == 1,
                    "Expected exactly one native ocular aiming read");
            int playerAimReads = 0;
            for (var m : model.methods) playerAimReads += calls(m, "com/tacz/guns/api/client/gameplay/IClientPlayerGunOperator", "getClientAimingProgress", "(F)F");
            check(playerAimReads == 1, "Another native ocular aiming read requires isolation");
            var index = read(jar, "client/resource/index/ClientAttachmentIndex");
            method(index, "getAttachmentModel", "()Lcom/tacz/guns/client/model/BedrockAttachmentModel;");
            var factory = method(index, "getOrLoadAttachmentModel", "(Lnet/minecraft/resources/ResourceLocation;)Lcom/tacz/guns/client/model/BedrockAttachmentModel;");
            check(calls(factory, "com/tacz/guns/client/resource/index/ClientAttachmentIndex", "getAttachmentModel",
                    "(Lcom/tacz/guns/client/resource/pojo/model/BedrockModelPOJO;)Lcom/tacz/guns/client/model/BedrockAttachmentModel;") == 1, "Owned factory delegation changed");
            var constructor = method(index, "getAttachmentModel", "(Lcom/tacz/guns/client/resource/pojo/model/BedrockModelPOJO;)Lcom/tacz/guns/client/model/BedrockAttachmentModel;");
            int allocations = 0;
            for (var ins : constructor.instructions) if (ins instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW
                    && type.desc.equals("com/tacz/guns/client/model/BedrockAttachmentModel")) allocations++;
            check(allocations == 2, "Factory must construct owned models for both formats");
            var camera = read(jar, "client/event/CameraSetupEvent");
            for (String name : new String[]{"applyScopeMagnification", "applyGunModelFovModifying"}) {
                var callback = method(camera, name, "(Lnet/minecraftforge/client/event/ViewportEvent$ComputeFov;)V");
                check((callback.access & Opcodes.ACC_STATIC) != 0, "Native FOV handler must be static");
            }
            method(read(jar, "compat/ar/ARCompat"), "shouldAccelerate", "()Z");
            method(read(jar, "resource/index/CommonAttachmentIndex"), "getPojo", "()Lcom/tacz/guns/resource/pojo/AttachmentIndexPOJO;");
            method(model, "getScopeViewPath", "(I)Ljava/util/List;");
            String compiled = System.getProperty("hhu.opticsClasses");
            if (compiled != null) compiledReferences(jar, Path.of(compiled));
            System.out.println("TaczOpticNativeContractTest passed: " + Path.of(arg).getFileName());
        }
    }
}

package dev.felix.hostilehumans.client.render.tacz;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Checks the real Minecraft cache contract and compiled cleanup order without an OpenGL context. */
public final class TaczOpticShaderStateTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static ClassNode read(ZipFile jar, String name) throws Exception {
        try (var in = jar.getInputStream(jar.getEntry(name + ".class"))) {
            var node = new ClassNode(); new ClassReader(in).accept(node, 0); return node;
        }
    }
    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst().orElseThrow();
    }
    private static AbstractInsnNode previous(AbstractInsnNode node) {
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        return node;
    }
    private static boolean call(AbstractInsnNode ins, String owner, String name) {
        return ins instanceof MethodInsnNode method && method.owner.equals(owner) && method.name.equals(name);
    }
    public static void main(String[] args) throws Exception {
        check(args.length == 2, "compiled main classes root and mapped Minecraft 1.20.1 jar required");
        String shader = "net/minecraft/client/renderer/ShaderInstance";
        String programs = "com/mojang/blaze3d/shaders/ProgramManager";
        String states = "com/mojang/blaze3d/platform/GlStateManager";
        try (var mc = new ZipFile(Path.of(args[1]).toFile())) {
            var type = read(mc, shader);
            var clear = method(type, "clear", "()V");
            boolean cacheInvalid = false, appliedInvalid = false;
            for (var ins : clear.instructions) if (ins instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTSTATIC && field.owner.equals(shader)) {
                if (field.name.equals("lastProgramId")) cacheInvalid = previous(ins).getOpcode() == Opcodes.ICONST_M1;
                if (field.name.equals("lastAppliedShader")) appliedInvalid = previous(ins).getOpcode() == Opcodes.ACONST_NULL;
            }
            check(cacheInvalid && appliedInvalid, "ShaderInstance.clear must invalidate both program caches");
            boolean cachedApply = false;
            for (var ins : method(type, "apply", "()V").instructions)
                if (ins instanceof FieldInsnNode field && field.owner.equals(shader) && field.name.equals("lastProgramId") && field.getOpcode() == Opcodes.GETSTATIC) cachedApply = true;
            check(cachedApply, "Expected ShaderInstance.apply program cache");
            boolean delegates = false, rawBind = false;
            for (var ins : method(read(mc, programs), "glUseProgram", "(I)V").instructions) if (call(ins, states, "_glUseProgram")) delegates = true;
            for (var ins : method(read(mc, states), "_glUseProgram", "(I)V").instructions) if (call(ins, "org/lwjgl/opengl/GL20", "glUseProgram")) rawBind = true;
            check(delegates && rawBind, "Native public program binding chain changed");
        }
        var guard = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(args[0]).resolve("dev/felix/hostilehumans/client/render/tacz/TaczOpticGraphicsState.class"))).accept(guard, 0);
        var restore = method(guard, "restoreShader", "()V");
        boolean invalidated = false, restoredReference = false, restoredProgram = false, restoredTextures = false;
        for (var ins : restore.instructions) {
            check(!call(ins, shader, "apply"), "Restoring a caller must not upload unknown shared shader uniforms");
            check(!call(ins, "org/lwjgl/opengl/GL20", "glUseProgram"), "Use audited public native program binding after invalidation");
            if (call(ins, shader, "clear")) invalidated = true;
            if (call(ins, "com/mojang/blaze3d/systems/RenderSystem", "setShader")) restoredReference = true;
            if (call(ins, programs, "glUseProgram")) {
                check(invalidated && restoredReference, "Restore program only after cache invalidation and shader reference restoration");
                check(previous(ins) instanceof FieldInsnNode field && field.name.equals("program"), "Restore the actual saved GL program");
                restoredProgram = true;
            }
            if (call(ins, states, "_bindTexture")) restoredTextures = true;
        }
        check(restoredProgram && restoredTextures, "Restore shader program and bindings invalidated by clear");
        check(!restore.tryCatchBlocks.isEmpty() && !method(guard, "close", "()V").tryCatchBlocks.isEmpty(), "Shader and mask cleanup need failure-safe finally blocks");
        System.out.println("TaczOpticShaderStateTest passed: native cache invalidation, caller program/reference and texture cleanup");
    }
}

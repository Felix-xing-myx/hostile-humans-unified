package dev.felix.hostilehumans.client.render;

import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Verifies the upstream hand projection seam and the packaged invoker without a graphics context. */
public final class HumanSpectatorProjectionContractTest {
    private static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
    private static ClassNode read(byte[] bytes){var node=new ClassNode();new ClassReader(bytes).accept(node,0);return node;}
    private static ClassNode own(String name) throws Exception {
        try(var in=HumanSpectatorProjectionContractTest.class.getClassLoader().getResourceAsStream(name+".class")){return read(in.readAllBytes());}
    }
    public static void main(String[] args) throws Exception {
        check(args.length>=1,"mapped Minecraft jar required");
        try(var jar=new ZipFile(Path.of(args[0]).toFile())){
            var renderer=read(jar.getInputStream(jar.getEntry("net/minecraft/client/renderer/GameRenderer.class")).readAllBytes());
            check(renderer.methods.stream().anyMatch(m->m.name.equals("getFov")&&m.desc.equals("(Lnet/minecraft/client/Camera;FZ)D")&&(m.access&Opcodes.ACC_PRIVATE)!=0),"vanilla hand-FOV private seam matches");
            check(renderer.methods.stream().anyMatch(m->m.name.equals("getProjectionMatrix")&&m.desc.equals("(D)Lorg/joml/Matrix4f;")),"native projection factory remains available");
            check(renderer.methods.stream().anyMatch(m->m.name.equals("bobView")&&m.desc.equals("(Lcom/mojang/blaze3d/vertex/PoseStack;F)V")
                    &&(m.access&Opcodes.ACC_PRIVATE)!=0),"vanilla bob seam matches the client-only extension");
        }
        var bob=own("dev/felix/hostilehumans/mixin/HumanSpectatorBobMixin");
        var bobMethod=bob.methods.stream().filter(m->m.name.equals("hostileHumans$humanBob")).findFirst().orElseThrow();
        check(bobMethod.visibleAnnotations.stream().anyMatch(a->a.values.toString().contains("m_109138_(Lcom/mojang/blaze3d/vertex/PoseStack;F)V")),
                "bob extension targets verified production SRG descriptor");
        var access=own("dev/felix/hostilehumans/mixin/HumanSpectatorProjectionAccess");
        var method=access.methods.stream().filter(m->m.name.equals("hostileHumans$handFov")).findFirst().orElseThrow();
        check(method.visibleAnnotations.stream().anyMatch(a->a.values.contains("m_109141_")),"production invoker uses the exact SRG name");
        var scope=own("dev/felix/hostilehumans/client/render/HumanSpectatorProjection");
        var close=scope.methods.stream().filter(m->m.name.equals("close")).findFirst().orElseThrow();
        check(close.tryCatchBlocks.stream().anyMatch(b->b.type==null),"projection restore is protected by a finally region");
        boolean restores=false,clearsDepth=false;
        for(var m:scope.methods)for(var ins:m.instructions)if(ins instanceof MethodInsnNode call){
            if(m.name.equals("close")&&call.owner.endsWith("RenderSystem")&&call.name.equals("setProjectionMatrix"))restores=true;
            if(call.owner.endsWith("RenderSystem")&&call.name.equals("clear"))clearsDepth=true;
        }
        check(restores&&!clearsDepth,"hand projection is restored without clearing world depth");
        System.out.println("HumanSpectatorProjectionContractTest passed");
    }
}

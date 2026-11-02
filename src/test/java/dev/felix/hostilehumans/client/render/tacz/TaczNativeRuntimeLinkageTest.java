package dev.felix.hostilehumans.client.render.tacz;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Resolves exact external JVM descriptors, including lambdas, without loading TaCZ or launching Minecraft. */
public final class TaczNativeRuntimeLinkageTest {
    private static final String PREFIX="com/tacz/";
    private static final Map<String,ClassNode> classes=new HashMap<>();
    private static ZipFile jar;
    private static int checked;
    private static ClassNode type(String name) throws Exception {
        if(classes.containsKey(name))return classes.get(name);
        ClassNode node=null;
        var entry=jar.getEntry(name+".class");
        if(entry!=null)try(var in=jar.getInputStream(entry)){node=new ClassNode();new ClassReader(in).accept(node,0);}
        else if(!name.startsWith(PREFIX))try(var in=TaczNativeRuntimeLinkageTest.class.getClassLoader().getResourceAsStream(name+".class")){
            if(in!=null){node=new ClassNode();new ClassReader(in).accept(node,0);}
        }
        classes.put(name,node);return node;
    }
    private static boolean resolves(String owner,String name,String desc,boolean field,Set<String> seen) throws Exception {
        if(owner==null||!seen.add(owner))return false;
        ClassNode node=type(owner);if(node==null)return false;
        if(field?node.fields.stream().anyMatch(f->f.name.equals(name)&&f.desc.equals(desc)&&(f.access&Opcodes.ACC_PUBLIC)!=0)
                :node.methods.stream().anyMatch(m->m.name.equals(name)&&m.desc.equals(desc)&&(m.access&Opcodes.ACC_PUBLIC)!=0))return true;
        if(name.equals("<init>"))return false;
        if(resolves(node.superName,name,desc,field,seen))return true;
        for(String parent:node.interfaces)if(resolves(parent,name,desc,field,seen))return true;
        return false;
    }
    private static void checkReference(String site,String owner,String name,String desc,boolean field,List<String> failures) throws Exception {
        if(!owner.startsWith(PREFIX))return;
        checked++;
        if(!resolves(owner,name,desc,field,new HashSet<>()))failures.add(site+" -> "+owner+"."+name+desc);
    }
    private static void handle(String site,Handle handle,List<String> failures) throws Exception {
        checkReference(site,handle.getOwner(),handle.getName(),handle.getDesc(),handle.getTag()<=Opcodes.H_PUTSTATIC,failures);
    }
    private static Object annotationValue(AnnotationNode annotation, String key, Object fallback) {
        if (annotation.values != null) for (int i=0;i<annotation.values.size();i+=2)
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i+1);
        return fallback;
    }
    /** Check the real installed input seams too; optional names differ between 1.1.7 and 1.1.8. */
    private static void inputMixin(Path root, String mixin, String target) throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(root.resolve("dev/felix/hostilehumans/mixin/"+mixin+".class"))).accept(node,0);
        var upstream = type("com/tacz/guns/client/input/"+target);
        if (upstream == null) throw new AssertionError("Missing native input class: "+target);
        int matches=0;
        for (var handler : node.methods) {
            var annotations = new ArrayList<AnnotationNode>();
            if(handler.visibleAnnotations!=null) annotations.addAll(handler.visibleAnnotations);
            if(handler.invisibleAnnotations!=null) annotations.addAll(handler.invisibleAnnotations);
            for(var annotation:annotations) if(annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) {
                @SuppressWarnings("unchecked") var selectors=(List<String>)annotationValue(annotation,"method",List.of());
                int found=0;
                for(String selector:selectors) for(var method:upstream.methods) if(method.name.equals(selector)) {
                    boolean returnsBoolean=Type.getReturnType(method.desc).equals(Type.BOOLEAN_TYPE);
                    if((method.access&Opcodes.ACC_STATIC)==0 || (handler.access&Opcodes.ACC_STATIC)==0
                            || returnsBoolean != handler.desc.contains("CallbackInfoReturnable"))
                        throw new AssertionError("Incompatible native input callback: "+target+"."+selector);
                    found++;
                }
                if(found < (Integer)annotationValue(annotation,"require",1))
                    throw new AssertionError("Required native input seam missing: "+target+selectors);
                if(!Boolean.TRUE.equals(annotationValue(annotation,"cancellable",false)))
                    throw new AssertionError("Real player input must be cancellable");
                matches+=found;
            }
        }
        if(matches<2)throw new AssertionError("Input mixin was not inspected: "+mixin);
    }
    public static void main(String[] args) throws Exception {
        if(args.length<2)throw new AssertionError("compiled classes root and at least one actual TaCZ jar are required");
        var root=Path.of(args[0]);
        for(int a=1;a<args.length;a++){
            classes.clear();checked=0;List<String> failures=new ArrayList<>();
            try(var opened=new ZipFile(Path.of(args[a]).toFile());var files=Files.walk(root)){
                jar=opened;
                inputMixin(root,"TaczMonitorShootInputMixin","ShootKey");
                inputMixin(root,"TaczMonitorAimInputMixin","AimKey");
                inputMixin(root,"TaczMonitorReloadInputMixin","ReloadKey");
                var bobOwner=type("com/tacz/guns/client/event/FirstPersonRenderGunEvent");
                var bob=bobOwner.methods.stream().filter(m->m.name.equals("cancelItemInHandViewBobbing")
                        &&m.desc.equals("(Lcom/tacz/guns/api/client/event/RenderItemInHandBobEvent$BobView;)V")).findFirst().orElseThrow();
                boolean cancels=false;
                for(var ins:bob.instructions)if(ins instanceof MethodInsnNode call && call.name.equals("setCanceled")
                        &&call.desc.equals("(Z)V"))cancels=true;
                if(!cancels)throw new AssertionError("Native player gun hand bob policy changed");
                var soundOwner=type("com/tacz/guns/sound/SoundManager");
                String soundDescriptor="(Lnet/minecraft/world/entity/LivingEntity;ILnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;FF)V";
                if(soundOwner==null||soundOwner.methods.stream().noneMatch(m->m.name.equals("sendSoundToNearby")&&m.desc.equals(soundDescriptor)
                        &&(m.access&Opcodes.ACC_STATIC)!=0))throw new AssertionError("Native sound broadcast seam changed");
                for(Path file:files.filter(f->f.toString().endsWith(".class")).toList()){
                    String relative=root.relativize(file).toString().replace('\\','/');
                    boolean nativeModule=(relative.startsWith("dev/felix/hostilehumans/client/render/tacz/") || relative.startsWith("dev/felix/hostilehumans/client/ui/gun/TaczMonitorHud"));
                    boolean clockOrAudio=relative.matches("dev/felix/hostilehumans/compat/tacz/(TaczHumanReloadTransactions|TaczReloadVisualTimeline|TaczReloadSoundClient|TaczMonitorAudio|TaczMonitorWeapons|TaczMonitorInput)(\\$.*)?\\.class");
                    if(!nativeModule&&!clockOrAudio)continue;
                    ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(file)).accept(node,0);
                    for(MethodNode method:node.methods)for(var instruction:method.instructions){
                        String site=node.name+"."+method.name;
                        if(instruction instanceof MethodInsnNode call){
                            if(node.name.endsWith("TaczNativePresentation$Observation")
                                    &&call.owner.equals("dev/felix/hostilehumans/client/render/HumanSpectatorMotion")&&call.name.equals("bob"))
                                failures.add(site+" duplicates vanilla hand bob on authored native gun gait");
                            checkReference(site,call.owner,call.name,call.desc,false,failures);
                        }
                        else if(instruction instanceof FieldInsnNode field)checkReference(site,field.owner,field.name,field.desc,true,failures);
                        else if(instruction instanceof InvokeDynamicInsnNode dynamic){
                            handle(site,dynamic.bsm,failures);
                            for(Object argument:dynamic.bsmArgs)if(argument instanceof Handle h)handle(site,h,failures);
                        }
                    }
                }
            }
            if(!failures.isEmpty())throw new AssertionError("Runtime ABI mismatch in "+Path.of(args[a]).getFileName()+":\n"+String.join("\n",failures));
            if(checked<50)throw new AssertionError("Native adapter references were not inspected");
            System.out.println("TaczNativeRuntimeLinkageTest: "+checked+" exact references resolved against "+Path.of(args[a]).getFileName());
        }
    }
}

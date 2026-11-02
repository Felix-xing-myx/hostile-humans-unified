package dev.felix.hostilehumans.common;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.zip.ZipFile;

/** Read actual runtime class bytes without loading Minecraft classes; an explicit Forge jar is optional. */
public final class DamagePipelineContractTest {
    public static void main(String[] args) throws Exception {
        try (var jar = args.length > 0 ? new ZipFile(args[0]) : null) {
            for (String owner : new String[]{"net/minecraft/world/entity/LivingEntity", "net/minecraft/world/entity/player/Player"}) {
                ClassNode node = read(jar, owner);
                MethodNode actual = method(node, "actuallyHurt", "m_6475_");
                int hurt = callPosition(actual,"onLivingHurt"), armor = callPosition(actual,"getDamageAfterArmorAbsorb","m_21161_"),
                        magic = callPosition(actual,"getDamageAfterMagicAbsorb","m_6515_"), damage = callPosition(actual,"onLivingDamage");
                check(hurt >= 0 && armor > hurt && magic > armor && damage > magic, owner+" Forge order");
                int armorCalls = 0;
                for (var instruction : actual.instructions) if (instruction instanceof MethodInsnNode call
                        && (call.name.equals("getDamageAfterArmorAbsorb") || call.name.equals("m_21161_"))) {
                    check(call.owner.equals(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(owner)),owner+" redirect owner"); armorCalls++;
                }
                check(armorCalls==1, owner+" exact armor call count");
            }
            var living = read(jar,"net/minecraft/world/entity/LivingEntity");
            check(callPosition(method(living,"hurt","m_6469_"),"onShieldBlock")>=0,"Forge shield hook");
            var player = read(jar,"net/minecraft/world/entity/player/Player");
            check(player.superName.equals(living.name), "Player mixin must extend the actual LivingEntity parent");
            check(living.methods.stream().anyMatch(m -> m.name.equals("getDamageAfterArmorAbsorb") || m.name.equals("m_21161_")),
                    "armor implementation is declared by LivingEntity");
            check(player.methods.stream().noneMatch(m -> m.name.equals("getDamageAfterArmorAbsorb") || m.name.equals("m_21161_")),
                    "Player inherits armor: a declared Player shadow would fail preprocessing");
            var itemStack = read(jar, "net/minecraft/world/item/ItemStack");
            var wear = itemStack.methods.stream().filter(m -> m.desc.equals(
                    "(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V")).findFirst().orElseThrow();
            int wearCalls = 0;
            for (var instruction : wear.instructions) if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(itemStack.name)) && call.desc.equals(
                    "(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z")) wearCalls++;
            check(wearCalls == 1, "wild armor limit redirects exactly one native hurt call inside hurtAndBreak");
            checkWildArmorSelector(wear.desc);
        }
        System.out.println("DamagePipelineContractTest passed");
    }
    private static void checkWildArmorSelector(String wearDescriptor) throws Exception {
        var mixin = read(null, "dev/felix/hostilehumans/mixin/ItemStackWildArmorWearMixin");
        int redirects = 0;
        for (var method : mixin.methods) {
            var annotations = new java.util.ArrayList<org.objectweb.asm.tree.AnnotationNode>();
            if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
            if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
            for (var annotation : annotations) if (annotation.desc.endsWith("/Redirect;")) {
                var values = new java.util.HashMap<String, Object>();
                for (int i = 0; i < annotation.values.size(); i += 2)
                    values.put((String) annotation.values.get(i), annotation.values.get(i + 1));
                check(((java.util.List<?>) values.get("method")).contains("m_41622_" + wearDescriptor), "production armor method selector");
                check(Boolean.FALSE.equals(values.get("remap")), "armor SRG seam is explicit");
                check(Integer.valueOf(1).equals(values.get("require")) && Integer.valueOf(1).equals(values.get("allow")), "exactly one required armor redirect");
                var at = (org.objectweb.asm.tree.AnnotationNode) values.get("at");
                String target = null;
                for (int i = 0; i < at.values.size(); i += 2)
                    if (at.values.get(i).equals("target")) target = (String) at.values.get(i + 1);
                check(("Lnet/minecraft/world/item/ItemStack;m_220157_"
                        + "(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z").equals(target), "production armor invocation selector");
                redirects++;
            }
        }
        check(redirects == 1, "one armor wear seam");
    }
    private static ClassNode read(ZipFile jar,String name) throws Exception {
        var node = new ClassNode();
        String resource = name+".class";
        try (var stream = jar == null
                ? DamagePipelineContractTest.class.getClassLoader().getResourceAsStream(resource)
                : jar.getInputStream(java.util.Objects.requireNonNull(jar.getEntry(resource), resource))) {
            if (stream == null) throw new AssertionError("Missing runtime class resource: " + resource);
            new ClassReader(stream).accept(node,0);
        }
        return node;
    }
    private static MethodNode method(ClassNode node,String named,String srg) {
        return node.methods.stream().filter(m->m.name.equals(named)||m.name.equals(srg)).findFirst().orElseThrow();
    }
    private static int callPosition(MethodNode method,String... names) {
        int result=-1, index=0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) for (String name:names) if(call.name.equals(name)) result=index;
            index++;
        }
        return result;
    }
    private static void check(boolean valid,String label) { if(!valid)throw new AssertionError(label); }
}

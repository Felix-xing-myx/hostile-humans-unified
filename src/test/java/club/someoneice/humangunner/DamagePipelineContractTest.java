package club.someoneice.humangunner;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.zip.ZipFile;

/** Check the real Forge jar, including Player's separate override. Input: mapped or SRG Forge jar. */
public final class DamagePipelineContractTest {
    public static void main(String[] args) throws Exception {
        try (var jar = new ZipFile(args[0])) {
            for (String owner : new String[]{"net/minecraft/world/entity/LivingEntity", "net/minecraft/world/entity/player/Player"}) {
                ClassNode node = read(jar, owner);
                MethodNode actual = method(node, "actuallyHurt", "m_6475_");
                int hurt = callPosition(actual,"onLivingHurt"), armor = callPosition(actual,"getDamageAfterArmorAbsorb","m_21161_"),
                        magic = callPosition(actual,"getDamageAfterMagicAbsorb","m_6515_"), damage = callPosition(actual,"onLivingDamage");
                check(hurt >= 0 && armor > hurt && magic > armor && damage > magic, owner+" Forge order");
                int armorCalls = 0;
                for (var instruction : actual.instructions) if (instruction instanceof MethodInsnNode call
                        && (call.name.equals("getDamageAfterArmorAbsorb") || call.name.equals("m_21161_"))) {
                    check(call.owner.equals(owner),owner+" redirect owner"); armorCalls++;
                }
                check(armorCalls==1, owner+" exact armor call count");
            }
            var living = read(jar,"net/minecraft/world/entity/LivingEntity");
            check(callPosition(method(living,"hurt","m_6469_"),"onShieldBlock")>=0,"Forge shield hook");
        }
        System.out.println("DamagePipelineContractTest passed");
    }
    private static ClassNode read(ZipFile jar,String name) throws Exception {
        var node = new ClassNode();
        try (var stream=jar.getInputStream(jar.getEntry(name+".class"))) { new ClassReader(stream).accept(node,0); }
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

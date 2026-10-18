package club.someoneice.humangunner;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.UUID;

/** Real AttributeInstance lifecycle: idempotence, equipment changes, caps and transient save/load. */
public final class DefenseAttributesTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        // Standalone JavaExec lacks Forge's event-bus transformation; skip the network hook.
        var bootstrapFlag=Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapFlag.setAccessible(true);
        bootstrapFlag.setBoolean(null,true);
        BuiltInRegistries.bootStrap();
        var type=Attributes.ARMOR;
        var armor=new AttributeInstance(type,ignored->{});
        UUID gear=UUID.randomUUID(),potion=UUID.randomUUID(),external=UUID.randomUUID();
        armor.addPermanentModifier(new AttributeModifier(gear,"armor gear",10,AttributeModifier.Operation.ADDITION));
        armor.addPermanentModifier(new AttributeModifier(potion,"foreign base scale",.5,AttributeModifier.Operation.MULTIPLY_BASE));
        armor.addPermanentModifier(new AttributeModifier(external,"foreign total scale",.2,AttributeModifier.Operation.MULTIPLY_TOTAL));
        DefenseAttributes.apply(armor,DefenseAttributes.ARMOR,1.4);
        close(armor.getValue(),25.2,"equipped armor benefits from specialty");
        var owned=armor.getModifier(DefenseAttributes.ARMOR);
        DefenseAttributes.apply(armor,DefenseAttributes.ARMOR,1.4);
        check(owned==armor.getModifier(DefenseAttributes.ARMOR)&&armor.getModifiers().size()==4,"same apply neither stacks nor replaces unchanged transient");
        close(DefenseAttributes.rawWithoutOwned(armor,DefenseAttributes.ARMOR),18,"read-only baseline excludes own factor");
        check(owned==armor.getModifier(DefenseAttributes.ARMOR),"snapshot reads do not mutate modifier state");
        armor.removeModifier(gear);
        armor.addPermanentModifier(new AttributeModifier(gear,"heavy armor gear",20,AttributeModifier.Operation.ADDITION));
        close(armor.getValue(),30,"equipment change updates total and final attribute cap");
        DefenseAttributes.apply(armor,DefenseAttributes.ARMOR,.5);
        close(armor.getValue(),18,"heavy raw total is scaled before final sanitize");
        var saved=armor.save();
        var loaded=new AttributeInstance(type,ignored->{}); loaded.load(saved);
        check(loaded.getModifier(DefenseAttributes.ARMOR)==null,"owned transient is absent from saved attributes");
        check(loaded.getModifier(external)!=null,"foreign permanent modifier survives save/load");
        DefenseAttributes.apply(loaded,DefenseAttributes.ARMOR,.5);
        DefenseAttributes.apply(loaded,DefenseAttributes.ARMOR,.5);
        close(loaded.getValue(),18,"reload rehydrates allocation exactly once");
        DefenseAttributes.apply(loaded,DefenseAttributes.ARMOR,1);
        check(loaded.getModifier(DefenseAttributes.ARMOR)==null&&loaded.getModifiers().size()==3,"disabling ordinary specialty removes only owned modifier");
        close(loaded.getValue(),30,"disabling keeps all equipment and foreign modifiers");
        loaded.addPermanentModifier(new AttributeModifier(DefenseAttributes.ARMOR,"stale persisted owned copy",.5,AttributeModifier.Operation.MULTIPLY_TOTAL));
        DefenseAttributes.apply(loaded,DefenseAttributes.ARMOR,1.5);
        var repaired=new AttributeInstance(type,ignored->{}); repaired.load(loaded.save());
        check(repaired.getModifier(DefenseAttributes.ARMOR)==null,"stale permanent owned UUID is repaired to transient");
        var boss=new AttributeInstance(type,ignored->{});
        boss.addTransientModifier(new AttributeModifier(gear,"armor",10,AttributeModifier.Operation.ADDITION));
        DefenseAttributes.apply(boss,DefenseAttributes.ARMOR,1.2*1.6);
        close(boss.getValue(),19.2,"boss multiplier is independent and applied once");
        DefenseAttributes.apply(boss,DefenseAttributes.ARMOR,1.6);
        close(boss.getValue(),16,"disabling specialty preserves independent boss defense");
        DefenseAttributes.apply(boss,DefenseAttributes.ARMOR,1);
        close(boss.getValue(),10,"removing boss and specialty returns equipped value");
        var toughness=new AttributeInstance(Attributes.ARMOR_TOUGHNESS,ignored->{});
        toughness.addTransientModifier(new AttributeModifier(gear,"diamond toughness",8,AttributeModifier.Operation.ADDITION));
        DefenseAttributes.apply(toughness,DefenseAttributes.TOUGHNESS,1.5);
        close(toughness.getValue(),12,"toughness uses its own stable modifier");
        System.out.println("DefenseAttributesTest: passed");
    }
    private static void close(double actual,double expected,String message) { if(Math.abs(actual-expected)>1e-8) throw new AssertionError(message+": "+actual); }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
}

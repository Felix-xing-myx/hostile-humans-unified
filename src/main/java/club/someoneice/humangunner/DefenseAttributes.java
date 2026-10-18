package club.someoneice.humangunner;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import java.util.UUID;

/** The two owned modifiers are transient and never replace another provider's modifiers. */
final class DefenseAttributes {
    static final UUID ARMOR=UUID.fromString("f8a8c5d0-6e45-4d19-a690-20f268ca3401");
    static final UUID TOUGHNESS=UUID.fromString("f8a8c5d0-6e45-4d19-a690-20f268ca3402");
    private DefenseAttributes() { }

    static void apply(AttributeInstance attribute,UUID id,double factor) {
        if(attribute==null) return;
        if(!Double.isFinite(factor)||factor<=0) throw new IllegalArgumentException("Defense multiplier must be finite and positive");
        // Repair an accidentally persisted copy with our exact UUID, without touching other providers.
        attribute.removePermanentModifier(id);
        AttributeModifier existing=attribute.getModifier(id);
        double amount=factor-1;
        if(amount==0) {
            if(existing!=null) attribute.removeModifier(id);
            return;
        }
        if(existing!=null&&existing.getOperation()==AttributeModifier.Operation.MULTIPLY_TOTAL
                &&Double.compare(existing.getAmount(),amount)==0) return;
        if(existing!=null) attribute.removeModifier(id);
        attribute.addTransientModifier(new AttributeModifier(id,"Hostile Humans total defense allocation",amount,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** Read-only projection. Never remove the live modifier to ask what the value would be without it. */
    static double rawWithoutOwned(AttributeInstance attribute,UUID id) {
        var modifiers=attribute.getModifiers().stream().map(modifier->new DefenseAttributePolicy.Modifier(modifier.getId(),
                switch(modifier.getOperation()) {
                    case ADDITION -> DefenseAttributePolicy.Operation.ADDITION;
                    case MULTIPLY_BASE -> DefenseAttributePolicy.Operation.MULTIPLY_BASE;
                    case MULTIPLY_TOTAL -> DefenseAttributePolicy.Operation.MULTIPLY_TOTAL;
                },modifier.getAmount())).toList();
        return DefenseAttributePolicy.rawWithout(attribute.getBaseValue(),modifiers,id);
    }
}

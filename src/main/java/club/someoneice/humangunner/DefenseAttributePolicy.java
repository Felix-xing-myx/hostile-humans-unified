package club.someoneice.humangunner;

import java.util.List;
import java.util.UUID;

/** Vanilla operation order, before the attribute's final sanitizeValue call. */
final class DefenseAttributePolicy {
    enum Operation { ADDITION, MULTIPLY_BASE, MULTIPLY_TOTAL }
    record Modifier(UUID id, Operation operation, double amount) { }
    private DefenseAttributePolicy() { }

    static double rawWithout(double base, List<Modifier> modifiers, UUID excluded) {
        double added=base;
        for(var modifier:modifiers) if(!modifier.id().equals(excluded)&&modifier.operation()==Operation.ADDITION) added+=modifier.amount();
        double result=added;
        for(var modifier:modifiers) if(!modifier.id().equals(excluded)&&modifier.operation()==Operation.MULTIPLY_BASE) result+=added*modifier.amount();
        for(var modifier:modifiers) if(!modifier.id().equals(excluded)&&modifier.operation()==Operation.MULTIPLY_TOTAL) result*=1+modifier.amount();
        return result;
    }
}

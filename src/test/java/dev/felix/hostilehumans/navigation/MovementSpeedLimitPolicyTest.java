package dev.felix.hostilehumans.navigation;

import dev.felix.hostilehumans.progression.DefenseAttributePolicy;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import dev.felix.hostilehumans.navigation.SoftSpeedLimit;

/** Status effects are applied after compression, even with simultaneous buffs/debuffs and old receipts. */
public final class MovementSpeedLimitPolicyTest {
    private static DefenseAttributePolicy.Modifier modifier(UUID id, DefenseAttributePolicy.Operation op, double n) {
        return new DefenseAttributePolicy.Modifier(id, op, n);
    }
    private static void close(double actual, double expected) {
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > 1e-10)
            throw new AssertionError(actual + " != " + expected);
    }
    public static void main(String[] args) {
        UUID gear=UUID.randomUUID(), speed=UUID.randomUUID(), slow=UUID.randomUUID(), cap=UUID.randomUUID();
        var total=DefenseAttributePolicy.Operation.MULTIPLY_TOTAL;
        double capped=SoftSpeedLimit.apply(.4);
        var modifiers=List.of(modifier(gear,total,1), modifier(speed,total,.4), modifier(slow,total,-.15), modifier(cap,total,-.7));
        close(MovementSpeedLimitPolicy.project(.2,modifiers,Set.of(speed,slow),cap),capped*1.4*.85);
        close(MovementSpeedLimitPolicy.project(.1,List.of(modifier(speed,total,.2)),Set.of(speed),cap),.12);
        close(MovementSpeedLimitPolicy.project(.4,List.of(modifier(speed,total,1)),Set.of(speed),cap),capped*2);
        close(MovementSpeedLimitPolicy.project(.4,List.of(modifier(slow,total,-.5)),Set.of(slow),cap),capped*.5);
        close(MovementSpeedLimitPolicy.project(.4,List.of(),Set.of(speed,slow),cap),capped);
        close(MovementSpeedLimitPolicy.project(.4,List.of(modifier(cap,total,-.99)),Set.of(),cap),capped);
        var addition=DefenseAttributePolicy.Operation.ADDITION;
        var base=DefenseAttributePolicy.Operation.MULTIPLY_BASE;
        close(MovementSpeedLimitPolicy.project(.4,List.of(modifier(speed,addition,.05),modifier(slow,base,.1)),Set.of(speed,slow),cap),(capped+.05)*1.1);
        if (!(MovementSpeedLimitPolicy.project(.4,List.of(modifier(speed,total,1)),Set.of(speed),cap)>SoftSpeedLimit.CEILING))
            throw new AssertionError("potion bonus was incorrectly capped again");
    }
}

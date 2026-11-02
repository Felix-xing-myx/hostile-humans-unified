package dev.felix.hostilehumans.progression;

import dev.felix.hostilehumans.navigation.MovementSpeedLimitPolicyTest;

import java.util.List;
import java.util.UUID;

/** JDK-only checks for excluding the owned factor and deferring attribute caps. */
public final class DefenseAttributePolicyTest {
    public static void main(String[] args) {
        MovementSpeedLimitPolicyTest.main(args);
        UUID gear=UUID.randomUUID(),potion=UUID.randomUUID(),external=UUID.randomUUID(),owned=UUID.randomUUID();
        var values=List.of(new DefenseAttributePolicy.Modifier(gear,DefenseAttributePolicy.Operation.ADDITION,10),
                new DefenseAttributePolicy.Modifier(potion,DefenseAttributePolicy.Operation.MULTIPLY_BASE,.5),
                new DefenseAttributePolicy.Modifier(external,DefenseAttributePolicy.Operation.MULTIPLY_TOTAL,.2),
                new DefenseAttributePolicy.Modifier(owned,DefenseAttributePolicy.Operation.MULTIPLY_TOTAL,.92));
        close(DefenseAttributePolicy.rawWithout(0,values,owned),18,"gear and all foreign operations compose before owned allocation");
        close(DefenseAttributePolicy.rawWithout(0,values,owned)*1.2*1.6,34.56,"specialty and independent boss baseline occur once");
        close(DefenseAttributePolicy.rawWithout(2,values,owned),21.6,"nonzero tier base participates once");
        var heavy=List.of(new DefenseAttributePolicy.Modifier(gear,DefenseAttributePolicy.Operation.ADDITION,40));
        double raw=DefenseAttributePolicy.rawWithout(0,heavy,owned);
        close(raw,40,"baseline projection retains raw value above attribute limit");
        close(Math.min(30,raw*.5),20,"sanitize happens after specialization, not before");
        System.out.println("DefenseAttributePolicyTest: passed");
    }
    private static void close(double actual,double expected,String message) {
        if(Math.abs(actual-expected)>1e-9) throw new AssertionError(message+": "+actual+" != "+expected);
    }
}

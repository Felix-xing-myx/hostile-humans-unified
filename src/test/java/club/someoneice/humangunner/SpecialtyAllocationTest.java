package club.someoneice.humangunner;

import java.util.Arrays;
import java.util.Random;

/** Standalone pure policy regression, runnable without Minecraft or optional gun APIs. */
public final class SpecialtyAllocationTest {
    private static final double[] COSTS = {1,1.2,.8,.6,1.1,.5,1.3,1.2,1.2,.7,.9};
    private static final double[] CAPS = {.3,.3,.3,.3,.3,.3,.3,.2,.3,.5,.3};
    private static final double[][] PREFERENCES = {
        {1,1,1,1,1,1,1,1,1,1,1},
        {.89,.89,.94,.94,1.04,.94,1.34,.94,.94,1/.99,.94},
        {1.24,1.29,1.05,1.05,.81,.91,.86,.91,.91,1,1.32},
        {.91,.86,.91,.86,1.20,1.21,.96,1.01,1.06,1/.96,1.02},
        {.97,.92,.92,.92,1.07,.97,1.12,1.02,1.02,1/.92,.96}
    };
    public static void main(String[] args) {
        var counterexample = SpecialtyAllocation.solve(new double[]{.1,4}, new double[]{1,1},
                new double[]{1,1}, new double[]{3,3}, 4.1);
        require(counterexample.feasible(), "counterexample feasible");
        close(counterexample.multipliers()[0], 1.1, 1e-9, "released lower bound");
        close(counterexample.multipliers()[1], 3, 1e-9, "upper bound");
        var baseline = allocate(1, PREFERENCES[0], ones(), false);
        for (double value : baseline.multipliers()) close(value, 1, 1e-9, "neutral baseline");
        var center = SpecialtyAllocation.center(PREFERENCES[1], COSTS);
        close(sum(center), 10.5, 1e-10, "normalized center");
        for (double common : new double[]{.9,1,1.1}) {
            double[] equal = new double[11]; Arrays.fill(equal, common);
            var result = allocate(1, PREFERENCES[1], equal, false);
            for (int i = 0; i < 11; i++) close(result.multipliers()[i], center[i], 1e-8, "common tilt retains faction");
        }
        var infeasible = SpecialtyAllocation.solve(new double[]{1,1}, new double[]{1,1},
                new double[]{1,1}, new double[]{2,2}, 6);
        require(!infeasible.feasible(), "infeasible total reported");
        close(infeasible.residual(), -2, 1e-9, "explicit residual");
        rejects(() -> SpecialtyAllocation.solve(new double[]{1}, new double[]{0}, new double[]{1}, new double[]{2}, 1));
        rejects(() -> SpecialtyAllocation.solve(new double[]{Double.NaN}, new double[]{1}, new double[]{1}, new double[]{2}, 1));
        rejects(() -> SpecialtyAllocation.solve(new double[]{1}, new double[]{1}, new double[]{3}, new double[]{2}, 1));
        for (double[] preference : PREFERENCES) {
            var boss = allocate(1, preference, ones(), true);
            verify(boss, 1);
            double[] extreme = new double[11]; Arrays.fill(extreme, 100);
            var second = allocate(1, preference, extreme, true);
            for (int i = 0; i < 11; i++) close(second.multipliers()[i], boss.multipliers()[i], 0, "boss has no random tilt");
        }
        // Published boss example: full normalized faction center amplified by 1.8, conserved.
        var redBoss = allocate(1, PREFERENCES[1], ones(), true);
        close(redBoss.multipliers()[0], .8114, .0001, "red boss health");
        close(redBoss.multipliers()[6], 1.6261, .0001, "red boss melee");
        Random random = new Random(0x51ec1a17L);
        for (double[] preference : PREFERENCES) for (int n = 0; n < 20000; n++) {
            double budget = n % 2 == 0 ? .75 : 1.25;
            double[] tilts = new double[11];
            for (int i = 0; i < 11; i++) tilts[i] = Math.exp(random.nextGaussian() * (n % 3 == 0 ? 2 : .15));
            verify(allocate(budget, preference, tilts, false), budget);
        }
        // Accuracy remains positive utility: 0.5 produces spread 2.0, without an extra 1.8 clamp.
        close(1/.5, 2, 0, "inverse spread");
        System.out.println("SpecialtyAllocationTest: all checks passed (100000 randomized boundary cases)");
    }
    private static SpecialtyAllocation.Result allocate(double b, double[] p, double[] e, boolean boss) {
        return SpecialtyAllocation.allocate(b,p,COSTS,CAPS,e,.25,.5,1.8,boss);
    }
    private static void verify(SpecialtyAllocation.Result result, double budget) {
        require(result.feasible(), "default policy feasible");
        close(sum(result.multipliers()), 10.5 * budget, 2e-8, "cost conserved");
        double floatSum = 0;
        for (int i = 0; i < 11; i++) {
            double value = result.multipliers()[i];
            require(Double.isFinite(value) && value >= result.lower()[i] - 1e-10 && value <= result.upper()[i] + 1e-10, "merged boundaries");
            require(value >= .5 && value <= 1.8, "absolute boundaries");
            floatSum += (float) value * COSTS[i];
        }
        close(floatSum, budget * 10.5, 2e-6, "persisted float conservation");
    }
    private static double sum(double[] values) { double sum=0; for(int i=0;i<11;i++) sum += values[i]*COSTS[i]; return sum; }
    private static double[] ones() { double[] values = new double[11]; Arrays.fill(values,1); return values; }
    private static void close(double value,double expected,double tolerance,String message) { require(Math.abs(value-expected)<=tolerance, message + ": " + value + " != " + expected); }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private static void rejects(Runnable task) { try { task.run(); throw new AssertionError("invalid input accepted"); } catch(IllegalArgumentException expected) { } }
}

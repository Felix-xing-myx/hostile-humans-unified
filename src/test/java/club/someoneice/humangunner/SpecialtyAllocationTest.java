package club.someoneice.humangunner;

import java.util.Arrays;
import java.util.Random;

/** Pure 13-dimensional budget and ratio regressions, with no Minecraft runtime. */
public final class SpecialtyAllocationTest {
    private static final double[] COSTS = {1.2,1.4,1,.7,1.1,.4,1.25,1.3,1.2,.6,1.25,1.2,1.2};
    private static final double[] CAPS = {.3,.3,.3,.3,.3,.3,.3,.2,.3,.5,.3,.3,.3};
    private static final double[][] PREFERENCES = {
        {1,1,1,1,1,1,1,1,1,1,1,1,.01},
        {1,.89,.94,.94,1.04,.94,1.3,.9,.9,1,.8,1.2,.15},
        {1.3,1.2,1.1,1.05,.9,.91,1,.8,.9,1.03,1.2,1.05,.1},
        {.85,.95,1,.85,1.2,1.2,.9,1.2,1.25,1.1,1.1,1.25,.25},
        {1.05,1,1,1,1.08,1.3,1.1,1.05,1.15,1.08,1.05,1.1,.2}
    };
    public static void main(String[] args) {
        close(Arrays.stream(COSTS).sum(), 13.8, 1e-12, "table sum");
        close(SpecialtyAllocation.baselineCost(COSTS), 12.6, 1e-12, "zero-baseline cost");
        double[] neutral = SpecialtyAllocation.center(PREFERENCES[0], COSTS);
        close(neutral[12], .01*12.6/12.612, 1e-12, "seed remains about one percent, not one per mille");
        var counterexample = SpecialtyAllocation.solve(new double[]{.1,4}, new double[]{1,1},
                new double[]{1,1}, new double[]{3,3}, 4.1);
        close(counterexample.multipliers()[0], 1.1, 1e-9, "released lower bound");
        close(counterexample.multipliers()[1], 3, 1e-9, "upper bound");
        var infeasible = SpecialtyAllocation.solve(new double[]{1,1}, new double[]{1,1},
                new double[]{1,1}, new double[]{2,2}, 6);
        require(!infeasible.feasible(), "impossible total explicit");
        close(infeasible.residual(), -2, 1e-9, "explicit residual");
        Random random = new Random(0x51ec1a17L);
        for (double[] preference : PREFERENCES) {
            var center = SpecialtyAllocation.center(preference, COSTS);
            close(sum(center),12.6,1e-10,"normalized center");
            for (int n=0;n<20000;n++) {
                double budget = n%2==0 ? .8 : 1.3;
                double[] tilts = new double[13];
                for(int i=0;i<13;i++)tilts[i]=Math.exp(random.nextGaussian()*(n%3==0?2:.15));
                verify(allocate(budget,preference,tilts),budget);
            }
        }
        double[] tilted=ones();tilted[0]=1.3;tilted[6]=.7;
        var base=allocate(1.05,PREFERENCES[1],ones());
        var randomTilt=allocate(1.05,PREFERENCES[1],tilted);
        require(Math.abs(base.multipliers()[0]-randomTilt.multipliers()[0])>1e-5,"random tilt has an effect");
        verify(base,1.05);verify(randomTilt,1.05);
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.01,1.01})
            rejects(()->SpecialtyAllocation.allocate(1,PREFERENCES[0],COSTS,CAPS,ones(),invalid,.5,1.8));
        System.out.println("SpecialtyAllocationTest: all checks passed (100000 random allocations; actual boss budgets are covered by SpecialtyPersistenceTest)");
    }
    private static SpecialtyAllocation.Result allocate(double b,double[] p,double[] e) {
        return SpecialtyAllocation.allocate(b,p,COSTS,CAPS,e,.25,.5,1.8);
    }
    private static void verify(SpecialtyAllocation.Result result,double budget) {
        require(result.feasible(),"default policy feasible");
        close(sum(result.multipliers()),12.6*budget,2e-8,"cost conserved");
        double floatSum=0;
        for(int i=0;i<13;i++) {
            double value=result.multipliers()[i];
            require(Double.isFinite(value)&&value>=result.lower()[i]-1e-10&&value<=result.upper()[i]+1e-10,"merged bounds");
            require(i==12?value>0&&value<=.45:value>=.5&&value<=1.8,"dimension-specific bounds");
            floatSum+=(float)value*COSTS[i];
        }
        close(floatSum,12.6*budget,2e-6,"persisted float conservation");
    }
    private static double sum(double[] values){double sum=0;for(int i=0;i<13;i++)sum+=values[i]*COSTS[i];return sum;}
    private static double[] ones(){double[] values=new double[13];Arrays.fill(values,1);return values;}
    private static void close(double a,double b,double tolerance,String message){require(Math.abs(a-b)<=tolerance,message+": "+a+" != "+b);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void rejects(Runnable task){try{task.run();throw new AssertionError("invalid accepted");}catch(IllegalArgumentException expected){}}
}

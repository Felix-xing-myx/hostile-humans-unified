package club.someoneice.humangunner;

import java.util.Random;

/** No Minecraft runtime needed; regression against rejection-loop stalls and endpoint masses. */
public final class SpecialtyBudgetSamplerTest {
    public static void main(String[] args) {
        Random random = new Random(123);
        double sum = 0; int high = 0, boundary = 0;
        for (int i=0;i<100000;i++) {
            double value = SpecialtyBudgetSampler.sample(random.nextDouble(),1.05,.1,.8,1.3);
            require(value>=.8&&value<=1.3,"ordinary interval");
            sum+=value;if(value>1.15)high++;if(value==.8||value==1.3)boundary++;
        }
        close(sum/100000,1.05,.002,"ordinary mean");
        close(high/100000.0,.1544,.006,"ordinary upper tail");
        require(boundary==0,"no clamped endpoint mass");
        sum=0;boundary=0;
        double minimum=1, maximum=1+1e-12;
        for(int i=0;i<10000;i++) {
            double value=SpecialtyBudgetSampler.sample((i+.5)/10000,1,.5,minimum,maximum);
            require(value>=minimum&&value<=maximum,"thin interval");
            sum+=(value-minimum)/(maximum-minimum);
            if(value==minimum||value==maximum)boundary++;
        }
        close(sum/10000,.5,.001,"thin interval retains spread");
        require(boundary<10,"only natural floating-point endpoint rounding");
        // A half-normal truncated at one standard deviation has mean about 0.459862 sigma.
        sum=0;
        for(int i=0;i<10000;i++)sum+=SpecialtyBudgetSampler.sample((i+.5)/10000,1,.5,1,1.5);
        close(sum/10000,1+.5*.4598622293,1e-8,"mean on lower boundary");
        close(SpecialtyBudgetSampler.sample(.25,1,.5,1,1.5)
                +SpecialtyBudgetSampler.sample(.75,1,.5,.5,1),2,1e-14,"edge symmetry");
        close(SpecialtyBudgetSampler.sample(.9,1.05,0,.8,1.3),1.05,0,"zero sigma");
        close(SpecialtyBudgetSampler.sample(.9,1, .5,1,1),1,0,"point interval");
        double tiny=SpecialtyBudgetSampler.sample(.75,0,1e-200,-1,1);
        require(Double.isFinite(tiny)&&tiny>0&&tiny<1e-199,"tiny sigma has bounded inverse work");
        System.out.println("SpecialtyBudgetSamplerTest: all checks passed");
    }
    private static void close(double a,double b,double tolerance,String message){require(Math.abs(a-b)<=tolerance,message+": "+a+" != "+b);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

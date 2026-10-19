package club.someoneice.humangunner;

/** Pure Java regression cases; no game classes or Forge launch needed. */
public final class RevisionCombatPolicyTest {
    private static int checks;
    public static void main(String[] args) {
        near(GunTrackingPolicy.flightTicks(30,0,0,0,0,0,3,0),10,"stationary flight time");
        near(GunTrackingPolicy.flightTicks(30,0,0,1,0,0,3,0),15,"receding target needs longer lead");
        near(GunTrackingPolicy.flightTicks(30,0,0,-1,0,0,3,0),7.5,"approaching target needs less lead");
        near(GunTrackingPolicy.flightTicks(2,0,0,0,0,1,3,0),2/Math.sqrt(8),"near lateral crossing intercept");
        for (double distance : new double[]{2,10,40}) for(double lateral : new double[]{.1,.6,1.2}) {
            double t=GunTrackingPolicy.flightTicks(distance,0,0,0,0,lateral,4,0);
            near(Math.sqrt(distance*distance+lateral*lateral*t*t),4*t,"bullet and target arrive together");
        }
        double t=GunTrackingPolicy.flightTicks(20,0,0,0,0,0,3,.05);
        check(Math.abs(Math.sqrt(400+Math.pow(.025*t*t,2))-3*t)<1e-5,"gravity refinement matches corrected path length");
        near(GunTrackingPolicy.flightTicks(1000,0,0,0,0,0,1,0),20,"lead horizon bounded");
        near(GunTrackingPolicy.flightTicks(10,0,0,0,0,0,0,0),0,"unknown speed must not predict");
        near(GunTrackingPolicy.flightTicks(Double.NaN,0,0,0,0,0,3,0),0,"invalid physics rejected");
        check(FinisherPolicy.score(200,20,10)>FinisherPolicy.score(20,2,10),"high maximum health wins at equal fraction");
        check(FinisherPolicy.score(200,10,10)>FinisherPolicy.score(200,40,10),"lower current health wins");
        check(FinisherPolicy.score(100,35,5)>0&&FinisherPolicy.score(100,36,5)==0,"35 percent eligibility boundary");
        check(FinisherPolicy.score(100,0,5)==0,"dead target excluded");
        check(FinisherPolicy.score(100,10,5)>FinisherPolicy.score(100,10,30),"distance cost avoids remote obsession");
        FinisherPolicy.Attempts attempts=new FinisherPolicy.Attempts();
        attempts.interrupt(10);attempts.interrupt(10);attempts.interrupt(30);
        check(!attempts.blocked(31),"same tick cannot count twice");
        attempts.interrupt(50);
        check(attempts.blocked(349)&&!attempts.blocked(350),"three interruptions suspend pursuit for 300 ticks");
        attempts=new FinisherPolicy.Attempts();attempts.interrupt(0);attempts.interrupt(201);attempts.interrupt(220);
        check(!attempts.blocked(221),"old interruptions expire after 200 ticks");
        attempts.interrupt(230);check(attempts.blocked(231),"fresh repeated interruptions suspend opportunity");
        System.out.println("RevisionCombatPolicyTest passed: "+checks+" checks");
    }
    private static void near(double actual,double expected,String message){check(Math.abs(actual-expected)<1e-7,message+": "+actual);}
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}

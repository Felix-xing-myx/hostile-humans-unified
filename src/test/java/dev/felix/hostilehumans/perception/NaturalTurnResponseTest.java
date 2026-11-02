package dev.felix.hostilehumans.perception;

import dev.felix.hostilehumans.common.PerformancePoliciesTest;

import dev.felix.hostilehumans.perception.AttentionFacingPolicy;
import dev.felix.hostilehumans.perception.TurnGesture;
import dev.felix.hostilehumans.perception.TurnResponse;

/** Real motor regressions, registered through PerformancePoliciesTest; no world objects required. */
public final class NaturalTurnResponseTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static int run(){
        checks=0;
        for(long seed=0;seed<16;seed++) {
            var a=new TurnResponse.Frame(seed);var b=new TurnResponse.Frame(seed);
            var pose=new AttentionFacingPolicy.Facing(0,0,0);
            for(int tick=0;tick<100;tick++) {
                var next=a.step(tick,pose.body(),pose.head(),pose.pitch(),90,90,0,75,TurnResponse.COMBAT,30,1);
                b.step(tick,pose.body(),pose.head(),pose.pitch(),-90,-90,0,75,TurnResponse.URGENT,40,2);
                var repeated=b.step(tick,130,130,0,90,90,0,75,TurnResponse.COMBAT,30,1);
                check(next.equals(repeated),"same-tick provisional actions cannot consume extra randomness or integrate twice");
                check(Float.isFinite(next.head())&&Math.abs(TurnResponse.wrap(next.head()-next.body()))<=75.001,"finite pose and anatomical neck bound");
                check(Math.abs(TurnResponse.wrap(next.body()-pose.body()))<=30.001,"ordinary body motion has a bounded angular step");
                pose=next;
            }
            check(Math.abs(TurnResponse.wrap(pose.body()-90))<.1&&Math.abs(TurnResponse.wrap(pose.head()-90))<.1,"stationary target settles without a limit cycle");
            var reset=a.step(200,45,45,0,45,45,0,75,TurnResponse.COMBAT,30,1);
            check(reset.body()==45&&reset.head()==45,"clock interruption resets physical momentum");
        }
        var prior=TurnGesture.sample(42,1,90,false);
        for(int episode=2;episode<=50;episode++){
            var style=TurnGesture.sample(42,episode,90,false);
            check(style.tempo()>=.65&&style.tempo()<=1.10,"bounded episode duration distribution");
            check(Math.abs(style.skew())<=.30&&Math.abs(style.landing()*90)<=1.5001,"bounded shape and primary landing deviation");
            check(style.skew()!=prior.skew(),"successive gestures of one identity vary in normalized shape");prior=style;
            var urgent=TurnGesture.sample(42,episode,90,true);
            check(urgent.landing()==0&&Math.abs(urgent.skew())<=.15,"urgent actions do not inject deliberate landing errors");
        }
        for(int angularSpeed:new int[]{4,8,12,18}){
            var frame=new TurnResponse.Frame(14);var pose=new AttentionFacingPolicy.Facing(0,0,0);
            var profile=angularSpeed>=12?TurnResponse.URGENT:TurnResponse.COMBAT;
            for(int tick=0;tick<180;tick++){
                float target=(float)TurnResponse.wrap(tick*angularSpeed);
                pose=frame.step(tick,pose.body(),pose.head(),0,target,target,0,75,profile,profile.speed(),1);
                if(tick>=60)check(Math.abs(TurnResponse.wrap(target-pose.head()))<5&&Math.abs(TurnResponse.wrap(target-pose.body()))<5,
                        "continuous tracking remains eligible for the unchanged five-degree gun gate");
            }
        }
        for(double skew:new double[]{-.3,0,.3}){
            var curve=new TurnGesture.Segment(10,3,-.5,90,10,skew,.01);
            var start=curve.at(0,90,0);var end=curve.at(30,90,0);
            check(Math.abs(start.angle()-10)<1.e-9&&Math.abs(start.velocity()-3)<1.e-9&&Math.abs(start.acceleration()+.5)<1.e-9,
                    "curve splice preserves position, velocity and acceleration");
            check(end.angle()==90&&end.velocity()==0&&end.acceleration()==0&&end.jerk()==0,"completed correction cannot leave persistent force or drift");
        }
        var seam=new TurnResponse.Frame();
        var crossing=seam.step(0,179,179,0,-179,-179,0,75,TurnResponse.COMBAT,30);
        check(TurnResponse.wrap(crossing.head()-179)>0,"shortest path across yaw seam");
        for(int releaseAt:new int[]{1,3,5,8}){
            var frame=new TurnResponse.Frame(42);var p=new AttentionFacingPolicy.Facing(0,0,0);
            for(int tick=0;tick<releaseAt;tick++)p=frame.step(tick,p.body(),p.head(),0,90,90,0,75,TurnResponse.COMBAT,30,1);
            AttentionFacingPolicy.Facing late=null;
            for(int tick=releaseAt;tick<releaseAt+70;tick++){
                p=frame.stop(tick,p.body(),p.head(),p.pitch(),75);
                check(p.equals(frame.stop(tick,100,100,0,75)),"release is idempotent within a tick");
                if(tick==releaseAt+60)late=p;
            }
            check(late!=null&&Math.abs(TurnResponse.wrap(p.head()-late.head()))<.001
                    &&Math.abs(TurnResponse.wrap(p.body()-late.body()))<.001,"no goal releases the motor without self-induced spinning");
        }
        var constrained=new TurnResponse.Frame();
        var pose=new AttentionFacingPolicy.Facing(0,0,0);
        for(int tick=0;tick<100;tick++){
            pose=constrained.step(tick,pose.body(),pose.head(),pose.pitch(),0,180,89,60,TurnResponse.COMBAT,30);
            check(Math.abs(pose.body())<.001&&Math.abs(pose.head())<=60.001&&Math.abs(pose.pitch())<=89.001,
                    "looking does not replace a fixed navigation body or exceed neck/pitch limits");
        }
        var idle=new TurnResponse.Frame();pose=new AttentionFacingPolicy.Facing(0,0,0);
        for(int tick=0;tick<120;tick++){
            var next=idle.step(tick,pose.body(),pose.head(),pose.pitch(),90,90,0,75,TurnResponse.IDLE,1.5);
            check(Math.abs(TurnResponse.wrap(next.head()-pose.head()))<=1.5001,"idle cannot sweep beyond the perception-aware speed ceiling");pose=next;
        }
        return checks;
    }
}

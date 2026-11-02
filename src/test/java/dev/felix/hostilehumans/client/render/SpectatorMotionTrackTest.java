package dev.felix.hostilehumans.client.render;

import dev.felix.hostilehumans.client.render.tacz.NativeMovementPlan;
import dev.felix.hostilehumans.client.render.tacz.NativeJumpSway;
import java.util.Map;

public final class SpectatorMotionTrackTest {
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    private static void near(double a, double b, String why) { check(Math.abs(a-b)<1e-6, why+": "+a+" != "+b); }
    private static double jumpAtFrameRate(int rate) {
        var sway = new NativeJumpSway();
        sway.sample(0, 0, true, true);
        double peak = 0, flight = 2 * 8.4 / 32;
        for (int i=1;i<=2*rate;i++) {
            double time=i/(double)rate;
            double y=time<flight ? 8.4*time-16*time*time : 0;
            double offset=sway.sample(time,y,time>=flight,true);
            check(Double.isFinite(offset) && Math.abs(offset)<=.125,"jump displacement bounded at "+rate+" FPS");
            near(offset,sway.sample(time,y,time>=flight,true),"same-frame jump sampling idempotent");
            peak=Math.max(peak,Math.abs(offset));
        }
        check(peak>.02,"jump/landing has an observable response");
        check(Math.abs(sway.sample(2,0,true,true))<1e-5,"jump settles without repeated impulses");
        return peak;
    }
    public static void main(String[] args) {
        // At this age float(gameTime)+partialTick freezes; the display clock must stay continuous.
        double age = 1L << 26;
        var track = new SpectatorMotionTrack();
        track.sample(age, 0, 0, true);
        double previous = 0;
        for (int i=1; i<=120; i++) {
            var f = track.sample(age+i/6D, i/30D, 0, true);
            check(f.seconds()>previous, "each 120 Hz frame advances in an old world"); previous=f.seconds();
            check(f.bob()>=0 && f.bob()<=0.1, "vanilla amplitude bounded");
            check(f==track.sample(age+i/6D, i/30D, 0, true), "same-frame world/hand sampling idempotent");
        }
        near(track.sample(age+20, 4, 0, true).distance(), 2.4, "distance drives animation phase");
        for (int i=1;i<=40;i++) track.sample(age+20+i, 4, 0, false);
        check(track.sample(age+60, 4, 0, false).bob()<1e-8,"airborne/stationary bob decays");
        near(track.sample(age+61, 100, 0, true).distance(),0,"teleport resets rather than amplifying bob");
        var plan = new NativeMovementPlan(Map.of("idle",1D,"walk_forward",1D,"walk_backward",1D,
                "walk_sideway",1D,"walk_aiming",1D,"run_start",0.2,"run",1D,"run_end",0.3,"run_hold",0.6));
        plan.sample(0,0,false,false,true,0,1,0,false,false);
        var walk=plan.sample(.2,1,true,false,true,0,1,0,false,false);
        near(walk.get("walk_forward").seconds(),.5,"walk loops use distance, not render FPS");
        var start=plan.sample(.3,1.1,true,true,true,0,1,0,false,false);
        check(start.containsKey("run_start"),"run transition entered");
        var run=plan.sample(.7,1.5,true,true,true,0,1,0,false,false);
        check(run.containsKey("run"),"run loop follows startup");
        var reload=plan.sample(1,1.6,true,true,true,0,1,0,true,false);
        check(reload.isEmpty(),"reload owns pose after bounded fade");
        var aim=plan.sample(1.2,1.7,true,false,true,1,1,0,false,false);
        check(aim.containsKey("walk_aiming"),"ADS uses native aiming gait");
        double total=aim.values().stream().mapToDouble(NativeMovementPlan.Sample::weight).sum();
        check(total<=1.00001,"crossfade never amplifies overlapping poses");
        check(plan.sample(1.2,1.7,true,false,true,1,1,0,false,false).equals(aim),"same-frame native plan stable");
        check(plan.sample(0,0,false,false,true,0,1,0,false,false).isEmpty(),"clock reset releases old movement");
        near(NativeMovementPlan.movementScale(0),1,"hip movement amplitude unchanged");
        near(NativeMovementPlan.movementScale(1),.2,"full ADS retains twenty percent movement");
        double previousScale=1;
        for(int i=0;i<=100;i++) {
            float scale=NativeMovementPlan.movementScale(i/100F);
            check(scale<=previousScale+1e-6 && scale>=.19999,"ADS attenuation continuous and bounded");
            previousScale=scale;
        }
        var hipPlan=new NativeMovementPlan(Map.of("walk_forward",1D,"walk_aiming",1D));
        hipPlan.sample(0,0,true,false,true,0,1,0,false,false);
        var hip=hipPlan.sample(.2,1,true,false,true,0,1,0,false,false).get("walk_forward");
        var adsPlan=new NativeMovementPlan(Map.of("walk_forward",1D,"walk_aiming",1D));
        adsPlan.sample(0,0,true,false,true,1,1,0,false,false);
        var ads=adsPlan.sample(.2,1,true,false,true,1,1,0,false,false).get("walk_aiming");
        near(ads.seconds(),hip.seconds(),"ADS changes amplitude without slowing gait");
        near(ads.weight(),hip.weight()*.2,"actual sampled ADS pose weight reduced");
        double jump30=jumpAtFrameRate(30),jump60=jumpAtFrameRate(60),jump144=jumpAtFrameRate(144);
        check(Math.abs(jump30-jump144)<.02 && Math.abs(jump60-jump144)<.02,"frame rate does not amplify jump pulse");
        var jump=new NativeJumpSway();
        near(jump.sample(0,10,false,true),0,"joining a view in mid-air does not replay takeoff");
        near(jump.sample(.02,100,false,true),0,"vertical teleport resets sway");
        near(jump.sample(.04,100.2,true,true),0,"non-falling ground contact creates no landing impulse");
        near(jump.sample(.05,100.3,true,true),0,"grounded stairs are not jumps");
        near(jump.sample(.06,100.6,false,false),0,"swimming/riding/climbing disable jump presentation");
        near(jump.sample(.07,100.7,false,true),0,"re-entering eligible movement initializes quietly");
        near(jump.sample(Double.NaN,0,true,true),0,"invalid clock clears safely");
        var contact=new NativeJumpSway();
        contact.sample(0,0,true,true);
        contact.sample(.01,0,false,true);
        check(contact.sample(.02,.08,false,true)>0,"zero-displacement takeoff endpoint waits for actual upward interpolation");
        var landing=new NativeJumpSway();
        landing.sample(0,1,false,true);
        landing.sample(.02,.8,false,true);
        landing.sample(.04,.8,false,true);
        check(landing.sample(.06,.8,true,true)>0,"landing preserves descent across a stationary contact endpoint");
        var airPlan=new NativeMovementPlan(Map.of("idle",1D,"run_start",.2,"run",1D,"run_hold",.6,"run_end",.3));
        airPlan.sample(0,0,true,true,false,0,1,0,false,false);
        check(airPlan.sample(.1,.1,true,true,false,0,1,0,false,false).containsKey("run_start"),"airborne run still finishes authored startup");
        check(airPlan.sample(.3,.3,true,true,false,0,1,0,false,false).containsKey("run_hold"),"airborne run settles to authored hold");
        var stoppedAir=airPlan.sample(.4,.4,false,false,false,0,1,0,false,false);
        check(!stoppedAir.containsKey("run_end"),"run ending cannot replace airborne stabilization");
        System.out.println("SpectatorMotionTrackTest passed");
    }
}

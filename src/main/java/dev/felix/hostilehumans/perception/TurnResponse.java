package dev.felix.hostilehumans.perception;

/** Stateful angular execution only: gesture references, bounded acceleration/jerk and physical limits. */
public final class TurnResponse {
    /** Degrees/tick, degrees/tick², response frequency/tick, degrees/tick³. */
    public record Profile(double speed,double acceleration,double gain,double jerk) {
        public Profile {
            if(!Double.isFinite(speed+acceleration+gain+jerk)||speed<=0||acceleration<=0||gain<=0||jerk<=0)
                throw new IllegalArgumentException("Turn response parameters must be finite and positive");
        }
    }
    public static final Profile IDLE=new Profile(5,1.5,.6,1);
    public static final Profile COMBAT=new Profile(30,14,.5,20);
    public static final Profile URGENT=new Profile(40,20,.6,30);
    private static final Profile IDLE_BODY=new Profile(4.5,1.2,.48,.8);
    private static final Profile COMBAT_BODY=new Profile(27,11.2,.4,16);
    private static final TurnGesture.Style TRACKING=new TurnGesture.Style(1,0,0,1,1,1);
    private static final int SUBSTEPS=4;
    record Axis(double angle,double velocity,double acceleration,double target,double tracking) {
        static Axis rest(double angle){return new Axis(finiteAngle(angle),0,0,Double.NaN,0);}
    }
    private record Snapshot(Axis body,Axis head,Axis pitch,TurnGesture gesture,long number,long intent) {}
    private TurnResponse() {}
    public static double wrap(double value){return (value%360+540)%360-180;}
    private static double finiteAngle(double x){return Double.isFinite(x)?wrap(x):0;}
    private static double clamp(double x,double low,double high){return Math.max(low,Math.min(high,x));}

    private static Axis advance(Axis from,double goal,TurnGesture.Segment segment,double elapsed,
            TurnGesture.Style style,Profile profile,double speed,boolean resetTracking,double body,double neck) {
        double delta=wrap(goal-from.target),tracking=!resetTracking&&Math.abs(delta)<=Math.max(12,profile.speed*.5)?.5*from.tracking+.5*delta:0;
        double angle=from.angle,velocity=from.velocity,acceleration=from.acceleration,dt=1.0/SUBSTEPS;
        double maximumSpeed=Math.max(.01,Math.min(profile.speed,speed));
        // A lower requested speed does not instantly discard inherited angular momentum.
        double ceiling=Math.max(maximumSpeed,Math.abs(from.velocity));
        for(int i=0;i<SUBSTEPS;i++) {
            double localTime=(i+1)*dt;
            // The current observed goal is the end of this tick, not a future target position.
            double sampledGoal=goal+tracking*(localTime-1);
            var reference=segment==null?new TurnGesture.Reference(sampledGoal,tracking,0,0):segment.at(elapsed+localTime,sampledGoal,tracking);
            double refAngle=reference.angle(),refVelocity=clamp(reference.velocity(),-maximumSpeed,maximumSpeed);
            double refAcceleration=reference.acceleration(),refJerk=reference.jerk();
            double available=neck-Math.abs(wrap(angle-body));
            if(neck<180) {
                if(Math.abs(wrap(refAngle-body))>neck){refVelocity=0;refAcceleration=0;refJerk=0;}
                refAngle=body+clamp(wrap(refAngle-body),-neck,neck);
                if(wrap(angle-body)*refVelocity>0)
                    refVelocity=Math.copySign(Math.min(Math.abs(refVelocity),Math.sqrt(2*profile.acceleration*Math.max(0,available-2))),refVelocity);
            }
            double gain=profile.gain*style.response();
            // Third-order damped tracking controls jerk itself; acceleration is a continuous state.
            double requested=refJerk+gain*gain*gain*wrap(refAngle-angle)
                    +3*gain*gain*(refVelocity-velocity)+3*gain*(refAcceleration-acceleration);
            double braking=requested*velocity<0?style.braking():1;
            double bound=profile.acceleration*braking;
            if(Math.abs(velocity)>maximumSpeed+1)bound=Math.max(bound,Math.min(16,Math.abs(velocity)*.65));
            double jerk=Math.max(profile.jerk*style.jerk(),Math.abs(velocity)>maximumSpeed+1?bound*.6:0);
            // Brake outward acceleration before reaching the speed ceiling.
            if(acceleration*velocity>0&&Math.abs(acceleration)>Math.sqrt(2*jerk*Math.max(0,maximumSpeed-Math.abs(velocity))))
                requested=-Math.copySign(jerk,acceleration);
            double inheritedBound=Math.max(bound,Math.abs(from.acceleration));
            acceleration=clamp(acceleration+clamp(requested,-jerk,jerk)*dt,-inheritedBound,inheritedBound);
            double nextVelocity=clamp(velocity+acceleration*dt,-ceiling,ceiling);
            angle=wrap(angle+(velocity+nextVelocity)*.5*dt);velocity=nextVelocity;
        }
        if(Math.abs(wrap(goal-angle))<.02&&Math.abs(velocity)<.04&&Math.abs(acceleration)<.08&&Math.abs(tracking)<.02)
            return new Axis(wrap(goal),0,0,goal,0);
        return new Axis(angle,velocity,acceleration,goal,tracking);
    }
    private static Axis brake(Axis from){
        double angle=from.angle,v=from.velocity,a=from.acceleration,dt=1.0/SUBSTEPS;
        for(int i=0;i<SUBSTEPS;i++){
            double jerk=clamp(-4*v-4*a,-30,30);
            a=clamp(a+jerk*dt,-Math.max(16,Math.abs(from.acceleration)),Math.max(16,Math.abs(from.acceleration)));
            double next=v+a*dt;
            if(v*next<0){angle=wrap(angle+v*.5*dt);v=0;a=0;break;}
            angle=wrap(angle+(v+next)*.5*dt);v=next;
        }
        if(Math.abs(v)<.02&&Math.abs(a)<.04){v=0;a=0;}
        return new Axis(angle,v,a,Double.NaN,0);
    }

    /** One entity/session. All same-tick requests are evaluated from an immutable starting snapshot. */
    public static final class Frame {
        private final long seed;
        private long tick=Long.MIN_VALUE;
        private Snapshot current,start;
        private AttentionFacingPolicy.Facing cached;
        private float lastBody,lastHead,lastPitch,lastLimit;
        private double lastSpeed;
        private long lastIntent;
        private Profile lastProfile;
        private boolean lastStop;
        public Frame(){this(0);}
        public Frame(long seed){this.seed=seed;}
        private void begin(long now,float actualBody,float actualHead,float actualPitch,long intent){
            if(start==null||now!=tick) {
                if(current==null||now!=tick+1)current=new Snapshot(Axis.rest(actualBody),Axis.rest(actualHead),Axis.rest(actualPitch),null,current==null?0:current.number,intent);
                start=current;tick=now;
            }
        }
        public AttentionFacingPolicy.Facing stop(long now,float actualBody,float actualHead,float actualPitch,float headLimit){
            if(start!=null&&now==tick&&lastStop&&cached!=null&&headLimit==lastLimit)return cached;
            begin(now,actualBody,actualHead,actualPitch,0);
            Axis body=brake(start.body),head=brake(start.head),pitch=brake(start.pitch);
            double relative=wrap(head.angle-body.angle),limit=Math.max(0,headLimit);
            if(Math.abs(relative)>limit)head=new Axis(wrap(body.angle+clamp(relative,-limit,limit)),body.velocity,body.acceleration,Double.NaN,0);
            if(Math.abs(pitch.angle)>89)pitch=Axis.rest(Math.copySign(89,pitch.angle));
            current=new Snapshot(body,head,pitch,null,start.number,0);lastStop=true;lastLimit=headLimit;
            cached=new AttentionFacingPolicy.Facing((float)body.angle,(float)head.angle,(float)pitch.angle);return cached;
        }
        public AttentionFacingPolicy.Facing step(long now,float actualBody,float actualHead,float actualPitch,
                float wantedBody,float wantedHead,float wantedPitch,float headLimit,Profile profile,double speed) {
            return step(now,actualBody,actualHead,actualPitch,wantedBody,wantedHead,wantedPitch,headLimit,profile,speed,0);
        }
        public AttentionFacingPolicy.Facing step(long now,float actualBody,float actualHead,float actualPitch,
                float wantedBody,float wantedHead,float wantedPitch,float headLimit,Profile profile,double speed,long intent) {
            if(!Float.isFinite(wantedBody)||!Float.isFinite(wantedHead)||!Float.isFinite(wantedPitch)
                    ||!Float.isFinite(headLimit)||!Double.isFinite(speed)||speed<=0||profile==null)
                return new AttentionFacingPolicy.Facing((float)finiteAngle(actualBody),(float)finiteAngle(actualHead),(float)clamp(finiteAngle(actualPitch),-89,89));
            if(!lastStop&&start!=null&&now==tick&&cached!=null&&wantedBody==lastBody&&wantedHead==lastHead&&wantedPitch==lastPitch
                    &&headLimit==lastLimit&&speed==lastSpeed&&intent==lastIntent&&profile.equals(lastProfile))return cached;
            begin(now,actualBody,actualHead,actualPitch,intent);lastStop=false;
            wantedPitch=(float)clamp(wantedPitch,-89,89);
            double amplitude=Math.max(Math.abs(wrap(wantedBody-start.body.angle)),Math.hypot(wrap(wantedHead-start.head.angle),wantedPitch-start.pitch.angle));
            TurnGesture gesture=start.gesture;
            boolean changed=intent!=start.intent;
            if(changed)gesture=null;
            if(gesture!=null&&now-gesture.started>gesture.body.duration()*1.45+2&&amplitude<.08
                    &&Math.abs(start.head.velocity)+Math.abs(start.body.velocity)+Math.abs(start.pitch.velocity)<.12)gesture=null;
            boolean urgentRedirect=gesture!=null&&profile.equals(URGENT)
                    &&(!gesture.urgent||Math.abs(wrap(wantedHead-start.head.target))>20);
            boolean replan=urgentRedirect||gesture!=null&&now-gesture.started>=6&&amplitude>35
                    &&Math.abs(wrap(wantedHead-gesture.head.goal()))>20;
            long number=start.number;
            if((gesture==null&&amplitude>=8)||(changed&&amplitude>3)||replan)
                gesture=TurnGesture.create(seed,++number,now,intent,profile,start.body,start.head,start.pitch,wantedBody,wantedHead,wantedPitch,speed);
            var style=gesture==null?TRACKING:gesture.style;
            double elapsed=gesture==null?0:now-gesture.started;
            Profile bodyProfile=profile.equals(IDLE)?IDLE_BODY:profile.equals(COMBAT)?COMBAT_BODY:profile;
            Axis body=advance(start.body,wantedBody,gesture==null?null:gesture.body,elapsed,style,bodyProfile,bodyProfile==profile?speed:speed*.9,changed,0,180);
            Axis head=advance(start.head,wantedHead,gesture==null?null:gesture.head,elapsed,style,profile,speed,changed,body.angle,Math.max(0,headLimit));
            Axis pitch=advance(start.pitch,wantedPitch,gesture==null?null:gesture.pitch,elapsed,style,profile,Math.min(speed,12),changed,0,180);
            if(Math.abs(pitch.angle)>89)pitch=new Axis(Math.copySign(89,pitch.angle),0,0,pitch.target,pitch.tracking);
            double relative=wrap(head.angle-body.angle),limit=Math.max(0,headLimit);
            if(Math.abs(relative)>limit)head=new Axis(wrap(body.angle+clamp(relative,-limit,limit)),body.velocity,body.acceleration,head.target,head.tracking);
            current=new Snapshot(body,head,pitch,gesture,number,intent);
            lastBody=wantedBody;lastHead=wantedHead;lastPitch=wantedPitch;lastLimit=headLimit;lastSpeed=speed;lastIntent=intent;lastProfile=profile;
            cached=new AttentionFacingPolicy.Facing((float)body.angle,(float)head.angle,(float)pitch.angle);return cached;
        }
    }
}

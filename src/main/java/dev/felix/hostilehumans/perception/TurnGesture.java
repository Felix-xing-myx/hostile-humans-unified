package dev.felix.hostilehumans.perception;

/** One sampled gesture, shared by yaw/pitch/body. Sampling never consumes a caller's random stream. */
final class TurnGesture {
    record Style(double tempo,double skew,double landing,double response,double braking,double jerk) {}
    record Reference(double angle,double velocity,double acceleration,double jerk) {}
    record Segment(double angle,double velocity,double acceleration,double goal,double duration,double skew,double landing) {
        Reference at(double elapsed,double currentGoal,double tracking) {
            double drift=TurnResponse.wrap(currentGoal-goal);
            if(elapsed>=duration) {
                double correction=Math.max(2,duration*.45);
                if(elapsed>=duration+correction)return new Reference(currentGoal,tracking,0,0);
                double u=clamp((elapsed-duration)/correction,0,1);
                double residual=TurnResponse.wrap(goal-angle)*landing;
                return new Reference(currentGoal+residual*(1-smooth(u)),
                        tracking-residual*derivative(u)/correction,-residual*second(u)/(correction*correction),
                        -residual*third(u)/(correction*correction*correction));
            }
            double u=clamp(elapsed/duration,0,1),g=u*(1-u);
            // This warp preserves both endpoint velocity and acceleration (w'=1,w''=0).
            double w=u+16*skew*g*g*g;
            double dw=1+48*skew*g*g*(1-2*u);
            double ddw=96*skew*g*(1-5*u+5*u*u),dddw=96*skew*(1-12*u+30*u*u-20*u*u*u);
            double distance=TurnResponse.wrap(goal-angle)*(1+landing),v=velocity*duration,a=acceleration*duration*duration;
            double c3=10*distance-6*v-1.5*a,c4=-15*distance+8*v+1.5*a,c5=6*distance-3*v-.5*a;
            double q=angle+w*(v+w*(.5*a+w*(c3+w*(c4+w*c5))));
            double dq=v+w*(a+w*(3*c3+w*(4*c4+w*5*c5)));
            double ddq=a+w*(6*c3+w*(12*c4+w*20*c5));
            double dddq=6*c3+24*c4*w+60*c5*w*w;
            double blend=smooth(u),db=derivative(u)/duration,ddb=second(u)/(duration*duration);
            return new Reference(q+blend*drift,dq*dw/duration+db*drift+blend*tracking,
                    (ddq*dw*dw+dq*ddw)/(duration*duration)+ddb*drift+2*db*tracking,
                    (dddq*dw*dw*dw+3*ddq*dw*ddw+dq*dddw)/(duration*duration*duration)
                            +third(u)*drift/(duration*duration*duration)+3*ddb*tracking);
        }
    }
    final long number,started,intent;
    final boolean urgent;
    final Style style;
    final Segment body,head,pitch;
    private TurnGesture(long number,long started,long intent,boolean urgent,Style style,Segment body,Segment head,Segment pitch){
        this.number=number;this.started=started;this.intent=intent;this.urgent=urgent;this.style=style;this.body=body;this.head=head;this.pitch=pitch;
    }
    static TurnGesture create(long seed,long number,long now,long intent,TurnResponse.Profile profile,
            TurnResponse.Axis body,TurnResponse.Axis head,TurnResponse.Axis pitch,double wantedBody,double wantedHead,double wantedPitch,double speed) {
        double amplitude=Math.max(Math.abs(TurnResponse.wrap(wantedBody-body.angle())),
                Math.hypot(TurnResponse.wrap(wantedHead-head.angle()),wantedPitch-pitch.angle()));
        Style style=sample(seed,number,amplitude,profile.equals(TurnResponse.URGENT));
        double base=profile.equals(TurnResponse.URGENT)?1.5+.58*Math.sqrt(amplitude):2+.75*Math.sqrt(amplitude);
        double planning=profile.equals(TurnResponse.URGENT)?1.25:profile.equals(TurnResponse.COMBAT)?1.50:1;
        double duration=Math.max(3,base*style.tempo*planning);
        // Fit sampled curves to physical limits before execution, rather than clipping an impossible flick.
        if(!profile.equals(TurnResponse.IDLE))for(int pass=0;pass<2;pass++) {
            double bodyFactor=profile.equals(TurnResponse.URGENT)?1:1.08;
            double ratio=Math.max(required(segment(head,wantedHead,duration,style),profile,style,1,speed),
                    Math.max(required(segment(pitch,wantedPitch,duration,style),profile,style,1,Math.min(speed,12)),
                            required(segment(body,wantedBody,duration*bodyFactor,style),profile,style,
                                    profile.equals(TurnResponse.URGENT)?1:.8,profile.equals(TurnResponse.URGENT)?speed:speed*.9)));
            if(ratio<=1)break;
            duration*=Math.min(1.6,ratio)*1.02;
        }
        double bodyDuration=duration*(profile.equals(TurnResponse.URGENT)?1:1.08);
        return new TurnGesture(number,now,intent,profile.equals(TurnResponse.URGENT),style,
                segment(body,wantedBody,bodyDuration,style),segment(head,wantedHead,duration,style),segment(pitch,wantedPitch,duration,style));
    }
    private static Segment segment(TurnResponse.Axis from,double goal,double duration,Style style){
        return new Segment(from.angle(),from.velocity(),from.acceleration(),goal,duration,style.skew,style.landing);
    }
    private static double required(Segment segment,TurnResponse.Profile profile,Style style,double bodyFactor,double speed){
        double ratio=1;
        for(int i=0;i<=12;i++){
            Reference r=segment.at(segment.duration*i/12,segment.goal,0);
            ratio=Math.max(ratio,Math.abs(r.velocity)/Math.max(.01,Math.min(speed,profile.speed()*(bodyFactor<1?.9:1))));
            double acceleration=Math.max(profile.acceleration()*bodyFactor*Math.min(1,style.braking),Math.abs(segment.acceleration));
            ratio=Math.max(ratio,Math.sqrt(Math.abs(r.acceleration)/acceleration));
            ratio=Math.max(ratio,Math.cbrt(Math.abs(r.jerk)/(profile.jerk()*bodyFactor*style.jerk)));
        }
        return ratio;
    }
    static Style sample(long seed,long number,double amplitude,boolean urgent){
        long key=mix(seed+number*0x9e3779b97f4a7c15L);
        double individual=.95+.1*unit(seed),tempo=clamp((.8+(urgent?.045:.10)*normal(key))*individual,urgent?.72:.65,urgent?.95:1.10);
        double skew=(unit(key+1)*2-1)*(urgent?.15:.30),landing=0;
        if(!urgent&&amplitude>=10&&unit(key+2)<.30)
            landing=clamp(-.15+.6*normal(key+3),-Math.min(1.5,amplitude*.02),Math.min(1.5,amplitude*.02))/amplitude;
        return new Style(tempo,skew,landing,.9+.2*unit(key+4),.85+.3*unit(key+5),.85+.3*unit(key+6));
    }
    static long mix(long x){x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;x=(x^(x>>>27))*0x94d049bb133111ebL;return x^(x>>>31);}
    private static double unit(long seed){return (mix(seed)>>>11)*0x1.0p-53;}
    private static double normal(long seed){return Math.sqrt(-2*Math.log(Math.max(0x1.0p-53,unit(seed))))*Math.cos(2*Math.PI*unit(seed+31));}
    static double smooth(double u){return u*u*u*(10+u*(-15+6*u));}
    private static double derivative(double u){return 30*u*u*(1-u)*(1-u);}
    private static double second(double u){return 60*u*(1-u)*(1-2*u);}
    private static double third(double u){return 60*(1-6*u+6*u*u);}
    private static double clamp(double x,double low,double high){return Math.max(low,Math.min(high,x));}
}

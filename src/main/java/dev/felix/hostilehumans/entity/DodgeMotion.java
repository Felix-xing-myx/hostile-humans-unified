package dev.felix.hostilehumans.entity;

/** Pure client presentation clock. Rejecting a replay never rejects a successful gameplay dodge. */
public final class DodgeMotion {
    public static final float DURATION = 12;
    public static final float SHUTTER_TICKS = 3F;
    public static final int COOLDOWN_TICKS = 10;
    /** Full exposure tail plus half a second of rest; gameplay dodge rolls remain independent. */
    public static final int MIN_REPLAY_TICKS = (int)Math.ceil(DURATION + SHUTTER_TICKS + COOLDOWN_TICKS);
    public static final float PEAK_DISTANCE = .60F;
    private static final float PEAK_TICK = 3F;
    private static final float RETURN_TICK = 7F;
    private static final float REBOUND_TICK = 9F;
    private record Stroke(int tick,int direction) {
        float sample(float elapsed){
            if(elapsed<=0||elapsed>=DURATION)return 0;
            // Continuous velocity through the return shoulder; zero velocity at the peak and rebound.
            float displacement;
            if(elapsed<PEAK_TICK)displacement=settle(elapsed/PEAK_TICK,0,PEAK_DISTANCE);
            else if(elapsed<RETURN_TICK)displacement=hermite(elapsed-PEAK_TICK,RETURN_TICK-PEAK_TICK,PEAK_DISTANCE,.12F,0,-.10F);
            else if(elapsed<REBOUND_TICK)displacement=hermite(elapsed-RETURN_TICK,REBOUND_TICK-RETURN_TICK,.12F,-.035F,-.10F,0);
            else displacement=settle((elapsed-REBOUND_TICK)/(DURATION-REBOUND_TICK),-.035F,0);
            return direction*displacement;
        }
    }
    private Stroke current;
    public boolean trigger(int tick){
        if(current!=null&&tick>=current.tick()&&(long)tick-current.tick()<MIN_REPLAY_TICKS)return false;
        current=new Stroke(tick,current==null?1:-current.direction());return true;
    }
    public float offset(int tick,float partial){return current==null?0:current.sample(elapsed(tick,partial));}
    public float attackBodyWeight(int tick,float partial){
        if(current==null)return 1;
        float age=Math.max(0,phase(tick,partial));
        float enter=Math.min(1,age/.65F),exit=Math.min(1,Math.max(0,(age-3.5F)/(9F-3.5F)));
        return 1-.92F*ease(enter,0,1)*(1-ease(exit,0,1));
    }
    public boolean blink(int tick,float partial){
        if(current==null)return false;float age=phase(tick,partial);
        return age>=.7F&&age<1.5F||age>=2.8F&&age<3.35F;
    }
    public boolean hasExposure(int tick,float partial,float shutter){
        return current!=null&&elapsed(tick,partial)>=0&&elapsed(tick,partial)<DURATION+shutter;
    }
    private float phase(int tick,float partial){return elapsed(tick,partial)*9F/DURATION;}
    private float elapsed(int tick,float partial){return (long)tick-current.tick()+partial;}
    private static float settle(float t,float from,float to){return from+(to-from)*t*t*t*(t*(t*6-15)+10);}
    private static float hermite(float elapsed,float duration,float from,float to,float startVelocity,float endVelocity){
        float t=elapsed/duration,t2=t*t,t3=t2*t;
        return (2*t3-3*t2+1)*from+(t3-2*t2+t)*duration*startVelocity
                +(-2*t3+3*t2)*to+(t3-t2)*duration*endVelocity;
    }
    private static float ease(float t,float from,float to){return from+(to-from)*t*t*(3-2*t);}
}

package dev.felix.hostilehumans.entity;

/** Exact visual replay boundaries, independent of damage randomness or a client/server. */
public final class DodgeMotionTest {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        var motion=new DodgeMotion();check(motion.trigger(100),"first visual");
        float peak=motion.offset(103,0);check(Math.abs(peak-.60F)<1e-6,"six-tenths peak");
        for(int t=100;t<100+DodgeMotion.MIN_REPLAY_TICKS;t++)check(!motion.trigger(t),"active/tail/cooldown event cannot restart visual");
        check(Math.abs(motion.offset(103,0)-peak)<1e-6,"ignored events do not alter curve/direction");
        check(!motion.hasExposure(115,0,DodgeMotion.SHUTTER_TICKS),"trail completed before cooldown");
        check(motion.offset(119,0)==0,"no displacement during cooldown");
        check(DodgeMotion.MIN_REPLAY_TICKS==25,"full exposure plus ten-tick rest rounded up");
        check(motion.trigger(125),"next visual after complete effect and half-second rest");
        check(Math.abs(motion.offset(128,0)+.60F)<1e-6,"accepted replays alternate direction");
        for(int i=0;i<=320;i++)check(Math.abs(motion.offset(125+i/20,(i%20)/20F))<=DodgeMotion.PEAK_DISTANCE+1e-6,"no amplitude overshoot");
        check(Math.abs(motion.attackBodyWeight(145,0)-1)<1e-6,"ordinary attacks regain full motion");
        for(float boundary:new float[]{3,7,9,12}){
            float center=motion.offset(125,boundary),left=motion.offset(125,boundary-.001F),right=motion.offset(125,boundary+.001F);
            check(Math.abs((center-left)-(right-center))<1e-5,"velocity continuous at curve boundary");
        }
        check(motion.offset(132,0)<0&&motion.offset(134,0)>0,"return shoulder then small reverse settle");
        System.out.println("DodgeMotionTest: amplitude, complete-effect cooldown and ignored-trigger stability passed");
    }
}

package dev.felix.hostilehumans.core;

/** Keep a positive hit margin, including short-reach weapons and small targets. */
public final class MeleeSpacingPolicy {
    private MeleeSpacingPolicy() {}
    public static double preferred(double reach) {
        return Math.max(Math.min(0.35D, reach * 0.5D), Math.min(reach * 0.78D, reach - 0.5D));
    }
    public static double preferred(double reach, boolean profiled) {
        return Math.max(Math.min(.35, reach * .5), reach - (profiled ? .75 : .25));
    }
    public static double tooClose(double reach, boolean profiled) {
        return Math.max(Math.min(.15, reach * .25), reach - (profiled ? 1 : .5));
    }
    public static double tooClose(double reach) {
        return Math.max(Math.min(0.15D, preferred(reach) * 0.5D),
                Math.min(preferred(reach) - 0.25D, reach * 0.5D));
    }
    /** Movement reserve for a receding opponent, never an extension to the real attack shape. */
    public static double chasePreferred(double reach,double outwardSpeed,int upswing) {
        double normal=preferred(reach);
        if(!Double.isFinite(outwardSpeed)||outwardSpeed<.04)return normal;
        double reserve=Math.min(1.5,Math.max(0,outwardSpeed)*(Math.min(12,Math.max(1,upswing))+2));
        return Math.min(normal,Math.max(tooClose(reach)+.15,normal-reserve));
    }
    public static double chasePreferred(double reach, double outwardSpeed, int upswing, boolean profiled) {
        double normal = preferred(reach, profiled);
        if (!Double.isFinite(outwardSpeed) || outwardSpeed < .04) return normal;
        double reserve = Math.min(1.5, Math.max(0, outwardSpeed) * (Math.min(12, Math.max(1, upswing)) + 2));
        return Math.min(normal, Math.max(Math.min(.5, reach * .5), normal - reserve));
    }
}

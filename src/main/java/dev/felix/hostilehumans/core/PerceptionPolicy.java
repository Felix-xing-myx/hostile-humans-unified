package dev.felix.hostilehumans.core;

/** Pure timing/geometry rules; damage never resurrects an expired live-position lease. */
public final class PerceptionPolicy {
    private PerceptionPolicy() {}
    public static boolean live(long now,long until) { return now < until; }
    public static long hitDeadline(boolean marked,long now,long until,int duration) {
        return marked && live(now,until) ? now + duration : until;
    }
    public static int recognition(double distance,boolean alert,boolean familiar,double base,double slope,
            int maximum,int minimum,double alertFactor,double familiarFactor,double combinedFactor) {
        double single=Math.min(alertFactor,familiarFactor);
        double combined=Math.max(single*.75,Math.min(single,combinedFactor));
        double factor=alert ? familiar ? combined : alertFactor : familiar ? familiarFactor : 1;
        return Math.max(Math.min(minimum,maximum),(int)Math.ceil(Math.min(maximum,base+slope*Math.max(0,distance))*factor));
    }
    public static double halfAngle(double distance,boolean familiar,double multiplier,double capDegrees) {
        return halfAngle(distance,familiar,multiplier,capDegrees,8,32,64,160,120,90);
    }
    public static double halfAngle(double distance,boolean familiar,double multiplier,double capDegrees,
            double nearDistance,double middleDistance,double farDistance,double nearDegrees,double middleDegrees,double farDegrees) {
        double middle=Math.max(nearDistance+.01,middleDistance),far=Math.max(middle+.01,farDistance);
        double full;
        if(distance<=nearDistance)full=nearDegrees;
        else if(distance<=middle)full=lerp(nearDegrees,middleDegrees,(distance-nearDistance)/(middle-nearDistance));
        else full=lerp(middleDegrees,farDegrees,Math.min(1,(distance-middle)/(far-middle)));
        double angle=Math.toRadians(full*.5);
        // A legacy familiar cap must never make a known target harder to see than a new one.
        return familiar ? Math.max(angle,Math.min(Math.toRadians(capDegrees),angle*multiplier)) : angle;
    }
    private static double lerp(double a,double b,double t){return a+(b-a)*Math.max(0,Math.min(1,t));}
}

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
        double angle=Math.acos(Math.max(-1,Math.min(1,1-.4/Math.max(.1,distance))));
        return familiar ? Math.min(Math.toRadians(capDegrees),angle*multiplier) : angle;
    }
}

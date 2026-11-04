package dev.felix.hostilehumans.perception;

/** Bounded threat arithmetic. Identity/attack permission belongs to the server adapter. */
public final class ThreatScorePolicy {
    private ThreatScorePolicy() {}
    private static double bounded(double value,double cap){
        return Double.isFinite(value)?Math.max(0,Math.min(cap,value)):0;
    }
    public static double intrinsic(double health,double armor,double attack,boolean ranged){
        return 8+bounded(health,80)*.25+bounded(armor,20)*.6
                +bounded(attack,12)*2+(ranged?8:0);
    }
    public static double score(double intrinsic,double distance,double damage,double ownHealth,boolean attacking,double near){
        double d=bounded(distance,4096),base=bounded(intrinsic,100);
        double pressure=Math.min(60,bounded(damage,10000)/Math.max(1,bounded(ownHealth,10000))*120);
        return base/(1+d/12)+45/(1+d/4)+(d<=near?20:0)+pressure+(attacking?20:0);
    }
    /** Tactical danger is portable; sender distance and victim-specific aggro are not. */
    public static double reportPriority(boolean hostileHuman,double recentDamage,double ownHealth) {
        return (hostileHuman?60:0)+Math.min(20,bounded(recentDamage,10000)/Math.max(1,bounded(ownHealth,10000))*40);
    }
    /** Damage sums reward both hard hits and repeated hits in the same observation window. */
    public static double impactPriority(double damage, double currentDamage, double ownHealth,
                                        boolean hostileHuman) {
        double health = Math.max(1, bounded(ownHealth, 10000));
        double received = bounded(damage, 10000);
        double rival = bounded(currentDamage, 10000);
        double threshold = Math.max(2, health * (hostileHuman ? .04 : .08));
        return received >= threshold && received > rival * 1.5 + 1
                ? Math.min(100, received / health * 100) : 0;
    }

    public static boolean stronger(double current,double challenger,double ratio,double absolute){
        return Double.isFinite(challenger)&&challenger>Math.max(current*(1+ratio),current+absolute);
    }
}

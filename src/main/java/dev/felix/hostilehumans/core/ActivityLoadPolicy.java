package dev.felix.hostilehumans.core;

/** Pure load policy; changes work density, never gameplay authorization or survival rules. */
public final class ActivityLoadPolicy {
    public enum Mode { SPARSE, NORMAL, CROWDED, BATTLE }
    private ActivityLoadPolicy() {}
    public static Mode desired(int count,int sparse,int crowded,int battle) {
        if(count>=battle)return Mode.BATTLE;
        if(count>=crowded)return Mode.CROWDED;
        return count<=sparse?Mode.SPARSE:Mode.NORMAL;
    }
    public static int interval(int base,double factor,boolean urgent) {
        if(base<1||!Double.isFinite(factor)||factor<=0)throw new IllegalArgumentException("invalid interval");
        return urgent?base:Math.max(1,(int)Math.ceil(base*factor));
    }
    public static int capacity(int hardLimit,int minimumJob,double factor) {
        if(hardLimit<1||minimumJob<1||minimumJob>hardLimit)throw new IllegalArgumentException("invalid capacity");
        return Math.max(minimumJob,Math.min(hardLimit,(int)Math.floor(hardLimit*factor)));
    }
}

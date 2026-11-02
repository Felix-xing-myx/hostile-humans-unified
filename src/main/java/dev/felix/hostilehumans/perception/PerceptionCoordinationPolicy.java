package dev.felix.hostilehumans.perception;

/** Shared scheduling/selection rules, independent of Minecraft state and rendering. */
public final class PerceptionCoordinationPolicy {
    private PerceptionCoordinationPolicy() {}
    public static boolean switchAllowed(long now,long changed,int minimumHold,boolean close,boolean urgent){
        return close||urgent||now>=changed&&now-changed>=Math.max(0,minimumHold);
    }
    public static int evidenceInterval(int interval,int identifying,int rays,boolean maintainsMain){
        int rounds=(Math.max(1,identifying)*(maintainsMain?2:1)+Math.max(1,rays)-1)/Math.max(1,rays);
        return Math.max(1,interval)*Math.max(1,rounds);
    }
    public static int nextIndex(int cursor,int count){return Math.floorMod(cursor,Math.max(1,count));}
}

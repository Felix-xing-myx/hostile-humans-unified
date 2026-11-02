package dev.felix.hostilehumans.inventory;

/** Path failures outlive inventory edits and do not blindly retry after the first cooldown. */
public final class LootRetryPolicy {
    private LootRetryPolicy() {}
    public static int cooldown(int attempts){return Math.min(1200,200 << (Math.max(1,Math.min(4,attempts))-1));}
    public static boolean blocked(int elapsed,int attempts,boolean sceneChanged){
        return elapsed>=0&&(elapsed<cooldown(attempts)||elapsed<1200&&!sceneChanged);
    }
}

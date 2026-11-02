package dev.felix.hostilehumans.monitor.client;

/** Native TaCZ's 300ms marker envelope, isolated from its real-player global timestamps. */
public final class MonitorHitFeedback {
    public record Frame(float alpha, float extraOffset, boolean headshot) {}
    private long hit=Long.MIN_VALUE,head=Long.MIN_VALUE,kill=Long.MIN_VALUE;
    public void clear() { hit=head=kill=Long.MIN_VALUE; }
    public void mark(long now,boolean headshot,boolean killed) { hit=now;if(headshot)head=now;if(killed)kill=now; }
    private static double age(long now,long stamp) {return stamp==Long.MIN_VALUE||now<stamp?Double.POSITIVE_INFINITY:(now-stamp)/1_000_000D;}
    public Frame frame(long now) {
        double k=age(now,kill),h=age(now,hit),a=k<=300?k:h;
        return a>=300?null:new Frame((float)(1-a/300),(float)(k<=300?k*4/300:0),age(now,head)<=300);
    }
}

package dev.felix.hostilehumans.behavior.combat.ranged;

/** Progress is physical movement; lack of budget alone never penalizes an opponent. */
public final class RangedEngagementProgressTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        var p = new RangedEngagementProgress();
        for (int i=0;i<1000;i++) p.observe(i,0,0,0,false);
        check(p.relax() && !p.deferred(999));
        p = new RangedEngagementProgress();
        for (int i=0;i<=100;i++) {
            p.observe(i,0,0,0,false);
            if(i==20 || i==40 || i==80) p.failure(i);
        }
        check(p.deferred(100));
        p.observe(201,0,0,0,false); check(!p.deferred(201));
        p = new RangedEngagementProgress();
        for(int i=0;i<500;i++) { p.observe(i,i*.1,0,0,false); if(i%10==0)p.failure(i); }
        check(!p.deferred(499));
        p = new RangedEngagementProgress();
        for(int i=0;i<28;i++)p.observe(i,0,0,0,false);
        for(int i=0;i<500;i++)p.observe(27,0,0,0,false);
        check(!p.relax());
        p.observe(1000,0,0,0,false); check(!p.relax());
        p.observe(1001,0,0,0,false); check(p.relax());
        p.observe(1002,0,0,0,true); check(!p.relax() && !p.deferred(1002));
    }
}

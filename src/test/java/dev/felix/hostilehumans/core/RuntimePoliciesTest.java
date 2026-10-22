package dev.felix.hostilehumans.core;

import java.util.*;

/** Pure-JVM contract tests: no game, client or dedicated server is started. */
public final class RuntimePoliciesTest {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("check " + checks);
    }
    public static void main(String[] args) {
        ActionCoordinatorTest.main(args);
        RouteExecutionLeaseTest.main(args);
        SoundExclusionMemory sounds=new SoundExclusionMemory();
        sounds.fail("a",0,0,0,0,10,0,0,400,6,4,8,8);
        check(sounds.blocked("a",1,0,0,0,15,4,0));
        check(!sounds.blocked("a",1,0,0,0,17,0,0));
        check(!sounds.blocked("a",1,0,0,0,10,5,0));
        sounds.fail("a",300,0,0,0,11,0,0,400,6,4,8,8);
        check(sounds.regions().size()==1&&sounds.regions().get(0).until()==400);
        check(!sounds.blocked("a",400,0,0,0,10,0,0));
        sounds.fail("a",500,0,0,0,10,0,0,400,6,4,8,2);
        check(!sounds.blocked("a",501,9,0,0,10,0,0));
        sounds.fail("a",600,0,0,0,10,0,0,400,6,4,8,2);
        check(!sounds.blocked("b",601,0,0,0,10,0,0));
        for(int i=0;i<5;i++)sounds.fail("a",700,0,0,0,i*20,0,0,400,6,4,8,2);
        check(sounds.regions().size()==2);
        check(SoundExclusionMemory.sound("gunshot")&&SoundExclusionMemory.sound("aid"));
        check(!SoundExclusionMemory.sound("hurt")&&!SoundExclusionMemory.sound("beacon")&&!SoundExclusionMemory.sound("last"));
        SoundExclusionMemory dynamicSounds=new SoundExclusionMemory();
        long at=0;int[] cooldownStages={240,480,960,1200,1200};
        for(int duration:cooldownStages){
            dynamicSounds.fail("a",at,0,0,0,10,0,0,240,1200,1200,6,4,8,8);
            var region=dynamicSounds.regions().get(0);check(region.duration()==duration);
            check(dynamicSounds.blocked("a",at+duration-1,0,0,0,10,0,0));
            check(!dynamicSounds.blocked("a",at+duration,0,0,0,10,0,0));
            at+=duration;
        }
        dynamicSounds.fail("a",at+1200,0,0,0,10,0,0,240,1200,1200,6,4,8,8);
        check(dynamicSounds.regions().get(0).duration()==240); // history expires
        SoundExclusionMemory overlap=new SoundExclusionMemory();
        overlap.fail("a",0,0,0,0,0,0,0,240,1200,1200,6,4,8,8);
        overlap.fail("a",1,0,0,0,10,0,0,240,1200,1200,6,4,8,8);
        check(overlap.regions().get(1).duration()==480); // center outside old zone, volumes overlap
        overlap.fail("a",2,0,0,0,30,0,0,240,1200,1200,6,4,8,8);
        check(overlap.regions().get(2).duration()==240); // distinct region
        overlap.fail("a",3,0,0,0,0,9,0,240,1200,1200,6,4,8,8);
        check(overlap.regions().get(3).duration()==240); // vertical volumes don't overlap
        overlap.fail("a",4,0,0,0,10,0,0,240,1200,1200,6,4,8,8);
        check(overlap.regions().size()==4&&overlap.regions().get(1).until()==481); // filtered notification
        int[] samples={0};
        check(LocalPathFollowPolicy.corridor(0,0,2,2,(x,z,dx,dz)->{samples[0]++;return true;}));
        check(samples[0]<=13&&samples[0]>2);
        check(!LocalPathFollowPolicy.corridor(0,0,2,0,(x,z,dx,dz)->x<.9||x>1.1)); // floor gap mid-route
        check(!LocalPathFollowPolicy.corridor(0,0,2,2,(x,z,dx,dz)->!(x>.7&&x<1.3&&z>.7&&z<1.3))); // wall corner
        check(!LocalPathFollowPolicy.corridor(0,0,2,0,(x,z,dx,dz)->x<=1)); // command leash between endpoints
        check(!LocalPathFollowPolicy.corridor(0,0,4,0,(x,z,dx,dz)->true));
        check(!LocalPathFollowPolicy.corridor(0,0,Double.NaN,0,(x,z,dx,dz)->true));
        check(!LocalPathFollowPolicy.corridor(0,0,2,0,(x,z,dx,dz)->dx<=.25&&x<.5));
        check(Math.abs(MeleeSpacingPolicy.preferred(3.3,true)-2.55)<1e-9);
        check(Math.abs(MeleeSpacingPolicy.preferred(3.3,false)-3.05)<1e-9);
        check(MeleeSpacingPolicy.chasePreferred(3.3,.28,6,true)<2.55);
        check(MeleeSpacingPolicy.chasePreferred(3.3,.28,6,true)>=.5);
        for (double reach : new double[]{.1,.3,1,3.3,16}) for (boolean profiled : new boolean[]{false,true}) {
            check(MeleeSpacingPolicy.tooClose(reach,profiled)>0);
            check(MeleeSpacingPolicy.tooClose(reach,profiled)<MeleeSpacingPolicy.preferred(reach,profiled));
            check(MeleeSpacingPolicy.preferred(reach,profiled)<reach);
        }
        check(MeleeSpacingPolicy.chasePreferred(3.3,0,6)==MeleeSpacingPolicy.preferred(3.3));
        check(MeleeSpacingPolicy.chasePreferred(3.3,.28,6)<MeleeSpacingPolicy.preferred(3.3));
        check(MeleeSpacingPolicy.chasePreferred(3.3,.28,6)>MeleeSpacingPolicy.tooClose(3.3));
        check(MeleeSpacingPolicy.chasePreferred(3.3,.28,30)==MeleeSpacingPolicy.chasePreferred(3.3,.28,12));
        check(MeleeSpacingPolicy.chasePreferred(3.3,Double.NaN,6)==MeleeSpacingPolicy.preferred(3.3));
        check(SprintHopPolicy.clearRunway(0,0,0,.3,0,new double[][]{{1,0,0},{2,0,0},{3,0,0},{5,0,0}}));
        check(!SprintHopPolicy.clearRunway(0,0,0,.3,0,new double[][]{{1,0,0},{1,0,1},{1,0,5}}));
        check(!SprintHopPolicy.clearRunway(0,0,0,.3,0,new double[][]{{1,0,0},{2,0,1},{5,0,4}}));
        check(!SprintHopPolicy.clearRunway(0,0,0,.3,0,new double[][]{{1,0,0},{2,1,0},{5,1,0}}));
        check(!SprintHopPolicy.clearRunway(0,0,0,.3,0,new double[][]{{1,0,0}}));
        check(!SprintHopPolicy.clearRunway(0,0,0,.3,0,new double[][]{{-1,0,0},{-5,0,0}}));
        check(!SprintHopPolicy.clearRunway(0,0,0,Double.NaN,0,new double[][]{{1,0,0}}));
        for (int i = 1; i < 100; i++) {
            double reach = i / 100.0D;
            check(MeleeSpacingPolicy.preferred(reach) > 0 && MeleeSpacingPolicy.preferred(reach) < reach);
            check(MeleeSpacingPolicy.tooClose(reach) > 0
                    && MeleeSpacingPolicy.tooClose(reach) < MeleeSpacingPolicy.preferred(reach));
        }
        MeleePursuitProgress pursuit = new MeleePursuitProgress();
        for (int tick = 0; tick < 30; tick++) check(!pursuit.stalled(tick, 1, 0, 0, 0, true));
        check(pursuit.stalled(30, 1, 0, 0, 0, true));
        for (int tick = 31; tick < 60; tick++) check(!pursuit.stalled(tick, 1, 0, 0, 0, true));
        check(pursuit.stalled(60, 1, 0, 0, 0, true));
        // Repeated path replacement cannot reset observation of stationary feet.
        check(!pursuit.stalled(61, 2, 0, 0, 0, true));
        check(!pursuit.stalled(90, 2, 0, 0, 0, true));
        check(pursuit.stalled(91, 2, 0, 0, 0, true));
        check(!pursuit.stalled(92, 2, 0, 0, 0, false)); // legal in-reach pause or hold order
        check(!pursuit.stalled(120, 2, 0, 0, 0, true));
        check(!pursuit.stalled(121, 2, 0.31, 0, 0, true)); // real movement resets the timer
        check(!pursuit.stalled(150, 2, 0.31, 0, 0, true));
        check(pursuit.stalled(151, 2, 0.31, 0, 0, true));
        check(!pursuit.stalled(1, 2, 0.31, 0, 0, true)); // tick reset
        for (int tick = 2; tick < 300; tick++)
            check(!pursuit.stalled(tick, 2, tick * 0.105D, 0, 0, true)); // ordinary walking
        for (int i = 100; i <= 1800; i++) {
            double reach = i / 100.0D;
            check(MeleeSpacingPolicy.preferred(reach) < reach);
            check(reach - MeleeSpacingPolicy.preferred(reach) >= 0.5D - 1.0E-9D);
            check(MeleeSpacingPolicy.tooClose(reach) < MeleeSpacingPolicy.preferred(reach));
        }
        EquipmentWearCooldown wear = new EquipmentWearCooldown(4, 60);
        for (int slot = 0; slot < 4; slot++) check(wear.ready(slot, 0));
        wear.lostDurability(0, 0);
        check(!wear.ready(0, 59));
        for (int slot = 1; slot < 4; slot++) check(wear.ready(slot, 0));
        wear.lostDurability(1, 10);
        check(wear.ready(0, 60));
        check(!wear.ready(1, 69));
        check(wear.ready(1, 70));
        for (int tick = 0; tick < 600; tick++) {
            for (int slot = 0; slot < 4; slot++) {
                if (wear.ready(slot, tick)) {
                    wear.lostDurability(slot, tick);
                    check(!wear.ready(slot, tick + 59));
                    check(wear.ready(slot, tick + 60));
                }
            }
        }
        UUID human = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        OwnerIndex index = new OwnerIndex();
        check(index.assign(human, first) == null);
        check(index.members(first).equals(Set.of(human)));
        Set<UUID> snapshot = index.members(first);
        check(index.assign(human, second).equals(first));
        check(index.members(first).isEmpty());
        check(index.members(second).equals(Set.of(human)));
        check(snapshot.equals(Set.of(human)));
        check(index.assign(human, second).equals(second));
        check(index.members(second).size() == 1);
        for (int i = 0; i < 1000; i++) {
            check(index.assign(human, second).equals(second));
            check(index.owner(human).equals(second));
        }
        check(index.assign(human, null).equals(second));
        check(index.members(second).isEmpty());
        check(index.owner(human) == null);
        check(index.assign(human, null) == null);
        check(new OwnerIndex().members(first).isEmpty());
        try { snapshot.clear(); throw new AssertionError("mutable ownership snapshot"); }
        catch (UnsupportedOperationException expected) { checks++; }
        BudgetedUpdates<Integer> queue = new BudgetedUpdates<>();
        for (int repeat = 0; repeat < 10; repeat++)
            for (int n = 0; n < 10_000; n++) queue.offer(n);
        check(queue.size() == 10_000);
        check(queue.drain(0).isEmpty());
        int expected = 0;
        while (queue.size() > 0) {
            List<Integer> batch = queue.drain(32);
            check(batch.size() <= 32);
            for (int value : batch) check(value == expected++);
        }
        check(expected == 10_000);
        queue.offer(1);
        List<Integer> detached = queue.drain(1);
        queue.offer(1);
        check(detached.equals(List.of(1)) && queue.size() == 1);
        try { queue.drain(-1); throw new AssertionError("negative budget"); }
        catch (IllegalArgumentException expectedError) { checks++; }
        System.out.println("RuntimePoliciesTest: " + checks + " checks passed");
    }
}

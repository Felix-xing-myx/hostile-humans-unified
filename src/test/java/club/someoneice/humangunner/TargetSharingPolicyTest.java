package club.someoneice.humangunner;

/** Lease state-machine regression; no world, targeting mutation, or rendering is required. */
public final class TargetSharingPolicyTest {
    public static void main(String[] args){
        var expiry=new HumanTargetSharing.Window(100,true);
        check(expiry.update(219,true),"confirmed lease remains live before six seconds");
        check(!expiry.update(220,true),"six second boundary freezes without renewal");
        check(expiry.retained(819)&&!expiry.retained(820),"last position expires thirty seconds after freeze");
        var broken=new HumanTargetSharing.Window(100,true);
        check(!broken.update(101,false),"losing relationship, memory, loading or range freezes immediately");
        check(!broken.update(102,true),"restoring range or memory cannot thaw an old broadcast");
        check(broken.frozenAt==101,"repeated validation never extends last-position lifetime");
        var clue=new HumanTargetSharing.Window(100,false);
        check(!clue.update(100,true),"unidentified clue never grants target lock");
        check(clue.retained(699)&&!clue.retained(700),"clue lifetime is bounded");
        int remaining=HumanTargetSharing.COOLDOWN;
        for(int i=0;i<199;i++)remaining=HumanTargetSharing.nextCooldown(remaining);
        check(remaining==1,"cooldown lasts 200 loaded ticks across all sources and targets");
        check(HumanTargetSharing.nextCooldown(remaining)==0,"cooldown opens at exact boundary");
        check(HumanTargetSharing.nextCooldown(-5)==0,"corrupt negative counter is bounded");
        check(HumanTargetSharing.nextCooldown(Integer.MAX_VALUE)==199,"corrupt oversized counter is bounded");
        System.out.println("Target sharing lease policies passed");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

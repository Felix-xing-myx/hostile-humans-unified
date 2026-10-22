package club.someoneice.humangunner;

import java.util.List;

/** Pure geometry/sampling boundary checks. No world launch is implied. */
public final class ShieldDefensePolicyTest {
    public static void main(String[] args) {
        require(ShieldDefensePolicy.meleeCandidate(4, 3, 0, 1, 1, true), "cylinder exact edge");
        require(!ShieldDefensePolicy.meleeCandidate(3, 0, 3, 1, 1, true), "square corner outside cylinder");
        require(!ShieldDefensePolicy.meleeCandidate(1, 3.01, 0, 1, 1, true), "vertical bound");
        require(!ShieldDefensePolicy.meleeCandidate(1, 0, 0, .6, 1, true), "enemy facing required");
        require(!ShieldDefensePolicy.meleeCandidate(1, 0, 0, 1, .5, true), "self facing required");
        float yaw = ShieldDefensePolicy.coverageYaw(List.of(-40F, 40F), 0, true, .1);
        require(Math.abs(ShieldDefensePolicy.wrap(-40-yaw)) <= 45.001
                && Math.abs(ShieldDefensePolicy.wrap(40-yaw)) <= 45.001, "coverage between enemies");
        ShieldAdvancePolicy advance = new ShieldAdvancePolicy();
        for (int i=0;i<19;i++) require(!advance.observe(true, 10, () -> .99), "no forced success");
        require(!advance.observe(true, 10, () -> .99), "twenty failed batches retire window");
        require(advance.save().cooldown()==200, "failed window has cooldown");
        for(int i=0;i<20;i++) require(!advance.observe(true,10,()->0), "cooldown consumes loaded ticks");
        require(advance.observe(true,10,()->0), "new window can request");
        require(advance.begin(true) && advance.active(), "execution begins only after acceptance");
        for(int i=0;i<39;i++) advance.observe(true,10,()->0);
        require(advance.active(), "advance survives before maximum");
        advance.observe(true,10,()->0);
        require(!advance.active() && advance.save().cooldown()==200, "maximum execution ends into cooldown");
        advance.clear(); advance.observe(true,10,()->0); advance.begin(true);
        ShieldAdvancePolicy restored = new ShieldAdvancePolicy(); restored.load(advance.save());
        require(!restored.active() && restored.save().cooldown()==200, "reload cannot resurrect action or reroll");
        advance.clear(); advance.observe(true,10,()->.99);
        for(int i=0;i<4;i++) advance.observe(false,10,()->0);
        require(advance.save().cooldown()==200, "forty invalid ticks retire sampling window");
        System.out.println("ShieldDefensePolicyTest passed");
    }
    private static void require(boolean condition, String reason) { if(!condition) throw new AssertionError(reason); }
}

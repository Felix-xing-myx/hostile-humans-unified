package club.someoneice.humangunner;

import java.util.List;

/** Pure geometry/sampling boundary checks. No world launch is implied. */
public final class ShieldDefensePolicyTest {
    public static void main(String[] args) throws Exception {
        require(ShieldDefensePolicy.meleeCandidate(4, 3, 0, 1, 1, true), "cylinder exact edge");
        require(!ShieldDefensePolicy.meleeCandidate(3, 0, 3, 1, 1, true), "square corner outside cylinder");
        require(!ShieldDefensePolicy.meleeCandidate(1, 3.01, 0, 1, 1, true), "vertical bound");
        require(!ShieldDefensePolicy.meleeCandidate(1, 0, 0, .6, 1, true), "enemy facing required");
        require(!ShieldDefensePolicy.meleeCandidate(1, 0, 0, 1, .5, true), "self facing required");
        float yaw = ShieldDefensePolicy.coverageYaw(List.of(-40F, 40F), 0, true, .1);
        require(yaw == 0, "preserve current facing when it already maximizes coverage");
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
        verifyMovementAttackSeparation();
        System.out.println("ShieldDefensePolicyTest passed");
    }
    private static void verifyMovementAttackSeparation() throws Exception {
        String goal = "com/craftix/hostile_humans/entity/ai/goal/MeleeAttackGoal";
        String defense = "club/someoneice/humangunner/ShieldDefenseController";
        String human = "com/craftix/hostile_humans/entity/entities/Human";
        require(callIndex(goal, "tick", defense, "permitsMeleeMovement") >= 0,
                "melee movement checks defense movement permission rather than strike readiness");
        require(callIndex(goal, "tick", "club/someoneice/humangunner/PursuitNavigation", "update") >= 0,
                "mobile blocking continues through the existing budgeted pursuit executor");
        int attackGate = callIndex(goal, "checkAndPerformAttack", human, "humanGunner$canMeleeAttack");
        int lowerShield = callIndex(goal, "checkAndPerformAttack", human, "stopUsingItem");
        // javac may use HumanEntity as owner for an inherited invocation.
        if (lowerShield < 0) lowerShield = callIndex(goal, "checkAndPerformAttack",
                "com/craftix/hostile_humans/entity/HumanEntity", "stopUsingItem");
        require(attackGate >= 0 && lowerShield > attackGate,
                "attack permission is checked before a melee goal can lower its shield");
        for (String called : new String[]{"blocksMeleeAttack", "ownsCurrentShield", "shouldSuppressRecovery"})
            require(callIndex(defense, "permitsMeleeMovement", defense, called) >= 0,
                    "movement exemption retains validated defensive ownership and pressure checks: " + called);
        require(callIndex(defense, "permitsMeleeMovement", "club/someoneice/humangunner/ShieldAdvancePolicy", "active") >= 0,
                "an unaccepted, ended or expired advance cannot grant movement");
    }
    private static int callIndex(String owner, String entry, String target, String name) throws Exception {
        var node = new org.objectweb.asm.tree.ClassNode();
        try (var stream = ShieldDefensePolicyTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            if (stream == null) throw new AssertionError("Missing compiled class: " + owner);
            new org.objectweb.asm.ClassReader(stream).accept(node, 0);
        }
        for (var method : node.methods) if (method.name.equals(entry)) {
            int index = 0;
            for (var instruction : method.instructions) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && call.owner.equals(target) && call.name.equals(name)) return index;
                index++;
            }
        }
        return -1;
    }
    private static void require(boolean condition, String reason) { if(!condition) throw new AssertionError(reason); }
}

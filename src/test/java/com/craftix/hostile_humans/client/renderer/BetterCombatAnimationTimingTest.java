package com.craftix.hostile_humans.client.renderer;

/** Regression check for parity with Better Combat's player attack timing formula. */
public final class BetterCombatAnimationTimingTest {
    public static void main(String[] args) {
        float standard = BetterCombatNpcAnimator.calculateRecoverySpeed(3.0F, 0.25F, 1.0F);
        check(close(standard, 1.0F), "standard upswing uses Better Combat's recovery endpoint");

        float configured = BetterCombatNpcAnimator.calculateRecoverySpeed(2.0F, 0.6F, 1.5F);
        check(close(configured, 5.2F), "configured upswing multiplier matches Better Combat interpolation");

        var visual = new com.craftix.hostile_humans.entity.HumanMobEntityData.MeleeAttackVisual(
                7, 100L, "slash", 20, 0.5F);
        check(visual.active(100L) && visual.elapsed(100L) == 0, "new attack starts at zero");
        check(visual.active(111L) && visual.elapsed(111L) == 11, "late tracking resumes current phase");
        check(visual.active(120L), "nominal cooldown does not truncate recovery tail");
        check(!visual.active(142L) && !visual.active(150L), "expired visual cannot replay after re-tracking");
        check(visual.elapsed(99L) == 0, "client clock behind snapshot cannot seek backwards");
        var cancelled = new com.craftix.hostile_humans.entity.HumanMobEntityData.MeleeAttackVisual(
                8, 110L, "", 1, 0.5F);
        check(!cancelled.active(110L), "cancellation snapshot never plays");

        System.out.println("PASS: NPC recovery animation timing matches Better Combat player formula");
    }

    private static boolean close(float actual, float expected) {
        return Math.abs(actual - expected) < 1.0E-5F;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

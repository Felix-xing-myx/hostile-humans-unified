package com.craftix.hostile_humans.client.renderer;

/** Regression check for parity with Better Combat's player attack timing formula. */
public final class BetterCombatAnimationTimingTest {
    public static void main(String[] args) {
        float standard = BetterCombatNpcAnimator.calculateRecoverySpeed(3.0F, 0.25F, 1.0F);
        check(close(standard, 1.0F), "standard upswing uses Better Combat's recovery endpoint");

        float configured = BetterCombatNpcAnimator.calculateRecoverySpeed(2.0F, 0.6F, 1.5F);
        check(close(configured, 5.2F), "configured upswing multiplier matches Better Combat interpolation");

        System.out.println("PASS: NPC recovery animation timing matches Better Combat player formula");
    }

    private static boolean close(float actual, float expected) {
        return Math.abs(actual - expected) < 1.0E-5F;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

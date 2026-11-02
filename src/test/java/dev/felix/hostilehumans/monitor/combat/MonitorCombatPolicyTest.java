package dev.felix.hostilehumans.monitor.combat;

/** Input lifecycle tests without a world, native gun library or damage stubs. */
public final class MonitorCombatPolicyTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        var gate = new MonitorCombatInputGate();
        for (int tick = 0; tick < 100; tick++) check(!gate.accept(true, true, false, false), "held attack cannot arm a new session");
        check(!gate.accept(true, false, false, false), "neutral arms but does not perform a combat action");
        check(gate.accept(true, true, true, false), "fresh armed buttons are accepted");
        check(!gate.accept(false, false, false, false), "lost focus/input releases authority");
        check(!gate.accept(true, false, true, false), "held use after resume cannot draw or aim");
        check(!gate.accept(true, false, false, false), "release after resume rearms");
        check(gate.accept(true, false, false, true), "explicit reload accepted after release");
        gate.reset();
        check(!gate.accept(true, false, false, true), "weapon switch cannot carry an old reload press");
        var semi = new MonitorGunTrigger();
        check(semi.held(true) && semi.held(true), "native draw/cooldown failure must not consume the trigger");
        semi.accepted(false);
        for (int tick = 0; tick < 100; tick++) check(!semi.held(true), "semi and non-continuous burst need a new press");
        check(!semi.held(false) && semi.held(true), "release permits another shot");
        semi.accepted(true);
        for (int tick = 0; tick < 100; tick++) { check(semi.held(true), "automatic hold remains eligible"); semi.accepted(true); }
        semi.accepted(false); semi.reset();
        check(semi.held(true), "backend cancellation clears trigger state; session gate handles rearming");
        System.out.println("MonitorCombatPolicyTest: focus, weapon-change and accepted-shot trigger boundaries passed");
    }
}

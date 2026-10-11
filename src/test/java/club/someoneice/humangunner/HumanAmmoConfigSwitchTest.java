package club.someoneice.humangunner;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

/** Bytecode gate checks plus real transient-ledger tests; no Minecraft entity bootstrap required. */
public final class HumanAmmoConfigSwitchTest {
    private static final String AMMO = "club/someoneice/humangunner/HumanAmmo";
    private static final String SPAWN = "club/someoneice/humangunner/HumanAmmoSpawn";
    private static final String RELOAD = "club/someoneice/humangunner/TaczHumanReloadTransactions";
    private static final String CONFIG = "club/someoneice/humangunner/UnifiedConfig";
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(calls(AMMO, "consumptionEnabled", CONFIG, "ammoConsumptionEnabled"),
                "runtime gate reads the shared cached configuration API");
        for (String name : List.of("hasArrows", "consumeArrow", "canReload", "consumeMagazines")) {
            check(calls(AMMO, name, AMMO, "consumptionEnabled"), "consumption gate wired: " + name);
            for (int reserve : new int[]{0, 19}) {
                int[] backpack = {reserve};
                check(evaluate(name, false, true, backpack), "disabled mode accepts empty/present reserves: " + name);
                check(backpack[0] == reserve, "disabled mode preserves physical inventory: " + name);
            }
        }
        for (String name : List.of("canReload", "consumeMagazines")) {
            check(!evaluate(name, false, false, new int[]{0}), "free mode still rejects non-guns: " + name);
        }
        for (String name : List.of("hasArrows", "consumeArrow", "canReload", "consumeMagazines")) {
            check(!evaluate(name, true, true, new int[]{0}), "enabled mode retains empty-reserve rejection: " + name);
        }
        check(calls(AMMO, "canUseGun", AMMO, "canReload"), "empty gun usability follows the reserve gate");
        check(evaluate("canUseGun", false, true, new int[]{0}), "empty real gun remains usable without reserves in free mode");
        check(!evaluate("canUseGun", false, false, new int[]{0}), "free mode cannot make a non-gun usable");
        check(!evaluate("canUseGun", true, true, new int[]{0}), "enabled empty gun without reserves stays unusable");
        check(calls("club/someoneice/humangunner/AmmoWeaponSelection", "usableGun", AMMO, "canUseGun"),
                "selection retains free-mode empty guns and the last paid reload");
        check(calls("club/someoneice/humangunner/AmmoWeaponSelection", "usableGun",
                "club/someoneice/humangunner/GunSupport", "isReloading"), "active reload remains usable");
        check(calls(RELOAD, "start", "club/someoneice/humangunner/ReloadPaymentLedger", "begin"),
                "free refill still requires a bound timed transaction");
        check(calls(RELOAD, "start", "club/someoneice/humangunner/GunReloadPolicy", "reloadThresholdSatisfied"),
                "start retains combat-empty and idle-below25-percent thresholds");
        check(anyCalls(RELOAD, AMMO, "canReload") && anyCalls(RELOAD, AMMO, "consumeMagazines"),
                "transaction callbacks use the same gate for authorization and payment");
        check(!calls("club/someoneice/humangunner/TaczIntegration", "hasLoadedAmmo", CONFIG, "ammoConsumptionEnabled"),
                "gun loaded-round queries remain physical even in free mode");
        check(calls(SPAWN, "applyOnce", CONFIG, "ammoReserve"), "initial reserves use per-tier configuration");
        for (String field : List.of("arrowMin", "arrowMax", "magazineMin", "magazineMax")) {
            check(calls(SPAWN, "applyOnce", CONFIG + "$AmmoReserve", field), "spawn uses range component: " + field);
        }
        check(!anyCalls(SPAWN, CONFIG, "ammoConsumptionEnabled"), "consumption switch does not suppress initial supplies");
        check(calls(SPAWN, "applyOnce", "net/minecraft/nbt/CompoundTag", "getBoolean")
                && calls(SPAWN, "applyOnce", "net/minecraft/nbt/CompoundTag", "putBoolean"),
                "initial grant retains the persistent one-time guard");
        for (String name : List.of("canReload", "consumeMagazines", "canUseGun")) {
            for (boolean enabled : new boolean[]{false, true}) {
                int[] reserve = {19};
                check(!evaluate(name, enabled, true, false, reserve), "blacklist overrides ammo switch: " + name);
                check(reserve[0] == 19, "banned gun never spends reserves: " + name);
            }
        }
        freeReloadCycle();
        System.out.println("HumanAmmoConfigSwitchTest: " + checks + " checks passed; live TaCZ acceptance remains required");
    }

    private static void freeReloadCycle() {
        var ledger = new ReloadPaymentLedger<Object, Object>();
        Object holder = new Object(), gun = new Object();
        int[] magazines = {0}, nativeStarts = {0};
        check(!ledger.begin(holder, gun, 100, () -> true, () -> false, () -> true),
                "free mode cannot bypass a native start refusal");
        check(!ledger.authorized(holder, gun, 0), "refused free cycle has no authorization");
        check(ledger.begin(holder, gun, 100, () -> true,
                () -> { nativeStarts[0]++; return true; }, () -> true), "empty reserve starts a free legal cycle");
        check(magazines[0] == 0 && nativeStarts[0] == 1, "free cycle leaves inventory unchanged");
        check(!ledger.begin(holder, gun, 100, () -> true,
                () -> { throw new AssertionError("duplicate native start"); }, () -> true), "free duplicate refused");
        check(ledger.authorized(holder, gun, 99), "free manual-feed cycle authorizes later shells before deadline");
        check(!ledger.authorized(holder, new Object(), 99), "free authorization cannot transfer to another weapon");
        check(!ledger.authorized(holder, gun, 99), "switching back cannot revive a cancelled free cycle");
        check(ledger.begin(holder, gun, 100, () -> true, () -> true, () -> true), "new legal free cycle permitted");
        check(!ledger.authorized(holder, gun, 100), "free cycle expires at its hard deadline");
        check(ledger.begin(holder, gun, 200, () -> true, () -> true, () -> true), "free mode can retry after expiry");
        ledger.end(holder);
        check(!ledger.authorized(holder, gun, 101), "explicit cancellation clears free authorization");
        check(GunReloadPolicy.reloadThresholdSatisfied(7, 30, false)
                && !GunReloadPolicy.reloadThresholdSatisfied(8, 30, false), "idle free reload retains strict threshold");
        check(GunReloadPolicy.reloadThresholdSatisfied(0, 30, true)
                && !GunReloadPolicy.reloadThresholdSatisfied(1, 30, true), "combat free reload waits for chambered last round");
    }

    /** Follow actual compiled short-circuit branches, rejecting any reserve access in disabled mode. */
    private static boolean evaluate(String name, boolean enabled, boolean gun, int[] inventory) throws Exception {
        return evaluate(name, enabled, gun, true, inventory);
    }

    private static boolean evaluate(String name, boolean enabled, boolean gun, boolean allowed, int[] inventory) throws Exception {
        var stack = new ArrayList<Object>();
        AbstractInsnNode instruction = method(AMMO, name).instructions.getFirst();
        while (instruction != null) {
            int opcode = instruction.getOpcode();
            if (instruction instanceof MethodInsnNode call) {
                int arguments = Type.getArgumentTypes(call.desc).length;
                for (int i = 0; i < arguments; i++) pop(stack);
                if (opcode != Opcodes.INVOKESTATIC) pop(stack);
                Object result;
                switch (call.name) {
                    case "get" -> result = new Object();
                    case "isGun" -> result = gun ? 1 : 0;
                    case "accepts" -> result = allowed ? 1 : 0;
                    case "hasLoadedAmmo" -> result = 0;
                    case "canReload" -> result = evaluate("canReload", enabled, gun, allowed, inventory) ? 1 : 0;
                    case "consumptionEnabled" -> result = enabled ? 1 : 0;
                    case "arrowCount", "magazineCount", "magazineCost", "consume" -> {
                        if (!enabled) throw new AssertionError("Disabled mode accessed reserves in " + name + ": " + call.name);
                        if (call.name.equals("magazineCost")) result = 1;
                        else if (call.name.equals("consume")) {
                            result = inventory[0] > 0 ? 1 : 0;
                            if (inventory[0] > 0) inventory[0]--;
                        } else result = inventory[0];
                    }
                    default -> throw new AssertionError("Unexpected gate call " + call.name);
                }
                stack.add(result);
            } else if (instruction instanceof JumpInsnNode jump) {
                if (opcode == Opcodes.GOTO) { instruction = jump.label; continue; }
                int right = (int) pop(stack);
                boolean take = switch (opcode) {
                    case Opcodes.IFEQ -> right == 0;
                    case Opcodes.IFNE -> right != 0;
                    case Opcodes.IFLE -> right <= 0;
                    case Opcodes.IFGT -> right > 0;
                    case Opcodes.IF_ICMPLT -> (int) pop(stack) < right;
                    case Opcodes.IF_ICMPGE -> (int) pop(stack) >= right;
                    default -> throw new AssertionError("Unexpected gate branch " + opcode);
                };
                if (take) { instruction = jump.label; continue; }
            } else if (opcode == Opcodes.ALOAD || opcode == Opcodes.GETSTATIC) stack.add(new Object());
            else if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) stack.add(opcode - Opcodes.ICONST_0);
            else if (opcode == Opcodes.IRETURN) return (int) pop(stack) != 0;
            else if (opcode >= 0) throw new AssertionError("Unexpected gate opcode " + opcode);
            instruction = instruction.getNext();
        }
        throw new AssertionError("Gate has no return: " + name);
    }

    private static Object pop(ArrayList<Object> stack) { return stack.remove(stack.size() - 1); }
    private static ClassNode node(String owner) throws Exception {
        var result = new ClassNode();
        try (var input = HumanAmmoConfigSwitchTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            if (input == null) throw new AssertionError("Missing compiled class " + owner);
            new ClassReader(input).accept(result, 0);
        }
        return result;
    }
    private static MethodNode method(String owner, String name) throws Exception {
        return node(owner).methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
    private static boolean calls(String owner, String name, String calledOwner, String calledName) throws Exception {
        for (var instruction : method(owner, name).instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(calledOwner)
                    && call.name.equals(calledName)) return true;
        return false;
    }
    private static boolean anyCalls(String owner, String calledOwner, String calledName) throws Exception {
        for (MethodNode method : node(owner).methods)
            if (calls(owner, method.name, calledOwner, calledName)) return true;
        return false;
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}

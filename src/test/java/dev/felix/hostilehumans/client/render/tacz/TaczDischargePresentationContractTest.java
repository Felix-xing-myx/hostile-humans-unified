package dev.felix.hostilehumans.client.render.tacz;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/**
 * Real native bytecode distinguishes trigger acceptance from each native burst discharge and visual
 * callback.
 */
public final class TaczDischargePresentationContractTest {
    private static final String FIRE = "com/tacz/guns/api/event/common/GunFireEvent",
            SHOOT = "com/tacz/guns/api/event/common/GunShootEvent";

    private static void check(boolean b, String reason) {
        if (!b) throw new AssertionError(reason);
    }

    private static ClassNode read(ZipFile jar, String name) throws Exception {
        var entry = jar.getEntry(name + ".class");
        check(entry != null, "Missing real native class " + name);
        var node = new ClassNode();
        try (var in = jar.getInputStream(entry)) {
            new ClassReader(in).accept(node, 0);
        }
        return node;
    }

    private static MethodNode method(ClassNode node, String name) {
        var matches = node.methods.stream().filter(m -> m.name.equals(name)).toList();
        check(
                matches.size() == 1,
                "Expected unique method "
                        + node.name
                        + "."
                        + name
                        + "; overloaded methods need an exact descriptor");
        return matches.get(0);
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream()
                .filter(m -> m.name.equals(name) && m.desc.equals(descriptor))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "Missing exact method "
                                                + node.name
                                                + "."
                                                + name
                                                + descriptor));
    }

    private static int exactCall(
            MethodNode method, String owner, String name, String descriptor, int opcode) {
        int found = -1;
        for (int i = 0; i < method.instructions.size(); i++)
            if (method.instructions.get(i) instanceof MethodInsnNode m
                    && m.owner.equals(owner)
                    && m.name.equals(name)
                    && m.desc.equals(descriptor)
                    && m.getOpcode() == opcode) {
                check(
                        found < 0,
                        "Expected one exact native call " + owner + "." + name + descriptor);
                found = i;
            }
        check(found >= 0, "Missing exact native call " + owner + "." + name + descriptor);
        return found;
    }

    private static int allocation(MethodNode method, String name) {
        for (int i = 0; i < method.instructions.size(); i++)
            if (method.instructions.get(i) instanceof TypeInsnNode t
                    && t.getOpcode() == Opcodes.NEW
                    && t.desc.equals(name)) return i;
        return -1;
    }

    private static int call(MethodNode method, String owner, String name) {
        for (int i = 0; i < method.instructions.size(); i++)
            if (method.instructions.get(i) instanceof MethodInsnNode m
                    && m.owner.equals(owner)
                    && m.name.equals(name)) return i;
        return -1;
    }

    private static long allocations(MethodNode method, String type) {
        return java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(
                        i ->
                                i instanceof TypeInsnNode t
                                        && t.getOpcode() == Opcodes.NEW
                                        && t.desc.equals(type))
                .count();
    }

    private static void clientPacket(ZipFile jar, String suffix, String event) throws Exception {
        var packet = read(jar, "com/tacz/guns/network/message/event/ServerMessageGun" + suffix);
        var method = method(packet, "doClientEvent");
        check(
                allocations(method, event) == 1,
                "Remote " + suffix + " publishes exactly its actual CLIENT event");
        check(
                call(method, "net/minecraftforge/eventbus/api/IEventBus", "post")
                        > allocation(method, event),
                "Native packet posts constructed event");
        check(
                java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                        .anyMatch(
                                i ->
                                        i instanceof FieldInsnNode f
                                                && f.owner.equals(
                                                        "net/minecraftforge/fml/LogicalSide")
                                                && f.name.equals("CLIENT")),
                "Native remote event logical side is CLIENT");
    }

    private static void own(Path root, String name, String callback) throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(root.resolve(name + ".class"))).accept(node, 0);
        var handler = method(node, callback);
        check(
                handler.desc.equals("(L" + FIRE + ";)V"),
                name + " listens to native Fire, not trigger Shoot");
        int registrations = 0;
        for (var m : node.methods) {
            check(!m.desc.contains(SHOOT), name + " has no duplicate Shoot listener");
            for (var i : m.instructions)
                if (i instanceof InvokeDynamicInsnNode dynamic)
                    for (Object arg : dynamic.bsmArgs)
                        if (arg instanceof Handle h
                                && h.getOwner().equals(name)
                                && h.getName().equals(callback)) {
                            check(
                                    h.getDesc().equals("(L" + FIRE + ";)V"),
                                    "Registered listener descriptor must be Fire");
                            registrations++;
                        }
        }
        check(
                registrations == 1,
                name + " registers its discharge listener exactly once in its registration seam");
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 2, "Pass actual TaCZ jar and compiled HHU classes directory");
        try (var jar = new ZipFile(Path.of(args[0]).toFile())) {
            clientPacket(jar, "Fire", FIRE);
            clientPacket(jar, "Shoot", SHOOT);
            var shooter = read(jar, "com/tacz/guns/entity/shooter/LivingEntityShoot");
            String bodyDescriptor =
                    "(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)Lcom/tacz/guns/api/entity/ShootResult;";
            var trigger = method(shooter, "shoot", bodyDescriptor);
            check(
                    (trigger.access & Opcodes.ACC_PRIVATE) != 0,
                    "Native event/discharge implementation is the private five-argument shoot"
                        + " body");
            int wrappers = 0;
            for (var m : shooter.methods)
                if (m.name.equals("shoot") && (m.access & Opcodes.ACC_PUBLIC) != 0) {
                    exactCall(m, shooter.name, "shoot", bodyDescriptor, Opcodes.INVOKEVIRTUAL);
                    wrappers++;
                }
            check(
                    wrappers == 2,
                    "Both public native shoot overloads delegate to the checked private"
                        + " implementation");
            int triggerEvent = allocation(trigger, SHOOT),
                    triggerPacket =
                            allocation(
                                    trigger,
                                    "com/tacz/guns/network/message/event/ServerMessageGunShoot");
            int post =
                    exactCall(
                            trigger,
                            "net/minecraftforge/eventbus/api/IEventBus",
                            "post",
                            "(Lnet/minecraftforge/eventbus/api/Event;)Z",
                            Opcodes.INVOKEINTERFACE);
            int send =
                    exactCall(
                            trigger,
                            "com/tacz/guns/network/NetworkHandler",
                            "sendToTrackingEntity",
                            "(Ljava/lang/Object;Lnet/minecraft/world/entity/Entity;)V",
                            Opcodes.INVOKESTATIC);
            int dispatch =
                    exactCall(
                            trigger,
                            "com/tacz/guns/api/item/gun/AbstractGunItem",
                            "shoot",
                            "(Lcom/tacz/guns/entity/shooter/ShooterDataHolder;Lnet/minecraft/world/item/ItemStack;Ljava/util/function/Supplier;Ljava/util/function/Supplier;Lnet/minecraft/world/entity/LivingEntity;)V",
                            Opcodes.INVOKEVIRTUAL);
            check(
                    triggerEvent >= 0
                            && post > triggerEvent
                            && triggerPacket > post
                            && send > triggerPacket
                            && dispatch > send,
                    "Trigger event/post/packet send precede native scripted gun shoot");
            var api = read(jar, "com/tacz/guns/item/ModernKineticGunScriptAPI");
            var schedule = method(api, "shootOnce");
            check(
                    call(schedule, "com/tacz/guns/resource/pojo/data/gun/BurstData", "getCount")
                            >= 0,
                    "Actual native burst count drives shootOnce");
            check(
                    call(schedule, "com/tacz/guns/util/CycleTaskHelper", "addCycleTask") >= 0,
                    "Native burst is a repeated task, not one trigger event");
            var discharge =
                    api.methods.stream()
                            .filter(
                                    m ->
                                            m.name.startsWith("lambda$shootOnce$")
                                                    && allocations(m, FIRE) == 1)
                            .findFirst()
                            .orElseThrow();
            int fireEvent = allocation(discharge, FIRE),
                    firePacket =
                            allocation(
                                    discharge,
                                    "com/tacz/guns/network/message/event/ServerMessageGunFire"),
                    consume =
                            call(
                                    discharge,
                                    "com/tacz/guns/item/ModernKineticGunScriptAPI",
                                    "reduceAmmoOnce"),
                    bullet = allocation(discharge, "com/tacz/guns/entity/EntityKineticBullet");
            check(
                    fireEvent >= 0
                            && firePacket > fireEvent
                            && consume > firePacket
                            && bullet > consume,
                    "Fire packet is per native discharge cycle, before ammo/bullet creation; it is"
                        + " not hit confirmation");
            check(allocations(discharge, FIRE) == 1, "One Fire event per burst cycle");
            boolean pelletLoop = false;
            for (int i = bullet + 1; i < discharge.instructions.size(); i++)
                if (discharge.instructions.get(i) instanceof JumpInsnNode j) {
                    int target = discharge.instructions.indexOf(j.label);
                    if (target > firePacket && target <= bullet && i > bullet) pelletLoop = true;
                }
            check(
                    pelletLoop,
                    "Actual bullet spawning loop repeats after Fire; pellets must not multiply"
                        + " recoil/flash callbacks");
            boolean scheduled = false;
            for (var i : schedule.instructions)
                if (i instanceof InvokeDynamicInsnNode d)
                    for (Object a : d.bsmArgs)
                        if (a instanceof Handle h
                                && h.getOwner().equals(api.name)
                                && h.getName().equals(discharge.name)) scheduled = true;
            check(scheduled, "Burst callback producing Fire is the actual repeated task");
            var nativeCamera =
                    method(
                            read(jar, "com/tacz/guns/client/event/CameraSetupEvent"),
                            "initialCameraRecoil");
            var nativeModel =
                    method(
                            read(jar, "com/tacz/guns/client/event/FirstPersonRenderGunEvent"),
                            "onGunFire");
            check(
                    nativeCamera.desc.equals("(L" + FIRE + ";)V")
                            && nativeModel.desc.equals("(L" + FIRE + ";)V"),
                    "Native camera and first-person firing movement share Fire semantics");
        }
        var root = Path.of(args[1]);
        own(root, "dev/felix/hostilehumans/client/render/tacz/TaczMonitorRecoil", "shot");
        own(root, "dev/felix/hostilehumans/client/render/tacz/TaczNativePresentation", "shot");
        own(root, "dev/felix/hostilehumans/client/render/TaczNpcEffects", "onShoot");
        System.out.println(
                "Actual TaCZ trigger/burst discharge/native presentation contracts passed; no"
                    + " duplicate trigger listener");
    }
}

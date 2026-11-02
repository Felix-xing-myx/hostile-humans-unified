package dev.felix.hostilehumans.compat;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Bytecode-only guards: never initialize a client/optional class during Mixin discovery. */
public final class SpectatorSoundContract {
    private SpectatorSoundContract() {}
    public static final String ANIMATION = "com/tacz/guns/api/client/animation/ObjectAnimationSoundChannel";
    public static final String ANIMATION_METHOD = "playSound(DDLnet/minecraft/world/entity/Entity;IFF)V";
    private static final String TACZ_CONSTRUCTOR = "(Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;"
            + "FFLnet/minecraft/world/entity/Entity;ILnet/minecraft/resources/ResourceLocation;Z)V";
    private static final String CRAYFISH_CONSTRUCTOR = "(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/sounds/SoundSource;"
            + "FFFFFZLnet/minecraft/util/RandomSource;)V";

    public static boolean supports(ClassNode target, boolean animation) {
        if (animation) {
            if (!ANIMATION.equals(target.name)) return false;
            return target.methods.stream().filter(m -> (m.name + m.desc).equals(ANIMATION_METHOD))
                    .anyMatch(SpectatorSoundContract::distanceBody);
        }
        String descriptor;
        if (target.name.equals("com/tacz/guns/client/sound/GunSoundInstance")) {
            var constructors = target.methods.stream().filter(m -> m.name.equals("<init>")).toList();
            if (target.superName.equals("net/minecraft/client/resources/sounds/EntityBoundSoundInstance"))
                return constructors.size() == 1 && constructors.get(0).desc.equals(TACZ_CONSTRUCTOR)
                        && distanceBody(constructors.get(0));
            // 1.1.8 adds relative playback: the old overload delegates to a nine-argument body.
            String relative = TACZ_CONSTRUCTOR.replace(";Z)V", ";ZZ)V");
            if (!target.superName.equals("net/minecraft/client/resources/sounds/AbstractSoundInstance")
                    || constructors.size() != 2) return false;
            var delegating = constructors.stream().filter(m -> m.desc.equals(TACZ_CONSTRUCTOR)).findFirst();
            var body = constructors.stream().filter(m -> m.desc.equals(relative)).findFirst();
            return delegating.isPresent() && body.isPresent() && distanceBody(body.get())
                    && java.util.stream.StreamSupport.stream(delegating.get().instructions.spliterator(), false)
                        .anyMatch(i -> i instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                                && call.owner.equals(target.name) && call.name.equals("<init>") && call.desc.equals(relative));
        }
        else if (List.of("top/ribs/scguns/client/audio/GunShotSound", "ttv/migami/jeg/client/audio/GunShotSound").contains(target.name)
                && target.superName.equals("net/minecraft/client/resources/sounds/AbstractSoundInstance"))
            descriptor = CRAYFISH_CONSTRUCTOR;
        else return false;
        var constructors = target.methods.stream().filter(m -> m.name.equals("<init>")).toList();
        return constructors.size() == 1 && constructors.get(0).desc.equals(descriptor)
                && distanceBody(constructors.get(0));
    }

    /** The takeover adapter uses this public native overload; do not link it on incompatible releases. */
    public static boolean supportsShotPlayback(ClassNode target) {
        if (!target.name.equals("com/tacz/guns/client/sound/SoundPlayManager")) return false;
        return hasStatic(target, "playMessageSound", "(Lcom/tacz/guns/network/message/ServerMessageSound;)V")
                && hasStatic(target, "playClientSound", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/resources/ResourceLocation;FFIZ)Lcom/tacz/guns/client/sound/GunSoundInstance;");
    }

    private static boolean hasStatic(ClassNode target, String name, String descriptor) {
        return target.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor)
                && (m.access & Opcodes.ACC_STATIC) != 0 && (m.access & Opcodes.ACC_PUBLIC) != 0);
    }

    private static boolean distanceBody(MethodNode method) {
        int roots = 0, playerDistances = 0;
        for (var instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals("java/lang/Math")
                    && call.name.equals("sqrt") && call.desc.equals("(D)D")) roots++;
            if (call.owner.equals("net/minecraft/client/player/LocalPlayer")
                    && (call.desc.equals("(DDD)D") || call.desc.equals("(Lnet/minecraft/world/phys/Vec3;)D"))) playerDistances++;
        }
        return roots == 1 && playerDistances == 1;
    }
}

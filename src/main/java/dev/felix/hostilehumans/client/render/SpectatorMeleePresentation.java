package dev.felix.hostilehumans.client.render;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.entity.HumanUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Retarget only the isolated first-person presentation; never ticks or rewrites an attack. */
final class SpectatorMeleePresentation {
    private static final float BODY_MOTION_WEIGHT = .25F;
    private static final double RESPONSE_TICKS = 1.25;
    private final Map<Human, State> states = new WeakHashMap<>();

    private static final class State {
        double age;
        Offset offset = Offset.ZERO;
        final Item main, off;
        State(double age, Human human) {
            this.age = age;
            main = human.getMainHandItem().getItem();
            off = human.getOffhandItem().getItem();
        }
    }

    record Offset(double x, double y, double z) {
        static final Offset ZERO = new Offset(0, 0, 0);
        boolean nearZero() { return Math.abs(x) + Math.abs(y) + Math.abs(z) < .0001; }
    }

    static float bodyMotionWeight(Human human) {
        return HumanUtil.isMeleeWeapon(human.getMainHandItem()) && !human.isUsingItem()
                ? BODY_MOTION_WEIGHT : 1;
    }

    void apply(Human human, PlayerModel<Human> model, float ageInTicks) {
        // Foreground use/defense owns the arms immediately. Do not carry a melee
        // presentation offset into another weapon, passenger pose or dead entity.
        if (!human.isAlive() || human.isRemoved() || HumanShieldPresentation.active(human)
                || human.isUsingItem() || human.isPassenger() || human.isVisuallySwimming()
                || human.isFallFlying() || !HumanUtil.isMeleeWeapon(human.getMainHandItem())) {
            states.remove(human);
            return;
        }
        float partialTick = ageInTicks - (float) Math.floor(ageInTicks);
        var visual = human.getNpcBetterCombatAttackVisual();
        boolean custom = BetterCombatNpcAnimator.hasActiveCustomAnimation(human)
                && human.level().getGameTime() >= visual.startedAt()
                && visual.active(human.level().getGameTime());
        boolean vanilla = human.swinging && !BetterCombatNpcAnimator.shouldSuppressVanillaSwing(human);
        InteractionHand hand = custom ? (visual.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND)
                : vanilla && human.swingingArm != null ? human.swingingArm : InteractionHand.MAIN_HAND;
        Offset target = Offset.ZERO;
        if ((custom || vanilla) && HumanUtil.isMeleeWeapon(human.getItemInHand(hand))) {
            HumanoidArm side = hand == InteractionHand.MAIN_HAND ? human.getMainArm() : human.getMainArm().getOpposite();
            double weight = custom ? attackWeight(visual.elapsed(human.level().getGameTime()) + partialTick,
                    visual.duration(), visual.upswing()) : Math.sin(Math.PI * human.getAttackAnim(partialTick));
            ModelPart arm = side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
            PoseStack sample = new PoseStack();
            arm.translateAndRotate(sample);
            // Use the same bend contract as the item layer; only sample it, never advance playback.
            BetterCombatNpcAnimator.applyHeldItemBend(human, side, sample);
            Vector3f wrist = sample.last().pose().transformPosition(new Vector3f(0, .625F, 0));
            target = fit(new Vec3(wrist.x, wrist.y, wrist.z), side == HumanoidArm.RIGHT, weight);
        }
        State state = states.get(human);
        if (state != null && (state.main != human.getMainHandItem().getItem()
                || state.off != human.getOffhandItem().getItem())) {
            states.remove(human);
            state = null;
        }
        if (state == null) {
            if (target.nearZero()) return;
            state = new State(ageInTicks, human);
            // Late attachment samples the current pose instead of replaying its windup.
            state.offset = target;
            states.put(human, state);
        } else {
            double delta = ageInTicks - state.age;
            state.offset = delta < 0 || delta > 10 ? target : blend(state.offset, target, delta);
            state.age = ageInTicks;
        }
        translateArms(model, state.offset);
        if (target.nearZero() && state.offset.nearZero()) states.remove(human);
    }

    /** Windup -> visible strike -> relaxed recovery; bounded by the existing visual session. */
    static double attackWeight(double elapsed, int duration, float upswing) {
        if (!Double.isFinite(elapsed) || elapsed < 0 || duration <= 0 || !Float.isFinite(upswing)) return 0;
        double progress = elapsed / duration;
        double impact = Mth.clamp(upswing, .05F, .95F);
        if (progress < impact) return smooth(progress / (impact * .8));
        if (progress < 1) return 1 - .65 * smooth((progress - impact) / (1 - impact));
        return .35 * (1 - smooth(progress - 1));
    }

    private static double smooth(double value) {
        double t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** A soft common correction, not a hard screen clamp or replacement swing animation. */
    static Offset fit(Vec3 wrist, boolean right, double weight) {
        if (!Double.isFinite(wrist.x) || !Double.isFinite(wrist.y) || !Double.isFinite(wrist.z)
                || !Double.isFinite(weight)) return Offset.ZERO;
        double strength = Mth.clamp(weight, 0, 1);
        return new Offset(Mth.clamp(((right ? -.20 : .20) - wrist.x) * .35, -.18, .18) * strength,
                Mth.clamp((.16 - wrist.y) * .85, -.60, .14) * strength,
                Mth.clamp((-.30 - wrist.z) * .25, -.12, .12) * strength);
    }

    static Offset blend(Offset before, Offset target, double ticks) {
        double fraction = 1 - Math.exp(-Math.max(0, ticks) / RESPONSE_TICKS);
        return new Offset(Mth.lerp(fraction, before.x, target.x), Mth.lerp(fraction, before.y, target.y),
                Mth.lerp(fraction, before.z, target.z));
    }

    static void translateArms(PlayerModel<?> model, Offset offset) {
        translateArm(model.rightArm, offset);
        translateArm(model.leftArm, offset);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    private static void translateArm(ModelPart arm, Offset offset) {
        arm.x += (float) offset.x * 16;
        arm.y += (float) offset.y * 16;
        arm.z += (float) offset.z * 16;
    }
}

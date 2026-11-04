package dev.felix.hostilehumans.perception;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.HumanTargetCoordinator;
import dev.felix.hostilehumans.behavior.combat.retreat.RetreatRecoveryPolicy;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Short self-defense reorientation after a real hit, never ordinary visual discovery. */
public final class IncomingHitResponseGoal extends Goal {
    private final Human human;
    private long handled=Long.MIN_VALUE,started;
    private Vec3 direction;
    public IncomingHitResponseGoal(Human human){this.human=human;setFlags(EnumSet.of(Flag.MOVE));}
    private static boolean responseAllowed(Human human){
        if(human.hhu$shieldDefense().shouldSuppressRecovery()||HumanTargetCoordinator.suspended(human)||human.isFleeing||human.isInLava()||human.explosionThreat!=null
                ||RetreatRecoveryPolicy.shouldForceRetreat(human))return false;
        var current=human.getTarget();
        return HumanPerception.impactGlance(human)
                || !(HumanPerception.canTarget(human,current)&&human.distanceToSqr(current)<=36);
    }
    public static boolean wantsResponse(Human human){
        return responseAllowed(human)&&HumanPerception.incomingReactionPending(human);
    }
    @Override public boolean canUse(){
        if(!wantsResponse(human))return false;
        direction=HumanPerception.incomingReactionPoint(human);
        return direction!=null&&HumanPerception.incomingReactionSequence(human)!=handled;
    }
    @Override public void start(){
        handled=HumanPerception.incomingReactionSequence(human);HumanPerception.acknowledgeReaction(human);started=HumanPerception.now(human);
        // GoalSelector has released the previous MOVE owner before start.
        human.getNavigation().stop();human.clearRangedStrafeMotion();
        human.getMoveControl().setWantedPosition(human.getX(),human.getY(),human.getZ(),0);
    }
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public boolean canContinueToUse(){
        // Survival can become necessary after start; equal-priority retreat goals cannot preempt MOVE.
        if(!responseAllowed(human)||direction==null||HumanPerception.incomingReactionPoint(human)==null)return false;
        long elapsed=HumanPerception.now(human)-started;
        if(elapsed>=PerceptionConfig.get().integer("attention.hit_response_ticks"))return false;
        Vec3 delta=direction.subtract(human.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90;
        return elapsed < (HumanPerception.impactGlance(human) ? 8 : 2) || Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw-human.getYHeadRot()))>15;
    }
    @Override public void tick(){HumanPerception.decision(human,"受击自卫回看","真实受击短暂调整，恢复原移动；不授予隐藏目标知识");}
    @Override public void stop(){direction=null;}
}

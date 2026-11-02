package dev.felix.hostilehumans.perception;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.inventory.HumanInventorySession;

import dev.felix.hostilehumans.perception.TurnResponse;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.phys.Vec3;

/** Shared physical orientation motor; callers choose intent, never integrate their own curve. */
public final class HumanTurnControl {
    private static final Map<Human,State> STATES=new WeakHashMap<>();
    private static final class State {
        TurnResponse.Frame frame;String dimension;Vec3 position;
        State(Human h){frame=frame(h);}
    }
    private static TurnResponse.Frame frame(Human h){
        var id=h.getUUID();return new TurnResponse.Frame(id.getMostSignificantBits()^Long.rotateLeft(id.getLeastSignificantBits(),17));
    }
    private static long intent(net.minecraft.world.entity.LivingEntity target){
        if(target==null)return 0;
        var id=target.getUUID();return id.getMostSignificantBits()^Long.rotateLeft(id.getLeastSignificantBits(),7);
    }
    private HumanTurnControl() {}
    public static void clear(Human h){if(!h.level().isClientSide)STATES.remove(h);}
    public static void apply(Human h,float body,float head,float pitch,float limit,TurnResponse.Profile profile,double speed){
        apply(h,body,head,pitch,limit,profile,speed,intent(h.getTarget()));
    }
    private static void apply(Human h,float body,float head,float pitch,float limit,TurnResponse.Profile profile,double speed,long intent){
        State state=state(h);if(state==null)return;
        if(!Float.isFinite(body)||!Float.isFinite(head)||!Float.isFinite(pitch))return;
        var facing=state.frame.step(h.tickCount,h.yBodyRot,h.getYHeadRot(),h.getXRot(),body,head,pitch,limit,profile,speed,intent);
        publish(h,facing);
    }
    public static void release(Human h){
        State state=state(h);if(state==null)return;
        publish(h,state.frame.stop(h.tickCount,h.yBodyRot,h.getYHeadRot(),h.getXRot(),h.getMaxHeadYRot()));
    }
    private static State state(Human h){
        if(h.level().isClientSide)return null;
        if(!h.isAlive()||h.isNoAi()||h.isSleeping()||HumanInventorySession.isEditing(h)){clear(h);return null;}
        State state=STATES.computeIfAbsent(h,State::new);
        String dimension=h.level().dimension().location().toString();
        if(!dimension.equals(state.dimension)||state.position!=null&&state.position.distanceToSqr(h.position())>16)state.frame=frame(h);
        state.dimension=dimension;state.position=h.position();return state;
    }
    private static void publish(Human h,dev.felix.hostilehumans.perception.AttentionFacingPolicy.Facing facing){
        h.setYRot(facing.body());h.setYBodyRot(facing.body());h.setYHeadRot(facing.head());h.setXRot(facing.pitch());
    }
    public static void action(Human h,float yaw,float pitch){
        apply(h,yaw,yaw,pitch,h.getMaxHeadYRot(),TurnResponse.URGENT,TurnResponse.URGENT.speed());
    }
    public static void ballistic(Human h,net.minecraft.world.entity.LivingEntity target,float yaw,float pitch){
        var profile=h.distanceToSqr(target)<=36?TurnResponse.URGENT:TurnResponse.COMBAT;
        apply(h,yaw,yaw,pitch,h.getMaxHeadYRot(),profile,profile.speed(),intent(target));
    }
    public static boolean aligned(Human h,float yaw,float pitch){
        return Math.abs(TurnResponse.wrap(yaw-h.getYHeadRot()))<=5
                &&Math.abs(TurnResponse.wrap(yaw-h.yBodyRot))<=5&&Math.abs(pitch-h.getXRot())<=5;
    }
}

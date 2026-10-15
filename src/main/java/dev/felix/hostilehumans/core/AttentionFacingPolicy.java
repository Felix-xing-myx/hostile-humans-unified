package dev.felix.hostilehumans.core;

/** Bounded attention rotation after navigation, without changing movement or position. */
public final class AttentionFacingPolicy {
    public static final float BODY_STEP=30,HEAD_STEP=45,PITCH_STEP=35;
    public record Facing(float body,float head,float pitch) {}
    private AttentionFacingPolicy() {}
    public static Facing step(float body,float head,float pitch,float wantedYaw,float wantedPitch,
            float maxHeadAngle,boolean turnBody){
        float nextBody=turnBody?approach(body,wantedYaw,BODY_STEP):body;
        float nextHead=approach(head,wantedYaw,HEAD_STEP);
        float relative=wrap(nextHead-nextBody),limit=Math.max(0,maxHeadAngle);
        nextHead=nextBody+Math.max(-limit,Math.min(limit,relative));
        return new Facing(wrap(nextBody),wrap(nextHead),approach(pitch,wantedPitch,PITCH_STEP));
    }
    private static float approach(float from,float to,float amount){
        float delta=wrap(to-from);return from+Math.max(-amount,Math.min(amount,delta));
    }
    private static float wrap(float angle){float value=angle%360;return value>=180?value-360:value<-180?value+360:value;}
}

package dev.felix.hostilehumans.perception;

/** Shared pose value and perception-aware survey ceiling. The stateful motor is TurnResponse. */
public final class AttentionFacingPolicy {
    public record Facing(float body,float head,float pitch) {}
    private AttentionFacingPolicy() {}
    public static float surveyStep(float configured,float farAngle,int interval){
        return Math.min(configured,farAngle/(3*Math.max(1,interval)));
    }
}

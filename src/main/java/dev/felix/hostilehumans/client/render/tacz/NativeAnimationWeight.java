package dev.felix.hostilehumans.client.render.tacz;

import com.tacz.guns.api.client.animation.ObjectAnimationChannel.ChannelType;
import com.tacz.guns.util.math.MathUtil;

/** Match native listener input rules: rotation can be Euler or quaternion; scale is relative to one. */
final class NativeAnimationWeight {
    private NativeAnimationWeight() {}
    static void apply(float[] value, ChannelType type, float weight, float[] out) {
        float[] coordinates = type == ChannelType.ROTATION && value.length == 4 ? MathUtil.toEulerAngles(value) : value;
        float neutral = type == ChannelType.SCALE ? 1 : 0;
        for (int i = 0; i < 3; i++) out[i] = neutral + (coordinates[i] - neutral) * weight;
    }
}

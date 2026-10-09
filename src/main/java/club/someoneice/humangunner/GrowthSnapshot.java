package club.someoneice.humangunner;

import net.minecraft.network.FriendlyByteBuf;

/** Server-produced display values. Contains no configuration or entity dependencies. */
public record GrowthSnapshot(boolean enabled, int level, int stagePercent,
                             int experiencePercent, int experienceMinPercent, int experienceCapPercent,
                             double stageProgress, double stageRequired,
                             double experienceProgress, double experienceRequired) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(enabled);
        buffer.writeVarInt(level);
        buffer.writeVarInt(stagePercent);
        buffer.writeVarInt(experiencePercent);
        buffer.writeVarInt(experienceMinPercent);
        buffer.writeVarInt(experienceCapPercent);
        buffer.writeDouble(stageProgress);
        buffer.writeDouble(stageRequired);
        buffer.writeDouble(experienceProgress);
        buffer.writeDouble(experienceRequired);
    }

    public static GrowthSnapshot decode(FriendlyByteBuf buffer) {
        return new GrowthSnapshot(buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
}

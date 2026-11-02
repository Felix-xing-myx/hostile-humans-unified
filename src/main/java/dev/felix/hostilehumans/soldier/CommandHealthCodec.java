package dev.felix.hostilehumans.soldier;

import net.minecraft.network.FriendlyByteBuf;

/** Shared wire extension for the two existing command channels. */
public final class CommandHealthCodec {
    private CommandHealthCodec() {}

    public static void write(FriendlyByteBuf out, CommandHealthSnapshot health) {
        out.writeUUID(health.human());
        out.writeUtf(health.name(), 256);
        out.writeFloat(health.current());
        out.writeFloat(health.maximum());
        out.writeBoolean(health.mixed());
    }

    public static CommandHealthSnapshot read(FriendlyByteBuf in) {
        return new CommandHealthSnapshot(
                in.readUUID(), in.readUtf(256), in.readFloat(), in.readFloat(), in.readBoolean());
    }
}

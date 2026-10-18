package club.someoneice.humangunner;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.List;
import java.util.UUID;

/** Codec regression; run with the mod's Minecraft test runtime after integration. */
public final class FactionVisualProtocolTest {
    public static void main(String[] args) {
        var definitions = new FactionVisualNetwork.Definitions(12, true, false,
                List.of(new FactionVisualNetwork.Definition("hostile_humans_unified:crimson", 0xA52A36, "赤獠", "base")));
        FriendlyByteBuf buffer = buffer();
        try {
            FactionVisualNetwork.writeDefinitions(definitions, buffer);
            check(FactionVisualNetwork.readDefinitions(buffer).equals(definitions), "definition roundtrip");
        } finally { buffer.release(); }
        var band = new FactionVisualNetwork.PlayerBand(UUID.randomUUID(), "hostile_humans_unified:crimson", 4, 12);
        buffer = buffer();
        try {
            FactionVisualNetwork.writeBand(band, buffer);
            check(FactionVisualNetwork.readBand(buffer).equals(band), "selection roundtrip");
        } finally { buffer.release(); }
        buffer = buffer();
        try {
            buffer.writeLong(1); buffer.writeBoolean(true); buffer.writeBoolean(true); buffer.writeVarInt(257);
            FriendlyByteBuf invalid = buffer;
            rejects(() -> FactionVisualNetwork.readDefinitions(invalid));
        } finally { buffer.release(); }
        buffer = buffer();
        try {
            buffer.writeUUID(UUID.randomUUID()); buffer.writeUtf("hostile_humans_unified:crimson"); buffer.writeVarInt(5); buffer.writeLong(12);
            FriendlyByteBuf invalid = buffer; rejects(() -> FactionVisualNetwork.readBand(invalid));
        } finally { buffer.release(); }
        buffer = buffer();
        try {
            FactionVisualNetwork.writeDefinitions(definitions, buffer); buffer.writeByte(1);
            FriendlyByteBuf invalid = buffer; rejects(() -> FactionVisualNetwork.readDefinitions(invalid));
        } finally { buffer.release(); }
        System.out.println("FactionVisualProtocolTest: all checks passed");
    }
    private static FriendlyByteBuf buffer() { return new FriendlyByteBuf(Unpooled.buffer()); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("invalid packet accepted"); } catch (IllegalArgumentException expected) { }
    }
}

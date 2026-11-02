package dev.felix.hostilehumans.faction.visual;

import dev.felix.hostilehumans.faction.FactionIdentityModelTest;

import dev.felix.hostilehumans.faction.visual.FactionBandFit;
import dev.felix.hostilehumans.faction.visual.FactionVisualCodec;
import dev.felix.hostilehumans.faction.visual.FactionVisualNetwork;

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
            FactionVisualCodec.writeDefinitions(definitions, buffer);
            check(FactionVisualCodec.readDefinitions(buffer).equals(definitions), "definition roundtrip");
        } finally { buffer.release(); }
        var band = new FactionVisualNetwork.PlayerBand(UUID.randomUUID(), "hostile_humans_unified:crimson", 4, 12);
        buffer = buffer();
        try {
            FactionVisualCodec.writeBand(band, buffer);
            check(FactionVisualCodec.readBand(buffer).equals(band), "selection roundtrip");
        } finally { buffer.release(); }
        buffer = buffer();
        try {
            buffer.writeLong(1); buffer.writeBoolean(true); buffer.writeBoolean(true); buffer.writeVarInt(257);
            FriendlyByteBuf invalid = buffer;
            rejects(() -> FactionVisualCodec.readDefinitions(invalid));
        } finally { buffer.release(); }
        buffer = buffer();
        try {
            buffer.writeUUID(UUID.randomUUID()); buffer.writeUtf("hostile_humans_unified:crimson"); buffer.writeVarInt(5); buffer.writeLong(12);
            FriendlyByteBuf invalid = buffer; rejects(() -> FactionVisualCodec.readBand(invalid));
        } finally { buffer.release(); }
        buffer = buffer();
        try {
            FactionVisualCodec.writeDefinitions(definitions, buffer); buffer.writeByte(1);
            FriendlyByteBuf invalid = buffer; rejects(() -> FactionVisualCodec.readDefinitions(invalid));
        } finally { buffer.release(); }
        for (boolean slim : new boolean[]{false, true}) {
            var bare = FactionBandFit.arm(slim, false, 0);
            var armor = FactionBandFit.arm(slim, true, 0);
            check(Math.abs(bare.depth() - 2.37F) < .001, "bare arm follows skin sleeve");
            check(Math.abs(armor.depth() - 3.12F) < .001, "chest armor shell retained");
            check(armor.outer() < bare.outer() && armor.inner() > bare.inner(), "armor expands both sides");
            check(FactionBandFit.arm(slim, false, .015F).depth() > bare.depth(), "trim outside cloth");
        }
        check(FactionBandFit.leg(false, false, 0).depth() < FactionBandFit.leg(true, false, 0).depth(), "leg shell follows leggings");
        check(Math.abs(FactionBandFit.leg(true, false, 0).depth() - 2.62F) < .001, "leggings use half-pixel inner armor");
        check(Math.abs(FactionBandFit.leg(false, true, 0).depth() - 3.12F) < .001, "boots cover the knee band with outer armor");
        FactionIdentityModelTest.verify();
        System.out.println("FactionVisualProtocolTest: all checks passed");
    }
    private static FriendlyByteBuf buffer() { return new FriendlyByteBuf(Unpooled.buffer()); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("invalid packet accepted"); } catch (IllegalArgumentException expected) { }
    }
}

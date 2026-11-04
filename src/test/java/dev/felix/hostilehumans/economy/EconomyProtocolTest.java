package dev.felix.hostilehumans.economy;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.*;

public final class EconomyProtocolTest {
    private static void require(boolean v, String reason) {
        if (!v) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        UUID token = UUID.randomUUID(), request = UUID.randomUUID();
        var metadata = new CompoundTag();
        metadata.putLong("server_game_time", 24001);
        var snapshot =
                new EconomyView.Snapshot(
                        token,
                        7,
                        EconomyView.Page.BROKER,
                        BlockPos.ZERO,
                        Currency.ASHEN.faction(),
                        true,
                        true,
                        "test",
                        List.of(
                                new EconomyView.WalletRow(
                                        UUID.randomUUID(),
                                        "curios:wallet:0",
                                        2,
                                        true,
                                        true,
                                        List.of(1L, 2L, 3L, 4L))),
                        List.of(new EconomyView.FundsRow(Currency.AZURE, 100, 5, 105)),
                        List.of(
                                new EconomyView.OfferRow(
                                        "test:bread",
                                        "minecraft:bread",
                                        3,
                                        250,
                                        83,
                                        true,
                                        true,
                                        "quote",
                                        30,
                                        20,
                                        true)),
                        List.of(
                                new EconomyView.TaskRow(
                                        "test:task",
                                        "Test",
                                        "BASIC",
                                        "READY",
                                        3,
                                        3,
                                        10,
                                        5,
                                        20,
                                        false,
                                        false,
                                        true,
                                        "ready", "DELIVERY", "minecraft:bread", "", 25000, 1200,
                                        true, "Test station", Currency.ASHEN.faction())),
                        new EconomyView.PermitRow(
                                true, true, 24000, 0, 100, true, false, 1500, false, true, "fixed"),
                        List.of(
                                new EconomyView.ExchangeRow(
                                        Currency.ASHEN,
                                        Currency.CRIMSON,
                                        100,
                                        216,
                                        10,
                                        true,
                                        "quoted")),
                        metadata,
                        request);
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        EconomyView.snapshot(snapshot, buf);
        var copy = EconomyView.snapshot(buf);
        require(copy.equals(snapshot), "typed rows and strict ack roundtrip");
        buf.release();
        var command = EconomyView.Command.offer("test:bread", 3, -1);
        buf = new FriendlyByteBuf(Unpooled.buffer());
        EconomyView.command(command, buf);
        require(
                command.equals(EconomyView.command(buf)),
                "actual source and batch command roundtrip");
        buf.release();
        buf = new FriendlyByteBuf(Unpooled.buffer());
        EconomyView.snapshot(snapshot, buf);
        buf.writeByte(1);
        try {
            EconomyView.snapshot(buf);
            throw new AssertionError("trailing packet accepted");
        } catch (IllegalArgumentException expected) {
        }
        buf.release();
        try {
            EconomyView.Command.offer("test:bread", 4097, -1);
            throw new AssertionError("unbounded quantity accepted");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new EconomyView.WalletRow(
                    token, "inventory:0", 0, true, true, List.of(-1L, 0L, 0L, 0L));
            throw new AssertionError("negative wallet accepted");
        } catch (IllegalArgumentException expected) {
        }
        System.out.println(
                "EconomyProtocolTest: typed rows, batch/source bounds, ack and trailing data checks"
                    + " passed");
    }
}

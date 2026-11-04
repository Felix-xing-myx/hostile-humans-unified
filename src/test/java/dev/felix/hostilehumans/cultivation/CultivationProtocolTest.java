package dev.felix.hostilehumans.cultivation;

import io.netty.buffer.Unpooled;

import net.minecraft.network.FriendlyByteBuf;

import java.util.*;

/** Typed UI round trips include precise request acknowledgements and bounded numeric arrays. */
public final class CultivationProtocolTest {
    private static void check(boolean b, String m) {
        if (!b) throw new AssertionError(m);
    }

    public static void main(String[] args) throws Exception {
        double[] values = new double[13];
        Arrays.fill(values, 1);
        values[12] = .01;
        UUID token = UUID.randomUUID(), tx = UUID.randomUUID(), soldier = UUID.randomUUID();
        var candidate =
                new CultivationSnapshot.CandidateView(
                        0, "steady", 55, .1, .1, 1, 600000, 1.2, 1.14, values, values, values,
                        values);
        var view =
                new CultivationSnapshot.SoldierView(
                        soldier,
                        "Soldier",
                        "hostile_humans_unified:azure",
                        2,
                        true,
                        3,
                        1.1,
                        1.1,
                        .6,
                        0,
                        false,
                        false,
                        List.of("health"),
                        values,
                        values,
                        values,
                        List.of(candidate),
                        null,
                        "");
        var original =
                new CultivationSnapshot(
                        token,
                        "minecraft:overworld",
                        0,
                        64,
                        0,
                        2,
                        5,
                        true,
                        new CultivationSnapshot.AccountView(2, 700000, 300000),
                        List.of(new CultivationSnapshot.SeatView(0, null, "AVAILABLE")),
                        List.of(view),
                        "",
                        tx);
        var b = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CultivationSnapshotCodec.write(original, b);
            var read = CultivationSnapshotCodec.read(b);
            check(
                    read.acknowledgedRequest().equals(tx)
                            && read.revision() == 5
                            && read.facilityRevision() == 2,
                    "Ack is independent from unchanged revisions");
            check(
                    Arrays.equals(
                            read.soldiers().get(0).candidates().get(0).successValues(), values),
                    "All13 native values roundtrip");
            var roster = read.soldiers().get(0);
            check(roster.faction().equals("hostile_humans_unified:azure")
                    && roster.tier() == 2 && roster.breakthroughReady(),
                    "Faction, tier and breakthrough node roundtrip");
            b.clear();
            CultivationSnapshotCodec.write(original, b);
            b.writeByte(1);
            try {
                CultivationSnapshotCodec.read(b);
                throw new AssertionError();
            } catch (IllegalArgumentException expected) {
            }
            var write =
                    CultivationNetwork.class.getDeclaredMethod(
                            "write", CultivationNetwork.Request.class, FriendlyByteBuf.class);
            write.setAccessible(true);
            var decode = CultivationNetwork.class.getDeclaredMethod("read", FriendlyByteBuf.class);
            decode.setAccessible(true);
            for (var action : CultivationNetwork.Action.values()) {
                b.clear();
                var request =
                        new CultivationNetwork.Request(
                                token,
                                tx,
                                5,
                                soldier,
                                action,
                                2,
                                55,
                                CultivationNetwork.Mode.GROW,
                                true,
                                List.of("health"),
                                1);
                write.invoke(null, request, b);
                var actual = (CultivationNetwork.Request) decode.invoke(null, b);
                check(
                        actual.equals(request),
                        "Request " + action + " keeps opaque identity and revision");
            }
        } finally {
            b.release();
        }
        var first = view.values();
        first[0] = 999;
        check(view.values()[0] == 1, "Snapshot arrays are not mutable authority");
        System.out.println("Cultivation bounded typed protocol and exact acknowledgement passed");
    }
}

package dev.felix.hostilehumans.cultivation;

import net.minecraft.network.FriendlyByteBuf;

import java.util.*;

/**
 * Fixed bounds apply before allocating lists or arrays; UI never supplies authoritative numeric
 * outcomes.
 */
public final class CultivationSnapshotCodec {
    private CultivationSnapshotCodec() {}

    public static void write(CultivationSnapshot s, FriendlyByteBuf b) {
        b.writeUUID(s.token());
        b.writeUtf(s.dimension(), 256);
        b.writeInt(s.x());
        b.writeInt(s.y());
        b.writeInt(s.z());
        b.writeVarLong(s.facilityRevision());
        b.writeVarLong(s.revision());
        b.writeBoolean(s.testMode());
        b.writeVarLong(s.account().stock());
        b.writeVarLong(s.account().creditMicro());
        b.writeVarLong(s.account().reservedMicro());
        b.writeVarInt(s.seats().size());
        for (var seat : s.seats()) {
            b.writeVarInt(seat.index());
            uuid(b, seat.soldier());
            b.writeUtf(seat.status(), 64);
        }
        b.writeVarInt(s.soldiers().size());
        for (var v : s.soldiers()) {
            b.writeUUID(v.id());
            b.writeUtf(v.name(), 128);
            b.writeUtf(v.faction(), 256);
            b.writeVarInt(v.tier());
            b.writeBoolean(v.breakthroughReady());
            b.writeVarLong(v.revision());
            b.writeDouble(v.baseBudget());
            b.writeDouble(v.budget());
            b.writeDouble(v.potential());
            b.writeDouble(v.used());
            b.writeBoolean(v.breakthrough());
            b.writeBoolean(v.expanded());
            b.writeVarInt(v.primary().size());
            for (String p : v.primary()) b.writeUtf(p, 64);
            array(b, v.values());
            array(b, v.lower());
            array(b, v.upper());
            b.writeVarInt(v.candidates().size());
            for (var c : v.candidates()) {
                b.writeVarInt(c.index());
                b.writeUtf(c.style(), 32);
                b.writeLong(c.signature());
                b.writeDouble(c.nominalStep());
                b.writeDouble(c.potentialCost());
                b.writeDouble(c.materialCost());
                b.writeVarLong(c.durationMillis());
                b.writeDouble(c.successBudget());
                b.writeDouble(c.failureBudget());
                array(b, c.successValues());
                array(b, c.failureValues());
                array(b, c.lower());
                array(b, c.upper());
            }
            var c = v.cycle();
            b.writeBoolean(c != null);
            if (c != null) {
                b.writeUUID(c.id());
                b.writeUtf(c.status(), 32);
                b.writeVarLong(c.elapsedMillis());
                b.writeVarLong(c.durationMillis());
                b.writeBoolean(c.automatic());
                b.writeVarInt(c.selected());
                b.writeUtf(c.reason(), 256);
            }
            b.writeUtf(v.reason(), 256);
        }
        b.writeUtf(s.reason(), 256);
        b.writeUUID(s.acknowledgedRequest());
    }

    public static CultivationSnapshot read(FriendlyByteBuf b) {
        UUID token = b.readUUID();
        String dim = b.readUtf(256);
        int x = b.readInt(), y = b.readInt(), z = b.readInt();
        long fr = positive(b.readVarLong()), rev = positive(b.readVarLong());
        boolean test = b.readBoolean();
        var account =
                new CultivationSnapshot.AccountView(
                        positive(b.readVarLong()),
                        positive(b.readVarLong()),
                        positive(b.readVarLong()));
        List<CultivationSnapshot.SeatView> seats = new ArrayList<>();
        for (int n = count(b, 3); n > 0; n--)
            seats.add(new CultivationSnapshot.SeatView(count(b, 2), uuid(b), b.readUtf(64)));
        List<CultivationSnapshot.SoldierView> soldiers = new ArrayList<>();
        for (int n = count(b, 128); n > 0; n--) {
            UUID id = b.readUUID();
            String name = b.readUtf(128), faction = b.readUtf(256);
            int tier = b.readVarInt();
            boolean breakthroughReady = b.readBoolean();
            long revision = positive(b.readVarLong());
            double base = number(b), budget = number(b), potential = number(b), used = number(b);
            boolean breakthrough = b.readBoolean(), expanded = b.readBoolean();
            List<String> primary = new ArrayList<>();
            for (int k = count(b, 5); k > 0; k--) primary.add(b.readUtf(64));
            double[] values = array(b), lower = array(b), upper = array(b);
            List<CultivationSnapshot.CandidateView> candidates = new ArrayList<>();
            for (int k = count(b, 3); k > 0; k--)
                candidates.add(
                        new CultivationSnapshot.CandidateView(
                                count(b, 2),
                                b.readUtf(32),
                                b.readLong(),
                                number(b),
                                number(b),
                                number(b),
                                positive(b.readVarLong()),
                                number(b),
                                number(b),
                                array(b),
                                array(b),
                                array(b),
                                array(b)));
            CultivationSnapshot.CycleView cycle =
                    b.readBoolean()
                            ? new CultivationSnapshot.CycleView(
                                    b.readUUID(),
                                    b.readUtf(32),
                                    positive(b.readVarLong()),
                                    positive(b.readVarLong()),
                                    b.readBoolean(),
                                    count(b, 2),
                                    b.readUtf(256))
                            : null;
            soldiers.add(
                    new CultivationSnapshot.SoldierView(
                            id,
                            name,
                            faction,
                            tier,
                            breakthroughReady,
                            revision,
                            base,
                            budget,
                            potential,
                            used,
                            breakthrough,
                            expanded,
                            primary,
                            values,
                            lower,
                            upper,
                            candidates,
                            cycle,
                            b.readUtf(256)));
        }
        var s =
                new CultivationSnapshot(
                        token,
                        dim,
                        x,
                        y,
                        z,
                        fr,
                        rev,
                        test,
                        account,
                        seats,
                        soldiers,
                        b.readUtf(256),
                        b.readUUID());
        if (b.isReadable()) throw new IllegalArgumentException("Trailing cultivation snapshot");
        return s;
    }

    private static int count(FriendlyByteBuf b, int max) {
        int n = b.readVarInt();
        if (n < 0 || n > max) throw new IllegalArgumentException("Cultivation list bound");
        return n;
    }

    private static long positive(long n) {
        if (n < 0) throw new IllegalArgumentException("Negative training value");
        return n;
    }

    private static double number(FriendlyByteBuf b) {
        double n = b.readDouble();
        if (!Double.isFinite(n) || n < 0)
            throw new IllegalArgumentException("Invalid training number");
        return n;
    }

    private static void array(FriendlyByteBuf b, double[] a) {
        if (a.length != 13) throw new IllegalArgumentException("Expected13 dimensions");
        for (double v : a) b.writeDouble(v);
    }

    private static double[] array(FriendlyByteBuf b) {
        double[] a = new double[13];
        for (int i = 0; i < 13; i++) a[i] = number(b);
        return a;
    }

    private static void uuid(FriendlyByteBuf b, UUID id) {
        b.writeBoolean(id != null);
        if (id != null) b.writeUUID(id);
    }

    private static UUID uuid(FriendlyByteBuf b) {
        return b.readBoolean() ? b.readUUID() : null;
    }
}

package dev.felix.hostilehumans.settlement;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Real protocol codecs: request correlation survives unchanged revisions and negative/unsolicited
 * snapshots.
 */
public final class SettlementNetworkCodecTest {
    private static int checks;

    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }

    private static Method method(String name, Class<?>... parameters) throws Exception {
        var method = SettlementNetwork.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    public static void main(String[] args) throws Exception {
        var encode =
                method("encodeSnapshot", SettlementNetwork.Snapshot.class, FriendlyByteBuf.class);
        var decode = method("decodeSnapshot", FriendlyByteBuf.class);
        UUID request = UUID.randomUUID(), token = UUID.randomUUID(), instance = UUID.randomUUID();
        var payload = new CompoundTag();
        payload.putString("name", "Site");
        for (boolean valid : new boolean[] {true, false}) {
            var expected =
                    new SettlementNetwork.Snapshot(
                            new BlockPos(-3, 64, 8),
                            token,
                            instance,
                            7,
                            7,
                            false,
                            valid,
                            "unchanged or rejected",
                            payload,
                            request);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            encode.invoke(null, expected, buffer);
            var actual = (SettlementNetwork.Snapshot) decode.invoke(null, buffer);
            check(
                    actual.acknowledgedRequest().equals(request)
                            && actual.instanceRevision() == 7
                            && actual.valid() == valid,
                    "same revision and refusal echo exact request");
            check(
                    actual.data().equals(payload) && actual.anchor().equals(expected.anchor()),
                    "payload and negative positions round trip");
            buffer.release();
        }
        check(
                new SettlementNetwork.Snapshot(
                                BlockPos.ZERO, token, instance, 0, 0, false, true, "open", payload)
                        .acknowledgedRequest()
                        .equals(new UUID(0, 0)),
                "unsolicited open is never a request acknowledgement");
        Class<?> requestClass = Class.forName(SettlementNetwork.class.getName() + "$Request");
        var constructor =
                requestClass.getDeclaredConstructor(
                        UUID.class,
                        UUID.class,
                        int.class,
                        int.class,
                        SettlementNetwork.Action.class,
                        CompoundTag.class);
        constructor.setAccessible(true);
        Object original =
                constructor.newInstance(
                        request, token, 7, 7, SettlementNetwork.Action.REFRESH, payload);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        method("encodeRequest", requestClass, FriendlyByteBuf.class).invoke(null, original, buffer);
        var decoded = method("decodeRequest", FriendlyByteBuf.class).invoke(null, buffer);
        var accessor = requestClass.getDeclaredMethod("requestId");
        accessor.setAccessible(true);
        check(accessor.invoke(decoded).equals(request), "REFRESH carries request UUID");
        buffer.release();
        check(
                SettlementNetwork.class
                                .getMethod(
                                        "request",
                                        SettlementNetwork.Snapshot.class,
                                        SettlementNetwork.Action.class,
                                        CompoundTag.class)
                                .getReturnType()
                        == UUID.class,
                "client request exposes the correlation ID");
        buffer = new FriendlyByteBuf(Unpooled.buffer());
        encode.invoke(
                null,
                new SettlementNetwork.Snapshot(
                        BlockPos.ZERO,
                        token,
                        instance,
                        0,
                        0,
                        false,
                        false,
                        "invalid token",
                        new CompoundTag(),
                        request),
                buffer);
        buffer.writeByte(1);
        boolean refused = false;
        try {
            decode.invoke(null, buffer);
        } catch (java.lang.reflect.InvocationTargetException invalid) {
            refused = invalid.getCause() instanceof IllegalArgumentException;
        }
        check(refused, "trailing packet payload rejected");
        buffer.release();
        System.out.println(
                "SettlementNetworkCodecTest: "
                        + checks
                        + " codec checks passed; server authorization integration remains"
                        + " separate");
    }
}

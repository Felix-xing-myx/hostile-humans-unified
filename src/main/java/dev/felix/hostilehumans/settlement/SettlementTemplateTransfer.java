package dev.felix.hostilehumans.settlement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Transfers an immutable export snapshot to the requesting player, in bounded packets. */
public final class SettlementTemplateTransfer {
    private static final int CHUNK_BYTES = 48 * 1024;
    private static final int DIGEST_BYTES = 32;
    private static final int MAX_ACTIVE = 4;
    private static final Map<ServerPlayer, Outgoing> OUTGOING = new WeakHashMap<>();
    private static Incoming incoming;

    private SettlementTemplateTransfer() {}

    private static final class Outgoing {
        final UUID token = UUID.randomUUID();
        final ResourceLocation id;
        final byte[] bytes;
        final byte[] digest;
        int offset;

        Outgoing(ResourceLocation id, byte[] bytes) {
            this.id = id;
            this.bytes = bytes;
            digest = digest(bytes);
        }
    }

    private static final class Incoming {
        final Chunk first;
        final byte[] bytes;
        int offset;

        Incoming(Chunk first) {
            this.first = first;
            bytes = new byte[first.total()];
        }
    }

    public record Chunk(UUID token, ResourceLocation id, int total, int offset, byte[] digest, byte[] data) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeUUID(token);
            buffer.writeUtf(id.toString(), 256);
            buffer.writeVarInt(total);
            buffer.writeVarInt(offset);
            buffer.writeByteArray(digest);
            buffer.writeByteArray(data);
        }

        static Chunk decode(FriendlyByteBuf buffer) {
            return new Chunk(buffer.readUUID(), new ResourceLocation(buffer.readUtf(256)),
                    buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readByteArray(DIGEST_BYTES), buffer.readByteArray(CHUNK_BYTES));
        }
    }

    static Component queue(ServerPlayer player, ResourceLocation id) {
        if (!player.canUseGameMasterBlocks()) return message("denied", id);
        if (player.getServer().isSingleplayerOwner(player.getGameProfile())) return message("local", id);
        if (OUTGOING.containsKey(player) || OUTGOING.size() >= MAX_ACTIVE) return message("busy", id);
        try {
            Path path = SettlementTemplateLibrary.path(id);
            byte[] bytes;
            try (var input = Files.newInputStream(path)) {
                bytes = input.readNBytes((int) SettlementTemplateLibrary.MAX_COMPRESSED_BYTES + 1);
            }
            if (id.toString().length() > 256 || bytes.length == 0
                    || bytes.length > SettlementTemplateLibrary.MAX_COMPRESSED_BYTES) {
                return message("failed", id);
            }
            OUTGOING.put(player, new Outgoing(id, bytes));
            return message("queued", id);
        } catch (IOException | IllegalArgumentException error) {
            return message("failed", id);
        }
    }

    static void tick(ServerPlayer player) {
        var transfer = OUTGOING.get(player);
        if (transfer == null) return;
        if (!player.canUseGameMasterBlocks()) {
            cancel(player);
            return;
        }
        int end = Math.min(transfer.offset + CHUNK_BYTES, transfer.bytes.length);
        SettlementNetwork.sendTemplateChunk(player, new Chunk(transfer.token, transfer.id,
                transfer.bytes.length, transfer.offset, transfer.digest,
                Arrays.copyOfRange(transfer.bytes, transfer.offset, end)));
        transfer.offset = end;
        if (end == transfer.bytes.length) cancel(player);
    }

    static void cancel(ServerPlayer player) {
        OUTGOING.remove(player);
    }

    public static void resetClient() {
        incoming = null;
    }

    /** Returns a translated status only when a transfer finishes or fails. */
    public static Component receive(Chunk chunk) {
        if (chunk.total() <= 0 || chunk.total() > SettlementTemplateLibrary.MAX_COMPRESSED_BYTES
                || chunk.offset() < 0 || chunk.data().length == 0
                || chunk.data().length > chunk.total() - chunk.offset()
                || chunk.digest().length != DIGEST_BYTES) {
            resetClient();
            return message("failed", chunk.id());
        }
        if (chunk.offset() == 0) incoming = new Incoming(chunk);
        var transfer = incoming;
        if (transfer == null || !transfer.first.token().equals(chunk.token())
                || !transfer.first.id().equals(chunk.id()) || transfer.offset != chunk.offset()
                || transfer.bytes.length != chunk.total() || !Arrays.equals(transfer.first.digest(), chunk.digest())) {
            resetClient();
            return message("failed", chunk.id());
        }
        System.arraycopy(chunk.data(), 0, transfer.bytes, transfer.offset, chunk.data().length);
        transfer.offset += chunk.data().length;
        if (transfer.offset < transfer.bytes.length) return null;
        resetClient();
        if (!MessageDigest.isEqual(digest(transfer.bytes), chunk.digest())) return message("failed", chunk.id());

        try {
            Path file = SettlementTemplateLibrary.path(chunk.id());
            Files.createDirectories(file.getParent());
            Path staging = Files.createTempFile(file.getParent(), ".hhu-download-", ".tmp");
            try {
                Files.write(staging, transfer.bytes);
                SettlementTemplateLibrary.read(staging);
                if (Files.exists(file)) {
                    if (Files.mismatch(file, staging) == -1) return message("received", chunk.id());
                    return message("conflict", chunk.id());
                }
                Files.move(staging, file);
                return message("received", chunk.id());
            } finally {
                Files.deleteIfExists(staging);
            }
        } catch (IOException | RuntimeException error) {
            return message("failed", chunk.id());
        }
    }

    private static Component message(String status, ResourceLocation id) {
        return Component.translatable("gui.hostile_humans_unified.settlement.download." + status, id.toString());
    }

    private static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}

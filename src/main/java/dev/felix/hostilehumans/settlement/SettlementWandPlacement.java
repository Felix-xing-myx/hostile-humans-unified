package dev.felix.hostilehumans.settlement;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/** Placement authority is tied to the player and world, independent of any author carrier. */
public final class SettlementWandPlacement {
    private static final int SESSION_TICKS = 2400;
    private record Session(UUID token, ServerLevel level, long expires, Request placement) {}
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private SettlementWandPlacement() {}

    public record Request(UUID token, String id, int turns, Mirror mirror, boolean entities) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(token);
            buf.writeUtf(id, 256);
            buf.writeVarInt(turns);
            buf.writeEnum(mirror);
            buf.writeBoolean(entities);
        }

        static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readUUID(), buf.readUtf(256), buf.readVarInt(),
                    buf.readEnum(Mirror.class), buf.readBoolean());
        }
    }

    public record View(UUID token, boolean armed, String reason, ListTag choices) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(token);
            buf.writeBoolean(armed);
            buf.writeUtf(reason, 256);
            var data = new net.minecraft.nbt.CompoundTag();
            data.put("choices", choices);
            buf.writeNbt(data);
        }

        static View decode(FriendlyByteBuf buf) {
            var token = buf.readUUID();
            boolean armed = buf.readBoolean();
            String reason = buf.readUtf(256);
            var data = buf.readNbt();
            return new View(token, armed, reason, data == null ? new ListTag()
                    : data.getList("choices", net.minecraft.nbt.Tag.TAG_COMPOUND));
        }
    }

    private static boolean allowed(ServerPlayer player) {
        return SettlementAccess.author(player) && player.canUseGameMasterBlocks();
    }

    static boolean holding(ServerPlayer player) {
        var tool = SettlementContent.SELECTION_TOOL.get();
        return player.getMainHandItem().is(tool) || player.getOffhandItem().is(tool);
    }

    static void open(ServerPlayer player) {
        if (!allowed(player) || !holding(player)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "gui.hostile_humans_unified.settlement.wand_permission"), true);
            return;
        }
        var level = player.serverLevel();
        var token = UUID.randomUUID();
        SESSIONS.put(player, new Session(token, level, level.getGameTime() + SESSION_TICKS, null));
        SettlementNetwork.wandView(player, new View(token, false, "", SettlementTemplateFiles.list(level)));
    }

    static void arm(ServerPlayer player, Request request) {
        var session = current(player);
        if (session == null || !session.token().equals(request.token()) || session.placement() != null
                || !holding(player)) {
            SettlementNetwork.wandView(player, new View(request.token(), false, "wand_session_expired", new ListTag()));
            return;
        }
        var id = ResourceLocation.tryParse(request.id());
        if (id == null || request.id().isBlank() || request.turns() < 0 || request.turns() > 3) {
            SettlementNetwork.wandView(player, new View(request.token(), false, "invalid_input", new ListTag()));
            return;
        }
        if (session.level().getStructureManager().get(id).isEmpty()) {
            SettlementNetwork.wandView(player, new View(request.token(), false, "wand_missing", new ListTag()));
            return;
        }
        SettlementSelectionTool.clear(player);
        SettlementTemplateFiles.cancel(player);
        SESSIONS.put(player, new Session(request.token(), session.level(),
                session.level().getGameTime() + SESSION_TICKS, request));
        SettlementNetwork.preview(player, new SettlementPreview(session.level().dimension().location().toString(),
                List.of(), null, null, true, List.of(), "place", id.toString()));
        SettlementNetwork.wandView(player, new View(request.token(), true, "", new ListTag()));
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "item.hostile_humans_unified.settlement_selection_tool.pick_placement"), true);
    }

    private static Session current(ServerPlayer player) {
        var session = SESSIONS.get(player);
        if (session != null && allowed(player) && session.level() == player.serverLevel()
                && session.level().getGameTime() < session.expires()) return session;
        if (session != null) cancel(player);
        return null;
    }

    static boolean armed(ServerPlayer player) {
        var session = current(player);
        return session != null && session.placement() != null;
    }

    static void tick(ServerPlayer player) {
        current(player);
    }

    static void preview(ServerPlayer player, BlockPos corner) {
        var session = current(player);
        if (session == null || session.placement() == null) return;
        var request = session.placement();
        var rotation = switch (request.turns()) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        String message = SettlementTemplateFiles.preview(player, new ResourceLocation(request.id()),
                corner, rotation, request.mirror(), request.entities());
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
    }

    static void cancel(ServerPlayer player) {
        var old = SESSIONS.remove(player);
        if (old != null && old.placement() != null) {
            SettlementTemplateFiles.cancel(player);
            SettlementNetwork.preview(player, SettlementPreview.EMPTY);
        }
    }
}

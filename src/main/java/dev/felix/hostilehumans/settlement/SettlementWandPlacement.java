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
    private record Session(UUID token, ServerLevel level, long expires, Request placement, BlockPos corner) {}
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private SettlementWandPlacement() {}

    public record Request(UUID token, String id, int turns, Mirror mirror, boolean entities,
            BlockPos corner, boolean confirm) {
        public Request(UUID token, String id, int turns, Mirror mirror, boolean entities) {
            this(token, id, turns, mirror, entities, null, false);
        }
        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(token);
            buf.writeUtf(id, 256);
            buf.writeVarInt(turns);
            buf.writeEnum(mirror);
            buf.writeBoolean(entities);
            buf.writeBoolean(corner != null);
            if (corner != null) buf.writeBlockPos(corner);
            buf.writeBoolean(confirm);
        }

        static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readUUID(), buf.readUtf(256), buf.readVarInt(),
                    buf.readEnum(Mirror.class), buf.readBoolean(), buf.readBoolean() ? buf.readBlockPos() : null, buf.readBoolean());
        }
    }

    public record View(UUID token, boolean armed, String reason, ListTag choices, Request adjustment) {
        View(UUID token, boolean armed, String reason, ListTag choices) {
            this(token, armed, reason, choices, null);
        }
        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(token);
            buf.writeBoolean(armed);
            buf.writeUtf(reason, 256);
            var data = new net.minecraft.nbt.CompoundTag();
            data.put("choices", choices);
            buf.writeNbt(data);
            buf.writeBoolean(adjustment != null);
            if (adjustment != null) adjustment.encode(buf);
        }

        static View decode(FriendlyByteBuf buf) {
            var token = buf.readUUID();
            boolean armed = buf.readBoolean();
            String reason = buf.readUtf(256);
            var data = buf.readNbt();
            return new View(token, armed, reason, data == null ? new ListTag()
                    : data.getList("choices", net.minecraft.nbt.Tag.TAG_COMPOUND),
                    buf.readBoolean() ? Request.decode(buf) : null);
        }
    }

    private static boolean allowed(ServerPlayer player) {
        return SettlementAccess.author(player) && player.canUseGameMasterBlocks();
    }

    static boolean holding(ServerPlayer player) {
        var tool = SettlementContent.PLACEMENT_TOOL.get();
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
        SESSIONS.put(player, new Session(token, level, level.getGameTime() + SESSION_TICKS, null, null));
        SettlementNetwork.wandView(player, new View(token, false, "", SettlementTemplateFiles.list(level)));
    }

    static void arm(ServerPlayer player, Request request) {
        var session = current(player);
        if (request.corner() != null) {
            adjust(player, session, request);
            return;
        }
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
        SettlementTemplateFiles.cancel(player);
        SESSIONS.put(player, new Session(request.token(), session.level(),
                session.level().getGameTime() + SESSION_TICKS, request, null));
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
        preview(player, request, corner);
        SESSIONS.put(player, new Session(session.token(), session.level(),
                session.level().getGameTime() + SESSION_TICKS, request, corner.immutable()));
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "gui.hostile_humans_unified.settlement.wand_preview_ready"), true);
    }

    private static void preview(ServerPlayer player, Request request, BlockPos corner) {
        var rotation = switch (request.turns()) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        SettlementTemplateFiles.preview(player, new ResourceLocation(request.id()),
                corner, rotation, request.mirror(), request.entities(), SESSION_TICKS, request.token());
    }

    static boolean openAdjustment(ServerPlayer player) {
        var session = current(player);
        if (session == null || session.corner() == null || !SettlementTemplateFiles.hasPreview(player, session.token())) return false;
        SettlementTemplateFiles.keepPreview(player, SESSION_TICKS, session.token());
        SESSIONS.put(player, new Session(session.token(), session.level(),
                session.level().getGameTime() + SESSION_TICKS, session.placement(), session.corner()));
        var selected = session.placement();
        var adjustment = new Request(session.token(), selected.id(), selected.turns(), selected.mirror(),
                selected.entities(), session.corner(), false);
        SettlementNetwork.wandView(player, new View(session.token(), false, "", new ListTag(), adjustment));
        return true;
    }

    private static void adjust(ServerPlayer player, Session session, Request request) {
        if (session == null || session.corner() == null || !session.token().equals(request.token())
                || !holding(player) || !request.id().equals(session.placement().id())
                || request.turns() < 0 || request.turns() > 3 || !SettlementTemplateFiles.hasPreview(player, session.token())) {
            SettlementNetwork.wandView(player, new View(request.token(), false, "wand_session_expired", new ListTag()));
            return;
        }
        try {
            boolean changed = !request.corner().equals(session.corner()) || request.turns() != session.placement().turns()
                    || request.mirror() != session.placement().mirror();
            var selected = new Request(session.token(), request.id(), request.turns(), request.mirror(),
                    session.placement().entities());
            if (changed) preview(player, selected, request.corner());
            else SettlementTemplateFiles.keepPreview(player, SESSION_TICKS, session.token());
            SESSIONS.put(player, new Session(session.token(), session.level(),
                    session.level().getGameTime() + SESSION_TICKS, selected, request.corner()));
            if (request.confirm()) {
                String message = SettlementTemplateFiles.confirm(player, session.token());
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
                cancel(player);
            }
            SettlementNetwork.wandView(player, new View(request.token(), true, "", new ListTag()));
        } catch (IllegalArgumentException bad) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(bad.getMessage()));
            SettlementNetwork.wandView(player, new View(request.token(), false, "wand_adjust_rejected", new ListTag()));
        }
    }

    static void cancel(ServerPlayer player) {
        var old = SESSIONS.remove(player);
        if (old != null && old.placement() != null) {
            SettlementTemplateFiles.cancelWand(player, old.token());
        }
    }
}

package dev.felix.hostilehumans.loot;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.settlement.SettlementAccess;
import dev.felix.hostilehumans.soldier.CommandRateLimit;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Small author protocol. No client-provided file paths, player objects or inventory mutations. */
public final class LootEditorNetwork {
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private static final Set<MinecraftServer> RELOADING = Collections.newSetFromMap(new WeakHashMap<>());
    private record Session(UUID token, String dimension, BlockPos position) {}
    public record Request(UUID token, String action, String id, String revision, String json, String filter, int page, boolean replace) {}
    public record View(CompoundTag data) {}
    private static final class Connection {
        static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HumanRuntime.MOD_ID, "loot_editor"),
                () -> "1", "1"::equals, "1"::equals);
    }
    private LootEditorNetwork() {}
    public static void register() {
        Connection.CHANNEL.registerMessage(0, Request.class, LootEditorNetwork::encode, LootEditorNetwork::decode, LootEditorNetwork::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        Connection.CHANNEL.registerMessage(1, View.class, (v,b) -> b.writeNbt(v.data()), b -> new View(Objects.requireNonNull(b.readNbt())),
                LootEditorNetwork::receive, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void request(Request request) { Connection.CHANNEL.sendToServer(request); }
    private static boolean allowed(ServerPlayer p) {
        return SettlementAccess.author(p) && (p.getMainHandItem().getItem() instanceof LootEditorTool || p.getOffhandItem().getItem() instanceof LootEditorTool);
    }
    public static void open(ServerPlayer player, BlockPos position) {
        if (!allowed(player)) return;
        Session session = new Session(UUID.randomUUID(), player.level().dimension().location().toString(), position == null ? null : position.immutable());
        SESSIONS.put(player, session);
        CompoundTag data = base(session); data.putBoolean("opening", true);
        if (position != null) {
            try {
                var selected = LootContainers.inspect(player, position); selection(data, selected);
                var id = ResourceLocation.tryParse(selected.table());
                if (id != null) document(data, LootLibrary.document(player.getServer(), id));
            }
            catch (Exception error) { data.putString("error", code(error)); }
        }
        catalog(player, data, "", 0); send(player, data);
    }
    private static CompoundTag base(Session session) {
        CompoundTag data = new CompoundTag(); data.putUUID("token", session.token());
        if (session.position() != null) data.put("position", NbtUtils.writeBlockPos(session.position()));
        return data;
    }
    private static void selection(CompoundTag data, LootContainers.Selection selection) {
        data.putString("containerRevision", selection.revision()); data.putString("containerTable", selection.table());
        data.putBoolean("occupied", selection.occupied()); data.putInt("halves", selection.positions().size());
    }
    private static void document(CompoundTag data, LootLibrary.Document doc) {
        data.putString("id", doc.id()); data.putString("json", doc.json()); data.putString("revision", doc.revision());
        data.putString("source", doc.source()); data.putBoolean("active", doc.active());
    }
    private static void catalog(ServerPlayer player, CompoundTag data, String filter, int page) {
        var ids = LootLibrary.ids(player.getServer()).stream().filter(id -> id.contains(filter.toLowerCase(Locale.ROOT))).toList();
        int pages = Math.max(1, (ids.size()+23)/24), current = Math.min(page, pages-1);
        ListTag list = new ListTag(); ids.stream().skip(current*24L).limit(24).forEach(id -> list.add(StringTag.valueOf(id)));
        data.put("ids", list); data.putInt("pages", pages); data.putInt("page", current); data.putInt("total", ids.size());
    }
    private static void handle(Request request, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); context.enqueueWork(() -> {
            ServerPlayer player = context.getSender(); if (player == null) return;
            Session session = SESSIONS.get(player);
            if (session == null || !session.token().equals(request.token())) return;
            if (request.action().equals("close")) { SESSIONS.remove(player); return; }
            CompoundTag data = base(session);
            if (!allowed(player) || !session.dimension().equals(player.level().dimension().location().toString())) {
                data.putString("error", "permission_changed"); send(player, data); return;
            }
            if (!CommandRateLimit.allow(player)) { data.putString("error", "rate_limited"); send(player, data); return; }
            try {
                ResourceLocation id = request.id().isEmpty() ? null : ResourceLocation.tryParse(request.id());
                switch (request.action()) {
                    case "list" -> catalog(player, data, request.filter(), request.page());
                    case "load" -> document(data, LootLibrary.document(player.getServer(), required(id)));
                    case "publish" -> {
                        if (RELOADING.contains(player.getServer())) throw new IllegalArgumentException("reload_busy");
                        document(data, LootLibrary.save(player.getServer(), required(id), request.revision(), request.json()));
                        reload(player, session, id); return;
                    }
                    case "reload" -> { reload(player, session, id); return; }
                    case "export" -> { data.putString("export", LootLibrary.export(player.getServer(), required(id))); data.putString("notice", "exported"); }
                    case "bind" -> {
                        if (session.position() == null) throw new IllegalArgumentException("container_select");
                        if (!LootLibrary.document(player.getServer(), required(id)).active()) throw new IllegalArgumentException("table_inactive");
                        LootContainers.bind(player, session.position(), request.revision(), required(id), request.replace());
                        selection(data, LootContainers.inspect(player, session.position())); data.putString("notice", "bound");
                    }
                    default -> throw new IllegalArgumentException("request_unknown");
                }
            } catch (Exception error) { data.putString("error", code(error)); }
            send(player, data);
        }); context.setPacketHandled(true);
    }
    private static ResourceLocation required(ResourceLocation id) { if (id == null) throw new IllegalArgumentException("id_invalid"); return id; }
    private static String code(Exception error) {
        String message = error.getMessage();
        return message == null ? "failed" : message.substring(0, Math.min(1200, message.length()));
    }
    private static void reload(ServerPlayer player, Session session, ResourceLocation id) {
        var server = player.getServer();
        if (!RELOADING.add(server)) { var data = base(session); data.putString("error", "reload_busy"); send(player, data); return; }
        try {
            server.getPackRepository().reload();
            var packs = new HashSet<>(server.getPackRepository().getSelectedIds()); packs.add(LootLibraryPack.ID);
            server.reloadResources(packs).whenComplete((unused, error) -> server.execute(() -> {
                RELOADING.remove(server);
                if (SESSIONS.get(player) != session || !allowed(player)) return;
                var data = base(session); data.putString(error == null ? "notice" : "error", error == null ? "reloaded" : "reload_failed");
                if (id != null) {
                    try { document(data, LootLibrary.document(server, id)); }
                    catch (Exception bad) { data.putString("error", code(bad)); }
                }
                catalog(player, data, "", 0); send(player, data);
            }));
        } catch (RuntimeException error) { RELOADING.remove(server); var data = base(session); data.putString("error", code(error)); send(player, data); }
    }
    private static void send(ServerPlayer player, CompoundTag data) { Connection.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new View(data)); }
    private static void receive(View view, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.felix.hostilehumans.loot.client.LootEditorScreen.show(view.data()))); context.setPacketHandled(true);
    }
    private static void encode(Request r, FriendlyByteBuf b) {
        b.writeUUID(r.token()); b.writeUtf(r.action(),16); b.writeUtf(r.id(),256); b.writeUtf(r.revision(),64);
        b.writeUtf(r.json(),65536); b.writeUtf(r.filter(),128); b.writeVarInt(r.page()); b.writeBoolean(r.replace());
    }
    private static Request decode(FriendlyByteBuf b) {
        var r = new Request(b.readUUID(),b.readUtf(16),b.readUtf(256),b.readUtf(64),b.readUtf(65536),b.readUtf(128),b.readVarInt(),b.readBoolean());
        if (r.page() < 0 || r.page() > 100000 || b.readableBytes() != 0) throw new IllegalArgumentException("Invalid loot author request");
        return r;
    }
}

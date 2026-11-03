package dev.felix.hostilehumans.settlement;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Bounded server-authoritative editor/management protocol. Client pages install a callback, never
 * become authority.
 */
public final class SettlementNetwork {
    private static final UUID NONE = new UUID(0, 0);
    private static final int NBT_LIMIT = 1048576;
    private static final String VERSION = "8";
    private static final int SESSION_TICKS = 1200;
    public static final String SELECTION_READY = "selection mode updated";

    private static final class Connection {
        static final SimpleChannel CHANNEL =
                NetworkRegistry.newSimpleChannel(
                        new ResourceLocation(HumanRuntime.MOD_ID, "settlement"),
                        () -> VERSION,
                        VERSION::equals,
                        VERSION::equals);
    }

    public enum Action {
        REFRESH,
        SAVE_AUTHOR,
        IMPORT_NEW,
        JOIN_INSTANCE,
        EXPORT_AUTHOR,
        UPDATE_INSTANCE,
        DELETE_INSTANCE,
        RENAME,
        SELECTION_MODE,
        CLOSE,
        RESTORE_AUTHOR,
        SAVE_TEMPLATE_FILE,
        KEEP_ALIVE,
        BOUNDS_AS_OUTER
    }

    public record Snapshot(
            BlockPos anchor,
            UUID token,
            UUID instance,
            int authorRevision,
            int instanceRevision,
            boolean author,
            boolean valid,
            String reason,
            CompoundTag data,
            UUID acknowledgedRequest) {
        public Snapshot {
            anchor = anchor.immutable();
            data = data.copy();
            java.util.Objects.requireNonNull(acknowledgedRequest);
        }

        public Snapshot(
                BlockPos anchor,
                UUID token,
                UUID instance,
                int authorRevision,
                int instanceRevision,
                boolean author,
                boolean valid,
                String reason,
                CompoundTag data) {
            this(
                    anchor,
                    token,
                    instance,
                    authorRevision,
                    instanceRevision,
                    author,
                    valid,
                    reason,
                    data,
                    NONE);
        }
    }

    private record Session(
            UUID token,
            BlockPos anchor,
            ResourceLocation dimension,
            UUID instance,
            boolean author,
            long expires) {}

    private record Request(
            UUID requestId,
            UUID token,
            int authorRevision,
            int instanceRevision,
            Action action,
            CompoundTag data) {}

    private record PostRequest(
            UUID requestId, UUID instance, String facility, UUID human, int revision) {}

    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Integer> MODULE_PAGES = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();
    private static final Map<ServerPlayer, LinkedHashSet<UUID>> REQUEST_IDS = new WeakHashMap<>();
    private static volatile Consumer<Snapshot> receiver = s -> {};
    private static boolean registered;

    private SettlementNetwork() {}

    private static volatile Consumer<SettlementPreview> previewReceiver = value -> {};

    private static volatile Consumer<SettlementWandPlacement.View> wandReceiver = value -> {};

    private record SelectorRequest(CompoundTag data) {}
    private record SelectorView(CompoundTag data) {}
    private static Consumer<CompoundTag> selectorReceiver = data -> {};

    public static void clientSelectorReceiver(Consumer<CompoundTag> receiver) {
        selectorReceiver = java.util.Objects.requireNonNull(receiver);
    }

    public static void configureSelector(CompoundTag data) {
        Connection.CHANNEL.sendToServer(new SelectorRequest(data));
    }

    static void selectorView(ServerPlayer player, CompoundTag data) {
        Connection.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SelectorView(data));
    }

    public static void clientWandReceiver(Consumer<SettlementWandPlacement.View> callback) {
        wandReceiver = java.util.Objects.requireNonNull(callback);
    }

    public static void armWand(SettlementWandPlacement.Request request) {
        Connection.CHANNEL.sendToServer(request);
    }

    static void wandView(ServerPlayer player, SettlementWandPlacement.View view) {
        Connection.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), view);
    }

    public static void clientPreviewReceiver(Consumer<SettlementPreview> callback) {
        previewReceiver = java.util.Objects.requireNonNull(callback);
    }

    public static void clientReceiver(Consumer<Snapshot> callback) {
        receiver = java.util.Objects.requireNonNull(callback);
    }

    public static void preview(ServerPlayer player, SettlementPreview value) {
        Connection.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), value);
    }

    public static UUID request(Snapshot snapshot, Action action, CompoundTag data) {
        UUID id = UUID.randomUUID();
        Connection.CHANNEL.sendToServer(
                new Request(
                        id,
                        snapshot.token(),
                        snapshot.authorRevision(),
                        snapshot.instanceRevision(),
                        action,
                        data.copy()));
        return id;
    }

    public static UUID assignPost(UUID instance, String facility, UUID human, int revision) {
        UUID id = UUID.randomUUID();
        Connection.CHANNEL.sendToServer(new PostRequest(id, instance, facility, human, revision));
        return id;
    }

    public static void register() {
        if (registered) return;
        registered = true;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                SettlementNetwork::playerTick);
        Connection.CHANNEL.registerMessage(
                0,
                Request.class,
                SettlementNetwork::encodeRequest,
                SettlementNetwork::decodeRequest,
                SettlementNetwork::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        Connection.CHANNEL.registerMessage(
                1,
                Snapshot.class,
                SettlementNetwork::encodeSnapshot,
                SettlementNetwork::decodeSnapshot,
                SettlementNetwork::receive,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        Connection.CHANNEL.registerMessage(
                2,
                PostRequest.class,
                SettlementNetwork::encodePost,
                SettlementNetwork::decodePost,
                SettlementNetwork::handlePost,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        Connection.CHANNEL.registerMessage(3, SettlementPreview.class, SettlementPreview::encode,
                SettlementPreview::decode, (value, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> previewReceiver.accept(value));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        Connection.CHANNEL.registerMessage(4, SettlementWandPlacement.Request.class,
                SettlementWandPlacement.Request::encode, SettlementWandPlacement.Request::decode,
                (request, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> {
                        var player = context.getSender();
                        if (player != null && throttle(player)) SettlementWandPlacement.arm(player, request);
                    });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        Connection.CHANNEL.registerMessage(5, SettlementWandPlacement.View.class,
                SettlementWandPlacement.View::encode, SettlementWandPlacement.View::decode,
                (view, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> wandReceiver.accept(view));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        Connection.CHANNEL.registerMessage(6, SelectorRequest.class,
                (request, buffer) -> buffer.writeNbt(request.data()), buffer -> new SelectorRequest(payload(buffer)),
                (request, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> {
                        var player = context.getSender();
                        if (player != null && throttle(player)) SettlementSelectionTool.configure(player, request.data());
                    });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        Connection.CHANNEL.registerMessage(7, SelectorView.class,
                (view, buffer) -> buffer.writeNbt(view.data()), buffer -> new SelectorView(payload(buffer)),
                (view, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> selectorReceiver.accept(view.data()));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void openAuthor(ServerPlayer player, BlockPos anchor) {
        var info = SettlementAuthoring.information(player, anchor);
        if (info == null) {
            tell(player, "Author permission, distance or loaded information block required.");
            return;
        }
        SettlementAuthoring.initialize(player, anchor);
        var s =
                new Session(
                        UUID.randomUUID(),
                        anchor.immutable(),
                        player.level().dimension().location(),
                        info.instance(),
                        true,
                        player.level().getGameTime() + SESSION_TICKS);
        SESSIONS.put(player, s);
        MODULE_PAGES.remove(player);
        REQUEST_IDS.remove(player);
        send(player, s, "author editor");
    }

    private static String controlId(ServerPlayer player, UUID id, BlockPos position) {
        var instance = SettlementIndex.get(player.serverLevel()).find(id);
        if (instance == null) return null;
        if (!(player.serverLevel().getBlockState(position).getBlock()
                        instanceof SettlementControlBlock block)
                || !instance.faction().endsWith(":" + block.faction())) return null;
        String found = null;
        for (var module : instance.modules())
            for (var f : module.facilities())
                if (f.type() == SettlementFacility.Type.CONTROL
                        && f.position()
                                .equals(
                                        new TemplateTransform.Cell(
                                                position.getX(),
                                                position.getY(),
                                                position.getZ()))) {
                    if (found != null) return null;
                    found = module.key() + "/" + f.id();
                }
        return found;
    }

    public static void openControl(ServerPlayer player, BlockPos anchor) {
        if (!near(player, anchor) || !player.serverLevel().hasChunkAt(anchor)) {
            tell(player, "Control block unavailable.");
            return;
        }
        var query =
                SettlementIndex.get(player.serverLevel())
                        .query(anchor.getX() + .5, anchor.getY(), anchor.getZ() + .5);
        if (query.unknown()) {
            tell(player, "Region data unknown; repair before management.");
            return;
        }
        UUID id = null;
        String facility = null;
        for (UUID candidate :
                query.hits().stream().map(SettlementIndex.Hit::instance).distinct().toList()) {
            String key = controlId(player, candidate, anchor);
            if (key == null) continue;
            if (id != null) {
                tell(player, "Ambiguous overlapping controls.");
                return;
            }
            id = candidate;
            facility = key;
        }
        if (id == null) {
            tell(player, "This block has no registered control facility.");
            return;
        }
        var permission =
                SettlementAccess.queryFacility(
                        player, id, facility, SettlementAccess.Operation.VIEW);
        if (!permission.allowed()) {
            tell(player, permission.reason());
            return;
        }
        var session =
                new Session(
                        UUID.randomUUID(),
                        anchor.immutable(),
                        player.level().dimension().location(),
                        id,
                        false,
                        player.level().getGameTime() + SESSION_TICKS);
        SESSIONS.put(player, session);
        MODULE_PAGES.remove(player);
        REQUEST_IDS.remove(player);
        send(player, session, "read-only management");
    }

    private static boolean near(ServerPlayer p, BlockPos at) {
        return p.isAlive()
                && !p.isSpectator()
                && p.distanceToSqr(at.getX() + .5, at.getY() + .5, at.getZ() + .5) <= 64;
    }

    private static Session renew(Session session, long now) {
        return new Session(session.token(), session.anchor(), session.dimension(),
                session.instance(), session.author(), now + SESSION_TICKS);
    }

    private static boolean valid(ServerPlayer player, Session session) {
        if (player.level().getGameTime() > session.expires()
                || !session.dimension().equals(player.level().dimension().location())
                || !player.serverLevel().hasChunkAt(session.anchor())) return false;
        if (session.author())
            return SettlementAuthoring.information(player, session.anchor()) != null;
        String facility = controlId(player, session.instance(), session.anchor());
        return near(player, session.anchor())
                && facility != null
                && SettlementAccess.queryFacility(
                                player,
                                session.instance(),
                                facility,
                                SettlementAccess.Operation.VIEW)
                        .allowed();
    }

    private static CompoundTag view(ServerPlayer player, SettlementInstance instance) {
        var data = new CompoundTag();
        data.putString("name", instance.name());
        data.putString("faction", instance.faction());
        data.putInt("tier", instance.tier());
        data.putString("residents", "not integrated");
        var modules = new ListTag();
        int page =
                SettlementPages.page(
                        MODULE_PAGES.getOrDefault(player, 0), instance.modules().size());
        MODULE_PAGES.put(player, page);
        data.putInt("module_page", page);
        data.putInt("module_pages", SettlementPages.pages(instance.modules().size()));
        data.putInt("module_total", instance.modules().size());
        for (var module : SettlementPages.slice(instance.modules(), page)) {
            var row = new CompoundTag();
            row.putString("key", module.key());
            row.put("regions", SettlementCodec.regions(module.regions()));
            var facilities = SettlementCodec.facilities(module.facilities());
            for (int i = 0; i < facilities.size(); i++) {
                var f = module.facilities().get(i);
                String id = module.key() + "/" + f.id();
                var t = facilities.getCompound(i);
                t.putString("stable_id", id);
                var op =
                        switch (f.type()) {
                            case CONTROL -> SettlementAccess.Operation.VIEW;
                            case SUPPLY -> SettlementAccess.Operation.SUPPLY;
                            case TRADER -> SettlementAccess.Operation.TRADE;
                            case TRAINING -> SettlementAccess.Operation.TRAIN;
                            case POST -> SettlementAccess.Operation.POST;
                            default -> SettlementAccess.Operation.ENTER_OUTER;
                        };
                var access = SettlementAccess.queryFacility(player, instance.id(), id, op);
                t.putString("status", access.status().name());
                t.putString("reason", access.reason());
            }
            row.put("facilities", facilities);
            modules.add(row);
        }
        data.put("modules", modules);
        var owned = new ListTag();
        var candidates =
                player.serverLevel()
                        .getEntitiesOfClass(
                                dev.felix.hostilehumans.entity.Human.class,
                                player.getBoundingBox().inflate(64),
                                human ->
                                        human.isAlive()
                                                && dev.felix.hostilehumans.faction.HumanRelations
                                                        .isOwnedBy(human, player));
        candidates.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
        for (var human : candidates.stream().limit(32).toList()) {
            var row = new CompoundTag();
            row.putUUID("id", human.getUUID());
            String name = human.getDisplayName().getString();
            row.putString("name", name.length() > 96 ? name.substring(0, 96) : name);
            owned.add(row);
        }
        data.put("owned_soldiers", owned);
        return data;
    }

    private static void send(ServerPlayer player, Session session, String reason) {
        send(player, session, reason, NONE);
    }

    private static void send(
            ServerPlayer player, Session session, String reason, UUID acknowledged) {
        if (!valid(player, session)) {
            SESSIONS.remove(player);
            Connection.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new Snapshot(
                            session.anchor(),
                            session.token(),
                            NONE,
                            0,
                            0,
                            session.author(),
                            false,
                            "session expired or permission changed",
                            new CompoundTag(),
                            acknowledged));
            return;
        }
        var info =
                session.author() ? SettlementAuthoring.information(player, session.anchor()) : null;
        UUID id = info == null ? session.instance() : info.instance();
        var instance = id == null ? null : SettlementIndex.get(player.serverLevel()).find(id);
        var data = instance == null ? new CompoundTag() : view(player, instance);
        if (info != null) {
            data.put("author", info.author());
            data.putString("module", info.module());
            try {
                data.put(
                        "author_transform",
                        SettlementCodec.transform(
                                SettlementAuthoring.currentTransform(player, info)));
            } catch (RuntimeException unknown) {
                data.putString("transform_reason", shortReason(unknown.getMessage()));
            }
        }
        Connection.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new Snapshot(
                        session.anchor(),
                        session.token(),
                        id == null ? NONE : id,
                        info == null ? 0 : info.revision(),
                        instance == null ? 0 : instance.revision(),
                        session.author(),
                        true,
                        shortReason(reason),
                        data,
                        acknowledged));
    }

    private static boolean throttle(ServerPlayer player) {
        long tick = player.level().getGameTime();
        Long last = LAST_REQUEST.get(player);
        if (last != null && tick >= last && tick - last < 4) return false;
        LAST_REQUEST.put(player, tick);
        return true;
    }

    private static void playerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0) return;
        SettlementTemplateFiles.tick(player);
        SettlementSelectionTool.tick(player);
        SettlementWandPlacement.tick(player);
        var session = SESSIONS.get(player);
        if (session != null && !valid(player, session))
            send(player, session, "permission, distance or session changed");
    }

    private static void handle(Request request, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(
                () -> {
                    var player = context.getSender();
                    if (player == null) return;
                    var session = SESSIONS.get(player);
                    if (session == null || !session.token().equals(request.token())) {
                        if (request.action() == Action.KEEP_ALIVE) return;
                        reject(
                                player,
                                request.token(),
                                request.requestId(),
                                "invalid session token");
                        return;
                    }
                    if (request.action() == Action.CLOSE) {
                        SESSIONS.remove(player);
                        reject(player, request.token(), request.requestId(), "closed");
                        return;
                    }
                    if (!valid(player, session)) {
                        send(player, session, "permission or session changed", request.requestId());
                        return;
                    }
                    if (request.action() == Action.KEEP_ALIVE) {
                        long now = player.level().getGameTime();
                        if (session.expires() - now <= SESSION_TICKS * 3 / 4)
                            SESSIONS.put(player, renew(session, now));
                        return;
                    }
                    if (!throttle(player)) {
                        reject(
                                player,
                                request.token(),
                                request.requestId(),
                                "request rate limited; reopen panel");
                        return;
                    }
                    session = renew(session, player.level().getGameTime());
                    SESSIONS.put(player, session);
                    var seen = REQUEST_IDS.computeIfAbsent(player, k -> new LinkedHashSet<>());
                    if (!seen.add(request.requestId())) {
                        send(player, session, "duplicate request ignored", request.requestId());
                        return;
                    }
                    while (seen.size() > 64) seen.remove(seen.iterator().next());
                    if (request.action() == Action.REFRESH
                            && request.data()
                                    .contains("module_page", net.minecraft.nbt.Tag.TAG_INT))
                        MODULE_PAGES.put(player, Math.max(0, request.data().getInt("module_page")));
                    String reason = "refreshed";
                    try {
                        if (request.action() == Action.RENAME) {
                            if (session.author())
                                reason = "use indexed management control to rename";
                            else {
                                var id = controlId(player, session.instance(), session.anchor());
                                var permission =
                                        SettlementAccess.queryFacility(
                                                player,
                                                session.instance(),
                                                id,
                                                SettlementAccess.Operation.RENAME);
                                reason =
                                        permission.allowed()
                                                ? SettlementIndex.get(player.serverLevel())
                                                        .rename(
                                                                session.instance(),
                                                                request.instanceRevision(),
                                                                request.data().getString("name"))
                                                        .reason()
                                                : permission.reason();
                            }
                        } else if (request.action() != Action.REFRESH) {
                            if (!session.author() || !SettlementAccess.author(player)) {
                                send(
                                        player,
                                        session,
                                        "author operation denied",
                                        request.requestId());
                                return;
                            }
                            var at = session.anchor();
                            var data = request.data();
                            reason =
                                    switch (request.action()) {
                                        case SAVE_AUTHOR ->
                                                SettlementAuthoring.save(
                                                        player,
                                                        at,
                                                        request.authorRevision(),
                                                        data.getCompound("author"));
                                        case IMPORT_NEW ->
                                                SettlementAuthoring.importNew(
                                                                player,
                                                                at,
                                                                request.authorRevision(),
                                                                data)
                                                        .reason();
                                        case JOIN_INSTANCE ->
                                                data.hasUUID("target")
                                                        ? SettlementAuthoring.join(
                                                                        player,
                                                                        at,
                                                                        request.authorRevision(),
                                                                        data.getUUID("target"),
                                                                        data.getInt(
                                                                                "target_revision"),
                                                                        data)
                                                                .reason()
                                                        : "target instance required";
                                        case EXPORT_AUTHOR ->
                                                SettlementAuthoring.export(
                                                        player,
                                                        at,
                                                        request.authorRevision(),
                                                        request.instanceRevision());
                                        case UPDATE_INSTANCE ->
                                                SettlementAuthoring.update(
                                                                player,
                                                                at,
                                                                request.authorRevision(),
                                                                request.instanceRevision(),
                                                                data)
                                                        .reason();
                                        case DELETE_INSTANCE -> {
                                            var info = SettlementAuthoring.information(player, at);
                                            yield info == null || info.instance() == null
                                                    ? "instance link missing"
                                                    : SettlementIndex.get(player.serverLevel())
                                                            .delete(
                                                                    info.instance(),
                                                                    request.instanceRevision())
                                                            .reason();
                                        }
                                        case RESTORE_AUTHOR ->
                                                data.hasUUID("target")
                                                        ? SettlementAuthoring.restore(
                                                                player,
                                                                at,
                                                                data.getUUID("target"),
                                                                data.getString("module"),
                                                                data.getInt("target_revision"))
                                                        : "target instance required";
                                        case SELECTION_MODE -> {
                                            var mode = data.contains("mode")
                                                    ? SettlementSelectionTool.Mode.valueOf(data.getString("mode"))
                                                    : SettlementSelectionTool.Mode.ADD;
                                            if (!SettlementSelectionTool.hasTool(player))
                                                yield SettlementSelectionTool.NEED_TOOL;
                                            int revision = request.authorRevision();
                                            if (data.contains("author", Tag.TAG_COMPOUND)) {
                                                var saved = SettlementAuthoring.save(player, at, revision, data.getCompound("author"));
                                                var info = SettlementAuthoring.information(player, at);
                                                if (info == null || info.revision() != (long) revision + 1
                                                        || !saved.startsWith("saved author revision ")) yield saved;
                                                revision = info.revision();
                                            }
                                            yield SettlementSelectionTool.mode(
                                                        player,
                                                        at,
                                                        data.getString("region"),
                                                        data.getBoolean("exclude"),
                                                        data.getBoolean("current_only"),
                                                        data.getInt("box_index"),
                                                        mode,
                                                        revision);
                                        }
                                        case BOUNDS_AS_OUTER ->
                                                SettlementAuthoring.useBoundsAsOuter(player, at,
                                                        request.authorRevision(), data.getString("region"));
                                        case SAVE_TEMPLATE_FILE ->
                                                SettlementTemplateFiles.save(player, at,
                                                        request.authorRevision(), data.getBoolean("entities"),
                                                        data.getBoolean("overwrite"));
                                        default -> "unsupported operation";
                                    };
                        }
                    } catch (RuntimeException invalid) {
                        reason = "rejected: " + invalid.getMessage();
                    }
                    send(player, session, reason, request.requestId());
                });
        context.setPacketHandled(true);
    }

    private static void handlePost(PostRequest request, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(
                () -> {
                    var player = context.getSender();
                    if (player == null) return;
                    if (!throttle(player)) {
                        reject(player, NONE, request.requestId(), "post request rate limited");
                        return;
                    }
                    var seen = REQUEST_IDS.computeIfAbsent(player, k -> new LinkedHashSet<>());
                    String reason;
                    if (!seen.add(request.requestId())) reason = "duplicate post request ignored";
                    else {
                        while (seen.size() > 64) seen.remove(seen.iterator().next());
                        try {
                            var management = SESSIONS.get(player);
                            reason =
                                    management != null
                                                    && valid(player, management)
                                                    && request.instance()
                                                            .equals(management.instance())
                                            ? SettlementPosts.assignFromControl(
                                                    player,
                                                    management.anchor(),
                                                    request.instance(),
                                                    request.facility(),
                                                    request.human(),
                                                    request.revision())
                                            : SettlementPosts.assign(
                                                    player,
                                                    request.instance(),
                                                    request.facility(),
                                                    request.human(),
                                                    request.revision());
                        } catch (RuntimeException bad) {
                            reason = "post rejected: " + bad.getMessage();
                        }
                    }
                    tell(player, reason);
                    var session = SESSIONS.get(player);
                    if (session != null && valid(player, session))
                        send(player, session, reason, request.requestId());
                    else reject(player, NONE, request.requestId(), reason);
                });
        context.setPacketHandled(true);
    }

    private static void reject(ServerPlayer player, UUID token, UUID requestId, String reason) {
        Connection.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new Snapshot(
                        BlockPos.ZERO,
                        token,
                        NONE,
                        0,
                        0,
                        false,
                        false,
                        shortReason(reason),
                        new CompoundTag(),
                        requestId));
    }

    private static void tell(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(shortReason(message)), true);
    }

    private static String shortReason(String value) {
        if (value == null) return "unknown";
        return value.length() > 256 ? value.substring(0, 256) : value;
    }

    private static CompoundTag payload(FriendlyByteBuf buffer) {
        CompoundTag value = buffer.readNbt(new NbtAccounter(NBT_LIMIT));
        if (value == null) value = new CompoundTag();
        if (buffer.readableBytes() != 0)
            throw new IllegalArgumentException("trailing settlement payload");
        return value;
    }

    private static void encodeRequest(Request value, FriendlyByteBuf b) {
        b.writeUUID(value.requestId());
        b.writeUUID(value.token());
        b.writeVarInt(value.authorRevision());
        b.writeVarInt(value.instanceRevision());
        b.writeEnum(value.action());
        b.writeNbt(value.data());
    }

    private static Request decodeRequest(FriendlyByteBuf b) {
        return new Request(
                b.readUUID(),
                b.readUUID(),
                b.readVarInt(),
                b.readVarInt(),
                b.readEnum(Action.class),
                payload(b));
    }

    private static void encodeSnapshot(Snapshot value, FriendlyByteBuf b) {
        b.writeBlockPos(value.anchor());
        b.writeUUID(value.token());
        b.writeUUID(value.instance());
        b.writeVarInt(value.authorRevision());
        b.writeVarInt(value.instanceRevision());
        b.writeBoolean(value.author());
        b.writeBoolean(value.valid());
        b.writeUtf(value.reason(), 256);
        b.writeUUID(value.acknowledgedRequest());
        b.writeNbt(value.data());
    }

    private static Snapshot decodeSnapshot(FriendlyByteBuf b) {
        var anchor = b.readBlockPos();
        var token = b.readUUID();
        var instance = b.readUUID();
        int author = b.readVarInt(), revision = b.readVarInt();
        boolean editing = b.readBoolean(), valid = b.readBoolean();
        String reason = b.readUtf(256);
        UUID acknowledged = b.readUUID();
        return new Snapshot(
                anchor,
                token,
                instance,
                author,
                revision,
                editing,
                valid,
                reason,
                payload(b),
                acknowledged);
    }

    private static void receive(Snapshot value, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> receiver.accept(value));
        context.setPacketHandled(true);
    }

    private static void encodePost(PostRequest value, FriendlyByteBuf b) {
        b.writeUUID(value.requestId());
        b.writeUUID(value.instance());
        b.writeUtf(value.facility(), 224);
        b.writeUUID(value.human());
        b.writeVarInt(value.revision());
    }

    private static PostRequest decodePost(FriendlyByteBuf b) {
        var result =
                new PostRequest(
                        b.readUUID(), b.readUUID(), b.readUtf(224), b.readUUID(), b.readVarInt());
        if (b.readableBytes() != 0) throw new IllegalArgumentException("trailing post payload");
        return result;
    }
}

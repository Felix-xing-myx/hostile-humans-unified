package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.data.HumanServerData;
import com.craftix.hostile_humans.entity.entities.Human;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/** Owner-only roster snapshots and dismissal commands. Never trusts client roster contents. */
final class SoldierRosterNetwork {
    private static final String PROTOCOL = "4";
    static final int PAGE_SIZE = 5;
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_REORDER_TICK = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_SCROLL_TICK = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_STATUS_REFRESH_TICK = new WeakHashMap<>();
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HumanGunner.MOD_ID, "soldier_roster"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    record Entry(UUID id, int tier, String name, String status, String dimension,
                 int x, int y, int z, int health, int maxHealth,
                 String order, String combat, boolean pickup,
                 boolean canMoveEarlier, boolean canMoveLater) {}
    private record Session(UUID token, int filter, int page) {}
    private record Snapshot(UUID token, boolean opening, int filter, int page, int total,
                            List<Entry> entries, int[] counts, int[] limits) {}
    private record Request(UUID token, int action, UUID human, int value) {}
    private SoldierRosterNetwork() {}

    static void register() {
        CHANNEL.registerMessage(0, Snapshot.class, SoldierRosterNetwork::writeSnapshot,
                SoldierRosterNetwork::readSnapshot, SoldierRosterNetwork::receiveSnapshot,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, Request.class, SoldierRosterNetwork::writeRequest,
                SoldierRosterNetwork::readRequest, SoldierRosterNetwork::receiveRequest,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    static void open(ServerPlayer player) {
        UUID token = UUID.randomUUID();
        Session session = new Session(token, -1, 0);
        SESSIONS.put(player, session);
        sendSnapshot(player, session, true);
    }

    static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SESSIONS.remove(player);
            LAST_REORDER_TICK.remove(player);
            LAST_SCROLL_TICK.remove(player);
            LAST_STATUS_REFRESH_TICK.remove(player);
        }
    }

    static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
        LAST_REORDER_TICK.clear();
        LAST_SCROLL_TICK.clear();
        LAST_STATUS_REFRESH_TICK.clear();
    }

    static void request(UUID token, int action, UUID human) {
        request(token, action, human, 0);
    }

    static void request(UUID token, int action, UUID human, int value) {
        CHANNEL.sendToServer(new Request(token, action, human, value));
    }

    static void refreshIfOpen(ServerPlayer player) {
        Session session = SESSIONS.get(player);
        if (session != null) {
            sendSnapshot(player, session, false);
        }
    }

    private static void sendSnapshot(ServerPlayer player, Session session, boolean opening) {
        MinecraftServer server = player.server;
        RecruitmentLedger ledger = RecruitmentLedger.get(server);
        ledger.refreshLoadedIndex(server, player.getUUID());
        RecruitmentLedger.RosterView view = ledger.roster(player.getUUID());
        List<RecruitmentLedger.Soldier> all = view.filtered(session.filter);
        int page = Math.min(session.page, Math.max(0, (all.size() - 1) / PAGE_SIZE));
        List<RecruitmentLedger.Soldier> displayed = all.subList(page * PAGE_SIZE,
                Math.min(all.size(), (page + 1) * PAGE_SIZE));
        SoldierRosterAudit.refresh(player, displayed.stream().map(RecruitmentLedger.Soldier::id).toList());
        // Auditing can remove stale entries; rebuild the page once against the authoritative ledger.
        RecruitmentLedger.RosterView audited = ledger.roster(player.getUUID());
        if (audited != view) {
            view = audited;
            all = view.filtered(session.filter);
        }
        page = Math.min(page, Math.max(0, (all.size() - 1) / PAGE_SIZE));
        displayed = all.subList(page * PAGE_SIZE, Math.min(all.size(), (page + 1) * PAGE_SIZE));
        HumanServerData saved = HumanServerData.get();
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < displayed.size(); i++) {
            RecruitmentLedger.Soldier soldier = displayed.get(i);
            int position = page * PAGE_SIZE + i;
            boolean canMoveEarlier = position > 0;
            boolean canMoveLater = position + 1 < all.size();
            Human live = findLoaded(server, soldier.id());
            if (live != null && (!live.isAlive() || !HumanRelations.isOwnedBy(live, player))) {
                live = null;
            }
            HumanData stored = saved == null ? null : saved.getHumanMob(soldier.id());
            if (live != null && saved != null
                    && (stored == null || live.hasCustomName()
                    && !live.getCustomName().getString().equals(stored.getName()))) {
                saved.updateOrRegisterHumanMob(live);
                stored = saved.getHumanMob(soldier.id());
            }
            String name = live != null ? live.getDisplayName().getString()
                    : stored != null && !stored.getName().isBlank() ? stored.getName() : "";
            String status = live != null ? "online"
                    : HiredHumanRecall.hasPending(server, soldier.id()) ? "recalling"
                    : stored == null ? "missing"
                    : SoldierRosterAudit.suspectedMissingInLoadedChunk(server, soldier.id())
                    ? "suspect" : "unloaded";
            net.minecraft.core.BlockPos pos = live != null ? live.blockPosition()
                    : stored != null ? stored.getBlockPos() : null;
            String dimension = live != null ? live.level().dimension().location().toString()
                    : stored != null && stored.getLevelKey() != null
                    ? stored.getLevelKey().location().toString() : "?";
            if (name.length() > 256) name = name.substring(0, 256);
            CompoundTag lastData = live != null || stored == null ? new CompoundTag()
                    : stored.getPersistedCommandData();
            String order = live != null ? SoldierOrder.get(live).name()
                    : validOrder(lastData.getString(SoldierOrder.ORDER));
            String combat = live != null ? SoldierCombatMode.get(live).name()
                    : validCombat(lastData.getString(SoldierCombatMode.MODE));
            boolean pickup = live != null ? SoldierPickupPolicy.isEnabled(live)
                    : lastData.getBoolean(SoldierPickupPolicy.ENABLED);
            entries.add(new Entry(soldier.id(), soldier.tier(), name, status, dimension,
                    pos == null ? 0 : pos.getX(), pos == null ? 0 : pos.getY(), pos == null ? 0 : pos.getZ(),
                    live == null ? -1 : Math.round(live.getHealth()),
                    live == null ? -1 : Math.round(live.getMaxHealth()),
                    order, combat, pickup, canMoveEarlier, canMoveLater));
        }
        int[] counts = new int[4], limits = new int[4];
        for (int tier = 0; tier < 4; tier++) {
            counts[tier] = view.count(tier);
            limits[tier] = RecruitmentPolicy.limit(tier);
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new Snapshot(session.token, opening, session.filter, page, all.size(), entries, counts, limits));
    }

    private static String validOrder(String value) {
        try { return SoldierOrder.valueOf(value).name(); }
        catch (IllegalArgumentException ignored) { return SoldierOrder.FOLLOW.name(); }
    }

    private static String validCombat(String value) {
        try { return SoldierCombatMode.valueOf(value).name(); }
        catch (IllegalArgumentException ignored) { return SoldierCombatMode.ACTIVE.name(); }
    }

    private static Human findLoaded(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Human human && !human.isRemoved()) return human;
        }
        return null;
    }

    private static void writeSnapshot(Snapshot packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.token);
        buffer.writeBoolean(packet.opening);
        buffer.writeVarInt(packet.filter);
        buffer.writeVarInt(packet.page);
        buffer.writeVarInt(packet.total);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buffer.writeUUID(entry.id);
            buffer.writeVarInt(entry.tier);
            buffer.writeUtf(entry.name, 256);
            buffer.writeUtf(entry.status, 32);
            buffer.writeUtf(entry.dimension, 256);
            buffer.writeInt(entry.x);
            buffer.writeInt(entry.y);
            buffer.writeInt(entry.z);
            buffer.writeInt(entry.health);
            buffer.writeInt(entry.maxHealth);
            buffer.writeUtf(entry.order, 32);
            buffer.writeUtf(entry.combat, 32);
            buffer.writeBoolean(entry.pickup);
            buffer.writeBoolean(entry.canMoveEarlier);
            buffer.writeBoolean(entry.canMoveLater);
        }
        for (int i = 0; i < 4; i++) {
            buffer.writeVarInt(packet.counts[i]);
            buffer.writeVarInt(packet.limits[i]);
        }
    }

    private static Snapshot readSnapshot(FriendlyByteBuf buffer) {
        UUID token = buffer.readUUID();
        boolean opening = buffer.readBoolean();
        int filter = buffer.readVarInt();
        int page = buffer.readVarInt();
        int total = buffer.readVarInt();
        int size = buffer.readVarInt();
        if (size < 0 || size > PAGE_SIZE || total < 0 || page < 0 || filter < -1 || filter > 3)
            throw new IllegalArgumentException("Invalid roster page");
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buffer.readUUID(), buffer.readVarInt(), buffer.readUtf(256),
                    buffer.readUtf(32), buffer.readUtf(256), buffer.readInt(), buffer.readInt(),
                    buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readUtf(32), buffer.readUtf(32),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean()));
        }
        int[] counts = new int[4], limits = new int[4];
        for (int i = 0; i < 4; i++) {
            counts[i] = buffer.readVarInt();
            limits[i] = buffer.readVarInt();
        }
        return new Snapshot(token, opening, filter, page, total, entries, counts, limits);
    }

    private static void receiveSnapshot(Snapshot packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> SoldierRosterScreen.show(packet.token, packet.opening, packet.filter,
                        packet.page, packet.total, packet.entries, packet.counts, packet.limits)));
        context.setPacketHandled(true);
    }

    private static void writeRequest(Request packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.token);
        buffer.writeVarInt(packet.action);
        buffer.writeUUID(packet.human);
        buffer.writeVarInt(packet.value);
    }

    private static Request readRequest(FriendlyByteBuf buffer) {
        return new Request(buffer.readUUID(), buffer.readVarInt(), buffer.readUUID(), buffer.readVarInt());
    }

    private static void receiveRequest(Request packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            Session session = SESSIONS.get(player);
            if (session == null || !session.token.equals(packet.token)) return;
            if (packet.action == 9) {
                SESSIONS.remove(player);
                LAST_REORDER_TICK.remove(player);
                LAST_SCROLL_TICK.remove(player);
                LAST_STATUS_REFRESH_TICK.remove(player);
                return;
            }
            if (packet.action == 7 && packet.value == -1 && session.filter != -1) {
                // ESC and the All tab must reliably return from a tier filter,
                // even immediately after the preceding navigation click.
                session = new Session(session.token, -1, 0);
                SESSIONS.put(player, session);
                sendSnapshot(player, session, false);
                return;
            }
            if (packet.action == 10) {
                // A periodic read must never consume the player's command cooldown.
                long tick = player.serverLevel().getGameTime();
                Long previous = LAST_STATUS_REFRESH_TICK.get(player);
                if (previous != null && tick >= previous && tick - previous < 20) return;
                LAST_STATUS_REFRESH_TICK.put(player, tick);
                sendSnapshot(player, session, false);
                return;
            }
            if (packet.action == 11 || packet.action == 12) {
                long tick = player.serverLevel().getGameTime();
                Long previous = LAST_REORDER_TICK.get(player);
                if (previous != null && tick >= previous && tick == previous) return;
                LAST_REORDER_TICK.put(player, tick);
                RecruitmentLedger.get(player.server).move(packet.human, player.getUUID(),
                        packet.action == 11 ? -1 : 1, session.filter);
                sendSnapshot(player, session, false);
                return;
            }
            if (packet.action == 8) {
                if (packet.value < 0) return;
                long tick = player.serverLevel().getGameTime();
                Long previous = LAST_SCROLL_TICK.get(player);
                if (previous != null && tick >= previous && tick == previous) return;
                LAST_SCROLL_TICK.put(player, tick);
                session = new Session(session.token, session.filter, packet.value);
                SESSIONS.put(player, session);
                sendSnapshot(player, session, false);
                return;
            }
            if (!CommandRateLimit.allow(player)) return;
            if (packet.action == 7) {
                if (packet.value < -1 || packet.value > 3) return;
                session = new Session(session.token, packet.value, 0);
                SESSIONS.put(player, session);
            } else if (packet.action == 1) {
                RecruitmentLedger ledger = RecruitmentLedger.get(player.server);
                if (!ledger.requestDismissal(packet.human, player.getUUID())) return;
                HiredHumanRecall.cancelPending(player.server, packet.human);
                Human live = findLoaded(player.server, packet.human);
                boolean ownedLive = live != null && player.getUUID().equals(live.getOwnerUUID());
                if (ownedLive) {
                    HumanRelations.dismiss(player, live);
                    ledger.finishDismissal(packet.human);
                }
                HumanServerData saved = HumanServerData.get();
                HumanData stored = saved == null ? null : saved.getHumanMob(packet.human);
                if (saved != null && stored != null && (live == null || ownedLive)
                        && player.getUUID().equals(stored.getOwnerUUID())) {
                    saved.humanGunner$removeHuman(packet.human);
                }
                player.displayClientMessage(Component.translatable(
                        live == null ? "message.humangunner.roster.dismissed_offline"
                                : "message.humangunner.dismissed.manual"), false);
            } else if (packet.action == 2) {
                HiredHumanRecall.recallAll(player);
            } else if (packet.action == 3) {
                if (!HiredHumanRecall.recallOne(player, packet.human)) {
                    player.displayClientMessage(Component.translatable(
                            "message.humangunner.roster.recall_failed"), false);
                }
            } else if (packet.action >= 4 && packet.action <= 6) {
                RecruitmentLedger ledger = RecruitmentLedger.get(player.server);
                if (!ledger.isOwnedRecord(packet.human, player.getUUID())) return;
                Human live = findLoaded(player.server, packet.human);
                if (live == null || !live.isAlive()
                        || !HumanRelations.isOwnedBy(live, player)) return;
                if (packet.action == 4) {
                    SoldierOrder[] values = SoldierOrder.values();
                    if (packet.value < 0 || packet.value >= values.length) return;
                    SoldierOrder.set(live, values[packet.value]);
                } else if (packet.action == 5) {
                    SoldierCombatMode[] values = SoldierCombatMode.values();
                    if (packet.value < 0 || packet.value >= values.length) return;
                    SoldierCombatMode.set(live, values[packet.value]);
                } else {
                    if (packet.value != 0 && packet.value != 1) return;
                    SoldierPickupPolicy.set(live, packet.value == 1);
                }
                HumanServerData data = HumanServerData.get();
                if (data != null) data.updateOrRegisterHumanMob(live);
            } else if (packet.action == 0) {
                SoldierRosterAudit.requestFullRefresh(player);
            } else {
                return;
            }
            sendSnapshot(player, session, false);
        });
        context.setPacketHandled(true);
    }
}

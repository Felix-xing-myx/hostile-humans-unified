package club.someoneice.humangunner;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server-authoritative display data only. No client request can change band identity or policy. */
public final class FactionVisualNetwork {
    public record Definition(String id, int color, String name, String style) { }
    public record Definitions(long revision, boolean humansVisible, boolean playersVisible, List<Definition> entries) {
        public Definitions { entries = List.copyOf(entries); }
    }
    public record PlayerBand(UUID player, String faction, int grade, long revision) { }
    private record Sent(long revision, boolean humansVisible, boolean playersVisible, IdentityBands.Selection selection) { }
    private static final Map<ServerPlayer, Sent> SENT = new WeakHashMap<>();
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HumanGunner.MOD_ID, "faction_visuals"), () -> "1", "1"::equals, "1"::equals);
    private static boolean registered;
    private FactionVisualNetwork() { }
    /** Call once from common setup. This also installs server lifecycle listeners. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(0, Definitions.class, FactionVisualNetwork::writeDefinitions, FactionVisualNetwork::readDefinitions,
                FactionVisualNetwork::handleDefinitions, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, PlayerBand.class, FactionVisualNetwork::writeBand, FactionVisualNetwork::readBand,
                FactionVisualNetwork::handleBand, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        MinecraftForge.EVENT_BUS.register(FactionVisualNetwork.class);
    }
    static Definitions snapshot() {
        var revision = FactionDefinitions.current();
        boolean visible = HumanFactions.enabled() && UnifiedConfig.get().factionBandRender();
        List<Definition> entries = revision.definitions().values().stream().sorted(Comparator.comparing(d -> d.id().toString()))
                .map(d -> new Definition(d.id().toString(), d.color(), d.name(), d.bandStyle().equals("none") ? "none" : "base")).toList();
        if (entries.size() > 256) throw new IllegalStateException("Faction visual definition limit exceeded");
        return new Definitions(revision.number(), visible,
                visible && UnifiedConfig.get().featureEnabled("identity_band", true), entries);
    }
    private static PlayerBand band(ServerPlayer player, IdentityBands.Selection selection, long revision) {
        return new PlayerBand(player.getUUID(), selection.faction(), selection.grade(), revision);
    }
    private static void send(ServerPlayer receiver, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> receiver), packet);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 == 0 && ModList.get().isLoaded("curios")) CuriosBandAccess.migrateRetiredSlot(player);
        var revision = FactionDefinitions.current();
        boolean humansVisible = HumanFactions.enabled() && UnifiedConfig.get().factionBandRender();
        boolean playersVisible = humansVisible && UnifiedConfig.get().featureEnabled("identity_band", true);
        IdentityBands.Selection selection = IdentityBands.selected(player);
        Sent before = SENT.get(player);
        boolean definitionsChanged = before == null || before.revision() != revision.number()
                || before.humansVisible() != humansVisible || before.playersVisible() != playersVisible;
        if (definitionsChanged) send(player, snapshot());
        if (definitionsChanged || !selection.equals(before.selection()))
            CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), band(player, selection, revision.number()));
        SENT.put(player, new Sent(revision.number(), humansVisible, playersVisible, selection));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) refreshViewer(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) refreshViewer(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) refreshViewer(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { SENT.remove(event.getEntity()); }
    private static void refreshViewer(ServerPlayer player) {
        Definitions definitions = snapshot();
        IdentityBands.Selection selection = IdentityBands.selected(player);
        send(player, definitions);
        send(player, band(player, selection, definitions.revision()));
        SENT.put(player, new Sent(definitions.revision(), definitions.humansVisible(), definitions.playersVisible(), selection));
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer)) return;
        Sent sent = SENT.get(viewer);
        if (sent == null || sent.revision() != FactionDefinitions.current().number()) refreshViewer(viewer);
        if (event.getTarget() instanceof ServerPlayer target)
            send(viewer, band(target, IdentityBands.selected(target), FactionDefinitions.current().number()));
    }
    static void writeDefinitions(Definitions packet, FriendlyByteBuf out) {
        if (packet.entries().size() > 256) throw new IllegalArgumentException("Faction visual count");
        out.writeLong(packet.revision()); out.writeBoolean(packet.humansVisible()); out.writeBoolean(packet.playersVisible());
        out.writeVarInt(packet.entries().size());
        for (Definition entry : packet.entries()) {
            if (ResourceLocation.tryParse(entry.id()) == null || (entry.color() & 0xFF000000) != 0
                    || (!entry.style().equals("base") && !entry.style().equals("none")))
                throw new IllegalArgumentException("Faction visual definition");
            out.writeUtf(entry.id(), 256); out.writeInt(entry.color()); out.writeUtf(entry.name(), 128); out.writeUtf(entry.style(), 8);
        }
    }
    static Definitions readDefinitions(FriendlyByteBuf in) {
        long revision = in.readLong(); boolean humans = in.readBoolean(), players = in.readBoolean();
        int count = in.readVarInt();
        if (count < 0 || count > 256) throw new IllegalArgumentException("Faction visual count");
        List<Definition> entries = new ArrayList<>(count); Set<String> unique = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String id = in.readUtf(256); int color = in.readInt(); String name = in.readUtf(128), style = in.readUtf(8);
            if (ResourceLocation.tryParse(id) == null || !unique.add(id) || (color & 0xFF000000) != 0
                    || (!style.equals("base") && !style.equals("none"))) throw new IllegalArgumentException("Faction visual definition");
            entries.add(new Definition(id, color, name, style));
        }
        if (in.isReadable()) throw new IllegalArgumentException("Trailing faction visual data");
        return new Definitions(revision, humans, players, entries);
    }
    static void writeBand(PlayerBand packet, FriendlyByteBuf out) {
        if (packet.grade() < 0 || packet.grade() > 4 || (packet.grade() > 0 && ResourceLocation.tryParse(packet.faction()) == null))
            throw new IllegalArgumentException("Player band state");
        out.writeUUID(packet.player()); out.writeUtf(packet.faction(), 256); out.writeVarInt(packet.grade()); out.writeLong(packet.revision());
    }
    static PlayerBand readBand(FriendlyByteBuf in) {
        PlayerBand packet = new PlayerBand(in.readUUID(), in.readUtf(256), in.readVarInt(), in.readLong());
        if (packet.grade() < 0 || packet.grade() > 4 || (packet.grade() > 0 && ResourceLocation.tryParse(packet.faction()) == null)
                || in.isReadable()) throw new IllegalArgumentException("Player band state");
        return packet;
    }
    private static void handleDefinitions(Definitions packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FactionVisualClient.accept(packet)));
        context.setPacketHandled(true);
    }
    private static void handleBand(PlayerBand packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FactionVisualClient.accept(packet)));
        context.setPacketHandled(true);
    }
}

package dev.felix.hostilehumans.observer;

import dev.felix.hostilehumans.soldier.SoldierRosterItem;
import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.faction.HumanRelations;
import dev.felix.hostilehumans.progression.EffectiveAttributes;
import dev.felix.hostilehumans.soldier.HumanCommandNetwork;
import dev.felix.hostilehumans.soldier.SoldierRosterNetwork;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** One bounded read-only inspection session per viewer. Sessions never force-load entities. */
public final class AttributeDetailNetwork {
    private record Session(UUID token, UUID target, int entityId, ResourceKey<Level> dimension,
                           boolean wildTool, long heartbeat, long lastSend) {
        Session beat(long now) { return new Session(token,target,entityId,dimension,wildTool,now,lastSend); }
        Session sent(long now) { return new Session(token,target,entityId,dimension,wildTool,heartbeat,now); }
    }
    private record Open(int entityId, UUID target, boolean wildTool, long sequence) { }
    private record Heartbeat(UUID token) { }
    private record Close(UUID token) { }
    private record Return(UUID token, boolean roster) { }
    private record View(UUID token, boolean opening, AttributeDetailSnapshot snapshot) { }
    private record Closed(UUID token) { }
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_OPEN = new WeakHashMap<>(), LAST_SEQUENCE = new WeakHashMap<>();
    private static long clientSequence;
    private static boolean registered;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HumanRuntime.MOD_ID,"attribute_detail"),
            () -> "3", "3"::equals,"3"::equals);
    private AttributeDetailNetwork() { }
    public static synchronized void register() {
        if(registered)return; registered=true;
        CHANNEL.registerMessage(0,Open.class,AttributeDetailNetwork::writeOpen,AttributeDetailNetwork::readOpen,AttributeDetailNetwork::openRequest,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1,Heartbeat.class,(p,b)->b.writeUUID(p.token()),b->new Heartbeat(readToken(b)),AttributeDetailNetwork::heartbeat,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2,Close.class,(p,b)->b.writeUUID(p.token()),b->new Close(readToken(b)),AttributeDetailNetwork::closeRequest,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3,View.class,(p,b)->{b.writeUUID(p.token());b.writeBoolean(p.opening());p.snapshot().write(b);},b->{
            View view=new View(b.readUUID(),b.readBoolean(),AttributeDetailSnapshot.read(b)); end(b);return view;
        },AttributeDetailNetwork::receive,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4,Closed.class,(p,b)->b.writeUUID(p.token()),b->new Closed(readToken(b)),AttributeDetailNetwork::closed,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5,Return.class,(p,b)->{b.writeUUID(p.token());b.writeBoolean(p.roster());},b->{
            Return packet=new Return(b.readUUID(),b.readBoolean());end(b);return packet;
        },AttributeDetailNetwork::returnRequest,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        MinecraftForge.EVENT_BUS.register(AttributeDetailNetwork.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->AttributeDetailClient::register);
    }
    /** Thin client button hook for an already identified own soldier. */
    public static void request(int entityId, UUID target, boolean wildTool) {
        CHANNEL.sendToServer(new Open(entityId,target,wildTool,++clientSequence));
    }
    public static void requestOwned(int entityId, UUID humanId) { request(entityId,humanId,false); }
    public static void keepAlive(UUID token) { CHANNEL.sendToServer(new Heartbeat(token)); }
    public static void close(UUID token) { CHANNEL.sendToServer(new Close(token)); }
    public static void returnToParent(UUID token, boolean roster) { CHANNEL.sendToServer(new Return(token,roster)); }
    private static long now(ServerPlayer viewer) { return viewer.serverLevel().getGameTime(); }
    private static boolean rateOpen(ServerPlayer viewer) {
        long now=now(viewer), previous=LAST_OPEN.getOrDefault(viewer,Long.MIN_VALUE/2);
        if(now>=previous && now-previous<10)return false;
        LAST_OPEN.put(viewer,now); return true;
    }
    static boolean permitted(ServerPlayer viewer,Human target,boolean wildTool) {
        return AttributeDetailPolicy.allowed(viewer.isAlive(),target.isAlive()&&!target.isRemoved(),viewer.level()==target.level(),viewer.distanceToSqr(target),
                HumanRelations.isOwnedBy(target,viewer),target.hasOwner(),target.getPersistentData().contains(HumanRelations.TEMP_OWNER),
                viewer.getMainHandItem().getItem() instanceof AttributeObserverItem||viewer.getOffhandItem().getItem() instanceof AttributeObserverItem,wildTool);
    }
    public static boolean openFor(ServerPlayer viewer, Human target, boolean wildTool) {
        if(!rateOpen(viewer))return false;
        if(!permitted(viewer,target,wildTool)) {
            viewer.displayClientMessage(Component.translatable("attribute_detail.denied"),true); return false;
        }
        UUID token=UUID.randomUUID();long now=now(viewer);
        Session session=new Session(token,target.getUUID(),target.getId(),viewer.level().dimension(),wildTool,now,now);
        SESSIONS.put(viewer,session);
        send(viewer,new View(token,true,EffectiveAttributes.detailSnapshot(target))); return true;
    }
    private static Human target(ServerPlayer viewer,Session session) {
        if(!viewer.level().dimension().equals(session.dimension()))return null;
        var entity=viewer.serverLevel().getEntity(session.entityId());
        return entity instanceof Human human&&human.getUUID().equals(session.target())&&permitted(viewer,human,session.wildTool())?human:null;
    }
    private static void send(ServerPlayer viewer,Object packet) { CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),packet); }
    private static void endSession(ServerPlayer viewer,Session session) { SESSIONS.remove(viewer);send(viewer,new Closed(session.token())); }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        for(var entry:new ArrayList<>(SESSIONS.entrySet())) {
            ServerPlayer viewer=entry.getKey(); Session session=entry.getValue(); long now=now(viewer);
            Human target=target(viewer,session);
            if(target==null||AttributeDetailPolicy.expired(now,session.heartbeat())) { endSession(viewer,session);continue; }
            if(now-session.lastSend()>=20) { send(viewer,new View(session.token(),false,EffectiveAttributes.detailSnapshot(target)));SESSIONS.put(viewer,session.sent(now)); }
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void inspect(PlayerInteractEvent.EntityInteract event) {
        if(!(event.getItemStack().getItem() instanceof AttributeObserverItem)||!(event.getTarget() instanceof Human human))return;
        if(event.getEntity() instanceof ServerPlayer viewer)openFor(viewer,human,true);
        event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity());LAST_OPEN.remove(event.getEntity());LAST_SEQUENCE.remove(event.getEntity());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.clear();LAST_OPEN.clear();LAST_SEQUENCE.clear(); }
    private static void writeOpen(Open packet,FriendlyByteBuf out) { out.writeVarInt(packet.entityId());out.writeUUID(packet.target());out.writeBoolean(packet.wildTool());out.writeLong(packet.sequence()); }
    private static Open readOpen(FriendlyByteBuf in) { Open packet=new Open(in.readVarInt(),in.readUUID(),in.readBoolean(),in.readLong());end(in);return packet; }
    private static UUID readToken(FriendlyByteBuf in) { UUID token=in.readUUID();end(in);return token; }
    private static void end(FriendlyByteBuf in) { if(in.isReadable())throw new IllegalArgumentException("Trailing attribute message data"); }
    private static void openRequest(Open packet,Supplier<NetworkEvent.Context> supplier) {
        var context=supplier.get();ServerPlayer viewer=context.getSender();context.enqueueWork(()->{
            if(viewer==null||!AttributeDetailPolicy.nextSequence(LAST_SEQUENCE.getOrDefault(viewer,0L),packet.sequence()))return;
            LAST_SEQUENCE.put(viewer,packet.sequence());
            var entity=viewer.serverLevel().getEntity(packet.entityId());
            if(entity instanceof Human human&&human.getUUID().equals(packet.target()))openFor(viewer,human,packet.wildTool());
            else rateOpen(viewer);
        });context.setPacketHandled(true);
    }
    private static void heartbeat(Heartbeat packet,Supplier<NetworkEvent.Context> supplier) {
        var context=supplier.get();ServerPlayer viewer=context.getSender();context.enqueueWork(()->{
            Session session=SESSIONS.get(viewer);if(session!=null&&session.token().equals(packet.token())) {
                long now=now(viewer);if(now-session.heartbeat()>=10)SESSIONS.put(viewer,session.beat(now));
            }
        });context.setPacketHandled(true);
    }
    private static void closeRequest(Close packet,Supplier<NetworkEvent.Context> supplier) {
        var context=supplier.get();ServerPlayer viewer=context.getSender();context.enqueueWork(()->{
            Session session=SESSIONS.get(viewer);if(session!=null&&session.token().equals(packet.token()))SESSIONS.remove(viewer);
        });context.setPacketHandled(true);
    }
    /** Consumes the detail capability; a fresh parent session always passes its original authority boundary. */
    private static void returnRequest(Return packet,Supplier<NetworkEvent.Context> supplier) {
        var context=supplier.get();ServerPlayer viewer=context.getSender();context.enqueueWork(()->{
            Session session=SESSIONS.get(viewer);
            if(viewer==null||session==null||!session.token().equals(packet.token()))return;
            Human human=target(viewer,session);
            SESSIONS.remove(viewer); // replay cannot reopen another parent or leave a hidden detail session
            if(human==null||AttributeDetailPolicy.expired(now(viewer),session.heartbeat())) {
                send(viewer,new Closed(session.token()));return;
            }
            if(packet.roster()) {
                boolean holdsRoster=viewer.getMainHandItem().getItem() instanceof SoldierRosterItem
                        ||viewer.getOffhandItem().getItem() instanceof SoldierRosterItem;
                if(holdsRoster)SoldierRosterNetwork.open(viewer);
                else send(viewer,new Closed(session.token()));
            } else HumanCommandNetwork.openFor(viewer,human);
        });context.setPacketHandled(true);
    }
    private static void receive(View packet,Supplier<NetworkEvent.Context> supplier) {
        var context=supplier.get();context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->AttributeDetailClient.accept(packet.token(),packet.opening(),packet.snapshot())));context.setPacketHandled(true);
    }
    private static void closed(Closed packet,Supplier<NetworkEvent.Context> supplier) {
        var context=supplier.get();context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->AttributeDetailClient.closed(packet.token())));context.setPacketHandled(true);
    }
}

package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.entity.Human;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Dedicated bounded route UI protocol; never receives a route, position or ownership claim from the client. */
@Mod.EventBusSubscriber(modid=HumanRuntime.MOD_ID)
public final class PatrolToolNetwork {
    private static final UUID NONE=new UUID(0,0);
    private static final String PROTOCOL="2";
    /** Keep pure codecs usable without initializing the Forge network event bus. */
    private static final class Connection {
        static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(HumanRuntime.MOD_ID,"patrol_tool"),
                ()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
    }
    private record Session(UUID token,UUID human,int entityId,long expires,boolean controller) {}
    private static final Map<ServerPlayer,Session> SESSIONS=new WeakHashMap<>();
    public record Row(BlockPos position,int ticks) {
        public Row { if(position==null||ticks<0||ticks>PatrolRoutePolicy.MAX_WAIT)throw new IllegalArgumentException("Invalid patrol row");position=position.immutable(); }
    }
    public record Control(int mode,boolean chase,int radius,int point,String phase,String pattern) {
        public Control {
            if(mode<0||mode>2||radius<8||radius>96||point<0||point>=256||phase==null||phase.length()>64
                    ||!List.of("loop","ping_pong","once","none").contains(pattern))
                throw new IllegalArgumentException("Invalid patrol control state");
        }
    }
    public record DraftPreview(UUID human,String dimension,List<BlockPos> points) {
        public DraftPreview {
            if(points.size()>257||dimension.length()>128)throw new IllegalArgumentException("Invalid patrol draft");
            points=points.stream().map(BlockPos::immutable).toList();
        }
    }
    public record Snapshot(UUID token,UUID human,int entityId,int revision,boolean opening,boolean valid,String name,List<Row> rows,Control control) {
        public Snapshot { rows=List.copyOf(rows); }
        public Snapshot(UUID token,UUID human,int entityId,int revision,boolean opening,boolean valid,String name,List<Row> rows) {
            this(token,human,entityId,revision,opening,valid,name,rows,null);
        }
    }
    private PatrolToolNetwork() {}
    public static void register() {
        Connection.CHANNEL.registerMessage(0,Request.class,Request::encode,Request::decode,Request::handle,java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
        Connection.CHANNEL.registerMessage(1,Snapshot.class,PatrolToolNetwork::encode,PatrolToolNetwork::decode,PatrolToolNetwork::receive,java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        Connection.CHANNEL.registerMessage(2,DraftPreview.class,PatrolToolNetwork::encodeDraft,PatrolToolNetwork::decodeDraft,PatrolToolNetwork::receiveDraft,java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void controllerMode(Snapshot s,int mode){request(s,4,0,mode);}
    public static void controllerChase(Snapshot s,boolean chase){request(s,5,0,chase?1:0);}
    public static void controllerRadius(Snapshot s,int radius){request(s,6,0,Math.max(8,Math.min(96,radius)));}
    public static void editPoint(Snapshot s,int index,int operation){request(s,7,index,operation);}
    private static void request(Snapshot s,int action,int index,int value){Connection.CHANNEL.sendToServer(new Request(action,s.entityId(),s.human(),s.token(),index,value,s.revision()));}
    public static void clearDraft(){Connection.CHANNEL.sendToServer(new Request(0,0,NONE,NONE,0,0,0));}
    public static void requestOpen(int entityId,UUID human){Connection.CHANNEL.sendToServer(new Request(1,entityId,human,NONE,0,0,0));}
    public static void setWait(Snapshot s,int index,int ticks){Connection.CHANNEL.sendToServer(new Request(2,s.entityId(),s.human(),s.token(),index,ticks,s.revision()));}
    public static void close(UUID token){Connection.CHANNEL.sendToServer(new Request(3,0,NONE,token,0,0,0));}
    public static void close(ServerPlayer p){SESSIONS.remove(p);}
    public static void clearSessions(){SESSIONS.clear();}
    static void draft(ServerPlayer p,UUID human,List<PatrolRoutePolicy.Waypoint> points) {
        Connection.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new DraftPreview(human,p.level().dimension().location().toString(),
                points.stream().map(point->point.block().above()).toList()));
    }
    static void clearPreview(ServerPlayer p){draft(p,NONE,List.of());}
    public static void openController(ServerPlayer p,Human h){open(p,h,true);}
    public static void open(ServerPlayer p,Human h){open(p,h,false);}
    private static void open(ServerPlayer p,Human h,boolean controller) {
        if(!(controller?PatrolControlService.mayControl(p,h):PatrolRouteEditor.mayConfigure(p,h))||p.distanceToSqr(h)>4096){p.displayClientMessage(Component.translatable("patrol_tool.denied"),true);return;}
        Session s=new Session(UUID.randomUUID(),h.getUUID(),h.getId(),p.serverLevel().getGameTime()+1200,controller);
        SESSIONS.put(p,s);send(p,s,h,true);
    }
    private static Human resolveHuman(ServerPlayer p,Session s) {
        if(p.serverLevel().getGameTime()>s.expires())return null;
        return p.level().getEntity(s.entityId()) instanceof Human h&&h.getUUID().equals(s.human())
                &&(s.controller()?PatrolControlService.mayControl(p,h):PatrolRouteEditor.mayConfigure(p,h))&&p.distanceToSqr(h)<=4096?h:null;
    }
    private static void send(ServerPlayer p,Session s,Human h,boolean opening) {
        var natural=s.controller()?NaturalState.controllerData(h):null;
        var definition=s.controller()?NaturalRoute.controllerDefinition(h,natural):NaturalRoute.definition(h,OrderDistances.context(h));List<Row> rows=new ArrayList<>();
        if(definition.valid())for(var point:definition.points())rows.add(new Row(BlockPos.containing(point.position()),point.waitTicks()));
        String name=h.getDisplayName().getString();if(name.length()>128)name=name.substring(0,128);
        Connection.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Snapshot(s.token(),h.getUUID(),h.getId(),PatrolRoutes.revision(h),opening,true,name,rows,s.controller()?PatrolControlService.view(h,natural):null));
    }
    private static void invalidate(ServerPlayer p,Session s) {
        SESSIONS.remove(p);Connection.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Snapshot(s.token(),s.human(),s.entityId(),0,false,false,"",List.of()));
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||p.tickCount%20!=0)return;
        Session s=SESSIONS.get(p);if(s==null)return;
        Human h=resolveHuman(p,s);if(h==null)invalidate(p,s);else send(p,s,h,false);
    }
    private record Request(int action,int entityId,UUID human,UUID token,int index,int ticks,int revision) {
        static void encode(Request p,FriendlyByteBuf b){b.writeVarInt(p.action);b.writeVarInt(p.entityId);b.writeUUID(p.human);b.writeUUID(p.token);b.writeVarInt(p.index);b.writeVarInt(p.ticks);b.writeInt(p.revision);}
        static Request decode(FriendlyByteBuf b){var p=new Request(b.readVarInt(),b.readVarInt(),b.readUUID(),b.readUUID(),b.readVarInt(),b.readVarInt(),b.readInt());
            if(p.action<0||p.action>7||p.index<0||p.index>=256||p.ticks<0||p.ticks>PatrolRoutePolicy.MAX_WAIT||b.readableBytes()!=0
                    ||p.action==1&&p.ticks>1||p.action==4&&p.ticks>2||p.action==5&&p.ticks>1
                    ||p.action==6&&(p.ticks<8||p.ticks>96)||p.action==7&&p.ticks>2)
                throw new IllegalArgumentException("Invalid patrol request");return p;}
        static void handle(Request request,Supplier<NetworkEvent.Context> supplier){var context=supplier.get();context.enqueueWork(()->{
            ServerPlayer p=context.getSender();if(p==null)return;
            if(request.action==0){if(p.getMainHandItem().getItem() instanceof PatrolCommandBatonItem)PatrolRouteEditor.clear(p,true);return;}
            if(request.action==3){Session s=SESSIONS.get(p);if(s!=null&&s.token().equals(request.token))SESSIONS.remove(p);return;}
            if(!CommandRateLimit.allow(p))return;
            if(request.action==1){if(p.level().getEntity(request.entityId) instanceof Human h&&h.getUUID().equals(request.human))open(p,h,request.ticks==1);return;}
            Session s=SESSIONS.get(p);if(s==null||!s.token().equals(request.token)||!s.human().equals(request.human)||s.entityId()!=request.entityId)return;
            Human h=resolveHuman(p,s);if(h==null){invalidate(p,s);return;}
            if(request.action==2&&PatrolRouteEditor.mayConfigure(p,h)){
                if(!PatrolRoutes.waitAt(h,request.index,request.ticks,request.revision))p.displayClientMessage(Component.translatable("patrol_tool.changed"),true);
            }
            else if(s.controller()) {
                if(request.action==4)PatrolControlService.mode(h,request.ticks);
                else if(request.action==5)PatrolControlService.settings(h,request.ticks==1,null);
                else if(request.action==6)PatrolControlService.settings(h,null,request.ticks);
                else if(request.action==7&&NaturalState.hasState(h)&&!PatrolRoutes.editPoint(h,request.index,request.ticks,request.revision))
                    p.displayClientMessage(Component.translatable("patrol_wait.edit_rejected"),true);
            }
            Session fresh=new Session(s.token(),s.human(),s.entityId(),p.serverLevel().getGameTime()+1200,s.controller());SESSIONS.put(p,fresh);send(p,fresh,h,false);
        });context.setPacketHandled(true);}
    }
    static void encode(Snapshot s,FriendlyByteBuf b){b.writeUUID(s.token());b.writeUUID(s.human());b.writeVarInt(s.entityId());b.writeInt(s.revision());
        b.writeBoolean(s.opening());b.writeBoolean(s.valid());b.writeUtf(s.name(),128);b.writeVarInt(s.rows().size());for(Row row:s.rows()){b.writeBlockPos(row.position());b.writeVarInt(row.ticks());}
        b.writeBoolean(s.control()!=null);if(s.control()!=null){var c=s.control();b.writeVarInt(c.mode());b.writeBoolean(c.chase());b.writeVarInt(c.radius());b.writeVarInt(c.point());b.writeUtf(c.phase(),64);b.writeUtf(c.pattern(),16);}}
    static Snapshot decode(FriendlyByteBuf b){UUID token=b.readUUID(),human=b.readUUID();int entityId=b.readVarInt(),revision=b.readInt();
        boolean opening=b.readBoolean(),valid=b.readBoolean();String name=b.readUtf(128);int count=b.readVarInt();if(count<0||count>256)throw new IllegalArgumentException("Patrol row count");
        List<Row> rows=new ArrayList<>();for(int i=0;i<count;i++){BlockPos point=b.readBlockPos();int ticks=b.readVarInt();if(ticks<0||ticks>1200)throw new IllegalArgumentException("Patrol wait");rows.add(new Row(point,ticks));}
        Control control=b.readBoolean()?new Control(b.readVarInt(),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readUtf(64),b.readUtf(16)):null;
        if(b.readableBytes()!=0)throw new IllegalArgumentException("Patrol snapshot boundary");return new Snapshot(token,human,entityId,revision,opening,valid,name,rows,control);}
    static void encodeDraft(DraftPreview s,FriendlyByteBuf b){b.writeUUID(s.human());b.writeUtf(s.dimension(),128);b.writeVarInt(s.points().size());for(var p:s.points())b.writeBlockPos(p);}
    static DraftPreview decodeDraft(FriendlyByteBuf b){UUID human=b.readUUID();String dimension=b.readUtf(128);int count=b.readVarInt();
        if(count<0||count>257)throw new IllegalArgumentException("Patrol draft count");List<BlockPos> points=new ArrayList<>();for(int i=0;i<count;i++)points.add(b.readBlockPos());
        if(b.readableBytes()!=0)throw new IllegalArgumentException("Patrol draft boundary");return new DraftPreview(human,dimension,points);}
    private static void receiveDraft(DraftPreview s,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
        ()->()->dev.felix.hostilehumans.client.ui.PatrolDraftClient.accept(s)));c.setPacketHandled(true);}
    private static void receive(Snapshot s,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
        ()->()->dev.felix.hostilehumans.client.ui.PatrolWaitScreen.show(s)));c.setPacketHandled(true);}
}

package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.client.ui.SoldierCommandScreen;
import dev.felix.hostilehumans.faction.HumanFactions;
import dev.felix.hostilehumans.faction.HumanRelations;
import dev.felix.hostilehumans.progression.GrowthSnapshot;
import dev.felix.hostilehumans.progression.HumanGrowth;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Owner/session/identity validated commands; all panel state is acknowledged by the server. */
public final class HumanCommandNetwork {
    private static final String PROTOCOL="10";
    private record Session(UUID token,UUID human,int entityId,ResourceKey<Level> dimension,long expiresAt) {}
    private static final Map<ServerPlayer,Session> SESSIONS=new WeakHashMap<>();
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(HumanRuntime.MOD_ID,"soldier_commands"),
            ()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
    private HumanCommandNetwork() {}
    public static void register() {
        CHANNEL.registerMessage(0,Snapshot.class,Snapshot::encode,Snapshot::decode,Snapshot::handle,java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Request.class,Request::encode,Request::decode,Request::handle,java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2,Close.class,Close::encode,Close::decode,Close::handle,java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3,Invalid.class,Invalid::encode,Invalid::decode,Invalid::handle,java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void openFor(ServerPlayer player,Human human) {
        if(!owned(player,human)||player.level()!=human.level()||player.distanceToSqr(human)>4096){
            player.displayClientMessage(Component.translatable("message.hostile_humans_unified.command.failed"),false);return;
        }
        Session session=new Session(UUID.randomUUID(),human.getUUID(),human.getId(),player.level().dimension(),now(player)+1200);
        SESSIONS.put(player,session);send(player,session,human,true);
    }
    private static boolean owned(ServerPlayer player,Human human){return human.isAlive()&&human.hasOwner()&&player.getUUID().equals(human.getOwnerUUID());}
    private static long now(ServerPlayer p){return p.serverLevel().getGameTime();}
    private static Human humanForSession(ServerPlayer p,Session s) {
        if(!p.isAlive()||!p.level().dimension().equals(s.dimension())||now(p)>s.expiresAt())return null;
        return p.level().getEntity(s.entityId()) instanceof Human h&&s.human().equals(h.getUUID())&&owned(p,h)&&p.distanceToSqr(h)<=4096?h:null;
    }
    private static void send(ServerPlayer p,Session s,Human h,boolean opening){
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Snapshot(s.token(),h.getUUID(),h.getId(),opening,h.getDisplayName(),
                CommandView.of(h),HumanGrowth.snapshot(h),HumanFactions.wireId(HumanFactions.id(h)),CommandHealthSnapshot.sample(h.getUUID(),h.getDisplayName().getString(),h.getHealth(),h.getMaxHealth())));
    }
    public static void tickServer(MinecraftServer server) {
        SquadCommandNetwork.tickServer(server);
        if(server.getTickCount()%20!=0)return;
        var iterator=SESSIONS.entrySet().iterator();while(iterator.hasNext()){
            var entry=iterator.next();ServerPlayer p=entry.getKey();Session s=entry.getValue();if(p.server!=server)continue;
            if(server.getPlayerList().getPlayer(p.getUUID())!=p){iterator.remove();continue;}
            Human h=humanForSession(p,s);if(h==null){iterator.remove();invalidate(p,s.token());continue;}
            Session fresh=new Session(s.token(),s.human(),s.entityId(),s.dimension(),now(p)+1200);entry.setValue(fresh);send(p,fresh,h,false);
        }
    }
    public static void submit(UUID token,UUID human,int entityId,SquadCommands.Update update){CHANNEL.sendToServer(new Request(token,human,entityId,0,update));}
    public static void sendDismiss(int entityId,UUID human,UUID token){CHANNEL.sendToServer(new Request(token,human,entityId,1,null));}
    public static void reanchor(UUID token,UUID human,int entityId){CHANNEL.sendToServer(new Request(token,human,entityId,2,null));}
    public static void closeScreen(UUID token){CHANNEL.sendToServer(new Close(token));}
    private static void invalidate(ServerPlayer p,UUID token){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Invalid(token));}
    public static void closePlayer(ServerPlayer player){SESSIONS.remove(player);SquadCommandNetwork.closePlayer(player);}
    public static void clearSessions(){SESSIONS.clear();SquadCommandNetwork.clearSessions();}
    private record Snapshot(UUID token,UUID human,int entityId,boolean opening,Component name,CommandView view,GrowthSnapshot growth,String faction,CommandHealthSnapshot health){
        static void encode(Snapshot p,FriendlyByteBuf b){b.writeUUID(p.token);b.writeUUID(p.human);b.writeVarInt(p.entityId);b.writeBoolean(p.opening);b.writeComponent(p.name);p.view.encode(b);p.growth.encode(b);b.writeUtf(HumanFactions.wireId(p.faction),256);CommandHealthCodec.write(b,p.health);}
        static Snapshot decode(FriendlyByteBuf b){
            UUID token=b.readUUID(),human=b.readUUID();int entityId=b.readVarInt();boolean opening=b.readBoolean();Component name=b.readComponent();
            CommandView view=CommandView.decode(b);GrowthSnapshot growth=GrowthSnapshot.decode(b);String faction=b.readUtf(256);CommandHealthSnapshot health=CommandHealthCodec.read(b);
            if(health.mixed()||health.available()&&!health.human().equals(human))throw new IllegalArgumentException("command health identity mismatch");
            return new Snapshot(token,human,entityId,opening,name,view,growth,faction,health);
        }
        static void handle(Snapshot p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->SoldierCommandScreen.show(p.token,p.human,p.entityId,p.opening,p.name,p.view,p.growth,p.faction,p.health)));c.setPacketHandled(true);}
    }
    private record Request(UUID token,UUID human,int entityId,int action,SquadCommands.Update update){
        static void encode(Request p,FriendlyByteBuf b){b.writeUUID(p.token);b.writeUUID(p.human);b.writeVarInt(p.entityId);b.writeVarInt(p.action);if(p.action==0)CommandView.writeUpdate(b,p.update);}
        static Request decode(FriendlyByteBuf b){UUID token=b.readUUID(),human=b.readUUID();int id=b.readVarInt();int action=b.readVarInt();if(action<0||action>2)throw new IllegalArgumentException("invalid command action");return new Request(token,human,id,action,action==0?CommandView.readUpdate(b):null);}
        static void handle(Request p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->{
            ServerPlayer player=c.getSender();if(player==null)return;Session s=SESSIONS.get(player);
            Human h=s==null?null:humanForSession(player,s);
            if(s==null||h==null||!s.token().equals(p.token)||s.entityId()!=p.entityId||!s.human().equals(p.human)){
                if(s!=null&&s.token().equals(p.token))SESSIONS.remove(player);invalidate(player,p.token);return;
            }
            if(!CommandRateLimit.allow(player)){send(player,s,h,false);return;}
            if(p.action==1){HumanRelations.dismiss(player,h);SquadData.get(player.server).removeMember(h.getUUID());SESSIONS.remove(player);invalidate(player,p.token);return;}
            if(p.action==2){SoldierOrder.set(h,SoldierOrder.GARRISON);CommandView.persist(h);send(player,s,h,false);SoldierRosterNetwork.refreshIfOpen(player);return;}
            if(!CommandView.valid(p.update)){invalidate(player,p.token);SESSIONS.remove(player);return;}
            CommandView.apply(h,p.update);send(player,s,h,false);SoldierRosterNetwork.refreshIfOpen(player);
        });c.setPacketHandled(true);}
    }
    private record Close(UUID token){
        static void encode(Close p,FriendlyByteBuf b){b.writeUUID(p.token);}static Close decode(FriendlyByteBuf b){return new Close(b.readUUID());}
        static void handle(Close p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->{ServerPlayer player=c.getSender();Session s=SESSIONS.get(player);if(s!=null&&s.token().equals(p.token))SESSIONS.remove(player);});c.setPacketHandled(true);}
    }
    private record Invalid(UUID token){
        static void encode(Invalid p,FriendlyByteBuf b){b.writeUUID(p.token);}static Invalid decode(FriendlyByteBuf b){return new Invalid(b.readUUID());}
        static void handle(Invalid p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->SoldierCommandScreen.invalidate(p.token)));c.setPacketHandled(true);}
    }
}

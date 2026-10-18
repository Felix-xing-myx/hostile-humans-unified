package club.someoneice.humangunner;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Every batch revalidates real held item type, owner binding and the server session. */
public final class SquadCommandNetwork {
    private static final String PROTOCOL="1";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(HumanGunner.MOD_ID,"squad_commands"),
            ()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
    private static final Map<ServerPlayer,SquadCommands.Session> OPEN=new WeakHashMap<>();
    public record View(UUID token,int number,String name,int total,int available,int unloaded,CommandView commands,
                       List<SquadCommands.Member> members,Map<SoldierOrder,Integer> modes,Component result) {}
    private SquadCommandNetwork() {}
    public static void register(){
        CHANNEL.registerMessage(0,Snapshot.class,Snapshot::encode,Snapshot::decode,Snapshot::handle,java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Request.class,Request::encode,Request::decode,Request::handle,java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2,Close.class,Close::encode,Close::decode,Close::handle,java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3,Invalid.class,Invalid::encode,Invalid::decode,Invalid::handle,java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    /** Only actual registered token item instances qualify. NBT alone never qualifies. */
    public static boolean holdsToken(ServerPlayer p,int number){return heldToken(p,number)!=null;}
    private static ItemStack heldToken(ServerPlayer p,int number){
        for(ItemStack stack:List.of(p.getMainHandItem(),p.getOffhandItem()))
            if(stack.getItem() instanceof SquadCommandTokenItem&&SquadCommands.boundSquad(p,stack)==number)return stack;
        return null;
    }
    public static void open(ServerPlayer player,ItemStack stack){
        if(!(stack.getItem() instanceof SquadCommandTokenItem)
                ||player.getMainHandItem()!=stack&&player.getOffhandItem()!=stack)return;
        var session=SquadCommands.openToken(player,stack);
        if(session==null){player.displayClientMessage(Component.translatable("message.hostile_humans_unified.squad.invalid_token"),false);return;}
        OPEN.put(player,session);send(player,session,true,Component.empty());
    }
    private static boolean valid(ServerPlayer p,UUID token,int number){
        var session=OPEN.get(p);return session!=null&&session.token().equals(token)&&session.squad()==number
                &&holdsToken(p,number)&&SquadCommands.valid(p,token,number);
    }
    private static CommandView representative(ServerPlayer p,SquadCommands.Snapshot snapshot){
        for(var member:snapshot.members())if(member.canSubmit()){
            var human=SquadCommands.findLoaded(p.server,member.id());if(human!=null)return CommandView.of(human);
        }
        var d=OrderDistances.defaults();var settings=new GarrisonSettings(false,true,d.garrisonRadius());
        var c=new OrderDistances.Context(true,false,true,net.minecraft.world.phys.Vec3.ZERO,settings,"empty");
        return new CommandView(SoldierOrder.FOLLOW,SoldierCombatMode.ACTIVE,false,settings,d.followDistance(),(int)c.combatRadius(),d.chaseDurationTicks(),"no_loaded_members");
    }
    private static void send(ServerPlayer p,SquadCommands.Session session,boolean opening,Component result){
        var snapshot=SquadCommands.snapshot(p,session.token(),session.squad());if(snapshot==null){invalidate(p,session.token());return;}
        var view=new View(snapshot.token(),snapshot.number(),snapshot.name(),snapshot.total(),snapshot.available(),snapshot.unsynchronized(),
                representative(p,snapshot),snapshot.members(),snapshot.modes(),result);
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Snapshot(opening,view));
    }
    public static void tickServer(MinecraftServer server){
        if(server.getTickCount()%20!=0)return;
        var iterator=OPEN.entrySet().iterator();while(iterator.hasNext()){
            var entry=iterator.next();ServerPlayer player=entry.getKey();var s=entry.getValue();if(player.server!=server)continue;
            if(server.getPlayerList().getPlayer(player.getUUID())!=player){iterator.remove();SquadCommands.close(player);continue;}
            if(!valid(player,s.token(),s.squad())){iterator.remove();SquadCommands.close(player);invalidate(player,s.token());continue;}
            send(player,s,false,Component.empty());
        }
    }
    public static void clearSessions(){OPEN.clear();SquadCommands.clearSessions();}
    public static void closePlayer(ServerPlayer player){OPEN.remove(player);SquadCommands.close(player);}
    static void submit(UUID token,int number,SquadCommands.Update update){CHANNEL.sendToServer(new Request(token,number,false,update));}
    static void reanchor(UUID token,int number){CHANNEL.sendToServer(new Request(token,number,true,null));}
    static void close(UUID token){CHANNEL.sendToServer(new Close(token));}
    private static void invalidate(ServerPlayer player,UUID token){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Invalid(token));}
    private record Snapshot(boolean opening,View view){
        static void encode(Snapshot p,FriendlyByteBuf b){
            View v=p.view;b.writeBoolean(p.opening);b.writeUUID(v.token);b.writeVarInt(v.number);b.writeUtf(v.name,64);
            b.writeVarInt(v.total);b.writeVarInt(v.available);b.writeVarInt(v.unloaded);v.commands.encode(b);b.writeComponent(v.result);
            for(var order:CommandView.ORDERS)b.writeVarInt(v.modes.getOrDefault(order,0));
            b.writeVarInt(v.members.size());for(var m:v.members){b.writeUUID(m.id());b.writeBoolean(m.loaded());b.writeBoolean(m.canSubmit());if(m.canSubmit())b.writeEnum(m.order());}
        }
        static Snapshot decode(FriendlyByteBuf b){
            boolean opening=b.readBoolean();UUID token=b.readUUID();int number=b.readVarInt();String name=b.readUtf(64);
            int total=b.readVarInt(),available=b.readVarInt(),unloaded=b.readVarInt();CommandView commands=CommandView.decode(b);Component result=b.readComponent();
            Map<SoldierOrder,Integer> modes=new EnumMap<>(SoldierOrder.class);for(var order:CommandView.ORDERS)modes.put(order,b.readVarInt());
            int size=b.readVarInt();if(number<1||number>16||size<0||size>64||total!=size||available<0||available>size||unloaded!=size-available)throw new IllegalArgumentException("invalid squad snapshot");
            List<SquadCommands.Member> members=new ArrayList<>();for(int i=0;i<size;i++){UUID id=b.readUUID();boolean loaded=b.readBoolean(),can=b.readBoolean();members.add(new SquadCommands.Member(id,loaded,can,can?b.readEnum(SoldierOrder.class):null));}
            return new Snapshot(opening,new View(token,number,name,total,available,unloaded,commands,List.copyOf(members),Map.copyOf(modes),result));
        }
        static void handle(Snapshot p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->SquadCommandScreen.show(p.opening,p.view)));c.setPacketHandled(true);}
    }
    private record Request(UUID token,int number,boolean reanchor,SquadCommands.Update update){
        static void encode(Request p,FriendlyByteBuf b){b.writeUUID(p.token);b.writeVarInt(p.number);b.writeBoolean(p.reanchor);if(!p.reanchor)CommandView.writeUpdate(b,p.update);}
        static Request decode(FriendlyByteBuf b){UUID token=b.readUUID();int number=b.readVarInt();boolean reanchor=b.readBoolean();return new Request(token,number,reanchor,reanchor?null:CommandView.readUpdate(b));}
        static void handle(Request p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->{
            ServerPlayer player=c.getSender();if(player==null)return;
            if(!valid(player,p.token,p.number)||!p.reanchor&&!CommandView.valid(p.update)){
                var session=OPEN.get(player);if(session!=null&&session.token().equals(p.token)){OPEN.remove(player);SquadCommands.close(player);}invalidate(player,p.token);return;
            }
            var session=OPEN.get(player);if(!CommandRateLimit.allow(player)){send(player,session,false,Component.translatable("message.hostile_humans_unified.squad.rate_limited"));return;}
            var result=p.reanchor?SquadCommands.reanchor(player,p.token,p.number):SquadCommands.apply(player,p.token,p.number,p.update);
            if(!result.validSession()){OPEN.remove(player);SquadCommands.close(player);invalidate(player,p.token);return;}
            var saved=com.craftix.hostile_humans.entity.data.HumanServerData.get();
            if(saved!=null)for(var member:SquadData.get(player.server).squad(player.getUUID(),p.number).members()){
                var h=SquadCommands.findLoaded(player.server,member);if(h!=null&&h.hasOwner()&&player.getUUID().equals(h.getOwnerUUID()))saved.updateOrRegisterHumanMob(h);
            }
            Component message=Component.translatable("message.hostile_humans_unified.squad.result",result.applied(),result.unloaded(),result.unauthorized(),result.invalid());
            send(player,session,false,message);SoldierRosterNetwork.refreshIfOpen(player);
        });c.setPacketHandled(true);}
    }
    private record Close(UUID token){
        static void encode(Close p,FriendlyByteBuf b){b.writeUUID(p.token);}static Close decode(FriendlyByteBuf b){return new Close(b.readUUID());}
        static void handle(Close p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->{ServerPlayer player=c.getSender();var s=OPEN.get(player);if(s!=null&&s.token().equals(p.token)){OPEN.remove(player);SquadCommands.close(player);}});c.setPacketHandled(true);}
    }
    private record Invalid(UUID token){
        static void encode(Invalid p,FriendlyByteBuf b){b.writeUUID(p.token);}static Invalid decode(FriendlyByteBuf b){return new Invalid(b.readUUID());}
        static void handle(Invalid p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->SquadCommandScreen.invalidate(p.token)));c.setPacketHandled(true);}
    }
}

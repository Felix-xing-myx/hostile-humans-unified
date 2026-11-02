package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.behavior.combat.CombatPreference;
import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Loaded-only batch API. No tickets, recall requests, deferred mutations or client authority. */
public final class SquadCommands {
    private static final String OWNER=HumanRuntime.MOD_ID+":squad_owner", NUMBER=HumanRuntime.MOD_ID+":squad_number";
    private static final Map<ServerPlayer,Session> SESSIONS=new WeakHashMap<>();
    public record Session(UUID token,int squad) {}
    public record Member(UUID id,boolean loaded,boolean canSubmit,SoldierOrder order) {}
    public record Snapshot(UUID token,int number,String name,int total,int available,int unsynchronized,
                           List<Member> members,Map<SoldierOrder,Integer> modes) {}
    public record Update(SoldierOrder order,GarrisonSettings garrison,SoldierCombatMode combat,Boolean pickup,Integer followDistance,
                         Boolean stay,Boolean chase,Integer radius,CombatPreference preference) {
        public Update(SoldierOrder order,GarrisonSettings garrison,SoldierCombatMode combat,Boolean pickup,Integer followDistance) {
            this(order,garrison,combat,pickup,followDistance,null,null,null,null);
        }
    }
    public record Result(boolean validSession,int applied,int unloaded,int unauthorized,int invalid,List<UUID> failed) {
        public int rejected(){return unauthorized+invalid;}
    }
    private SquadCommands() {}
    public static Human findLoaded(MinecraftServer server,UUID id) {
        for(var level:server.getAllLevels())if(level.getEntity(id) instanceof Human human)return human;return null;
    }
    public static Session open(ServerPlayer player,int number) {
        SquadData data=SquadData.get(player.server);if(!data.existingOrAllowed(player.getUUID(),number))return null;
        data.validate(player.server,player.getUUID());Session session=new Session(UUID.randomUUID(),number);SESSIONS.put(player,session);return session;
    }
    public static boolean valid(ServerPlayer player,UUID token,int number) {
        Session session=SESSIONS.get(player);return player.isAlive()&&session!=null&&session.token().equals(token)&&session.squad()==number
                &&SquadData.get(player.server).existingOrAllowed(player.getUUID(),number);
    }
    public static void close(ServerPlayer player){SESSIONS.remove(player);}
    public static void clearSessions(){SESSIONS.clear();}
    public static Snapshot snapshot(ServerPlayer player,UUID token,int number) {
        if(!valid(player,token,number))return null;
        SquadData data=SquadData.get(player.server);data.validate(player.server,player.getUUID());SquadData.Squad squad=data.squad(player.getUUID(),number);
        List<Member> members=new ArrayList<>();Map<SoldierOrder,Integer> modes=new EnumMap<>(SoldierOrder.class);int available=0;
        for(UUID id:squad.members()) {
            Human live=findLoaded(player.server,id);boolean can=live!=null&&live.isAlive()&&live.hasOwner()&&player.getUUID().equals(live.getOwnerUUID());
            SoldierOrder order=can?SoldierOrder.get(live):null;if(can){available++;modes.merge(order,1,Integer::sum);}
            members.add(new Member(id,live!=null,can,order));
        }
        return new Snapshot(token,number,squad.name(),members.size(),available,members.size()-available,List.copyOf(members),Map.copyOf(modes));
    }
    public static Result apply(ServerPlayer player,UUID token,int number,Update update) {
        if(!CommandView.valid(update)||!valid(player,token,number))
            return new Result(false,0,0,0,0,List.of());
        return applyLoaded(player, token, number, human -> {
            CommandView.apply(human,update);
        });
    }
    public static Result reanchor(ServerPlayer player, UUID token, int number) {
        return applyLoaded(player, token, number, human -> { SoldierOrder.set(human, SoldierOrder.GARRISON); CommandView.persist(human); });
    }
    /** Fixed member snapshot and permission checks shared by all loaded-only batch operations. */
    private static Result applyLoaded(ServerPlayer player, UUID token, int number, java.util.function.Consumer<Human> operation) {
        if(!valid(player,token,number)) return new Result(false,0,0,0,0,List.of());
        SquadData data=SquadData.get(player.server);
        List<UUID> ids=List.copyOf(data.squad(player.getUUID(),number).members()),failed=new ArrayList<>();
        int applied=0,unloaded=0,unauthorized=0,invalid=0;
        for(UUID id:ids) {
            if(!RecruitmentLedger.get(player.server).isOwnedRecord(id,player.getUUID())){unauthorized++;failed.add(id);continue;}
            Human human=findLoaded(player.server,id);if(human==null){unloaded++;continue;}
            if(!human.isAlive()){invalid++;failed.add(id);continue;}
            if(!human.hasOwner()||!player.getUUID().equals(human.getOwnerUUID())){unauthorized++;failed.add(id);continue;}
            operation.accept(human);
            applied++;
        }
        data.validate(player.server,player.getUUID());
        return new Result(true,applied,unloaded,unauthorized,invalid,List.copyOf(failed));
    }
    /** Item class calls these only after verifying its own item type. Copying NBT does not grant access. */
    public static boolean bind(ServerPlayer player,ItemStack item,int number) {
        if(item.isEmpty()||!SquadData.get(player.server).existingOrAllowed(player.getUUID(),number))return false;
        var tag=item.getOrCreateTag();if(tag.hasUUID(OWNER)&&!player.getUUID().equals(tag.getUUID(OWNER)))return false;
        tag.putUUID(OWNER,player.getUUID());tag.putInt(NUMBER,number);return true;
    }
    public static Session openToken(ServerPlayer player,ItemStack item) {
        int number=boundSquad(player,item);return number==0?null:open(player,number);
    }
    public static int boundSquad(ServerPlayer player,ItemStack item) {
        var tag=item.getTag();if(tag==null||!tag.hasUUID(OWNER)||!player.getUUID().equals(tag.getUUID(OWNER)))return 0;
        int number=tag.getInt(NUMBER);return SquadData.get(player.server).existingOrAllowed(player.getUUID(),number)?number:0;
    }
    public static int cycleToken(ServerPlayer player,ItemStack item) {
        var tag=item.getTag();if(tag!=null&&tag.hasUUID(OWNER)&&!player.getUUID().equals(tag.getUUID(OWNER)))return 0;
        int next=tag==null?1:Math.floorMod(tag.getInt(NUMBER),SquadData.limits().maxSquads())+1;
        return bind(player,item,next)?next:0;
    }
}

package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.entity.Human;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Overworld authority. Unloaded entities stay members; absence of a loaded entity is not death. */
public final class SquadData extends SavedData {
    private static final String ID = "hostile_humans_unified_squads";
    public record Limits(int maxSquads,int maxMembers,int nameLength) {
        public Limits {maxSquads=OrderDistances.clamp(maxSquads,1,16);maxMembers=OrderDistances.clamp(maxMembers,1,64);nameLength=OrderDistances.clamp(nameLength,4,32);}
    }
    private static volatile Limits limits=new Limits(8,32,16);
    public static void configureLimits(Limits value){limits=java.util.Objects.requireNonNull(value);}
    public static Limits limits(){return limits;}
    public record Squad(int number,String name,List<UUID> members) {}
    private static final class Mutable {String name;final LinkedHashSet<UUID> members=new LinkedHashSet<>();Mutable(String name){this.name=name;}}
    private final Map<UUID,TreeMap<Integer,Mutable>> owners=new HashMap<>();
    private boolean initialValidationDone;
    public static SquadData get(MinecraftServer server) {
        SquadData data=server.overworld().getDataStorage().computeIfAbsent(SquadData::load,SquadData::new,ID);
        if(!data.initialValidationDone){data.initialValidationDone=true;for(UUID owner:List.copyOf(data.owners.keySet()))data.validate(server,owner);}
        return data;
    }
    public boolean validNumber(int number){return number>=1&&number<=limits.maxSquads();}
    public boolean existingOrAllowed(UUID owner,int number){return validNumber(number)||number>=1&&number<=16
            &&owners.containsKey(owner)&&owners.get(owner).containsKey(number);}
    public Squad squad(UUID owner,int number) {
        if(!existingOrAllowed(owner,number))return null;
        Mutable data=owners.getOrDefault(owner,new TreeMap<>()).get(number);
        return data==null?new Squad(number,"小队 "+number,List.of()):new Squad(number,data.name,List.copyOf(data.members));
    }
    public List<Squad> squads(UUID owner){List<Squad> result=new ArrayList<>();int last=owners.containsKey(owner)&&!owners.get(owner).isEmpty()?owners.get(owner).lastKey():0;
        for(int i=1;i<=Math.max(last,limits.maxSquads());i++){Squad squad=squad(owner,i);if(squad!=null)result.add(squad);}return List.copyOf(result);}
    public int membership(UUID owner,UUID human){
        var data=owners.get(owner);if(data==null)return 0;
        for(var entry:data.entrySet())if(entry.getValue().members.contains(human))return entry.getKey();return 0;
    }
    /** Ledger grants formal employment; loaded entities override stale ledger claims. No chunk loads. */
    static boolean owned(MinecraftServer server,UUID owner,UUID id) {
        if(!RecruitmentLedger.get(server).isOwnedRecord(id,owner))return false;
        Human live=SquadCommands.findLoaded(server,id);
        return live==null||live.isAlive()&&live.hasOwner()&&owner.equals(live.getOwnerUUID());
    }
    public void validate(MinecraftServer server,UUID owner) {
        var data=owners.get(owner);if(data==null)return;
        LinkedHashSet<UUID> seen=new LinkedHashSet<>();boolean changed=false;
        for(Mutable squad:data.values())changed|=squad.members.removeIf(id->!owned(server,owner,id)||!seen.add(id));
        if(changed)setDirty(); // empty squad names intentionally survive
    }
    public boolean assign(MinecraftServer server,UUID owner,int number,UUID human) {
        if(number!=0&&!validNumber(number)||!owned(server,owner,human))return false;
        validate(server,owner);
        if(membership(owner,human)==number)return true;
        TreeMap<Integer,Mutable> data=owners.computeIfAbsent(owner,k->new TreeMap<>());
        Mutable destination=number==0?null:data.computeIfAbsent(number,n->new Mutable("小队 "+n));
        if(destination!=null&&destination.members.size()>=limits.maxMembers())return false;
        for(Mutable squad:data.values())squad.members.remove(human);
        if(destination!=null)destination.members.add(human);setDirty();return true;
    }
    public boolean rename(UUID owner,int number,String requested) {
        if(!existingOrAllowed(owner,number)||requested==null)return false;
        String value=cleanName(requested,limits.nameLength());if(value.isEmpty())return false;
        Mutable squad=owners.computeIfAbsent(owner,k->new TreeMap<>()).computeIfAbsent(number,n->new Mutable("小队 "+n));
        if(!value.equals(squad.name)){squad.name=value;setDirty();}return true;
    }
    private static String cleanName(String requested,int maximum) {
        StringBuilder name=new StringBuilder();
        requested.replaceAll("(?i)§[0-9A-FK-ORX]", "").codePoints().filter(c->!Character.isISOControl(c)&&!Character.isIdentifierIgnorable(c)&&c!=0xA7
                &&Character.getType(c)!=Character.FORMAT&&Character.getType(c)!=Character.SURROGATE)
                .limit(maximum).forEach(name::appendCodePoint);
        return name.toString().strip();
    }
    /** Hook dismissal/death/transfer; refresh also reconciles ledger removals without this optimization. */
    public void removeMember(UUID human){boolean changed=false;for(var squads:owners.values())for(Mutable squad:squads.values())changed|=squad.members.remove(human);if(changed)setDirty();}
    public static SquadData load(CompoundTag root) {
        SquadData result=new SquadData();
        for(Tag entry:root.getList("owners",Tag.TAG_COMPOUND)) {
            CompoundTag owner=(CompoundTag)entry;if(!owner.hasUUID("owner"))continue;
            TreeMap<Integer,Mutable> squads=result.owners.computeIfAbsent(owner.getUUID("owner"),k->new TreeMap<>());
            LinkedHashSet<UUID> seen=new LinkedHashSet<>();
            for(Tag value:owner.getList("squads",Tag.TAG_COMPOUND)) {
                CompoundTag tag=(CompoundTag)value;int number=tag.getInt("number");if(number<1||number>16||squads.containsKey(number))continue;
                Mutable squad=new Mutable("小队 "+number);
                String name=cleanName(tag.getString("name"),32);if(!name.isBlank())squad.name=name;
                for(Tag member:tag.getList("members",Tag.TAG_COMPOUND)) {
                    CompoundTag m=(CompoundTag)member;if(m.hasUUID("id")&&squad.members.size()<64&&seen.add(m.getUUID("id")))squad.members.add(m.getUUID("id"));
                }squads.put(number,squad);
            }
        }return result;
    }
    @Override public CompoundTag save(CompoundTag root) {
        ListTag list=new ListTag();
        for(var entry:owners.entrySet()) {
            CompoundTag owner=new CompoundTag();owner.putUUID("owner",entry.getKey());ListTag squads=new ListTag();
            for(var item:entry.getValue().entrySet()) {
                CompoundTag tag=new CompoundTag();tag.putInt("number",item.getKey());tag.putString("name",item.getValue().name);ListTag members=new ListTag();
                for(UUID id:item.getValue().members){CompoundTag member=new CompoundTag();member.putUUID("id",id);members.add(member);}tag.put("members",members);squads.add(tag);
            }owner.put("squads",squads);list.add(owner);
        }root.put("owners",list);return root;
    }
}

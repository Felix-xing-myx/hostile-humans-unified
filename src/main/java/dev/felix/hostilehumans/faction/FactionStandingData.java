package dev.felix.hostilehumans.faction;

import dev.felix.hostilehumans.config.UnifiedConfig;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Persistent true standing and per-faction reward allowance, independent of equipment and client state. */
final class FactionStandingData extends SavedData {
    private record Entry(int value, long rewardDay, int rewardEarned) {}
    private final Map<UUID, Map<String, Entry>> players = new HashMap<>();
    private final Map<UUID, Map<String, CompoundTag>> passage = new HashMap<>();
    private final Map<UUID,CompoundTag> economy=new HashMap<>();
    private final Map<UUID,CompoundTag> playerExtras=new HashMap<>();
    private final Map<UUID,Map<String,CompoundTag>> factionExtras=new HashMap<>();
    private final Map<UUID,LinkedHashMap<UUID,CompoundTag>> journals=new HashMap<>();
    private final Map<UUID,CompoundTag> externalReceipts=new HashMap<>();
    private long journalSequence;
    long nextSequence(){journalSequence=Math.incrementExact(journalSequence);return journalSequence;}
    CompoundTag external(UUID entity){var receipt=externalReceipts.get(entity);return receipt==null?null:receipt.copy();}
    private CompoundTag rootExtras=new CompoundTag();
    private CompoundTag unknownRoot;
    private final List<CompoundTag> quarantinedPlayers=new ArrayList<>();
    private final Set<UUID> unknownPlayers=new HashSet<>();
    boolean known(UUID player){return unknownRoot==null&&!unknownPlayers.contains(player);}
    static FactionStandingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FactionStandingData::load,
                FactionStandingData::new, "hostile_humans_unified_standing");
    }
    int standing(UUID player, String faction) { return known(player)?entry(player, faction).value():100; }
    Optional<PassagePermit> passage(UUID player, String faction) {
        if(!known(player))return Optional.empty();
        CompoundTag stored = passage.getOrDefault(player,Map.of()).get(faction);
        return PassageCodec.read(stored == null ? new CompoundTag() : stored);
    }
    void passage(UUID player, String faction, PassagePermit state) {
        if (passage(player,faction).isEmpty()) throw new IllegalStateException("Unknown passage data must not be overwritten");
        entry(player,faction);
        var values = passage.computeIfAbsent(player,ignored -> new HashMap<>());
        var previous = values.getOrDefault(faction,new CompoundTag()).copy();
        values.put(faction,previous.merge(PassageCodec.write(state)));
        setDirty();
    }
    /** Permission-gated author command only. Never resets or consumes the gameplay reward allowance. */
    int set(UUID player, String faction, long value) {
        if(!known(player))throw new IllegalStateException("Unknown political row retained");
        Entry previous = entry(player, faction);
        int next = (int) Math.max(-500L, Math.min(500L, value));
        players.get(player).put(faction, new Entry(next, previous.rewardDay(), previous.rewardEarned()));
        setDirty();
        return next;
    }
    private Entry entry(UUID player, String faction) {
        Map<String, Entry> values = players.computeIfAbsent(player, ignored -> new HashMap<>());
        Entry stored = values.get(faction);
        if (stored != null) return stored;
        var definition = FactionDefinitions.find(faction);
        stored = new Entry(definition == null ? 100 : definition.startingStanding(), -1, 0);
        values.put(faction, stored);
        setDirty();
        return stored;
    }
    /** Rewards use overworld gameTime, never mutable dayTime; rollback cannot replenish an allowance. */
    int change(UUID player, String faction, int amount, long gameTime) {
        if(!known(player))return 0;
        Entry previous = entry(player, faction);
        long day = Math.max(previous.rewardDay(), Math.max(0, gameTime) / 24000);
        int earned = day > previous.rewardDay() ? 0 : previous.rewardEarned();
        int cap = UnifiedConfig.get().standingQuota().cap(previous.value());
        int applied = amount > 0 ? StandingReward.positiveDelta(previous.value(),amount,Math.max(0,cap-earned)) : amount;
        int next = (int) Math.max(-500L, Math.min(500L, (long) previous.value() + applied));
        int actual=next-previous.value();
        if (actual > 0) earned += actual;
        players.get(player).put(faction, new Entry(next, day, earned));
        setDirty();
        return next - previous.value();
    }
    CompoundTag player(UUID id){
        if(!known(id))throw new IllegalStateException("Unknown political row retained");
        var result=playerExtras.getOrDefault(id,new CompoundTag()).copy();result.putUUID("uuid",id);var list=new ListTag();
        players.getOrDefault(id,Map.of()).entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(value->{
            var f=factionExtras.getOrDefault(id,Map.of()).getOrDefault(value.getKey(),new CompoundTag()).copy();
            f.putString("id",value.getKey());f.putInt("standing",value.getValue().value());f.putLong("reward_day",value.getValue().rewardDay());f.putInt("earned",value.getValue().rewardEarned());
            var pass=passage.getOrDefault(id,Map.of()).get(value.getKey());if(pass!=null)f.put("passage",pass.copy());list.add(f);
        });result.put("factions",list);var account=economy.get(id);if(account!=null)result.put("economy",account.copy());return result;
    }
    void player(UUID id,CompoundTag source){
        if(!source.hasUUID("uuid")||!id.equals(source.getUUID("uuid")))throw new IllegalArgumentException("political player identity");
        var values=new LinkedHashMap<String,Entry>();var passes=new LinkedHashMap<String,CompoundTag>();var extras=new LinkedHashMap<String,CompoundTag>();
        if(source.contains("factions")&&!(source.get("factions") instanceof ListTag list&& (list.isEmpty()||list.getElementType()==Tag.TAG_COMPOUND))||source.contains("economy")&&!source.contains("economy",Tag.TAG_COMPOUND))throw new IllegalArgumentException("unknown political row shape");
        var rows=source.getList("factions",Tag.TAG_COMPOUND);if(rows.size()>256)throw new IllegalArgumentException("faction count limit");
        for(Tag row:rows){var f=(CompoundTag)row;var key=FactionDefinitions.id(f.getString("id"));if(key==null)throw new IllegalArgumentException("invalid retained faction ID");String faction=key.toString();
            if(values.containsKey(faction))throw new IllegalArgumentException("duplicate faction row");
            if(f.getInt("standing")< -500||f.getInt("standing")>500||f.getLong("reward_day")< -1||f.getInt("earned")<0||f.getInt("earned")>1_000_000||f.contains("passage")&&!f.contains("passage",Tag.TAG_COMPOUND))throw new IllegalArgumentException("invalid political faction payload");
            values.put(faction,new Entry(Math.max(-500,Math.min(500,f.getInt("standing"))),Math.max(-1,f.getLong("reward_day")),Math.max(0,Math.min(1_000_000,f.getInt("earned")))));
            if(f.contains("passage",Tag.TAG_COMPOUND))passes.put(faction,f.getCompound("passage").copy());extras.put(faction,f.copy());
        }
        players.put(id,values);passage.put(id,passes);factionExtras.put(id,extras);playerExtras.put(id,source.copy());
        if(source.contains("economy",Tag.TAG_COMPOUND))economy.put(id,source.getCompound("economy").copy());else economy.remove(id);setDirty();
    }
    boolean journaled(UUID player, UUID transaction) {
        var receipts = journals.get(player);
        return receipts != null && receipts.containsKey(transaction);
    }

    boolean recognizesJournalStamp(UUID player, UUID transaction) {
        var receipts = journals.get(player);
        var receipt = receipts == null ? null : receipts.get(transaction);
        if (receipt == null) return false;
        String state = receipt.getString("state");
        return state.equals("COMMITTED") || state.equals("PREPARED");
    }

    CompoundTag latestJournal(UUID player) {
        var receipts = journals.get(player);
        if (receipts == null) return null;
        CompoundTag result = null;
        for (var receipt : receipts.values()) result = receipt;
        return result == null ? null : result.copy();
    }

    void journal(UUID player,UUID transaction,CompoundTag receipt){
        var receipts=journals.computeIfAbsent(player,k->new LinkedHashMap<>());
        if(!receipts.containsKey(transaction)&&receipts.size()>=1024){UUID first=receipts.keySet().iterator().next();String state=receipts.get(first).getString("state");if(state.equals("PREPARED")||state.equals("REPAIR_REQUIRED"))throw new IllegalStateException("unresolved transaction journal capacity");for(Tag old:receipts.get(first).getList("slots",Tag.TAG_COMPOUND)){String oldKey=((CompoundTag)old).getString("slot");if(oldKey.startsWith("external:"))try{UUID source=UUID.fromString(oldKey.substring(9));var indexed=externalReceipts.get(source);if(indexed!=null&&indexed.getUUID("transaction").equals(first))externalReceipts.remove(source);}catch(IllegalArgumentException ignored){}}receipts.remove(first);}
        receipts.put(transaction,receipt.copy());journalSequence=Math.max(journalSequence,receipt.getLong("sequence"));
        for(Tag row:receipt.getList("slots",Tag.TAG_COMPOUND)){String key=((CompoundTag)row).getString("slot");if(!key.startsWith("external:"))continue;
            try{UUID entity=UUID.fromString(key.substring(9));var indexed=externalReceipts.get(entity);
                if(!receipt.getString("state").equals("ABORTED")){
                    if(indexed==null||receipt.getLong("sequence")>=indexed.getLong("sequence"))externalReceipts.put(entity,receipt.copy());continue;
                }
                if(indexed==null||!indexed.getUUID("transaction").equals(transaction))continue;
                CompoundTag latest=null;
                for(var list:journals.values())for(var candidate:list.values()){
                    if(candidate.getString("state").equals("ABORTED")||latest!=null&&candidate.getLong("sequence")<latest.getLong("sequence"))continue;
                    boolean matches=false;for(Tag slot:candidate.getList("slots",Tag.TAG_COMPOUND))if(((CompoundTag)slot).getString("slot").equals(key)){matches=true;break;}
                    if(matches)latest=candidate;
                }if(latest==null)externalReceipts.remove(entity);else externalReceipts.put(entity,latest.copy());
            }catch(IllegalArgumentException ignored){}
        }setDirty();
    }
    static FactionStandingData load(CompoundTag root){
        var result=new FactionStandingData();result.journalSequence=Math.max(0,root.getLong("economy_sequence"));result.rootExtras=root.copy();result.rootExtras.remove("players");result.rootExtras.remove("economy_journals");result.rootExtras.remove("quarantined_players");
        if(root.contains("players")&&!root.contains("players",Tag.TAG_LIST)||root.contains("economy_journals")&&!root.contains("economy_journals",Tag.TAG_LIST)){result.unknownRoot=root.copy();return result;}
        for(String listKey:List.of("players","economy_journals","quarantined_players"))if(root.contains(listKey)&&!(root.get(listKey) instanceof ListTag list&&(list.isEmpty()||list.getElementType()==Tag.TAG_COMPOUND))){result.unknownRoot=root.copy();return result;}
        var seen=new HashMap<UUID,CompoundTag>();
        for(Tag row:root.getList("players",Tag.TAG_COMPOUND)){var p=(CompoundTag)row;try{if(!p.hasUUID("uuid"))throw new IllegalArgumentException("missing player UUID");UUID id=p.getUUID("uuid");if(seen.containsKey(id)){if(!result.unknownPlayers.contains(id))result.quarantinedPlayers.add(seen.get(id).copy());throw new IllegalArgumentException("duplicate player UUID");}seen.put(id,p.copy());result.player(id,p);}catch(RuntimeException invalid){result.quarantinedPlayers.add(p.copy());if(p.hasUUID("uuid"))result.unknownPlayers.add(p.getUUID("uuid"));}}
        for(Tag row:root.getList("quarantined_players",Tag.TAG_COMPOUND)){var p=(CompoundTag)row;result.quarantinedPlayers.add(p.copy());if(p.hasUUID("uuid"))result.unknownPlayers.add(p.getUUID("uuid"));}
        for(Tag row:root.getList("economy_journals",Tag.TAG_COMPOUND)){var t=(CompoundTag)row;if(t.getInt("schema")==1&&t.hasUUID("player")&&t.hasUUID("transaction")&&!result.journaled(t.getUUID("player"),t.getUUID("transaction")))result.journal(t.getUUID("player"),t.getUUID("transaction"),t);else{result.unknownRoot=root.copy();return result;}}
        return result;
    }
    @Override public CompoundTag save(CompoundTag root){
        if(unknownRoot!=null)return unknownRoot.copy();root.merge(rootExtras.copy());root.putLong("economy_sequence",journalSequence);var rows=new ListTag();players.keySet().stream().filter(this::known).sorted().forEach(id->rows.add(player(id)));root.put("players",rows);
        var receipts=new ListTag();journals.forEach((player,list)->list.forEach((transaction,t)->{var row=t.copy();row.putUUID("player",player);row.putUUID("transaction",transaction);receipts.add(row);}));root.put("economy_journals",receipts);var bad=new ListTag();quarantinedPlayers.forEach(t->bad.add(t.copy()));root.put("quarantined_players",bad);return root;
    }
}

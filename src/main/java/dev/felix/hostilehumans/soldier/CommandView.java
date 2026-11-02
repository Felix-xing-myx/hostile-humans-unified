package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.CombatPreference;
import dev.felix.hostilehumans.inventory.SoldierPickupPolicy;

import net.minecraft.network.FriendlyByteBuf;
import java.util.List;

/** Packet/view adapter only. Geometry and writes stay in the shared order modules. */
public record CommandView(SoldierOrder order, SoldierCombatMode combat, boolean pickup,
        GarrisonSettings garrison, int follow, int outer, int chaseTicks, String reason, CombatPreference preference, java.util.Set<Field> mixed) {
    public enum Field { ORDER, COMBAT, PICKUP, STAY, CHASE, RADIUS, FOLLOW, PREFERENCE }
    public CommandView { mixed=java.util.Set.copyOf(mixed); }
    public CommandView(SoldierOrder order,SoldierCombatMode combat,boolean pickup,GarrisonSettings garrison,int follow,int outer,int chaseTicks,String reason) {
        this(order,combat,pickup,garrison,follow,outer,chaseTicks,reason,CombatPreference.DEFAULT,java.util.Set.of());
    }
    public CommandView withMixed(java.util.Set<Field> fields) { return new CommandView(order,combat,pickup,garrison,follow,outer,chaseTicks,reason,preference,fields); }
    public Object field(Field f) { return switch(f) {
        case ORDER -> order; case COMBAT -> combat; case PICKUP -> pickup; case STAY -> garrison.stay();
        case CHASE -> garrison.chase(); case RADIUS -> garrison.radius(); case FOLLOW -> follow; case PREFERENCE -> preference;
    }; }
    public static final List<SoldierOrder> ORDERS=List.of(SoldierOrder.FOLLOW,SoldierOrder.BODYGUARD,SoldierOrder.GARRISON);
    public static final List<SoldierCombatMode> COMBAT=List.of(SoldierCombatMode.ACTIVE,SoldierCombatMode.AUTHORIZED,
            SoldierCombatMode.PASSIVE_PROTECTION,SoldierCombatMode.SELF_DEFENSE);
    public static CommandView of(Human human) {
        GarrisonSettings settings=GarrisonSettings.player(human);
        var context=new OrderDistances.Context(true,false,true,human.position(),settings,"snapshot");
        return new CommandView(SoldierOrder.get(human),SoldierCombatMode.get(human),SoldierPickupPolicy.isEnabled(human),
                settings,OrderDistances.follow(human),(int)context.combatRadius(),OrderDistances.defaults().chaseDurationTicks(),OrderDistances.context(human).reason(),CombatPreference.get(human),java.util.Set.of());
    }
    public static void apply(Human human,SquadCommands.Update update) {
        if(!valid(update))throw new IllegalArgumentException("invalid command update");
        dev.felix.hostilehumans.cultivation.CultivationService.interrupt(human,"Explicit soldier command",true);
        GarrisonSettings settings=update.garrison();
        if(update.stay()!=null||update.chase()!=null||update.radius()!=null) {
            settings=patchedSettings(GarrisonSettings.player(human),update);
        }
        SoldierOrder.apply(human,update.order(),settings,update.followDistance());
        if(update.combat()!=null)SoldierCombatMode.set(human,update.combat());
        if(update.pickup()!=null)SoldierPickupPolicy.set(human,update.pickup());
        if(update.preference()!=null)CombatPreference.set(human,update.preference());
        persist(human);
    }
    static GarrisonSettings patchedSettings(GarrisonSettings own,SquadCommands.Update update) {
        return new GarrisonSettings(update.stay()!=null?update.stay():own.stay(),
                update.chase()!=null?update.chase():own.chase(),update.radius()!=null?update.radius():own.radius());
    }
    public static void persist(Human human){
        var saved=dev.felix.hostilehumans.entity.HumanServerData.get();
        if(saved!=null)saved.updateOrRegisterHumanMob(human);
    }
    public static boolean valid(SquadCommands.Update update) {
        return update!=null&&(update.order()==null||ORDERS.contains(update.order()))
                &&(update.followDistance()==null||update.followDistance()>=8&&update.followDistance()<=96)
                &&(update.radius()==null||update.radius()>=8&&update.radius()<=96)
                &&(update.garrison()==null||update.stay()==null&&update.chase()==null&&update.radius()==null)
                &&(update.stay()!=null||update.chase()!=null||update.radius()!=null||update.preference()!=null||update.order()!=null||update.garrison()!=null||update.combat()!=null||update.pickup()!=null||update.followDistance()!=null);
    }
    public void encode(FriendlyByteBuf b) {
        b.writeEnum(order);b.writeEnum(combat);b.writeBoolean(pickup);writeSettings(b,garrison);
        b.writeVarInt(follow);b.writeVarInt(outer);b.writeVarInt(chaseTicks);b.writeUtf(reason,128);b.writeEnum(preference);
        int mask=0;for(Field field:mixed)mask|=1<<field.ordinal();b.writeVarInt(mask);
    }
    public static CommandView decode(FriendlyByteBuf b) {
        SoldierOrder order=b.readEnum(SoldierOrder.class);SoldierCombatMode combat=b.readEnum(SoldierCombatMode.class);
        boolean pickup=b.readBoolean();GarrisonSettings settings=readSettings(b);
        int follow=distance(b.readVarInt()),outer=distance(b.readVarInt()),ticks=b.readVarInt();String reason=b.readUtf(128);
        if(!ORDERS.contains(order)||ticks<40||ticks>1200)throw new IllegalArgumentException("invalid command snapshot");
        CombatPreference preference=b.readEnum(CombatPreference.class);int mask=b.readVarInt();
        if((mask&~255)!=0)throw new IllegalArgumentException("invalid mixed fields");
        java.util.Set<Field> mixed=java.util.EnumSet.noneOf(Field.class);for(Field f:Field.values())if((mask&(1<<f.ordinal()))!=0)mixed.add(f);
        return new CommandView(order,combat,pickup,settings,follow,outer,ticks,reason,preference,mixed);
    }
    static void writeSettings(FriendlyByteBuf b,GarrisonSettings s){b.writeBoolean(s.stay());b.writeBoolean(s.chase());b.writeVarInt(s.radius());}
    static GarrisonSettings readSettings(FriendlyByteBuf b){return new GarrisonSettings(b.readBoolean(),b.readBoolean(),distance(b.readVarInt()));}
    private static int distance(int value){if(value<8||value>96)throw new IllegalArgumentException("invalid activity distance");return value;}
    public static void writeUpdate(FriendlyByteBuf b,SquadCommands.Update u) {
        int mask=(u.order()!=null?1:0)|(u.garrison()!=null?2:0)|(u.combat()!=null?4:0)|(u.pickup()!=null?8:0)|(u.followDistance()!=null?16:0)|(u.stay()!=null?32:0)|(u.chase()!=null?64:0)|(u.radius()!=null?128:0)|(u.preference()!=null?256:0);
        b.writeVarInt(mask);if(u.order()!=null)b.writeEnum(u.order());if(u.garrison()!=null)writeSettings(b,u.garrison());
        if(u.combat()!=null)b.writeEnum(u.combat());if(u.pickup()!=null)b.writeBoolean(u.pickup());if(u.followDistance()!=null)b.writeVarInt(u.followDistance());
        if(u.stay()!=null)b.writeBoolean(u.stay());if(u.chase()!=null)b.writeBoolean(u.chase());
        if(u.radius()!=null)b.writeVarInt(u.radius());if(u.preference()!=null)b.writeEnum(u.preference());
    }
    public static SquadCommands.Update readUpdate(FriendlyByteBuf b) {
        int mask=b.readVarInt();if(mask==0||(mask&~511)!=0)throw new IllegalArgumentException("invalid command mask");
        var update=new SquadCommands.Update((mask&1)!=0?b.readEnum(SoldierOrder.class):null,(mask&2)!=0?readSettings(b):null,
                (mask&4)!=0?b.readEnum(SoldierCombatMode.class):null,(mask&8)!=0?b.readBoolean():null,(mask&16)!=0?distance(b.readVarInt()):null,
                (mask&32)!=0?b.readBoolean():null,(mask&64)!=0?b.readBoolean():null,
                (mask&128)!=0?distance(b.readVarInt()):null,(mask&256)!=0?b.readEnum(CombatPreference.class):null);
        if(!valid(update))throw new IllegalArgumentException("invalid command update");return update;
    }
    public static SquadCommands.Update order(SoldierOrder o){return new SquadCommands.Update(o,null,null,null,null);}
    public static SquadCommands.Update combat(SoldierCombatMode m){return new SquadCommands.Update(null,null,m,null,null);}
    public static SquadCommands.Update pickup(boolean value){return new SquadCommands.Update(null,null,null,value,null);}
    public static SquadCommands.Update settings(GarrisonSettings s){return new SquadCommands.Update(null,s,null,null,null);}
    public static SquadCommands.Update follow(int r){return new SquadCommands.Update(null,null,null,null,r);}
    public static SquadCommands.Update stay(boolean value){return new SquadCommands.Update(null,null,null,null,null,value,null,null,null);}
    public static SquadCommands.Update chase(boolean value){return new SquadCommands.Update(null,null,null,null,null,null,value,null,null);}
    public static SquadCommands.Update radius(int value){return new SquadCommands.Update(null,null,null,null,null,null,null,value,null);}
    public static SquadCommands.Update preference(CombatPreference value){return new SquadCommands.Update(null,null,null,null,null,null,null,null,value);}
}

package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.network.FriendlyByteBuf;
import java.util.List;

/** Packet/view adapter only. Geometry and writes stay in the shared order modules. */
public record CommandView(SoldierOrder order, SoldierCombatMode combat, boolean pickup,
        GarrisonSettings garrison, int follow, int outer, int chaseTicks, String reason) {
    public static final List<SoldierOrder> ORDERS=List.of(SoldierOrder.FOLLOW,SoldierOrder.BODYGUARD,SoldierOrder.GARRISON);
    public static final List<SoldierCombatMode> COMBAT=List.of(SoldierCombatMode.ACTIVE,SoldierCombatMode.AUTHORIZED,
            SoldierCombatMode.PASSIVE_PROTECTION,SoldierCombatMode.SELF_DEFENSE);
    public static CommandView of(Human human) {
        GarrisonSettings settings=GarrisonSettings.player(human);
        var context=new OrderDistances.Context(true,false,true,human.position(),settings,"snapshot");
        return new CommandView(SoldierOrder.get(human),SoldierCombatMode.get(human),SoldierPickupPolicy.isEnabled(human),
                settings,OrderDistances.follow(human),(int)context.combatRadius(),OrderDistances.defaults().chaseDurationTicks(),OrderDistances.context(human).reason());
    }
    public static void apply(Human human,SquadCommands.Update update) {
        if(!valid(update))throw new IllegalArgumentException("invalid command update");
        SoldierOrder.apply(human,update.order(),update.garrison(),update.followDistance());
        if(update.combat()!=null)SoldierCombatMode.set(human,update.combat());
        if(update.pickup()!=null)SoldierPickupPolicy.set(human,update.pickup());
        persist(human);
    }
    public static void persist(Human human){
        var saved=com.craftix.hostile_humans.entity.data.HumanServerData.get();
        if(saved!=null)saved.updateOrRegisterHumanMob(human);
    }
    public static boolean valid(SquadCommands.Update update) {
        return update!=null&&(update.order()==null||ORDERS.contains(update.order()))
                &&(update.followDistance()==null||update.followDistance()>=8&&update.followDistance()<=96)
                &&(update.order()!=null||update.garrison()!=null||update.combat()!=null||update.pickup()!=null||update.followDistance()!=null);
    }
    public void encode(FriendlyByteBuf b) {
        b.writeEnum(order);b.writeEnum(combat);b.writeBoolean(pickup);writeSettings(b,garrison);
        b.writeVarInt(follow);b.writeVarInt(outer);b.writeVarInt(chaseTicks);b.writeUtf(reason,128);
    }
    public static CommandView decode(FriendlyByteBuf b) {
        SoldierOrder order=b.readEnum(SoldierOrder.class);SoldierCombatMode combat=b.readEnum(SoldierCombatMode.class);
        boolean pickup=b.readBoolean();GarrisonSettings settings=readSettings(b);
        int follow=distance(b.readVarInt()),outer=distance(b.readVarInt()),ticks=b.readVarInt();String reason=b.readUtf(128);
        if(!ORDERS.contains(order)||ticks<40||ticks>1200)throw new IllegalArgumentException("invalid command snapshot");
        return new CommandView(order,combat,pickup,settings,follow,outer,ticks,reason);
    }
    static void writeSettings(FriendlyByteBuf b,GarrisonSettings s){b.writeBoolean(s.stay());b.writeBoolean(s.chase());b.writeVarInt(s.radius());}
    static GarrisonSettings readSettings(FriendlyByteBuf b){return new GarrisonSettings(b.readBoolean(),b.readBoolean(),distance(b.readVarInt()));}
    private static int distance(int value){if(value<8||value>96)throw new IllegalArgumentException("invalid activity distance");return value;}
    public static void writeUpdate(FriendlyByteBuf b,SquadCommands.Update u) {
        int mask=(u.order()!=null?1:0)|(u.garrison()!=null?2:0)|(u.combat()!=null?4:0)|(u.pickup()!=null?8:0)|(u.followDistance()!=null?16:0);
        b.writeByte(mask);if(u.order()!=null)b.writeEnum(u.order());if(u.garrison()!=null)writeSettings(b,u.garrison());
        if(u.combat()!=null)b.writeEnum(u.combat());if(u.pickup()!=null)b.writeBoolean(u.pickup());if(u.followDistance()!=null)b.writeVarInt(u.followDistance());
    }
    public static SquadCommands.Update readUpdate(FriendlyByteBuf b) {
        int mask=b.readUnsignedByte();if(mask==0||(mask&~31)!=0)throw new IllegalArgumentException("invalid command mask");
        var update=new SquadCommands.Update((mask&1)!=0?b.readEnum(SoldierOrder.class):null,(mask&2)!=0?readSettings(b):null,
                (mask&4)!=0?b.readEnum(SoldierCombatMode.class):null,(mask&8)!=0?b.readBoolean():null,(mask&16)!=0?distance(b.readVarInt()):null);
        if(!valid(update))throw new IllegalArgumentException("invalid command update");return update;
    }
    public static SquadCommands.Update order(SoldierOrder o){return new SquadCommands.Update(o,null,null,null,null);}
    public static SquadCommands.Update combat(SoldierCombatMode m){return new SquadCommands.Update(null,null,m,null,null);}
    public static SquadCommands.Update pickup(boolean value){return new SquadCommands.Update(null,null,null,value,null);}
    public static SquadCommands.Update settings(GarrisonSettings s){return new SquadCommands.Update(null,s,null,null,null);}
    public static SquadCommands.Update follow(int r){return new SquadCommands.Update(null,null,null,null,r);}
}

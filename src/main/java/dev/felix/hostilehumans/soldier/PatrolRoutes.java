package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.entity.Human;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** Transactional adapter for player-drawn routes on the existing NaturalRoute executor. */
public final class PatrolRoutes {
    public static final String AUTHORED = "player_route";
    public static final String ORIGIN = "route_origin";
    public static final String PLAYER_CENTER = "hostile_humans_unified:player_route_center";
    private PatrolRoutes() {}
    public static int revision(Human h) { return NaturalRoute.author(h,OrderDistances.context(h)).getInt("route_revision"); }
    public static boolean dynamic(Human h,OrderDistances.Context c) { return NaturalRoute.author(h,c).getBoolean(AUTHORED); }
    public static void install(Human h,List<PatrolRoutePolicy.Waypoint> draft) {
        if(draft==null||draft.size()<2||draft.size()>OrderDistances.defaults().routeMaxPoints()+1)
            throw new IllegalArgumentException("route size");
        draft=List.copyOf(draft);
        boolean loop=PatrolRoutePolicy.closed(draft.stream().map(PatrolRoutePolicy.Waypoint::block).toList());
        if(draft.size()<2||draft.size()-(loop?1:0)>OrderDistances.defaults().routeMaxPoints()
                ||draft.stream().map(PatrolRoutePolicy.Waypoint::block).distinct().count()<2)throw new IllegalArgumentException("route size");
        List<PatrolRoutePolicy.Waypoint> points=loop?List.copyOf(draft.subList(0,draft.size()-1)):List.copyOf(draft);
        for(int i=1;i<draft.size();i++)if(draft.get(i-1).feet().distanceToSqr(draft.get(i).feet())>PatrolRoutePolicy.MAX_LEG*PatrolRoutePolicy.MAX_LEG)
            throw new IllegalArgumentException("route leg too long");
        OrderDistances.Context c=OrderDistances.context(h);Vec3 first=points.get(0).feet();
        CompoundTag author=NaturalRoute.author(h,c).copy(),origin=new CompoundTag();
        origin.putDouble("x",first.x);origin.putDouble("y",first.y);origin.putDouble("z",first.z);
        origin.putString("dimension",h.level().dimension().location().toString());
        origin.putDouble("right_x",1);origin.putDouble("right_z",0);origin.putDouble("forward_x",0);origin.putDouble("forward_z",1);
        origin.putInt("placement_rotation",0);origin.putString("placement_mirror","none");
        ListTag list=new ListTag();
        for(var waypoint:points){Vec3 pos=waypoint.feet();CompoundTag point=new CompoundTag();point.putDouble("x",pos.x-first.x);
            point.putDouble("y",pos.y-first.y);point.putDouble("z",pos.z-first.z);
            point.putInt("wait",OrderDistances.defaults().routeWaitDefault());list.add(point);}
        author.put("route",list);author.putBoolean(AUTHORED,true);author.put("route_origin",origin);
        author.putString("route_mode",loop?"loop":"ping_pong");author.putInt("route_rotation",0);author.putString("route_mirror","none");
        author.putInt("route_revision",author.getInt("route_revision")+1);
        new GarrisonSettings(false,c.settings().chase(),c.settings().radius()).writeInto(author);
        replace(h,c,author,first);
    }
    private static void replace(Human h,OrderDistances.Context c,CompoundTag author,Vec3 first) {
        HumanBehaviorControl.interruptMovement(h,"patrol route replaced");NaturalRoute.clearRuntime(h);SoldierOrder.stopMovement(h);
        h.guardCombatState.reset();h.getPersistentData().put(c.natural()?NaturalState.STATE:GarrisonSettings.PLAYER,author);
        reached(h,c,first);persist(h);
    }
    /** 0 up, 1 down, 2 remove. Coordinates are always read from the server's current definition. */
    static boolean editPoint(Human h,int index,int operation,int expectedRevision) {
        var c=OrderDistances.context(h);var definition=NaturalRoute.definition(h,c);
        if(!c.constrained()||!definition.valid()||revision(h)!=expectedRevision||operation<0||operation>2)return false;
        var author=NaturalRoute.author(h,c).copy();ListTag list=author.getList("route",Tag.TAG_COMPOUND);
        if(index<0||index>=list.size())return false;
        var positions=new java.util.ArrayList<>(definition.points().stream().map(NaturalRoute.Point::position).toList());
        if(operation==2){if(list.size()<=2)return false;list.remove(index);positions.remove(index);}
        else {int next=index+(operation==0?-1:1);if(next<0||next>=list.size())return false;
            Tag a=list.get(index),b=list.get(next);list.set(index,b);list.set(next,a);java.util.Collections.swap(positions,index,next);}
        var blocks = new java.util.ArrayList<>(positions.stream().map(BlockPos::containing).toList());
        // Editing must preserve the same minimum route geometry as initial authoring.
        if (blocks.stream().distinct().count() < 2) return false;
        boolean loop = "loop".equals(definition.mode());
        if (loop) {
            blocks.add(blocks.get(0));
            loop = PatrolRoutePolicy.closed(blocks);
            author.putString("route_mode", loop ? "loop" : "ping_pong");
        }
        for(int i=1;i<positions.size();i++)if(positions.get(i-1).distanceToSqr(positions.get(i))>PatrolRoutePolicy.MAX_LEG*PatrolRoutePolicy.MAX_LEG)return false;
        if(loop&&positions.get(0).distanceToSqr(positions.get(positions.size()-1))>PatrolRoutePolicy.MAX_LEG*PatrolRoutePolicy.MAX_LEG)return false;
        author.putInt("route_revision",expectedRevision+1);replace(h,c,author,positions.get(0));return true;
    }
    /** The real leash anchor changes only on arrival; the route's immutable transform never moves. */
    static void reached(Human h,OrderDistances.Context c,Vec3 point) {
        if(!dynamic(h,c)||c.constrained()&&c.available()&&c.origin().distanceToSqr(point)<1.e-8)return;
        if(c.natural()){
            CompoundTag origin=h.getPersistentData().getCompound(NaturalState.ORIGIN).copy();
            origin.putDouble("x",point.x);origin.putDouble("y",point.y);origin.putDouble("z",point.z);
            origin.putString("dimension",h.level().dimension().location().toString());
            h.getPersistentData().put(NaturalState.ORIGIN,origin);
        }else{
            CompoundTag precise=new CompoundTag();precise.putDouble("x",point.x);precise.putDouble("y",point.y);precise.putDouble("z",point.z);
            precise.putString("dimension",h.level().dimension().location().toString());h.getPersistentData().put(PLAYER_CENTER,precise);
            h.getPersistentData().putLong(SoldierOrder.ANCHOR,BlockPos.containing(point).asLong());
            h.getPersistentData().putString(SoldierOrder.ANCHOR_DIMENSION,h.level().dimension().location().toString());
        }
    }
    static Vec3 playerCenter(Human h,Vec3 fallback) {
        if(!h.getPersistentData().getCompound(GarrisonSettings.PLAYER).getBoolean(AUTHORED))return fallback;
        CompoundTag p=h.getPersistentData().getCompound(PLAYER_CENTER);
        Vec3 exact=new Vec3(p.getDouble("x"),p.getDouble("y"),p.getDouble("z"));
        return p.contains("x",Tag.TAG_ANY_NUMERIC)&&p.contains("y",Tag.TAG_ANY_NUMERIC)&&p.contains("z",Tag.TAG_ANY_NUMERIC)
                &&Double.isFinite(exact.x)&&Double.isFinite(exact.y)&&Double.isFinite(exact.z)
                &&p.getString("dimension").equals(h.level().dimension().location().toString())
                &&BlockPos.containing(exact).equals(BlockPos.containing(fallback))?exact:fallback;
    }
    public static List<Integer> waits(Human h) {
        var c=OrderDistances.context(h);var definition=NaturalRoute.definition(h,c);
        return definition.valid()?definition.points().stream().map(NaturalRoute.Point::waitTicks).toList():List.of();
    }
    public static boolean waitAt(Human h,int index,int ticks,int expectedRevision) {
        if(ticks<0||ticks>PatrolRoutePolicy.MAX_WAIT||revision(h)!=expectedRevision)return false;
        var c=OrderDistances.context(h);CompoundTag author=NaturalRoute.author(h,c).copy();
        ListTag list=author.getList("route",Tag.TAG_COMPOUND);
        if(index<0||index>=list.size())return false;
        list.getCompound(index).putInt("wait",ticks);author.putInt("route_revision",expectedRevision+1);
        h.getPersistentData().put(c.natural()?NaturalState.STATE:GarrisonSettings.PLAYER,author);persist(h);return true;
    }
    private static void persist(Human h){if(h.hasOwner())CommandView.persist(h);}
}

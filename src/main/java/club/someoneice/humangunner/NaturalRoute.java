package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import dev.felix.hostilehumans.core.RouteProgressPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Optional author route on existing navigation and budget; no second combat controller. */
public final class NaturalRoute {
    public static final String PLAYER_RUNTIME = HumanGunner.MOD_ID + ":order_route_runtime";
    public record Point(Vec3 position, int waitTicks) {}
    public record Definition(List<Point> points, String mode, double speed, String error) {
        public boolean valid() { return error.isEmpty(); }
    }
    private static final Map<Human, Active> ACTIVE = new WeakHashMap<>();
    private static final class Active { Path path; boolean resume; long lastTick = -1; String key; }
    private NaturalRoute() {}
    static CompoundTag author(Human h, OrderDistances.Context c) {
        return h.getPersistentData().getCompound(c.natural() ? NaturalState.STATE : GarrisonSettings.PLAYER);
    }
    public static boolean hasRoute(Human h, OrderDistances.Context c) { return author(h,c).contains("route"); }
    public static Definition definition(Human h, OrderDistances.Context c) {
        CompoundTag author = author(h,c);
        var list = author.getList("route", Tag.TAG_COMPOUND);
        if (!author.contains("route", Tag.TAG_LIST) || list.isEmpty() || list.size() > OrderDistances.defaults().routeMaxPoints())
            return invalid("invalid_route_size");
        String mode = author.contains("route_mode") ? author.getString("route_mode") : "loop";
        if (!List.of("loop", "ping_pong", "once").contains(mode)) return invalid("invalid_route_mode");
        CompoundTag origin = h.getPersistentData().getCompound(c.natural() ? NaturalState.ORIGIN : GarrisonSettings.PLAYER);
        if (!origin.contains("placement_mirror", Tag.TAG_STRING) || !origin.contains("placement_rotation", Tag.TAG_ANY_NUMERIC)
                || !origin.contains("right_x", Tag.TAG_ANY_NUMERIC) || !origin.contains("right_z", Tag.TAG_ANY_NUMERIC)
                || !origin.contains("forward_x", Tag.TAG_ANY_NUMERIC) || !origin.contains("forward_z", Tag.TAG_ANY_NUMERIC))
            return invalid("missing_transform_context");
        String placementMirror = origin.getString("placement_mirror");
        String mirror = author.contains("route_mirror") ? author.getString("route_mirror") : "none";
        int rotation = author.getInt("route_rotation"), placed = origin.getInt("placement_rotation");
        if (!NaturalState.validRotation(rotation) || !NaturalState.validRotation(placed)
                || !NaturalState.validMirror(mirror) || !NaturalState.validMirror(placementMirror)) return invalid("invalid_transform_context");
        double rx=origin.getDouble("right_x"), rz=origin.getDouble("right_z"),
                fx=origin.getDouble("forward_x"), fz=origin.getDouble("forward_z");
        if(!Double.isFinite(rx)||!Double.isFinite(rz)||!Double.isFinite(fx)||!Double.isFinite(fz)
                ||Math.abs(rx*rx+rz*rz-1)>1.0E-4||Math.abs(fx*fx+fz*fz-1)>1.0E-4
                ||Math.abs(rx*fx+rz*fz)>1.0E-4)return invalid("invalid_transform_context");
        List<Point> points = new ArrayList<>();
        for (Tag entry : list) {
            CompoundTag p = (CompoundTag) entry;
            if (!p.contains("x",Tag.TAG_ANY_NUMERIC)||!p.contains("y",Tag.TAG_ANY_NUMERIC)||!p.contains("z",Tag.TAG_ANY_NUMERIC))
                return invalid("missing_route_coordinate");
            double x=p.getDouble("x"), y=p.getDouble("y"), z=p.getDouble("z");
            if (!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)) return invalid("nonfinite_route_coordinate");
            // Author correction first, then the placement transform. Each mirror precedes its rotation.
            Vec3 corrected = transform(x,y,z,mirror,rotation);
            Vec3 local = new Vec3(corrected.x*origin.getDouble("right_x")+corrected.z*origin.getDouble("forward_x"), y,
                    corrected.x*origin.getDouble("right_z")+corrected.z*origin.getDouble("forward_z"));
            Vec3 world = c.origin().add(transform(local.x,local.y,local.z,placementMirror,placed));
            if (!Double.isFinite(world.x)||!Double.isFinite(world.y)||!Double.isFinite(world.z)||!c.insideDaily(world))
                return invalid("route_outside_daily_radius");
            int wait = p.contains("wait",Tag.TAG_ANY_NUMERIC) ? p.getInt("wait") : OrderDistances.defaults().routeWaitDefault();
            if (wait<0||wait>1200) return invalid("invalid_route_wait");
            points.add(new Point(world,wait));
        }
        return new Definition(List.copyOf(points),mode,author.contains("route_speed",Tag.TAG_ANY_NUMERIC)
                ? OrderDistances.finite(author.getDouble("route_speed"),.65,.3,1) : .65, "");
    }
    private static Definition invalid(String reason) { return new Definition(List.of(), "loop", .65, reason); }
    /** Minecraft mirror semantics: LEFT_RIGHT flips Z, FRONT_BACK flips X. */
    public static Vec3 transform(double x,double y,double z,String mirror,int rotation) {
        if (mirror.equals("left_right")) z=-z; else if(mirror.equals("front_back")) x=-x;
        return switch(rotation) { case 90 -> new Vec3(-z,y,x); case 180 -> new Vec3(-x,y,-z);
            case 270 -> new Vec3(z,y,-x); default -> new Vec3(x,y,z); };
    }
    private static String runtimeKey(Human h){return h.hasOwner()?PLAYER_RUNTIME:NaturalState.RUNTIME;}
    public static void clearRuntime(Human h) { clear(h,runtimeKey(h)); }
    public static void clearPlayerRuntime(Human h) { clear(h,PLAYER_RUNTIME); }
    public static void clearNaturalRuntime(Human h) { clear(h,NaturalState.RUNTIME); }
    private static void clear(Human h,String key) {
        Active a=ACTIVE.get(h);
        if(a!=null&&(a.key==null||a.key.equals(key))){stopOwned(h);ACTIVE.remove(h);}
        h.getPersistentData().remove(key);
    }
    public static void stopOwned(Human h) {
        Active a=ACTIVE.get(h);if(a==null)return;
        if(a.path!=null&&MovementContinuity.ownsRoute(h,a.path))h.getNavigation().stop();a.path=null;a.lastTick=-1;
    }
    public static void pause(Human h, boolean resumeNearest) {
        Active a=ACTIVE.computeIfAbsent(h,ignored->new Active()); a.resume |= resumeNearest; a.lastTick=-1;
    }
    public static String status(Human h) { return h.getPersistentData().getCompound(NaturalState.RUNTIME).getString("status"); }
    private static void save(Human h,CompoundTag state,String status) { state.putString("status",status);h.getPersistentData().put(runtimeKey(h),state); }
    public static Vec3 returnPoint(Human h, OrderDistances.Context c) {
        if(c.settings().stay()||!hasRoute(h,c))return c.origin();
        Definition d=definition(h,c);return d.valid()?d.points().get(nearest(d,h.position())).position():c.origin();
    }
    private static int nearest(Definition d,Vec3 p) {
        int best=0;double distance=Double.MAX_VALUE;
        for(int i=0;i<d.points().size();i++){double candidate=d.points().get(i).position().distanceToSqr(p);if(candidate<distance){distance=candidate;best=i;}}
        return best;
    }
    static boolean reached(Vec3 position,Vec3 point,double horizontal) {
        double x=position.x-point.x,z=position.z-point.z;
        return x*x+z*z<=horizontal*horizontal&&Math.abs(position.y-point.y)<=OrderDistances.defaults().routeVerticalTolerance();
    }
    static boolean reached(Human human,Vec3 point,double horizontal) {
        if(!reached(human.position(),point,horizontal))return false;
        Vec3 from=human.position().add(0,.4,0),to=point.add(0,.4,0);
        return human.level().clip(new net.minecraft.world.level.ClipContext(from,to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,human))
                .getType()==net.minecraft.world.phys.HitResult.Type.MISS;
    }
    /** Returns true whenever route data exists, including invalid/backoff/completed routes. */
    static boolean tick(Human h,OrderDistances.Context c) {
        if(!hasRoute(h,c))return false;
        CompoundTag state=h.getPersistentData().getCompound(runtimeKey(h)).copy();
        Definition d=definition(h,c);
        if(!c.available()||!d.valid()){
            stopOwned(h);
            save(h,state,c.available()?d.error():c.reason());
            // Malformed/empty routes fall back to legal random patrol. Unknown placement must pause.
            return !c.available()||d.error().contains("transform_context");
        }
        if(state.getBoolean("completed")){save(h,state,"completed");return true;}
        long now=h.level().getGameTime();
        Active a=ACTIVE.computeIfAbsent(h,ignored->new Active());
        if(a.key!=null&&!a.key.equals(runtimeKey(h))){stopOwned(h);a=new Active();ACTIVE.put(h,a);}
        a.key=runtimeKey(h);
        if(a.lastTick==now)return true;
        long last=a.lastTick;a.lastTick=now;
        if(a.resume){state.putInt("index",nearest(d,h.position()));state.putInt("executed",0);state.putInt("waited",0);a.path=null;a.resume=false;}
        if(now<state.getLong("retry_at")){save(h,state,"failure_backoff");return true;}
        int index=Math.floorMod(state.getInt("index"),d.points().size());Point point=d.points().get(index);
        if(reached(h,point.position(),OrderDistances.defaults().routeReachDistance())){
            if(a.path!=null&&MovementContinuity.ownsRoute(h,a.path))h.getNavigation().stop();a.path=null;
            int waited=state.getInt("waited")+(last==now-1?1:0);
            state.putInt("waited",waited);state.putInt("executed",0);state.putInt("failed",0);
            if(waited>=point.waitTicks()) advance(state,d,index,false,now);
            save(h,state,"waiting");return true;
        }
        state.putInt("waited",0);
        if(a.path!=null&&MovementContinuity.ownsRoute(h,a.path)&&!SoldierOrder.allowsDailyPath(h,a.path))stopOwned(h);
        if(a.path!=null&&MovementContinuity.ownsRoute(h,a.path)&&!h.getNavigation().isDone()){
            state.putInt("executed",RouteProgressPolicy.executedTicks(state.getInt("executed"),last,now,true));
            if(state.getInt("executed")>=OrderDistances.defaults().routeTimeout()){
                stopOwned(h);advance(state,d,index,true,now);save(h,state,"point_timeout");
            }else save(h,state,"walking");
            return true;
        }
        if(a.path!=null&&MovementContinuity.ownsRoute(h,a.path)&&h.getNavigation().isDone()){
            a.path=null;advance(state,d,index,true,now);save(h,state,"path_ended_short");return true;
        }
        if(!h.level().hasChunkAt(BlockPos.containing(point.position()))){save(h,state,"point_unloaded");return true;}
        if(!BasicNavigationBudget.tryAcquire(h,1)){save(h,state,"budget_wait");return true;}
        try {
            Path path=h.getNavigation().createPath(BlockPos.containing(point.position()),0);
            if(path!=null&&path.canReach()&&SoldierOrder.allowsDailyPath(h,path)&&HumanTerrainSafety.safePath(h,path)
                    &&RetreatPathSafety.tryMove(h,path,d.speed())){a.path=h.getNavigation().getPath();save(h,state,"walking");}
            else {a.path=null;advance(state,d,index,true,now);save(h,state,"point_unreachable");}
        }finally{BasicNavigationBudget.cancel(h);}
        return true;
    }
    private static void advance(CompoundTag state,Definition d,int index,boolean failed,long now) {
        state.putInt("executed",0);state.putInt("waited",0);
        RouteProgressPolicy.Next next=RouteProgressPolicy.advance(index,state.getInt("direction"),d.points().size(),d.mode(),
                failed,state.getInt("failed"),now,OrderDistances.defaults().routeFailureBackoffTicks());
        state.putInt("direction",next.direction());state.putInt("index",next.index());state.putInt("failed",next.failures());
        state.putBoolean("completed",next.completed());state.putLong("retry_at",next.retryAt());
    }
}

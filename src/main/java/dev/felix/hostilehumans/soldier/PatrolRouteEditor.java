package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.entity.Human;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Per-player server drafts. No draft is written to an item, entity, world save or client authority. */
@Mod.EventBusSubscriber(modid=HumanRuntime.MOD_ID)
public final class PatrolRouteEditor {
    private static final Map<ServerPlayer,Draft> DRAFTS=new WeakHashMap<>();
    private static final class Draft {
        final UUID human; final int revision; final double width,height; final ArrayList<PatrolRoutePolicy.Waypoint> points=new ArrayList<>();
        long awaySince=-1,lastAction=Long.MIN_VALUE;
        Draft(Human h){human=h.getUUID();revision=PatrolRoutes.revision(h);var size=h.getDimensions(net.minecraft.world.entity.Pose.STANDING);
            width=Math.max(size.width,h.getBbWidth());height=Math.max(size.height,h.getBbHeight());}
    }
    private PatrolRouteEditor() {}
    public static boolean mayConfigure(ServerPlayer p,Human h) {
        if(!p.isAlive()||p.isSpectator()||!h.isAlive()||p.level()!=h.level())return false;
        return PatrolRoutePolicy.mayEdit(p.isCreative(),h.hasOwner()&&p.getUUID().equals(h.getOwnerUUID()),
                OrderDistances.context(h).constrained());
    }
    static boolean holding(Player p) {
        return p.getMainHandItem().getItem() instanceof PatrolCommandBatonItem
                ||p.getOffhandItem().getItem() instanceof PatrolCommandBatonItem;
    }
    private static void message(ServerPlayer p,String key,Object... args){p.displayClientMessage(Component.translatable("patrol_tool."+key,args),true);}
    public static void human(ServerPlayer p,Human h,boolean undo) {
        if(undo){undo(p);return;}
        if(!mayConfigure(p,h)||p.distanceToSqr(h)>64){message(p,"denied");return;}
        Draft d=DRAFTS.get(p);
        if(d==null||!d.human.equals(h.getUUID())){
            DRAFTS.put(p,new Draft(h));preview(p);message(p,"selected",h.getDisplayName());return;
        }
        long now=p.serverLevel().getGameTime();if(d.lastAction==now)return;d.lastAction=now;
        if(d.revision!=PatrolRoutes.revision(h)){DRAFTS.remove(p);message(p,"changed");return;}
        if(d.points.size()<2){message(p,"need_points");return;}
        try{PatrolRoutes.install(h,d.points);clear(p,false);message(p,"saved",PatrolRoutes.waits(h).size(),
                Component.translatable(PatrolRoutePolicy.closed(d.points.stream().map(PatrolRoutePolicy.Waypoint::block).toList())?"patrol_tool.loop":"patrol_tool.ping_pong"));}
        catch(IllegalArgumentException invalid){message(p,"invalid");}
    }
    public static void block(ServerPlayer p,BlockPos clicked) {
        if(p.isShiftKeyDown()){undo(p);return;}
        Draft d=DRAFTS.get(p);if(d==null){message(p,"select_first");return;}
        var target=p.serverLevel().getEntity(d.human);
        if(target instanceof Human h&&!mayConfigure(p,h)){clear(p,false);message(p,"denied");return;}
        long now=p.serverLevel().getGameTime();if(d.lastAction==now)return;d.lastAction=now;
        BlockPos point=clicked.above().immutable();
        if(p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(clicked))>64
                ||!p.level().hasChunkAt(point)||p.level().isOutsideBuildHeight(point)
                ||!p.level().getWorldBorder().isWithinBounds(point)
                ||p.level().getBlockState(clicked).getCollisionShape(p.level(),clicked).isEmpty()) {message(p,"invalid");return;}
        double height=p.level().getBlockState(clicked).getCollisionShape(p.level(),clicked).max(net.minecraft.core.Direction.Axis.Y);
        if(!Double.isFinite(height)||height<=0){message(p,"invalid");return;}
        var waypoint=new PatrolRoutePolicy.Waypoint(clicked,new net.minecraft.world.phys.Vec3(clicked.getX()+.5,clicked.getY()+height,clicked.getZ()+.5));
        var feet=waypoint.feet();
        var body=new net.minecraft.world.phys.AABB(feet.x-d.width/2,feet.y+.001,feet.z-d.width/2,
                feet.x+d.width/2,feet.y+d.height-.001,feet.z+d.width/2).deflate(.001);
        BlockPos low=BlockPos.containing(body.minX,body.minY,body.minZ),high=BlockPos.containing(body.maxX,body.maxY,body.maxZ);
        if(!p.level().hasChunksAt(low,high)||!p.level().getWorldBorder().isWithinBounds(low)
                ||!p.level().getWorldBorder().isWithinBounds(high)||p.level().getBlockCollisions(null,body).iterator().hasNext()){
            message(p,"invalid");return;
        }
        int count=d.points.size();boolean closing=count>=3&&clicked.equals(d.points.get(0).block());
        if(count>=OrderDistances.defaults().routeMaxPoints()&&!closing){message(p,"full",OrderDistances.defaults().routeMaxPoints());return;}
        if(count>OrderDistances.defaults().routeMaxPoints()){message(p,"full",OrderDistances.defaults().routeMaxPoints());return;}
        if(count>0&&d.points.get(count-1).block().equals(clicked))return;
        if(count>0&&waypoint.feet().distanceToSqr(d.points.get(count-1).feet())>PatrolRoutePolicy.MAX_LEG*PatrolRoutePolicy.MAX_LEG){message(p,"leg_long",(int)PatrolRoutePolicy.MAX_LEG);return;}
        d.points.add(waypoint);preview(p);BlockPos shown=BlockPos.containing(waypoint.feet());
        message(p,"point",d.points.size(),shown.getX(),shown.getY(),shown.getZ());
    }
    public static void undo(ServerPlayer p) {
        Draft d=DRAFTS.get(p);if(d==null){message(p,"select_first");return;}
        long now=p.serverLevel().getGameTime();if(d.lastAction==now)return;d.lastAction=now;
        if(!d.points.isEmpty())d.points.remove(d.points.size()-1);preview(p);message(p,"undone",d.points.size());
    }
    private static void preview(ServerPlayer p){Draft d=DRAFTS.get(p);if(d!=null)PatrolToolNetwork.draft(p,d.human,d.points);}
    public static void clear(ServerPlayer p,boolean notify) { if(DRAFTS.remove(p)!=null){PatrolToolNetwork.clearPreview(p);if(notify)message(p,"cleared");} }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;
        Draft d=DRAFTS.get(p);if(d==null)return;
        var target=p.serverLevel().getEntity(d.human);
        if(!p.isAlive()||p.isSpectator()||target instanceof Human h&&!mayConfigure(p,h)){clear(p,false);return;}
        long now=p.serverLevel().getGameTime();
        if(holding(p))d.awaySince=-1;
        else if(d.awaySince<0)d.awaySince=now;
        else if(now-d.awaySince>=PatrolRoutePolicy.AWAY_TIMEOUT){clear(p,false);message(p,"expired");}
    }
    @SubscribeEvent public static void attack(AttackEntityEvent e) {
        if(e.getEntity().getMainHandItem().getItem() instanceof PatrolCommandBatonItem){e.setCanceled(true);if(e.getEntity() instanceof ServerPlayer p)clear(p,true);}
    }
    @SubscribeEvent public static void leftBlock(PlayerInteractEvent.LeftClickBlock e) {
        if(e.getHand()==InteractionHand.MAIN_HAND&&e.getItemStack().getItem() instanceof PatrolCommandBatonItem){e.setCanceled(true);if(e.getEntity() instanceof ServerPlayer p)clear(p,true);}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){clear(p,false);PatrolToolNetwork.close(p);}}
    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){clear(p,false);PatrolToolNetwork.close(p);}}
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer p){clear(p,false);PatrolToolNetwork.close(p);}}
    @SubscribeEvent public static void removed(net.minecraftforge.event.entity.EntityLeaveLevelEvent e) {
        if(e.getLevel().isClientSide())return;
        if(e.getEntity() instanceof Human h&&(h.getRemovalReason()==net.minecraft.world.entity.Entity.RemovalReason.KILLED
                ||h.getRemovalReason()==net.minecraft.world.entity.Entity.RemovalReason.DISCARDED))
            for(var entry:new ArrayList<>(DRAFTS.entrySet()))if(entry.getValue().human.equals(h.getUUID()))clear(entry.getKey(),false);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){DRAFTS.clear();PatrolToolNetwork.clearSessions();}
}

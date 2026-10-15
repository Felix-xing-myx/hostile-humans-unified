package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;

/** Bounded server event adapter. Sound is a point-in-time clue, never a combat target. */
public final class HumanAwareness {
    private static final Map<Entity,Map<String,Long>> RECENT=new WeakHashMap<>();
    private static final Map<ServerLevel,ArrayDeque<Notice>> QUEUES=new WeakHashMap<>();
    private static final Map<LivingEntity,Movement> MOVEMENT=new WeakHashMap<>();
    private static final Map<ServerLevel,Counter> DELIVERY_QUERIES=new WeakHashMap<>();
    private static final Map<net.minecraft.server.MinecraftServer,Counter> SERVER_DELIVERY_QUERIES=new WeakHashMap<>();
    private static final class Counter {long tick=-1;int count;void at(long now){if(tick!=now){tick=now;count=0;}}}
    private record Movement(long tick,Vec3 position,double distance,boolean ground) {}
    private static final class Notice {
        final String kind;final UUID source;final Vec3 position;final double radius;final long until,queued;
        List<UUID> receivers;int index;
        Notice(String kind,UUID source,Vec3 position,double radius,long now,int ttl){this.kind=kind;this.source=source;
            this.position=position;this.radius=radius;this.until=now+ttl;this.queued=now;}
    }
    private HumanAwareness(){}
    private static PerceptionConfig c(){return PerceptionConfig.get();}
    public static void investigate(Entity actor,BlockPos position){emit(actor,"block",Vec3.atCenterOf(position));}
    public static void alert(Entity actor,boolean excludeActor){emit(actor,actor instanceof Player?"damage":"block",actor.position());}
    public static void door(Entity actor,BlockPos position){emit(actor,"door",Vec3.atCenterOf(position));}
    public static void dig(Entity actor,BlockPos position){emit(actor,"dig",Vec3.atCenterOf(position));}
    public static void damage(LivingEntity victim){
        if(victim instanceof Player||victim instanceof Human||victim.getType().getCategory()==MobCategory.MONSTER)emit(victim,"damage",victim.position());
    }
    public static void aid(Human victim){emit(victim,"aid",victim.position());}
    public static void explosion(ServerLevel level,Vec3 point){enqueue(level,"explosion",null,point,c().number("hearing.explosion_radius"),c().integer("hearing.urgent_notice_ticks"));}
    private static void emit(Entity actor,String kind,Vec3 point){
        if(!(actor.level() instanceof ServerLevel level)||actor instanceof Player p&&(p.isCreative()||p.isSpectator()))return;
        long now=level.getServer().overworld().getGameTime();
        int cooldown=c().integer(kind.equals("aid")?"hearing.aid_cooldown_ticks":kind.equals("dig")?"hearing.dig_cooldown_ticks":
                kind.equals("swim")?"hearing.swim_cooldown_ticks":kind.equals("walk")||kind.equals("run")?"hearing.step_cooldown_ticks":"hearing.environment_cooldown_ticks");
        Map<String,Long> recent=RECENT.computeIfAbsent(actor,k->new HashMap<>());
        if(now-recent.getOrDefault(kind,Long.MIN_VALUE/2)<cooldown)return;recent.put(kind,now);
        double radius=c().number("hearing."+kind+"_radius");
        int ttl=c().integer(kind.equals("aid")?"hearing.urgent_notice_ticks":kind.equals("walk")||kind.equals("run")||kind.equals("swim")?
                "hearing.step_notice_ticks":"hearing.notice_ticks");
        enqueue(level,kind,actor.getUUID(),point,radius,ttl);
    }
    private static void enqueue(ServerLevel level,String kind,UUID source,Vec3 point,double radius,int ttl){
        if(radius<=0)return;ArrayDeque<Notice> q=QUEUES.computeIfAbsent(level,k->new ArrayDeque<>());
        long now=level.getServer().overworld().getGameTime();
        if(q.size()>=512){if(kind.equals("aid"))q.removeFirst();else return;}
        q.addLast(new Notice(kind,source,point,radius,now,ttl));
    }
    public static void flush(Human worker){
        if(!(worker.level() instanceof ServerLevel level))return;ArrayDeque<Notice> q=QUEUES.get(level);if(q==null)return;
        long now=HumanPerception.now(worker);int processed=0;
        while(!q.isEmpty()&&processed++<4){Notice n=q.peekFirst();
            if(now>=n.until||now-n.queued>c().integer("budgets.notice_queue_ticks")){q.removeFirst();continue;}
            if(n.receivers==null){
                Counter local=DELIVERY_QUERIES.computeIfAbsent(level,k->new Counter());local.at(now);
                Counter global=SERVER_DELIVERY_QUERIES.computeIfAbsent(level.getServer(),k->new Counter());global.at(now);
                double fraction=c().number("budgets.event_query_fraction");
                if(local.count>=Math.max(1,(int)(c().integer("budgets.queries_per_level")*fraction))
                        ||global.count>=Math.max(1,(int)(c().integer("budgets.queries_per_server")*fraction)))return;
                if(!PerceptionBudget.claim(worker,"queries",1))return;
                local.count++;global.count++;
                List<Human> list=new ArrayList<>();
                level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Human.class),new AABB(n.position,n.position).inflate(n.radius),
                        h->h.isAlive()&&h.position().distanceToSqr(n.position)<=n.radius*n.radius,list,512);
                n.receivers=list.stream().map(Entity::getUUID).toList();
            }
            while(n.index<n.receivers.size()){
                Entity found=level.getEntity(n.receivers.get(n.index));if(!(found instanceof Human h)){n.index++;continue;}
                Entity actor=n.source==null?null:level.getEntity(n.source);
                boolean step=n.kind.equals("walk")||n.kind.equals("run")||n.kind.equals("swim");
                if(h==actor||step&&(!h.isAlert||actor instanceof LivingEntity e&&HumanRelations.allied(h,e))
                        ||n.kind.equals("aid")&&(!(actor instanceof Human ally)||!HumanRelations.allied(h,ally))){n.index++;continue;}
                if(!PerceptionBudget.claim(h,"notices",1))return;
                HumanPerception.notice(h,n.kind,n.position,n.source,(int)(n.until-now));n.index++;
            }
            q.removeFirst();
        }
    }
    public static void sampleMovement(Human listener,List<LivingEntity> nearby){
        if(!listener.isAlert)return;long now=HumanPerception.now(listener);double max=c().number("hearing.run_radius");
        for(LivingEntity actor:nearby){
            if(actor==listener||actor.distanceToSqr(listener)>max*max)continue;
            if(actor.isSilent()||actor.isShiftKeyDown()||actor instanceof Player p&&(p.isCreative()||p.isSpectator())){
                MOVEMENT.put(actor,new Movement(now,actor.position(),0,actor.onGround()));continue;
            }
            Movement old=MOVEMENT.get(actor);
            if(old!=null&&now-old.tick<c().integer("hearing.step_cooldown_ticks"))continue;
            if(!PerceptionBudget.claim(actor,"sources",1))continue;
            Vec3 point=actor.position();boolean ground=actor.onGround();
            if(old==null){MOVEMENT.put(actor,new Movement(now,point,0,ground));continue;}
            double moved=Math.sqrt(Math.pow(point.x-old.position.x,2)+Math.pow(point.z-old.position.z,2));
            if(moved>8||actor.hurtTime>0){MOVEMENT.put(actor,new Movement(now,point,0,ground));continue;}
            boolean swim=actor.isInWater();String kind=swim?"swim":actor.isSprinting()?"run":"walk";double total=old.distance+moved;
            if((ground||swim)&&total>=c().number("hearing."+kind+"_distance")){emit(actor,kind,point);total=0;}
            else if(ground&&!old.ground){emit(actor,"run",point);total=0;}
            MOVEMENT.put(actor,new Movement(now,point,total,ground));
        }
    }
}

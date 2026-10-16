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
        List<UUID> receivers;int index;final int importance;
        int evaluatedIndex=-1;HearingRelevance.Result evaluated;
        boolean urgent(){return importance>=3;}
        Notice(String kind,UUID source,Vec3 position,double radius,long now,int ttl,int importance){this.kind=kind;this.source=source;
            this.position=position;this.radius=radius;this.until=now+ttl;this.queued=now;this.importance=importance;}
    }
    private HumanAwareness(){}
    private static PerceptionConfig c(){return PerceptionConfig.get();}
    /** Native hostile contracts take precedence; ordinary animals/neutral NPCs are background. */
    public static boolean passiveCreature(Entity actor){
        return actor instanceof LivingEntity && !(actor instanceof Player) && !(actor instanceof Human)
                && !(actor instanceof net.minecraft.world.entity.monster.Enemy)
                && actor.getType().getCategory()!=MobCategory.MONSTER;
    }
    public static void investigate(Entity actor,BlockPos position){emit(actor,"block",Vec3.atCenterOf(position));}
    public static void alert(Entity actor,boolean excludeActor){emit(actor,actor instanceof Player?"damage":"block",actor.position());}
    public static void door(Entity actor,BlockPos position){emit(actor,"door",Vec3.atCenterOf(position));}
    public static void dig(Entity actor,BlockPos position){emit(actor,"dig",Vec3.atCenterOf(position));}
    public static void damage(LivingEntity victim){
        if(victim instanceof Player||victim instanceof Human||victim.getType().getCategory()==MobCategory.MONSTER)emit(victim,"damage",victim.position());
    }
    public static void gunshot(LivingEntity shooter){if(shooter!=null)emit(shooter,"gunshot",shooter.position());}
    public static void aid(Human victim){emit(victim,"aid",victim.position());}
    public static void explosion(ServerLevel level,Vec3 point){enqueue(level,"explosion",null,point,c().number("hearing.explosion_radius"),c().integer("hearing.urgent_notice_ticks"),4);}
    private static void emit(Entity actor,String kind,Vec3 point){
        if(passiveCreature(actor)||!(actor.level() instanceof ServerLevel level)||actor instanceof Player p&&(p.isCreative()||p.isSpectator()))return;
        long now=level.getServer().overworld().getGameTime();
        int cooldown=c().integer(kind.equals("aid")?"hearing.aid_cooldown_ticks":kind.equals("dig")?"hearing.dig_cooldown_ticks":
                kind.equals("gunshot")?"hearing.gunshot_cooldown_ticks":kind.equals("swim")?"hearing.swim_cooldown_ticks":kind.equals("walk")||kind.equals("run")?"hearing.step_cooldown_ticks":"hearing.environment_cooldown_ticks");
        Map<String,Long> recent=RECENT.computeIfAbsent(actor,k->new HashMap<>());
        if(now-recent.getOrDefault(kind,Long.MIN_VALUE/2)<cooldown)return;recent.put(kind,now);
        double radius=c().number("hearing."+kind+"_radius");
        int ttl=c().integer(kind.equals("aid")?"hearing.urgent_notice_ticks":kind.equals("walk")||kind.equals("run")||kind.equals("swim")?
                "hearing.step_notice_ticks":"hearing.notice_ticks");
        enqueue(level,kind,actor.getUUID(),point,radius,ttl,HearingRelevance.importance(actor,kind));
    }
    private static void enqueue(ServerLevel level,String kind,UUID source,Vec3 point,double radius,int ttl,int importance){
        if(radius<=0)return;ArrayDeque<Notice> q=QUEUES.computeIfAbsent(level,k->new ArrayDeque<>());
        long now=level.getServer().overworld().getGameTime();
        if(q.size()>=512){if(q.peekLast().importance>=importance)return;q.removeLast();}
        Notice notice=new Notice(kind,source,point,radius,now,ttl,importance);
        // Priority first, FIFO among equals; interrupted notices keep their receiver cursor.
        ArrayDeque<Notice> tail=new ArrayDeque<>();
        while(!q.isEmpty()&&q.peekLast().importance<importance)tail.addFirst(q.removeLast());
        q.addLast(notice);q.addAll(tail);
    }
    public static void flush(Human worker){
        if(!(worker.level() instanceof ServerLevel level))return;ArrayDeque<Notice> q=QUEUES.get(level);if(q==null)return;
        long now=HumanPerception.now(worker);int processed=0;
        while(!q.isEmpty()&&processed++<4){Notice n=q.peekFirst();
            if(now>=n.until||!n.urgent()&&now-n.queued>c().integer("budgets.notice_queue_ticks")){q.removeFirst();continue;}
            if(n.receivers==null){
                Counter local=DELIVERY_QUERIES.computeIfAbsent(level,k->new Counter());local.at(now);
                Counter global=SERVER_DELIVERY_QUERIES.computeIfAbsent(level.getServer(),k->new Counter());global.at(now);
                double fraction=c().number("budgets.event_query_fraction");
                if(local.count>=Math.max(1,(int)(c().integer("budgets.queries_per_level")*fraction))
                        ||global.count>=Math.max(1,(int)(c().integer("budgets.queries_per_server")*fraction)))return;
                if(!PerceptionBudget.claim(worker,"queries",1,":events"))return;
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
                if(passiveCreature(actor)||h==actor||step&&(actor instanceof LivingEntity e&&HumanRelations.allied(h,e))
                        ||n.kind.equals("aid")&&(!(actor instanceof Human ally)||!HumanRelations.allied(h,ally))){n.index++;continue;}
                if(SoundInvestigationMemory.blocked(h,n.kind,n.position)){n.index++;continue;}
                var relevance=n.evaluatedIndex==n.index?n.evaluated:HearingRelevance.evaluate(h,actor,n.kind,n.position,n.radius,n.importance);
                if(relevance.deferred())return;
                n.evaluatedIndex=n.index;n.evaluated=relevance;
                HumanPerception.soundPolicy(h,n.kind,relevance.importance(),relevance.reason());
                if(relevance.importance()<=0){n.index++;continue;}
                if(!PerceptionBudget.claim(h,"notices",1))return;
                HumanPerception.notice(h,n.kind,n.position,n.source,(int)(n.until-now),relevance.importance());n.index++;
            }
            q.removeFirst();
        }
    }
    /** Filter before bounded selection; distant background mobs never occupy sound slots. */
    static boolean movementCandidate(Human listener,LivingEntity actor){
        if(passiveCreature(actor)||actor==listener||HumanRelations.allied(listener,actor))return false;
        if(actor.distanceToSqr(listener)>Math.pow(movementRadius(),2))return false;
        return HearingRelevance.importance(actor,"walk")>0
                ||actor.distanceToSqr(listener)<=Math.pow(c().number("hearing.background_step_radius"),2)
                &&Math.abs(actor.getY()-listener.getY())<=c().number("hearing.background_vertical");
    }
    /** Oldest source first within each importance class, including before retention limits. */
    static Comparator<LivingEntity> movementOrder(Human listener){
        return Comparator.<LivingEntity>comparingInt(e->HearingRelevance.importance(e,"walk")).reversed()
                .thenComparingLong(e->{Movement previous=MOVEMENT.get(e);return previous==null?Long.MIN_VALUE:previous.tick;})
                .thenComparingDouble(listener::distanceToSqr).thenComparing(Entity::getUUID);
    }
    public static void sampleMovement(Human listener,List<LivingEntity> nearby){
        long now=HumanPerception.now(listener);int sampled=0;
        List<LivingEntity> ordered=new ArrayList<>(nearby);
        ordered.sort(movementOrder(listener));
        for(LivingEntity actor:ordered){
            if(!movementCandidate(listener,actor))continue;
            if(actor.isSilent()||actor.isShiftKeyDown()||actor instanceof Player p&&(p.isCreative()||p.isSpectator())){
                MOVEMENT.put(actor,new Movement(now,actor.position(),0,actor.onGround()));continue;
            }
            Movement old=MOVEMENT.get(actor);
            if(old!=null&&now-old.tick<c().integer("hearing.step_cooldown_ticks"))continue;
            if(sampled>=AdaptiveActivityBudget.sources(listener))break;
            if(!PerceptionBudget.claim(actor,"sources",1))continue;
            sampled++;
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
    public static double movementRadius(){
        return Math.max(c().number("hearing.walk_radius"),Math.max(c().number("hearing.run_radius"),c().number("hearing.swim_radius")));
    }
}

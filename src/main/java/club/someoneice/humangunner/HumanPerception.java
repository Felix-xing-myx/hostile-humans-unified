package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.Config;
import dev.felix.hostilehumans.core.PerceptionPolicy;
import dev.felix.hostilehumans.core.RecognitionEvidence;
import dev.felix.hostilehumans.core.BodyVisibilitySamples;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Server knowledge authority. Callers request observations; only this boundary confirms identities. */
public final class HumanPerception {
    private static final Map<Human,State> STATES=new WeakHashMap<>();
    private static final class Track {
        final UUID id; String name="目标";
        boolean marked; long visual=-1, liveUntil=-1, familiarUntil=-1, positionAt=-1, checked=-1;
        final RecognitionEvidence evidence=new RecognitionEvidence();
        BodyVisibilitySamples.Point bodySample;
        Vec3 position, eye; boolean visible;
        String refresh="", denied="";
        Track(UUID id){this.id=id;}
    }
    static final class Clue {
        final String kind; final UUID source; long created; long until; Vec3 position;
        final int priority; long completed=-1;int attempts;Vec3 pendingPosition;
        Clue(String kind,UUID source,long now,long until,Vec3 position,int priority){
            this.kind=kind;this.source=source;this.created=now;this.until=until;this.position=position;this.priority=priority;
        }
    }
    private record Observe(UUID source,Vec3 point,long until,boolean hurt,boolean familiar) {}
    private record Transition(long tick,String text) {}
    private record Ray(Vec3 from,Vec3 to,boolean clear,long tick) {}
    private static final class State {
        final LinkedHashMap<UUID,Track> tracks=new LinkedHashMap<>();
        final ArrayList<Clue> clues=new ArrayList<>();
        final LinkedHashMap<UUID,Observe> pending=new LinkedHashMap<>();
        final ArrayDeque<Transition> transitions=new ArrayDeque<>();
        final ArrayDeque<Ray> rays=new ArrayDeque<>();
        final LinkedHashMap<UUID,Integer> bodyCursors=new LinkedHashMap<>();
        final LinkedHashSet<UUID> pendingBodies=new LinkedHashSet<>();
        long alertUntil=-1, tick=-1, nextScan, focusStarted, focusLastVisible=-1, directionStarted;
        UUID focus; Vec3 look; boolean focusHurt, protectedFocus;
        int candidateCursor; List<UUID> candidates=List.of(); long nextCandidates;
        String decision="闲置观察",reason="等待视觉或声音证据",lookOwner="自然观察";
        String scanStatus="尚未执行视觉检测";long scanAt=-1;
        UUID owner; String order=""; boolean initialized;
        Clue glancedClue; Vec3 glancedPosition; long nextClueGlance;
        Map<UUID,Long> glanceAt=new HashMap<>();
    }
    private HumanPerception() {}
    private static PerceptionConfig c(){return PerceptionConfig.get();}
    static long now(Human h){return h.getServer()==null?h.level().getGameTime():h.getServer().overworld().getGameTime();}
    private static State state(Human h){return STATES.computeIfAbsent(h,k->new State());}
    public static void clear(Human h){STATES.remove(h);}
    private static void transition(State s,long now,String text){
        Transition last=s.transitions.peekLast(); if(last==null || !last.text.equals(text))s.transitions.addLast(new Transition(now,text));
        while(s.transitions.size()>c().integer("debug.transition_limit"))s.transitions.removeFirst();
        while(!s.transitions.isEmpty()&&now-s.transitions.peekFirst().tick>c().integer("debug.transition_ticks"))s.transitions.removeFirst();
    }
    public static double range(Human h){
        double original=h.getAttributeValue(Attributes.FOLLOW_RANGE);
        if(h.getPersistentData().hasUUID(HumanRelations.FORCED_HOSTILE_TARGET))original=Math.max(0,original-88);
        if(GunSupport.get().isGun(h.getMainHandItem()))original=Math.max(original,GunRangePolicy.forWeapon(h.getMainHandItem()).fireRange());
        return Math.max(c().number("vision.minimum_range"),original);
    }
    private static double halfAngle(double distance,boolean familiar){
        return PerceptionPolicy.halfAngle(distance,familiar,
                c().number("vision.familiar_angle_multiplier"),c().number("vision.maximum_half_angle"),
                c().number("vision.near_distance"),c().number("vision.middle_distance"),c().number("vision.far_distance"),
                c().number("vision.near_full_angle"),c().number("vision.middle_full_angle"),c().number("vision.far_full_angle"));
    }
    private static boolean valid(Human h,LivingEntity e){
        return e!=null && e!=h && e.isAlive()&&!e.isRemoved()&&e.level()==h.level()
                && !(e instanceof Player p &&(p.isCreative()||p.isSpectator()))
                && h.canAttack(e) && !HumanRelations.allied(h,e);
    }
    private static boolean enemy(Human h,LivingEntity e){
        if(!valid(h,e))return false;
        if(e instanceof Player)return !SoldierCombatMode.hasCombatController(h)
                || SoldierCombatMode.isExplicitlyAuthorizedAgainst(h,e);
        return h.isAngryAt(e)||HumanTargeting.isAutonomousPlayerEnemy(h,e)
                || SoldierCombatMode.isExplicitlyAuthorizedAgainst(h,e)
                || h.getLastHurtByMob()==e;
    }
    public static boolean canTarget(Human h,LivingEntity e){
        if(h.level().isClientSide || !valid(h,e))return false;
        State s=STATES.get(h); Track t=s==null?null:s.tracks.get(e.getUUID());
        return t!=null&&t.marked&&PerceptionPolicy.live(now(h),t.liveUntil);
    }
    public static Vec3 knownPosition(Human h,LivingEntity e){
        if(e==null)return null;State s=STATES.get(h);Track t=s==null?null:s.tracks.get(e.getUUID());
        if(t==null||!t.marked||!valid(h,e))return null;
        return PerceptionPolicy.live(now(h),t.liveUntil)?e.position():
                now(h)<t.positionAt+c().integer("memory.last_position_ticks")?t.position:null;
    }
    public static boolean protectedLook(Human h){State s=STATES.get(h);return s!=null&&s.protectedFocus&&s.focus!=null;}
    public static boolean allowCombatLook(Human h,LivingEntity target){
        if(NavigationDebugService.controls(h))return false;
        State s=STATES.get(h);
        return (s==null||!s.protectedFocus||Objects.equals(s.focus,target==null?null:target.getUUID()))&&canTarget(h,target);
    }
    public static boolean observing(Human h){State s=STATES.get(h);return s!=null&&s.look!=null&&!h.isSleeping()&&!h.isNoAi()&&!NavigationDebugService.controls(h);}
    public static void applyLook(Human h){
        State s=STATES.get(h);if(s==null||!observing(h))return;
        h.getLookControl().setLookAt(s.look.x,s.look.y,s.look.z,45,35);
    }
    private static void alert(Human h,State s,String reason){
        s.alertUntil=now(h)+c().integer("observation.alert_ticks");h.isAlert=true;
        transition(s,now(h),reason);
    }
    public static void notice(Human h,String kind,Vec3 position,UUID source,int ttl){
        if(h.level().isClientSide||!h.isAlive()||position==null||!finite(position)||ttl<=0)return;
        State s=state(h);long now=now(h);alert(h,s,"线索："+kind);
        int priority=switch(kind){case "aid","owner","hurt"->4;case "last","beacon"->3;case "explosion"->2;default->1;};
        Clue match=null;
        for(Clue old:s.clues)if(old.kind.equals(kind)&&Objects.equals(old.source,source)
                &&(source!=null||old.position.distanceToSqr(position)<4)){match=old;break;}
        if(match!=null){
            if(match.completed>=0&&now-match.completed<c().integer("investigation.retry_ticks")&&priority<=match.priority)return;
            if(match.completed<0){
                match.until=now+ttl;
                if(match.position.distanceToSqr(position)>Math.pow(c().number("investigation.repath_distance"),2))match.pendingPosition=position;
                return;
            }
            s.clues.remove(match);
        }
        s.clues.add(new Clue(kind,source,now,now+ttl,position,priority));
        s.clues.sort(Comparator.comparingInt((Clue a)->a.priority).reversed().thenComparing(Comparator.comparingLong((Clue a)->a.created).reversed()));
        while(s.clues.size()>c().integer("hearing.maximum_clues"))s.clues.remove(s.clues.size()-1);
    }
    public static void ownerClue(Human h,LivingEntity target){
        if(target!=null&&target.level()==h.level())notice(h,"owner",target.position(),target.getUUID(),c().integer("hearing.notice_ticks"));
    }
    public static void hurt(Human h,DamageSource source,float actual){
        if(h.level().isClientSide||actual<0||!h.isAlive())return;
        State s=state(h);long now=now(h);alert(h,s,"受到真实伤害");
        Entity direct=source.getDirectEntity(), owner=source.getEntity();
        boolean projectileImpact=source.is(DamageTypeTags.IS_PROJECTILE)||(direct!=null&&direct!=owner);
        boolean periodic=!projectileImpact&&(source.is(DamageTypeTags.IS_FIRE)||source.is(DamageTypeTags.IS_DROWNING)
                ||source.is(DamageTypeTags.IS_FALL)||source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)
                ||source.is(net.minecraft.world.damagesource.DamageTypes.WITHER));
        Vec3 direction=null;
        if(source.is(DamageTypeTags.IS_EXPLOSION)&&direct!=null&&direct!=owner)
            direction=direct.position().subtract(h.getEyePosition()).normalize();
        else if(direct!=null&&direct!=owner&&direct.getDeltaMovement().lengthSqr()>1.e-8)
            direction=direct.getDeltaMovement().normalize().scale(-1);
        else if(!projectileImpact&&!periodic&&owner instanceof LivingEntity living&&living!=h)
            direction=living.getEyePosition().subtract(h.getEyePosition()).normalize();
        else if(!projectileImpact&&!periodic&&source.getSourcePosition()!=null)
            direction=source.getSourcePosition().subtract(h.getEyePosition()).normalize();
        if(!(owner instanceof LivingEntity attacker)||!valid(h,attacker)){
            if(direction!=null&&!protectedLook(h)){s.look=h.getEyePosition().add(direction.scale(8));s.directionStarted=now;s.lookOwner="受击方向";}
            return;
        }
        Track t=s.tracks.get(attacker.getUUID()); boolean marked=t!=null&&t.marked&&(now<t.familiarUntil||now<t.liveUntil);
        if(actual>0&&marked&&!periodic&&PerceptionPolicy.live(now,t.liveUntil)){
            t.liveUntil=PerceptionPolicy.hitDeadline(true,now,t.liveUntil,c().integer("memory.dynamic_ticks"));
            t.refresh="本人受击";transition(s,now,"已标记来源受击刷新8秒");
        }
        if(periodic || ((direction==null||direction.lengthSqr()<1.e-8)&&!(marked&&PerceptionPolicy.live(now,t.liveUntil))))return;
        Vec3 point=marked&&PerceptionPolicy.live(now,t.liveUntil)?attacker.getEyePosition():h.getEyePosition().add(direction.scale(12));
        if(!s.glanceAt.containsKey(attacker.getUUID())||now-s.glanceAt.get(attacker.getUUID())>=c().integer("observation.look_cooldown_ticks")){
            s.glanceAt.put(attacker.getUUID(),now);
            s.pending.put(attacker.getUUID(),new Observe(attacker.getUUID(),point,
                    now+(marked?c().integer("memory.dynamic_ticks"):c().integer("observation.unknown_notice_ticks")),true,marked));
            trimQueue(s);if(!protectedLook(h))startNext(h,s,now);
        }
        if(actual>0)HumanAwareness.aid(h);
    }
    private static void trimQueue(State s){while(s.pending.size()>c().integer("observation.queue_size")){
        UUID last=null;for(UUID id:s.pending.keySet())last=id;s.pending.remove(last);
    }}
    private static void startNext(Human h,State s,long now){
        if(s.focus!=null)return;
        var it=s.pending.values().iterator();while(it.hasNext()){
            Observe o=it.next();it.remove();if(now>=o.until)continue;
            s.focus=o.source;s.look=o.point;s.focusHurt=o.hurt;s.protectedFocus=!o.familiar;
            s.focusStarted=now;s.focusLastVisible=-1;s.directionStarted=now;
            s.lookOwner=o.familiar?"已标记来源回看":"首次识别";s.reason="观察来袭方向";break;
        }
    }
    private static Track track(State s,UUID id){
        Track existing=s.tracks.get(id);if(existing!=null)return existing;
        if(s.tracks.size()>=c().integer("memory.maximum_targets")){
            UUID drop=null;for(Track t:s.tracks.values())if(!Objects.equals(t.id,s.focus)&&!t.evidence.active()&&(!t.marked||t.liveUntil<=s.tick)){drop=t.id;break;}
            if(drop==null)return null;s.tracks.remove(drop);
        }
        Track t=new Track(id);s.tracks.put(id,t);return t;
    }
    private static void release(State s,String reason){s.focus=null;s.look=null;s.protectedFocus=false;s.focusHurt=false;s.reason=reason;s.lookOwner="自然观察";}
    public static void tick(Human h){
        if(h.level().isClientSide||!(h.level() instanceof ServerLevel level))return;
        State s=state(h);long now=now(h);if(s.tick==now)return;s.tick=now;
        UUID owner=HumanRelations.effectiveOwner(h);String order=SoldierOrder.get(h).name();
        if(s.initialized&&(!Objects.equals(s.owner,owner)||!s.order.equals(order))){
            s.tracks.clear();s.clues.clear();s.pending.clear();s.bodyCursors.clear();s.pendingBodies.clear();release(s,"控制关系或命令改变");h.setTarget(null);
            s.glancedClue=null;s.glancedPosition=null;s.nextClueGlance=now;
        }s.owner=owner;s.order=order;s.initialized=true;
        HumanAwareness.flush(h);
        s.clues.removeIf(clue->now>=clue.until);
        for(Clue clue:s.clues)if(clue.pendingPosition!=null&&now-clue.created>=c().integer("investigation.repath_ticks")){
            clue.position=clue.pendingPosition;clue.pendingPosition=null;clue.created=now;
        }
        s.pending.values().removeIf(o->now>=o.until);s.glanceAt.entrySet().removeIf(e->now-e.getValue()>200);
        for(var it=s.tracks.values().iterator();it.hasNext();){Track t=it.next();
            Entity found=level.getEntity(t.id);
            if(!(found instanceof LivingEntity e)||!valid(h,e)){it.remove();continue;}
            if(t.marked&&PerceptionPolicy.live(now,t.liveUntil)){
                t.position=e.position();t.eye=e.getEyePosition();t.positionAt=now;
                if(Objects.equals(s.focus,t.id))s.look=t.eye;
            }else if(t.marked&&t.position!=null&&t.liveUntil==now){
                notice(h,"last",t.position,t.id,c().integer("memory.last_position_ticks"));
                t.visible=false;transition(s,now,"动态位置到期，使用最后位置");
            }
            if(t.marked&&now>=t.familiarUntil&&now>=t.liveUntil&&now>=t.positionAt+c().integer("memory.last_position_ticks"))it.remove();
        }
        LivingEntity current=h.getTarget();
        if(h.toAvoid!=null&&!canTarget(h,h.toAvoid))h.toAvoid=null;
        if(current!=null&&!canTarget(h,current)){
            Track t=s.tracks.get(current.getUUID());if(t!=null&&t.position!=null&&now<t.positionAt+c().integer("memory.last_position_ticks"))
                notice(h,"last",t.position,t.id,(int)(t.positionAt+c().integer("memory.last_position_ticks")-now));
            var expiredPath=h.getNavigation().getPath();
            h.setTarget(null);
            if(h.getTarget()==null&&!NavigationDebugService.controls(h)
                    &&h.getNavigation().getPath()==expiredPath)h.getNavigation().stop();
        }
        h.isAlert=now<s.alertUntil||h.getTarget()!=null;
        if(h.isNoAi()||h.isSleeping()||NavigationDebugService.controls(h)){
            s.decision=NavigationDebugService.controls(h)?"固定点测试接管":"自主AI暂停";s.reason="自主观察和动作暂停";return;
        }
        if(s.focus!=null){
            Entity e=level.getEntity(s.focus);Track t=s.tracks.get(s.focus);
            boolean lost=t!=null&&t.evidence.interrupted(now,c().integer("recognition.break_ticks"));
            boolean timeout=now-s.focusStarted>=(s.focusLastVisible<0?c().integer("observation.direction_ticks"):c().integer("observation.focus_ticks"));
            if(!(e instanceof LivingEntity living)||!valid(h,living)||lost||timeout){
                if(s.focusHurt&&s.look!=null){
                    Vec3 direction=s.look.subtract(h.getEyePosition()).multiply(1,0,1).normalize();
                    if(direction.lengthSqr()>1.e-8)notice(h,"hurt",h.position().add(direction.scale(c().number("investigation.radius"))),s.focus,c().integer("hearing.notice_ticks"));
                }
                if(t!=null)t.evidence.reset();release(s,lost?"遮挡中断识别":"观察失效或超时");
            }
        }
        for(Track t:s.tracks.values())if(t.evidence.interrupted(now,c().integer("recognition.break_ticks"))
                ||t.evidence.timedOut(now,c().integer("observation.focus_ticks")))t.evidence.reset();
        startNext(h,s,now);
        if(s.focus==null&&s.look!=null&&now-s.directionStarted>=c().integer("observation.direction_ticks")){
            s.look=null;s.lookOwner="自然观察";
        }
        reactToClue(h,s,now);
        int interval=c().integer(h.isAlert||s.focus!=null||recognizing(s)>0||h.getTarget()!=null?"budgets.active_interval_ticks":"budgets.idle_interval_ticks");
        if(now>=s.nextCandidates && PerceptionBudget.claim(h,"queries",1)){
            double r=Math.max(range(h),HumanAwareness.movementRadius());List<LivingEntity> nearby=new ArrayList<>();
            level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(LivingEntity.class),
                    h.getBoundingBox().inflate(r),e->e!=h&&e.isAlive(),nearby,c().integer("budgets.candidates")*4);
            HumanAwareness.sampleMovement(h,nearby);
            nearby.sort(Comparator.comparingDouble(h::distanceToSqr));
            s.candidates=nearby.stream().filter(e->enemy(h,e)).limit(c().integer("budgets.candidates")).map(Entity::getUUID).toList();s.nextCandidates=now+interval;
        }
        if(now<s.nextScan){describe(h,s);return;}
        int rays=Math.min(c().integer("budgets.rays_per_human"),Math.min(c().integer("budgets.rays_per_level"),c().integer("budgets.rays_per_server")));
        if(!PerceptionBudget.claim(h,"rays",rays)){
            s.scanStatus="等待视觉检测配额";s.reason=s.scanStatus;
            // A deferred body batch may own this tick's grant. Consume it without
            // demanding a second fast-ray reservation, especially with tiny custom pools.
            int resumed=0;
            for(UUID id:List.copyOf(s.pendingBodies)){
                if(resumed>=rays)break;
                if(level.getEntity(id) instanceof LivingEntity e&&enemy(h,e)){
                    check(h,s,e,now,interval,false);resumed++;
                }else{s.pendingBodies.remove(id);PerceptionBudget.cancelBodyRays(h,id);}
            }
            if(resumed>0)s.nextScan=now+interval;
            describe(h,s);return;
        }
        s.scanStatus="已执行服务器视觉采样";s.scanAt=now;
        s.nextScan=now+interval;
        List<LivingEntity> checks=new ArrayList<>();
        if(s.focus!=null&&level.getEntity(s.focus) instanceof LivingEntity focus)checks.add(focus);
        for(Track t:s.tracks.values())if(t.evidence.active()&&level.getEntity(t.id) instanceof LivingEntity e
                &&!checks.contains(e)&&checks.size()<rays)checks.add(e);
        // Reserve discovery capacity while fewer than two targets are being recognized.
        if(current!=null&&!checks.contains(current)&&checks.size()<rays
                &&(recognizing(s)>=RecognitionEvidence.MAX_CONCURRENT||checks.isEmpty()&&rays>1))checks.add(current);
        int inspected=0;
        while(checks.size()<rays&&inspected++<s.candidates.size()){
            if(s.candidates.isEmpty())break;UUID id=s.candidates.get(Math.floorMod(s.candidateCursor++,s.candidates.size()));
            if(level.getEntity(id) instanceof LivingEntity e&&!checks.contains(e))checks.add(e);
        }
        s.scanStatus=checks.isEmpty()?"本轮无符合敌对许可的视觉候选":"已执行服务器视觉采样："+Math.min(rays,checks.size())+"个候选";
        for(LivingEntity e:checks.subList(0,Math.min(rays,checks.size())))check(h,s,e,now,interval,true);
        if(s.focus==null)startNext(h,s,now);
        describe(h,s);
    }
    private static long recognizing(State s){return s.tracks.values().stream().filter(t->t.evidence.active()).count();}
    private record VisualProbe(Vec3 visiblePoint,boolean complete,String reason) {}
    private static boolean withinVision(Human h,Vec3 point,boolean familiar,double visibility){
        Vec3 delta=point.subtract(h.getEyePosition());double distance=delta.length();
        return distance<=Math.max(2,range(h)*visibility)&&(distance<1.e-4
                ||h.getViewVector(1).normalize().dot(delta.scale(1/distance))>Math.cos(halfAngle(distance,familiar)));
    }
    private static boolean clearVisualRay(Human h,State s,Vec3 point,long now){
        Vec3 eye=h.getEyePosition(),towardEye=eye.subtract(point);
        // Surface samples can lie exactly on the floor/adjacent wall. Test the approach side of the surface.
        Vec3 end=towardEye.lengthSqr()>1.e-8?point.add(towardEye.normalize().scale(1.e-4)):point;
        BlockHitResult hit=h.level().clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,h));
        boolean clear=hit.getType()==HitResult.Type.MISS;
        s.rays.addLast(new Ray(h.getEyePosition(),clear?point:hit.getLocation(),clear,now));
        while(s.rays.size()>8)s.rays.removeFirst();
        return clear;
    }
    private static VisualProbe bodyVisibility(Human h,State s,LivingEntity e,Track track,boolean familiar,long now,boolean fast){
        AABB box=e.getBoundingBox();double visibility=e.getVisibilityPercent(h);
        Vec3 primary=e.getEyePosition();
        if(track!=null&&track.bodySample!=null){
            var f=track.bodySample;
            primary=new Vec3(box.minX+box.getXsize()*f.x(),box.minY+box.getYsize()*f.y(),box.minZ+box.getZsize()*f.z());
        }
        // The ordinary candidate reservation already pays for this one fast ray.
        if(fast&&withinVision(h,primary,familiar,visibility)&&clearVisualRay(h,s,primary,now)){
            s.pendingBodies.remove(e.getUUID());
            PerceptionBudget.cancelBodyRays(h,e.getUUID());
            return new VisualProbe(primary,true,"身体部位无遮挡");
        }
        List<Vec3> samples=new ArrayList<>();
        for(var point:BodyVisibilitySamples.of(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ)){
            Vec3 candidate=new Vec3(point.x(),point.y(),point.z());
            if((!fast||candidate.distanceToSqr(primary)>1.e-8)&&withinVision(h,candidate,familiar,visibility))samples.add(candidate);
        }
        if(samples.isEmpty()){
            s.pendingBodies.remove(e.getUUID());
            PerceptionBudget.cancelBodyRays(h,e.getUUID());
            return new VisualProbe(null,true,"身体未进入有效视野或已采样部位被遮挡");
        }
        int count=Math.min(samples.size(),Math.min(c().integer("budgets.body_rays_per_target"),
                Math.min(c().integer("budgets.rays_per_level"),c().integer("budgets.rays_per_server"))));
        if(!PerceptionBudget.claimBodyRays(h,e.getUUID(),count)){
            s.pendingBodies.add(e.getUUID());
            while(s.pendingBodies.size()>c().integer("budgets.candidates")+c().integer("memory.maximum_targets")){
                UUID drop=s.pendingBodies.iterator().next();s.pendingBodies.remove(drop);PerceptionBudget.cancelBodyRays(h,drop);
            }
            return new VisualProbe(null,false,"等待身体遮挡检测配额");
        }
        s.pendingBodies.remove(e.getUUID());
        s.scanAt=now;
        int start=s.bodyCursors.getOrDefault(e.getUUID(),0);
        s.bodyCursors.put(e.getUUID(),(start+count)%samples.size());
        while(s.bodyCursors.size()>c().integer("budgets.candidates")+c().integer("memory.maximum_targets"))
            s.bodyCursors.remove(s.bodyCursors.keySet().iterator().next());
        for(int i=0;i<count;i++){
            Vec3 point=samples.get(Math.floorMod(start+i,samples.size()));
            if(clearVisualRay(h,s,point,now))return new VisualProbe(point,true,"身体部位无遮挡");
        }
        return new VisualProbe(null,count==samples.size(),count==samples.size()?"身体视野采样均被方块遮挡":"身体采样尚未完成");
    }
    private static BodyVisibilitySamples.Point fraction(AABB box,Vec3 point){
        return new BodyVisibilitySamples.Point(
                Math.max(0,Math.min(1,(point.x-box.minX)/Math.max(1.e-8,box.getXsize()))),
                Math.max(0,Math.min(1,(point.y-box.minY)/Math.max(1.e-8,box.getYsize()))),
                Math.max(0,Math.min(1,(point.z-box.minZ)/Math.max(1.e-8,box.getZsize()))));
    }
    private static void check(Human h,State s,LivingEntity e,long now,int interval,boolean fast){
        if(!enemy(h,e))return;Track t=s.tracks.get(e.getUUID());boolean familiar=t!=null&&t.marked&&(now<t.familiarUntil||now<t.liveUntil);
        VisualProbe probe=bodyVisibility(h,s,e,t,familiar,now,fast);
        if(probe.visiblePoint==null){
            if(t!=null){t.denied=probe.reason;if(probe.complete){t.visible=false;t.evidence.miss(now);}}
            if(!probe.complete){s.scanStatus=probe.reason;s.reason=probe.reason;}
            return;
        }
        double distance=probe.visiblePoint.distanceTo(h.getEyePosition());
        if(t==null)t=track(s,e.getUUID());if(t==null){s.reason="目标记忆容量已满";return;}
        t.visible=true;t.denied=probe.reason;t.name=bounded(e.getName().getString(),64);t.position=e.position();t.eye=probe.visiblePoint;t.positionAt=now;
        t.bodySample=fraction(e.getBoundingBox(),probe.visiblePoint);
        t.checked=now;
        boolean live=t.marked&&PerceptionPolicy.live(now,t.liveUntil);
        if(live){t.visual=now;t.liveUntil=now+c().integer("memory.dynamic_ticks");t.familiarUntil=now+c().integer("memory.familiar_ticks");t.refresh="目视";
            s.alertUntil=now+c().integer("observation.alert_ticks");}
        if(!live&&!t.evidence.active()&&recognizing(s)<RecognitionEvidence.MAX_CONCURRENT)t.evidence.begin(now);
        if(s.focus==null&&!live&&t.evidence.active()){
            s.focus=e.getUUID();s.protectedFocus=!familiar;s.focusHurt=false;s.focusStarted=now;s.focusLastVisible=now;s.lookOwner="视觉识别主观察";
        }
        if(Objects.equals(s.focus,e.getUUID())){
            s.focusLastVisible=now;s.look=probe.visiblePoint;
        }
        if(!live&&t.evidence.active()){
            int required=s.focusHurt&&Objects.equals(s.focus,e.getUUID())?Math.max(c().integer("recognition.minimum_ticks"),c().integer("recognition.hurt_ticks")):
                    PerceptionPolicy.recognition(distance,h.isAlert,familiar,c().number("recognition.base_ticks"),c().number("recognition.ticks_per_block"),
                        c().integer("recognition.maximum_ticks"),c().integer("recognition.minimum_ticks"),c().number("recognition.alert_factor"),
                        c().number("recognition.familiar_factor"),c().number("recognition.combined_factor"));
            if(t.evidence.visible(now,interval,required,c().integer("recognition.break_ticks"))){
                t.evidence.finish();t.marked=true;t.visual=now;t.liveUntil=now+c().integer("memory.dynamic_ticks");
                t.familiarUntil=now+c().integer("memory.familiar_ticks");t.refresh="首次目视确认";
                alert(h,s,"目视确认："+t.name);if(Objects.equals(s.focus,e.getUUID()))release(s,"识别完成");
            }
            s.nextScan=Math.min(s.nextScan,now+c().integer("budgets.active_interval_ticks"));
        }else if(live&&Objects.equals(s.focus,e.getUUID()))release(s,"已标记来源已回看");
        if(s.focus==null){
            for(Track other:s.tracks.values())if(other.evidence.active()&&other.visible){
                s.focus=other.id;s.look=other.eye;s.protectedFocus=!(other.marked&&now<other.familiarUntil);s.focusHurt=false;
                s.focusStarted=now;s.focusLastVisible=now;s.lookOwner="视觉识别主观察";break;
            }
        }
    }
    private static void describe(Human h,State s){
        if(s.focus!=null){s.decision=s.protectedFocus?"识别陌生目标":"回看已标记目标";}
        else if(h.isFleeing){s.decision="撤退与恢复";}
        else if(h.getTarget()!=null){s.decision="处理已确认威胁";if(!s.scanStatus.contains("配额"))s.reason="现有战斗调度";}
        else if(bestClue(h)!=null){s.decision="调查线索";}
        else{s.decision=h.isAlert?"警觉环顾":"闲置观察";}
    }
    public static LivingEntity candidate(Human h){
        if(!(h.level() instanceof ServerLevel level))return null;State s=STATES.get(h);if(s==null)return null;
        LivingEntity current=h.getTarget();if(canTarget(h,current))return current;
        LivingEntity best=null;double distance=Double.MAX_VALUE;
        for(Track t:s.tracks.values())if(t.marked&&PerceptionPolicy.live(now(h),t.liveUntil)&&level.getEntity(t.id) instanceof LivingEntity e&&valid(h,e)){
            if(e instanceof Player&&!SoldierCombatMode.hasCombatController(h)&&!HumanRelations.isForcedHostileTo(h,(Player)e)
                    && !isSelfAttacker(h,e)&&playerLimit(h,(Player)e))continue;
            double d=h.distanceToSqr(e);if(d<distance){best=e;distance=d;}
        }return best;
    }
    private static boolean isSelfAttacker(Human h,LivingEntity e){return h.getLastHurtByMob()==e&&h.tickCount-h.getLastHurtByMobTimestamp()<200;}
    private static boolean playerLimit(Human h,Player p){
        int limit=Config.maxTargeting();if(limit<=0)return true;
        if(!PerceptionBudget.claim(h,"queries",1))return true;
        List<Human> others=new ArrayList<>();p.level().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Human.class),p.getBoundingBox().inflate(15),
                e->e!=h&&e.getTarget()==p,others,limit);return others.size()>=limit;
    }
    static Clue bestClue(Human h){State s=STATES.get(h);if(s==null)return null;long now=now(h);
        return s.clues.stream().filter(a->now<a.until&&a.completed<0).findFirst().orElse(null);
    }
    static void finishClue(Human h,Clue clue,String reason){if(clue!=null)clue.completed=now(h);State s=state(h);s.reason=reason;transition(s,now(h),reason);}
    static void decision(Human h,String decision,String reason){State s=state(h);s.decision=decision;s.reason=reason;}
    static void lookAtClue(Human h,Clue clue){
        State s=state(h);if(s.focus!=null||h.getTarget()!=null||NavigationDebugService.controls(h))return;
        Vec3 point=clue.position.add(0,h.getEyeHeight(),0);
        if(point.distanceToSqr(h.getEyePosition())>1){s.look=point;s.directionStarted=now(h);s.lookOwner="线索调查";}
    }
    private static void reactToClue(Human h,State s,long now){
        if(s.focus!=null)return;
        if(h.getTarget()!=null||h.isFleeing){
            if(s.lookOwner.startsWith("线索")){s.look=null;s.lookOwner="自然观察";}
            return;
        }
        Clue clue=bestClue(h);if(clue==null||now<s.nextClueGlance)return;
        boolean moved=s.glancedPosition==null||s.glancedPosition.distanceToSqr(clue.position)>
                Math.pow(c().number("investigation.repath_distance"),2);
        if(clue==s.glancedClue&&!moved)return;
        // Attention is independent of MOVE: holding-position orders may turn without investigating on foot.
        lookAtClue(h,clue);
        s.glancedClue=clue;s.glancedPosition=clue.position;
        s.nextClueGlance=now+Math.max(c().integer("observation.look_cooldown_ticks"),c().integer("investigation.hold_ticks"));
        s.reason="转向线索："+clue.kind;s.lookOwner="线索观察："+clue.kind;
    }
    private static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}

    public static String actionName(net.minecraft.world.entity.ai.goal.Goal goal) {
        return switch(goal.getClass().getSimpleName()) {
            case "PerceptionLookGoal" -> "目标识别/回看";
            case "InvestigateSoundGoal", "PerceptionInvestigationGoal" -> "线索调查与搜索";
            case "AdaptiveCombatGoal" -> "战术/防御/撤退";
            case "SoldierOrderGoal", "TemporarySupportFollowGoal", "BodyguardMovementGoal" -> "行动指令";
            case "LavaEscapeGoal", "AvoidTNTGoal", "AvoidCreeperGoal", "RunFromTarget" -> "紧急避险";
            case "CombatFoodRecoveryGoal", "CombatConsumableGoal", "IdleRecoveryGoal" -> "恢复用品";
            case "GunnerGoal", "HumanBowAttackGoal", "HumanCrossbowAttackGoal" -> "远程武器控制";
            case "HumanMeleeAttackGoal", "MeleeAttackGoal" -> "近战控制";
            case "ValuableItemPickupGoal" -> "物品拾取";
            case "RandomLookAroundGoal", "HumanLookAtPlayerGoal" -> "自然环顾";
            default -> goal.getClass().getSimpleName();
        };
    }

    public static PerceptionDebugSnapshot snapshot(Human h){
        State s=STATES.get(h);if(s==null)return PerceptionDebugSnapshot.EMPTY;long now=now(h);double r=range(h);
        List<PerceptionDebugSnapshot.Row> rows=new ArrayList<>();List<PerceptionDebugSnapshot.Marker> markers=new ArrayList<>();
        row(rows,"overview","当前决策",s.decision);row(rows,"overview","原因",s.reason);
        row(rows,"overview","警觉剩余",remaining(s.alertUntil,now));row(rows,"actions","LOOK占用",s.lookOwner);
        row(rows,"actions","MOVE状态",NavigationDebugService.controls(h)?"固定点测试":h.isFleeing?"撤退":h.getTarget()!=null?"战斗":h.getNavigation().isDone()?"无活动路径":"命令/调查路径");
        row(rows,"actions","行动指令",SoldierOrder.get(h).name());row(rows,"actions","攻击模式",SoldierCombatMode.get(h).name());
        for(var action:h.humanGunner$actionSnapshot())row(rows,action.group(),action.label(),action.value());
        row(rows,"vision","基础视觉距离",String.format(java.util.Locale.ROOT,"%.1f 格",r));
        row(rows,"vision","完整视场角",String.format(java.util.Locale.ROOT,"近 %.0f° / 中 %.0f° / 远 %.0f°",
                c().number("vision.near_full_angle"),c().number("vision.middle_full_angle"),c().number("vision.far_full_angle")));
        row(rows,"vision","本轮检测",s.scanStatus);row(rows,"vision","最近采样年龄",s.scanAt<0?"暂无":(now-s.scanAt)+"tick");
        row(rows,"targets","待观察来源",s.pending.size()+"/"+c().integer("observation.queue_size"));
        row(rows,"targets","并行视觉识别",recognizing(s)+"/"+RecognitionEvidence.MAX_CONCURRENT);
        int pendingIndex=0;
        for(Observe o:s.pending.values())row(rows,"targets","排队来源 "+(++pendingIndex),
                (o.familiar?"已标记回看":"未标记来袭方向")+" · "+remaining(o.until,now));
        for(Track t:s.tracks.values()){
            if(!t.marked&&!t.evidence.active())continue;
            boolean live=t.marked&&now<t.liveUntil;String stage=t.evidence.active()?"识别中":live?"动态位置有效":"最后位置/旧敌标签";
            row(rows,"targets",t.name,stage+(Objects.equals(t.id,h.getTarget()==null?null:h.getTarget().getUUID())?" · 当前目标":""));
            row(rows,"targets","识别进度",String.format(java.util.Locale.ROOT,"%.0f%% · %s",t.evidence.progress()*100,
                    Objects.equals(t.id,s.focus)?"主观察/控制转头":t.evidence.active()?"视野内并行识别":"已确认记录"));
            row(rows,"targets","最近视觉结果",t.denied+" · 最近可见"+(t.checked<0?"暂无":(now-t.checked)+"tick前"));
            row(rows,"targets","动态剩余/刷新",remaining(t.liveUntil,now)+" / "+t.refresh);
            row(rows,"targets","最后位置/旧敌",remaining(t.positionAt+c().integer("memory.last_position_ticks"),now)+" / "+remaining(t.familiarUntil,now));
            if(h.level() instanceof ServerLevel level && level.getEntity(t.id) instanceof LivingEntity entity)
                row(rows,"targets","攻击许可",valid(h,entity)?"允许；动作仍需有效位置与射界":"不允许");
            if(t.position!=null&&now<t.positionAt+c().integer("memory.last_position_ticks"))
                marker(markers,t.evidence.active()?"recognition":live?t.visible&&now-t.checked<=c().integer("budgets.active_interval_ticks")?"visible":"dynamic":"last",t.name+" · "+stage,t.position,null,.4);
        }
        for(Clue clue:s.clues){row(rows,"clues",clue.kind,"优先级"+clue.priority+" · "+remaining(clue.until,now)+(clue.completed>=0?" · 已调查":""));
            marker(markers,clue.kind.equals("beacon")?"beacon":"sound",clue.kind,clue.position,null,clue.kind.equals("beacon")?c().number("beacon.grid_size")*.5:.4);}
        for(Observe o:s.pending.values())marker(markers,"direction","待观察来向",h.getEyePosition(),o.point,0);
        if(s.look!=null)marker(markers,"direction",s.lookOwner,h.getEyePosition(),s.look,0);
        for(Ray ray:s.rays)if(now-ray.tick<=20)marker(markers,ray.clear?"los":"blocked",(ray.clear?"可见采样":"遮挡采样")+" · "+(now-ray.tick)+"tick前",ray.from,ray.to,0);
        Vec3 view=h.getViewVector(1).normalize();
        for(double d:new double[]{4,c().number("vision.near_distance"),c().number("vision.middle_distance"),c().number("vision.far_distance"),r})if(d<=r){
            double a=halfAngle(d,false);
            double b=halfAngle(d,true);
            marker(markers,"vision_new_ring","陌生目标几何视野",h.getEyePosition().add(view.scale(d*Math.cos(a))),view,d*Math.sin(a));
            marker(markers,"vision_known_ring","旧敌几何视野",h.getEyePosition().add(view.scale(d*Math.cos(b))),view,d*Math.sin(b));
        }
        for(String kind:List.of("queries","rays","notices","sources"))row(rows,"budgets",kind,PerceptionBudget.usage(h,kind));
        for(var row:BeaconReportingService.debugRows(h))if(rows.size()<PerceptionDebugSnapshot.MAX_ROWS)rows.add(row);
        for(Transition t:s.transitions)if(now-t.tick<=c().integer("debug.transition_ticks"))row(rows,"history",(now-t.tick)+"tick前",t.text);
        return new PerceptionDebugSnapshot(now,h.getEyePosition(),view,r,
            halfAngle(r,false),
            halfAngle(r,true),
            h.isAlert?"警觉":"平静",s.decision,s.reason,List.copyOf(rows),List.copyOf(markers));
    }
    private static String remaining(long until,long now){return Math.max(0,until-now)+"tick";}
    private static String bounded(String text,int maximum){int end=Math.min(maximum,text.length());if(end>0&&Character.isHighSurrogate(text.charAt(end-1)))end--;return text.substring(0,end);}
    private static void row(List<PerceptionDebugSnapshot.Row> rows,String group,String label,String value){if(rows.size()<PerceptionDebugSnapshot.MAX_ROWS)rows.add(new PerceptionDebugSnapshot.Row(bounded(group,32),bounded(label,96),bounded(value,256)));}
    private static void marker(List<PerceptionDebugSnapshot.Marker> list,String kind,String label,Vec3 point,Vec3 end,double radius){if(list.size()<PerceptionDebugSnapshot.MAX_MARKERS)list.add(new PerceptionDebugSnapshot.Marker(bounded(kind,32),bounded(label,96),point,end,radius));}
}

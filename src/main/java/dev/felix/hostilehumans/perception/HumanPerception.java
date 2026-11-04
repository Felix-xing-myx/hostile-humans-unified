package dev.felix.hostilehumans.perception;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.perception.NegativeObservationSweep;
import dev.felix.hostilehumans.common.BoundedSelection;

import dev.felix.hostilehumans.behavior.combat.HumanTargetCoordinator;
import dev.felix.hostilehumans.behavior.combat.HumanTargeting;
import dev.felix.hostilehumans.behavior.combat.HumanThreatAssessment;
import dev.felix.hostilehumans.behavior.combat.gun.GunRangePolicy;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedFiringMemory;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedShotClearance;
import dev.felix.hostilehumans.faction.HumanRelations;
import dev.felix.hostilehumans.faction.ability.FactionAbilities;
import dev.felix.hostilehumans.monitor.HumanMonitorControl;
import dev.felix.hostilehumans.navigation.GarrisonReturnNavigation;
import dev.felix.hostilehumans.navigation.HumanNavigationOwnership;
import dev.felix.hostilehumans.navigation.HumanPathFollower;
import dev.felix.hostilehumans.observer.NavigationDebugService;
import dev.felix.hostilehumans.performance.AdaptiveActivityBudget;
import dev.felix.hostilehumans.soldier.SoldierCombatMode;
import dev.felix.hostilehumans.soldier.SoldierOrder;

import dev.felix.hostilehumans.config.Config;
import dev.felix.hostilehumans.perception.PerceptionPolicy;
import dev.felix.hostilehumans.perception.RecognitionEvidence;
import dev.felix.hostilehumans.perception.BodyVisibilitySamples;
import dev.felix.hostilehumans.perception.NearVisionPolicy;
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
        boolean marked; long visual=-1, liveUntil=-1, familiarUntil=-1, positionAt=-1, checked=-1, defenseUntil=-1;
        final RecognitionEvidence evidence=new RecognitionEvidence();
        BodyVisibilitySamples.Point bodySample;
        long lookAfter, frontlineProbe = Long.MIN_VALUE;
        Vec3 position, eye; boolean visible;
        String refresh="", denied="";
        Track(UUID id){this.id=id;}
    }
    static final class Clue {
        final String kind; final UUID source; long created; long until; Vec3 position;
        final int priority; long completed=-1, searchedTicks;int attempts, searchedPoints;Vec3 pendingPosition;
        Clue(String kind,UUID source,long now,long until,Vec3 position,int priority){
            this.kind=kind;this.source=source;this.created=now;this.until=until;this.position=position;this.priority=priority;
        }
    }
    private record Observe(UUID source,Vec3 point,long until,boolean hurt,boolean familiar) {}
    private record Transition(long tick,String text) {}
    private record Ray(Vec3 from,Vec3 to,boolean clear,long tick) {}
    private record TargetCap(long tick,boolean limited) {}
    private static final class State {
        final LinkedHashMap<UUID,Track> tracks=new LinkedHashMap<>();
        final Map<UUID,TargetCap> targetCaps=new HashMap<>();
        final ArrayList<Clue> clues=new ArrayList<>();
        final LinkedHashMap<UUID,Observe> pending=new LinkedHashMap<>();
        final ArrayDeque<Transition> transitions=new ArrayDeque<>();
        final ArrayDeque<Ray> rays=new ArrayDeque<>();
        final LinkedHashMap<UUID,Integer> bodyCursors=new LinkedHashMap<>();
        final LinkedHashMap<UUID,NegativeObservationSweep> bodySweeps=new LinkedHashMap<>();
        final LinkedHashSet<UUID> pendingBodies=new LinkedHashSet<>();
        long reactionHandled=Long.MIN_VALUE;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lootGroupDimension;
        long lootGroupUntil, alertUntil=-1, tick=-1, nextScan, focusStarted, focusLastVisible=-1, directionStarted, nextPriorityFocus;
        UUID focus; Vec3 look; boolean focusHurt, unfamiliarFocus, priorityFocus;
        int candidateCursor,recognitionCursor,bodyResumeCursor,deferredVisualRays; List<UUID> candidates=List.of(); long nextCandidates; boolean stableNeighborhood;
        String decision="闲置观察",reason="等待视觉或声音证据",lookOwner="自然观察";
        String scanStatus="尚未执行视觉检测";long scanAt=-1;
        String soundPolicy="暂无声音输入";int geometrySkipped,occlusionSkipped;long soundGlanceUntil;
        UUID owner; String order="", sharingMode=""; boolean initialized;
        Clue glancedClue; Vec3 glancedPosition,soundGlancePoint; long nextClueGlance;
        long directionProtectedUntil=-1;
        Map<UUID,Long> glanceAt=new HashMap<>();
    }
    private HumanPerception() {}
    private static PerceptionConfig c(){return PerceptionConfig.get();}
    public static long now(Human h){return h.getServer()==null?h.level().getGameTime():h.getServer().overworld().getGameTime();}
    private static State state(Human h){return STATES.computeIfAbsent(h,k->new State());}
    public static void clear(Human h){STATES.remove(h);VisualOcclusionMemory.clear(h);HumanTargetCoordinator.clear(h);HumanTargetSharing.clear(h);}
    private static void transition(State s,long now,String text){
        Transition last=s.transitions.peekLast(); if(last==null || !last.text.equals(text))s.transitions.addLast(new Transition(now,text));
        while(s.transitions.size()>c().integer("debug.transition_limit"))s.transitions.removeFirst();
        while(!s.transitions.isEmpty()&&now-s.transitions.peekFirst().tick>c().integer("debug.transition_ticks"))s.transitions.removeFirst();
    }
    public static double range(Human h){
        double original=h.getAttributeValue(Attributes.FOLLOW_RANGE);
        if(h.getPersistentData().hasUUID(HumanRelations.FORCED_HOSTILE_TARGET))original=Math.max(0,original-88);
        if(GunSupport.get().isGun(h.getMainHandItem()))original=Math.max(original,GunRangePolicy.forWeapon(h, h.getMainHandItem()).fireRange());
        return Math.max(c().number("vision.minimum_range"),original);
    }
    private static double halfAngle(double distance,boolean familiar){
        return PerceptionPolicy.halfAngle(distance,familiar,
                c().number("vision.familiar_angle_multiplier"),c().number("vision.maximum_half_angle"),
                c().number("vision.near_distance"),c().number("vision.middle_distance"),c().number("vision.far_distance"),
                c().number("vision.near_full_angle"),c().number("vision.middle_full_angle"),c().number("vision.far_full_angle"));
    }
    private static boolean observable(Human h,LivingEntity e){
        return e!=null && e!=h && e.isAlive()&&!e.isRemoved()&&e.level()==h.level()
                && !(e instanceof Player p &&(p.isCreative()||p.isSpectator()))
                && !HumanRelations.allied(h,e);
    }
    private static boolean allowsCreatureResponse(Human h,LivingEntity e){
        if(!HumanAwareness.passiveCreature(e))return true;
        State s=STATES.get(h);Track t=s==null?null:s.tracks.get(e.getUUID());long now=now(h);
        return t!=null&&PerceptionPolicy.defensiveCreature(now,t.defenseUntil,t.marked,t.liveUntil,t.familiarUntil);
    }
    private static void recordCreatureDefense(Human h,LivingEntity source){
        if(!HumanAwareness.passiveCreature(source)||!observable(h,source))return;
        Track t=track(state(h),source.getUUID());
        if(t!=null)t.defenseUntil=now(h)+c().integer("memory.familiar_ticks");
    }
    private static boolean allowsCreatureClue(Human h,String kind,UUID source){
        if(source==null||!(h.level() instanceof ServerLevel level)
                ||!(level.getEntity(source) instanceof LivingEntity e)||!HumanAwareness.passiveCreature(e))return true;
        return (kind.equals("hurt")||kind.equals("last")||kind.equals("owner"))&&allowsCreatureResponse(h,e);
    }
    private static boolean valid(Human h,LivingEntity e){
        return observable(h,e)&&allowsCreatureResponse(h,e)&&h.canAttack(e)
                &&!(e instanceof Player p&&p.getItemInHand(p.getUsedItemHand()).is(h.getTameItem()));
    }
    private static boolean enemy(Human h,LivingEntity e){
        if(!observable(h,e)||!allowsCreatureResponse(h,e))return false;
        if(HumanAwareness.passiveCreature(e))return true; // Only a recorded real defense source reaches here.
        if(e instanceof Player)return !SoldierCombatMode.hasCombatController(h)
                || SoldierCombatMode.isExplicitlyAuthorizedAgainst(h,e);
        return h.isAngryAt(e)||HumanTargeting.isAutonomousPlayerEnemy(h,e)
                || SoldierCombatMode.isExplicitlyAuthorizedAgainst(h,e)
                || h.getLastHurtByMob()==e;
    }
    /** Read-only evidence for a death witnessed at the most recent scheduled visual sample; no extra rays. */
    public static boolean recentlySaw(Human observer, java.util.UUID entity) {
        State state = STATES.get(observer);
        Track track = state == null ? null : state.tracks.get(entity);
        long now = now(observer);
        return track != null && track.visible && track.positionAt >= 0
                && now >= track.positionAt && now - track.positionAt <= 20;
    }
    public static boolean canTarget(Human h,LivingEntity e){
        if(h.level().isClientSide || !valid(h,e))return false;
        State s=STATES.get(h); Track t=s==null?null:s.tracks.get(e.getUUID());
        return (t!=null&&t.marked&&PerceptionPolicy.live(now(h),t.liveUntil))||HumanTargetSharing.canTarget(h,e);
    }
    public static List<LivingEntity> knownDynamicTargets(Human h){
        if(!(h.level() instanceof ServerLevel level))return List.of();State s=state(h);
        List<LivingEntity> result=new ArrayList<>();
        for(Track t:s.tracks.values())if(t.marked&&now(h)<t.liveUntil&&level.getEntity(t.id) instanceof LivingEntity e&&observable(h,e)&&allowsCreatureResponse(h,e))result.add(e);
        for(LivingEntity e:HumanTargetSharing.targets(h))if(!result.contains(e))result.add(e);
        return List.copyOf(result);
    }
    public static Vec3 knownPosition(Human h,LivingEntity e){
        if(e==null)return null;State s=STATES.get(h);Track t=s==null?null:s.tracks.get(e.getUUID());
        if(!observable(h,e)||!allowsCreatureResponse(h,e))return null;
        if(t!=null&&t.marked&&PerceptionPolicy.live(now(h),t.liveUntil))return e.position();
        Vec3 shared=HumanTargetSharing.position(h,e,t!=null&&t.marked?t.positionAt:-1);if(shared!=null)return shared;
        return t!=null&&t.marked&&now(h)<t.positionAt+c().integer("memory.last_position_ticks")?t.position:null;
    }
    /** Personal evidence only: relays preserve the original report rather than minting recognition. */
    static boolean personalDynamic(Human h,LivingEntity target){
        State s=STATES.get(h);Track t=s==null||target==null?null:s.tracks.get(target.getUUID());
        return t!=null&&t.marked&&PerceptionPolicy.live(now(h),t.liveUntil)&&sharingAllowed(h,target);
    }
    static boolean sharingAllowed(Human h,LivingEntity target){
        return target!=null&&valid(h,target)&&SoldierCombatMode.allowsTarget(h,target);
    }
    static boolean hasSharingInput(Human h) {
        State state = STATES.get(h);
        return state != null && (!state.tracks.isEmpty() || state.focusHurt || !state.clues.isEmpty()
                || state.look != null && now(h) < state.directionProtectedUntil);
    }

    static HumanTargetSharing.Evidence sharingEvidence(Human h){
        State s=STATES.get(h);if(s==null||!(h.level() instanceof ServerLevel level))return null;
        LivingEntity best=null;double highest=Double.NEGATIVE_INFINITY;
        HumanTargetSharing.Threat threat=HumanTargetSharing.Threat.NONE;
        for(Track t:s.tracks.values())if(level.getEntity(t.id) instanceof LivingEntity target
                &&personalDynamic(h,target)&&!HumanTargetSharing.seen(h,t.id)){
            double intrinsic=HumanThreatAssessment.intrinsic(h,target), priority=HumanThreatAssessment.priority(h,target);
            double rank=intrinsic+priority;
            if(rank>highest){best=target;highest=rank;threat=new HumanTargetSharing.Threat(intrinsic,priority);}
        }
        if(best!=null)return new HumanTargetSharing.Evidence(best.getUUID(),best.position(),true,threat);
        long now=now(h);
        for(Track t:s.tracks.values())if(t.visible&&t.position!=null&&t.checked>=0&&now-t.checked<=20
                &&level.getEntity(t.id) instanceof LivingEntity target&&sharingAllowed(h,target))
            return new HumanTargetSharing.Evidence(t.id,t.position,false);
        if(s.look!=null&&(s.focusHurt||now<s.directionProtectedUntil))
            return new HumanTargetSharing.Evidence(null,s.look,false);
        for(Clue clue:s.clues)if(clue.kind.equals("hurt")&&clue.completed<0&&now<clue.until)
            return new HumanTargetSharing.Evidence(clue.source,clue.position,false);
        return null;
    }
    static boolean groupedLoot(Human human) {
        State state = STATES.get(human);
        return state != null && now(human) < state.lootGroupUntil
                && human.level().dimension().equals(state.lootGroupDimension);
    }

    private static boolean attentionLocked(State s,long now){
        return s.focus!=null||now<s.directionProtectedUntil;
    }
    public static boolean allowCombatLook(Human h,LivingEntity target){
        return !NavigationDebugService.controls(h)&&canTarget(h,target)&&!priorityGlance(h);
    }
    private static boolean priorityGlance(Human h) {
        State s = STATES.get(h);
        return s != null && s.priorityFocus && s.look != null && canTarget(h,h.getTarget())
                && mayInspectPriority(h);
    }
    private static boolean mayInspectPriority(Human h) {
        return h.hurtTime == 0 && !h.isFleeing && !h.hhu$hasPendingMeleeAttack()
                && !h.hhu$shieldDefense().defenseOwnsFacing() && !h.isUsingItem();
    }
    public static Vec3 incomingReactionPoint(Human h){
        State s=STATES.get(h);if(s==null)return null;
        if(now(h)<s.directionProtectedUntil)return s.look;
        if(!s.focusHurt||s.focus==null||now(h)-s.focusStarted>=c().integer("observation.direction_ticks"))return null;
        Track t=s.tracks.get(s.focus);
        return t!=null&&t.marked&&now(h)<t.liveUntil?null:s.look;
    }
    public static boolean incomingReactionPending(Human h){State s=STATES.get(h);return s!=null&&incomingReactionSequence(h)!=s.reactionHandled&&incomingReactionPoint(h)!=null;}
    public static void acknowledgeReaction(Human h){State s=state(h);s.reactionHandled=incomingReactionSequence(h);}
    public static long incomingReactionSequence(Human h){State s=STATES.get(h);return s==null?-1:s.focus==null?s.directionStarted:s.focusStarted;}
    public static void finishLook(Human h){
        if (HumanMonitorControl.controls(h)) return;
        State s=STATES.get(h);
        boolean urgent=s!=null&&(s.focusHurt||now(h)<s.directionProtectedUntil)&&(!canTarget(h,h.getTarget())||h.distanceToSqr(h.getTarget())>36);
        boolean sound=!urgent&&soundGlanceActive(h);
        boolean priority = !urgent && priorityGlance(h);
        HumanAttention.finish(h, sound && !priority ? s.soundGlancePoint : s == null ? null : s.look,
                urgent, sound && !priority, priority);
    }
    private static void alert(Human h,State s,String reason){
        s.alertUntil=now(h)+c().integer("observation.alert_ticks");h.isAlert=true;
        transition(s,now(h),reason);
    }
    static void soundPolicy(Human h,String kind,int importance,String reason) {
        state(h).soundPolicy=kind+" · 重要性"+importance+" · "+reason;
    }
    public static void notice(Human h,String kind,Vec3 position,UUID source,int ttl){
        int priority=switch(kind){case "aid","owner","hurt"->4;case "last","beacon"->3;default->2;};
        notice(h,kind,position,source,ttl,priority);
    }
    static void notice(Human h,String kind,Vec3 position,UUID source,int ttl,int priority){
        if(h.level().isClientSide||!h.isAlive()||position==null||!finite(position)||ttl<=0||priority<2
                ||!allowsCreatureClue(h,kind,source)||SoundInvestigationMemory.blocked(h,kind,position))return;
        State s=state(h);long now=now(h);
        Clue match=null;
        for(Clue old:s.clues)if(old.kind.equals(kind)&&Objects.equals(old.source,source)
                &&(source!=null||old.position.distanceToSqr(position)<4)){match=old;break;}
        if(match!=null){
            if(match.completed>=0&&now-match.completed<c().integer("investigation.retry_ticks")&&priority<=match.priority)return;
            // A background step must not perpetuate a former active attack's priority.
            if(match.completed<0&&priority<match.priority&&now<match.until)return;
            if(match.completed<0&&priority==match.priority){
                alert(h,s,"有效线索："+kind);match.until=now+ttl;
                match.pendingPosition=match.position.distanceToSqr(position)>Math.pow(c().number("investigation.repath_distance"),2)?position:null;
                return;
            }
            match.completed=now;s.clues.remove(match);
        }
        alert(h,s,"有效线索："+kind);
        s.clues.add(new Clue(kind,source,now,now+ttl,position,priority));
        s.clues.sort(Comparator.comparingInt((Clue a)->a.priority).reversed().thenComparing(Comparator.comparingLong((Clue a)->a.created).reversed()));
        while(s.clues.size()>c().integer("hearing.maximum_clues"))s.clues.remove(s.clues.size()-1);
    }
    public static void ownerClue(Human h,LivingEntity target){ownerClue(h,target,false);}
    public static void ownerClue(Human h,LivingEntity target,boolean defendingOwner){
        if(h.level().isClientSide||target==null||target.level()!=h.level())return;
        if(defendingOwner)recordCreatureDefense(h,target);
        notice(h,"owner",target.position(),target.getUUID(),c().integer("hearing.notice_ticks"));
    }
    static boolean soundGlanceActive(Human h) {
        State s=STATES.get(h);
        return s!=null && now(h)<s.soundGlanceUntil && s.soundGlancePoint!=null
                && !s.focusHurt && now(h)>=s.directionProtectedUntil
                && (s.glancedClue == null || detailAllowsClue(h,s.glancedClue))
                && HumanThreatAssessment.maySoundGlance(h,s.soundGlancePoint.subtract(0,h.getEyeHeight(),0),3);
    }
    public static void hurt(Human h,DamageSource source,float actual){
        if(h.level().isClientSide||actual<=0||!h.isAlive())return;
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
        if(actual>0&&!periodic)HumanAwareness.aid(h);
        if(!periodic&&owner instanceof LivingEntity defenderSource)recordCreatureDefense(h,defenderSource);
        if(!(owner instanceof LivingEntity attacker)||!valid(h,attacker)){
            if(direction!=null&&direction.lengthSqr()>1.e-8&&!periodic&&!s.focusHurt&&now>=s.directionProtectedUntil){
                if(s.focus!=null)release(s,"真实受击优先于普通观察");
                s.look=h.getEyePosition().add(direction.scale(8));s.directionStarted=now;
                s.directionProtectedUntil=now+c().integer("observation.direction_ticks");
                s.lookOwner="受击方向";s.reason="真实来袭方向观察，不授予攻击许可";
            }
            return;
        }
        Track t=s.tracks.get(attacker.getUUID()); boolean marked=t!=null&&t.marked&&(now<t.familiarUntil||now<t.liveUntil);
        if(actual>0&&marked&&!periodic&&PerceptionPolicy.live(now,t.liveUntil)){
            t.liveUntil=PerceptionPolicy.hitDeadline(true,now,t.liveUntil,c().integer("memory.dynamic_ticks"));
            t.refresh="本人受击";transition(s,now,"已标记来源受击刷新动态期限");
        }
        if(periodic || ((direction==null||direction.lengthSqr()<1.e-8)&&!(marked&&PerceptionPolicy.live(now,t.liveUntil))))return;
        Vec3 point=marked&&PerceptionPolicy.live(now,t.liveUntil)?attacker.getEyePosition():h.getEyePosition().add(direction.scale(12));
        if(!s.glanceAt.containsKey(attacker.getUUID())||now-s.glanceAt.get(attacker.getUUID())>=c().integer("observation.look_cooldown_ticks")){
            s.glanceAt.put(attacker.getUUID(),now);
            s.pending.put(attacker.getUUID(),new Observe(attacker.getUUID(),point,
                    now+(marked?c().integer("memory.dynamic_ticks"):c().integer("observation.unknown_notice_ticks")),true,marked));
            trimQueue(s);
            if(s.focus!=null&&!s.focusHurt){release(s,"真实受击优先于普通观察");}
            if(!attentionLocked(s,now))startNext(h,s,now);
        }
    }
    private static void trimQueue(State s){while(s.pending.size()>c().integer("observation.queue_size")){
        UUID last=null;for(UUID id:s.pending.keySet())last=id;s.pending.remove(last);
    }}
    private static void startNext(Human h,State s,long now){
        if(s.focus!=null||now<s.directionProtectedUntil)return;
        var it=s.pending.values().iterator();while(it.hasNext()){
            Observe o=it.next();it.remove();if(now>=o.until||!allowsCreatureClue(h,"hurt",o.source))continue;
            s.focus=o.source;s.look=o.point;s.focusHurt=o.hurt;s.unfamiliarFocus=!o.familiar;
            s.focusStarted=now;s.focusLastVisible=-1;s.directionStarted=now;
            Track requested=s.tracks.get(o.source);
            if(o.hurt&&(requested==null||!requested.evidence.active())&&recognizing(s)>=RecognitionEvidence.MAX_CONCURRENT){
                Track pause=null;
                for(Track other:s.tracks.values())if(other.evidence.active()&&!other.id.equals(o.source)
                        &&(pause==null||other.position!=null&&pause.position!=null&&h.position().distanceToSqr(other.position)>h.position().distanceToSqr(pause.position)))pause=other;
                if(pause!=null)pause.evidence.pause();
            }
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
    private static void release(State s,String reason){s.focus=null;s.look=null;s.unfamiliarFocus=false;s.focusHurt=false;s.priorityFocus=false;
        s.directionProtectedUntil=-1;s.reason=reason;s.lookOwner="自然观察";}
    public static void tick(Human h){
        if(h.level().isClientSide||!(h.level() instanceof ServerLevel level))return;
        State s=state(h);long now=now(h);if(s.tick==now)return;s.tick=now;
        UUID owner=HumanRelations.effectiveOwner(h);String order=SoldierOrder.get(h).name();
        if(s.initialized&&(!Objects.equals(s.owner,owner)||!s.order.equals(order))){
            if(!Objects.equals(s.owner,owner)){s.tracks.clear();s.bodyCursors.clear();s.bodySweeps.clear();s.pendingBodies.clear();VisualOcclusionMemory.clear(h);}
            HumanTargetSharing.clear(h);
            s.clues.clear();s.pending.clear();release(s,"控制关系或命令改变，取消旧动作");h.setTarget(null);HumanTargetCoordinator.clear(h);
            s.glancedClue=null;s.glancedPosition=null;s.nextClueGlance=now;
        }s.owner=owner;s.order=order;s.initialized=true;
        String sharingMode=SoldierCombatMode.get(h).name();
        if(!s.sharingMode.isEmpty()&&!s.sharingMode.equals(sharingMode)){
            HumanTargetSharing.clear(h);s.clues.removeIf(clue->clue.kind.equals("shared"));
        }
        s.sharingMode=sharingMode;
        HumanTargetSharing.tick(h);
        HumanAwareness.flush(h);
        s.clues.removeIf(clue->{
            boolean sharedInvalid=clue.kind.equals("shared")&&(!HumanTargetSharing.available(h)
                    ||clue.source!=null&&(!(level.getEntity(clue.source) instanceof LivingEntity source)||!sharingAllowed(h,source)));
            boolean invalid=now>=clue.until||sharedInvalid||!allowsCreatureClue(h,clue.kind,clue.source);
            if(invalid)clue.completed=now; // A running investigation must release this clue too.
            return invalid;
        });
        for(Clue clue:s.clues)if(clue.pendingPosition!=null&&now-clue.created>=c().integer("investigation.repath_ticks")){
            clue.position=clue.pendingPosition;clue.pendingPosition=null;clue.created=now;
        }
        s.pending.values().removeIf(o->now>=o.until);s.glanceAt.entrySet().removeIf(e->now-e.getValue()>200);
        for(var it=s.tracks.values().iterator();it.hasNext();){Track t=it.next();
            Entity found=level.getEntity(t.id);
            if(!(found instanceof LivingEntity e)||!observable(h,e)||!allowsCreatureResponse(h,e)){it.remove();continue;}
            if(t.marked&&PerceptionPolicy.live(now,t.liveUntil)){
                t.position=e.position();t.eye=e.getEyePosition();t.positionAt=now;
                if(Objects.equals(s.focus,t.id))s.look=t.eye;
            }else if(t.marked&&t.position!=null&&t.liveUntil==now&&valid(h,e)){
                notice(h,"last",t.position,t.id,c().integer("memory.last_position_ticks"));
                t.visible=false;transition(s,now,"动态位置到期，使用最后位置");
            }
            if(t.marked&&now>=t.familiarUntil&&now>=t.liveUntil&&now>=t.positionAt+c().integer("memory.last_position_ticks"))it.remove();
        }
        LivingEntity current=h.getTarget();
        if(h.toAvoid!=null&&!canTarget(h,h.toAvoid))h.toAvoid=null;
        if(current!=null&&!HumanMonitorControl.controls(h)&&!canTarget(h,current)){
            Track t=s.tracks.get(current.getUUID());if(t!=null&&t.position!=null&&valid(h,current)&&now<t.positionAt+c().integer("memory.last_position_ticks"))
                notice(h,"last",t.position,t.id,(int)(t.positionAt+c().integer("memory.last_position_ticks")-now));
            var expiredRoute=HumanNavigationOwnership.captureRoute(h);
            var expiredPath=expiredRoute.path();
            HumanTargetCoordinator.select(h,null,"原目标失效，记忆接替");
            if(h.getTarget()==current)h.setTarget(null);
            if(h.getTarget()==null&&!NavigationDebugService.controls(h)
                    &&h.getNavigation().getPath()==expiredPath)HumanNavigationOwnership.stopCapturedRoute(h,expiredRoute);
        }
        h.isAlert=now<s.alertUntil||h.getTarget()!=null;
        if(h.isNoAi()||h.isSleeping()||NavigationDebugService.controls(h)){
            s.decision=NavigationDebugService.controls(h)?"固定点测试接管":"自主AI暂停";s.reason=NavigationDebugService.controls(h)?NavigationDebugService.controlDescription(h):"自主观察和动作暂停";return;
        }
        if(s.focus!=null){
            Entity e=level.getEntity(s.focus);Track t=s.tracks.get(s.focus);
            boolean lost=t!=null&&t.evidence.interrupted(now,c().integer("recognition.break_ticks"));
            boolean timeout=now-s.focusStarted>=(s.priorityFocus?40:s.focusLastVisible<0?c().integer("observation.direction_ticks"):c().integer("observation.focus_ticks"));
            if(!(e instanceof LivingEntity living)||!observable(h,living)||!allowsCreatureResponse(h,living)||lost||timeout){
                if(s.focusHurt&&s.look!=null){
                    Vec3 direction=s.look.subtract(h.getEyePosition()).multiply(1,0,1).normalize();
                    if(direction.lengthSqr()>1.e-8)notice(h,"hurt",h.position().add(direction.scale(c().number("investigation.radius"))),s.focus,c().integer("hearing.notice_ticks"));
                }
                if(t!=null){if(lost||!(e instanceof LivingEntity livingTarget)||!observable(h,livingTarget))t.evidence.reset();
                    else t.lookAfter=now+c().integer("observation.look_cooldown_ticks");}
                release(s,lost?"遮挡中断识别":"观察失效或超时");
            }
        }
        for(Track t:s.tracks.values())if(t.evidence.interrupted(now,c().integer("recognition.break_ticks")))t.evidence.reset();
        startNext(h,s,now);
        if(s.focus==null&&s.look!=null&&now-s.directionStarted>=c().integer("observation.direction_ticks")){
            s.look=null;s.lookOwner="自然观察";
        }
        if (!HumanMonitorControl.controls(h)) reactToClue(h,s,now);
        int baseInterval=c().integer(h.isAlert||s.focus!=null||recognizing(s)>0||h.getTarget()!=null?"budgets.active_interval_ticks":"budgets.idle_interval_ticks");
        int interval=AdaptiveActivityBudget.interval(h,baseInterval,s.focusHurt||now<s.directionProtectedUntil);
        boolean reduced = BattlePerceptionDetail.reduced(h);
        boolean urgentScan=s.focusHurt||now<s.directionProtectedUntil;
        boolean stable = !urgentScan && BattlePerceptionDetail.stableEngagement(h);
        if (s.stableNeighborhood && !stable) {
            s.nextCandidates = Math.min(s.nextCandidates, now);
            s.nextScan = Math.min(s.nextScan, now);
        }
        s.stableNeighborhood = stable;
        if(now>=s.nextCandidates && PerceptionBudget.claim(h,"queries",1,":candidates")){
            boolean measureBattle = BattlePerceptionDetail.measure(h);
            double movementRadius = reduced ? BattlePerceptionDetail.LOCAL_RADIUS : HumanAwareness.movementRadius();
            boolean collectPeers = HumanTargetSharing.readyToBroadcast(h);
            double shareRange = collectPeers ? HumanTargetSharing.range(h) : 0;
            double visionRange = reduced && !urgentScan ? BattlePerceptionDetail.LOCAL_RADIUS : range(h);
            double r=Math.max(shareRange,Math.max(visionRange,movementRadius));
            boolean sampleLootGroup = dev.felix.hostilehumans.inventory.SoldierPickupPolicy.canCollectNow(h);
            if (sampleLootGroup) r = Math.max(r, 12);
            if (measureBattle) r = Math.max(r, BattlePerceptionDetail.LOCAL_RADIUS);
            int cap=AdaptiveActivityBudget.candidates(h,c().integer("budgets.candidates"));
            if (reduced && !urgentScan) cap = Math.min(cap, 12);
            long epoch=now/Math.max(1,interval);
            Comparator<LivingEntity> nearest=Comparator.comparingDouble(h::distanceToSqr);
            Comparator<LivingEntity> rotating=Comparator.comparingLong(e->candidateRank(e.getUUID(),epoch));
            BoundedSelection<LivingEntity> close=new BoundedSelection<>((cap+1)/2,nearest);
            BoundedSelection<LivingEntity> discovery=new BoundedSelection<>(cap,rotating);
            Comparator<Human> friendOrder=Comparator.<Human>comparingDouble(h::distanceToSqr).thenComparing(Entity::getUUID);
            BoundedSelection<Human> friends=new BoundedSelection<>(HumanTargetSharing.peerLimit(h,cap),friendOrder);
            BoundedSelection<LivingEntity> sounds=new BoundedSelection<>(cap*4,HumanAwareness.movementOrder(h));
            int[] localHumans = measureBattle ? new int[2] : null; // All nearby humans and the hostile subset, excluding self.
            int[] nearbyAllies = {0};
            // Stream the loaded spatial section: unrelated entities cannot hide later enemies.
            long neighborhoodSample = BattlePerceptionMetrics.startSample(h);
            BattleNeighborhood.visit(level, h.getBoundingBox().inflate(r), measureBattle && !urgentScan, e->{
                        if(e!=h&&e.isAlive()){
                            if(collectPeers&&e instanceof Human friendly&&HumanTargetSharing.peers(h,friendly,shareRange))friends.offer(friendly);
                            if (sampleLootGroup && nearbyAllies[0] < 4 && e instanceof Human ally
                                    && h.distanceToSqr(ally) <= 144 && HumanRelations.allied(h,ally)) nearbyAllies[0]++;
                            boolean hostile = enemy(h,e);
                            if (hostile && (!reduced || urgentScan
                                    || h.distanceToSqr(e) <= BattlePerceptionDetail.LOCAL_RADIUS_SQUARED)) {
                                close.offer(e); discovery.offer(e);
                            }
                            if (measureBattle && e instanceof Human
                                    && (localHumans[0] < 7 || localHumans[1] < 2)
                                    && h.distanceToSqr(e) <= BattlePerceptionDetail.LOCAL_RADIUS_SQUARED) {
                                localHumans[0]++;
                                if (hostile) localHumans[1]++;
                            }
                            if ((!reduced || BattlePerceptionDetail.closeHuman(h,e)) && HumanAwareness.movementCandidate(h,e)) sounds.offer(e);
                        }
                    });
            BattlePerceptionMetrics.finishSample(h, neighborhoodSample,
                    BattlePerceptionMetrics.Work.NEIGHBORHOOD_SAMPLES, BattlePerceptionMetrics.Work.NEIGHBORHOOD_NS);
            if (sampleLootGroup) {
                s.lootGroupDimension = level.dimension();
                s.lootGroupUntil = nearbyAllies[0] >= 4 ? now + 40 : now;
            }
            if (measureBattle) BattlePerceptionDetail.sample(h, now, localHumans[0], localHumans[1]);
            if (collectPeers) HumanTargetSharing.broadcast(h,friends.values());
            HumanAwareness.sampleMovement(h,sounds.values());
            LinkedHashSet<UUID> chosen=new LinkedHashSet<>();
            for(LivingEntity e:close.values())chosen.add(e.getUUID());
            for(LivingEntity e:discovery.values())if(chosen.size()<cap)chosen.add(e.getUUID());
            s.candidates=List.copyOf(chosen);s.nextCandidates=BattlePerceptionDetail.nextNeighborhood(now,h,interval,stable);
        }
        if(now<s.nextScan){describe(h,s);return;}
        int probeLimit=urgentScan?c().integer("budgets.rays_per_human"):AdaptiveActivityBudget.probes(h,c().integer("budgets.rays_per_human"));
        int rays=Math.min(reduced && !urgentScan ? Math.min(probeLimit, 2) : probeLimit, PerceptionBudget.maxRayBatch(h));
        if (reduced && !urgentScan && s.deferredVisualRays > 0
                && !PerceptionBudget.prepareVisualRetry(h, Math.min(rays, s.deferredVisualRays))) {
            BattlePerceptionMetrics.add(level, BattlePerceptionMetrics.Work.VISION_RETRY_SKIPPED, 1);
            resumeBodies(h,s,level,now,interval,probeLimit);
            describe(h,s);
            return;
        }
        s.deferredVisualRays=0;
        s.geometrySkipped=0;s.occlusionSkipped=0;
        List<LivingEntity> checks=new ArrayList<>();
        LivingEntity main=h.getTarget();
        int candidateCursorBefore=s.candidateCursor, recognitionCursorBefore=s.recognitionCursor;
        long visualRound=Math.floorDiv(now,Math.max(1,interval));
        // In crowded battles, old recognitions otherwise consume every single-ray round.
        // Discovery still pays for actual geometry/LOS and never marks a target by proximity.
        if (PerceptionDiscoveryPolicy.reserveDiscovery(visualRound,rays,urgentScan)
                || !urgentScan && rays >= 2 && (main == null && recognizing(s) == 0
                        || reduced && Math.floorMod(visualRound, 2) == 1)) {
            LivingEntity nearestUnknown=null;
            for(UUID id:s.candidates){
                if(!(level.getEntity(id) instanceof LivingEntity e))continue;
                Track track=s.tracks.get(id);
                if(track!=null&&(track.marked||track.evidence.active()))continue;
                if(nearestUnknown!=null&&h.distanceToSqr(e)>=h.distanceToSqr(nearestUnknown))continue;
                if(eligibleVisual(h,s,e,now))nearestUnknown=e;
            }
            if(nearestUnknown!=null)checks.add(nearestUnknown);
        }
        // Reserve a periodic refresh, not every slot: two recognitions still run in alternating rounds.
        if(checks.size()<rays&&(s.focusHurt||s.priorityFocus)&&s.focus!=null&&level.getEntity(s.focus) instanceof LivingEntity hitFocus&&eligibleVisual(h,s,hitFocus,now))checks.add(hitFocus);
        if(checks.size()<rays&&!checks.contains(main)&&canTarget(h,main)&&eligibleVisual(h,s,main,now)&&PerceptionDiscoveryPolicy.maintainMain(visualRound,rays))checks.add(main);
        List<Track> identifying=s.tracks.values().stream().filter(t->t.evidence.active()).toList();
        for(int i=0;i<identifying.size()&&checks.size()<rays;i++){
            Track t=identifying.get(dev.felix.hostilehumans.perception.PerceptionCoordinationPolicy.nextIndex(s.recognitionCursor++,identifying.size()));
            if(level.getEntity(t.id) instanceof LivingEntity e&&!checks.contains(e)&&eligibleVisual(h,s,e,now))checks.add(e);
        }
        if(s.focus!=null&&level.getEntity(s.focus) instanceof LivingEntity focus&&!checks.contains(focus)&&checks.size()<rays&&eligibleVisual(h,s,focus,now))checks.add(focus);
        int inspected=0;
        while(checks.size()<rays&&inspected++<s.candidates.size()){
            if(s.candidates.isEmpty())break;UUID id=s.candidates.get(Math.floorMod(s.candidateCursor++,s.candidates.size()));
            if(level.getEntity(id) instanceof LivingEntity e&&!checks.contains(e)&&eligibleVisual(h,s,e,now))checks.add(e);
        }
        if(checks.isEmpty()){
            resumeBodies(h,s,level,now,interval,probeLimit);
            PerceptionBudget.cancel(h,"rays","");
            s.nextScan=now+interval;s.scanStatus="本轮无可见几何候选或处于遮挡退避";describe(h,s);return;
        }
        rays=checks.size(); // Reserve only actual eligible work, never an empty/default batch.
        if(!PerceptionBudget.claim(h,"rays",rays)){
            s.deferredVisualRays=rays;
            // A rejected reservation did not execute these candidates. Do not skip them.
            s.candidateCursor=candidateCursorBefore;s.recognitionCursor=recognitionCursorBefore;
            s.scanStatus="等待视觉检测配额";s.reason=s.scanStatus;
            // Body reservations already admitted for this tick may still be usable.
            resumeBodies(h,s,level,now,interval,probeLimit);
            describe(h,s);return;
        }
        s.scanStatus="已执行服务器视觉采样";s.scanAt=now;
        s.nextScan=now+(stable && recognizing(s)==0 ? Math.max(interval, 12) : interval);
        s.scanStatus=checks.isEmpty()?"本轮无符合敌对许可的视觉候选":"已执行服务器视觉采样："+Math.min(rays,checks.size())+"个候选";
        int evidenceInterval=dev.felix.hostilehumans.perception.PerceptionCoordinationPolicy.evidenceInterval(PerceptionDiscoveryPolicy.evidenceInterval(interval,rays),(int)recognizing(s),rays,canTarget(h,main));
        resumeBodies(h,s,level,now,interval,probeLimit);
        for(LivingEntity e:checks.subList(0,Math.min(rays,checks.size())))check(h,s,e,now,evidenceInterval,true);
        if(s.focus==null)startNext(h,s,now);
        describe(h,s);
    }
    private static int resumeBodies(Human h,State s,ServerLevel level,long now,int interval,int limit){
        int resumed=0;List<UUID> bodies=List.copyOf(s.pendingBodies);
        for(int i=0;i<bodies.size()&&resumed<Math.max(1,limit);i++){
            UUID id=bodies.get(Math.floorMod(s.bodyResumeCursor++,bodies.size()));
            if(level.getEntity(id) instanceof LivingEntity e && simplifiedVision(h,s,e,now)) {
                s.pendingBodies.remove(id);
                s.bodySweeps.remove(id);
                PerceptionBudget.cancelBodyRays(h,id);
            } else if(level.getEntity(id) instanceof LivingEntity e&&eligibleVisual(h,s,e,now)){
                check(h,s,e,now,interval,false);resumed++;
            }else{s.pendingBodies.remove(id);PerceptionBudget.cancelBodyRays(h,id);}
        }
        return resumed;
    }
    private static boolean eligibleVisual(Human h,State s,LivingEntity e,long now){
        if(!enemy(h,e))return false;
        Track t=s.tracks.get(e.getUUID());if(t!=null&&t.checked==now)return false;
        boolean familiar=t!=null&&t.marked&&(now<t.familiarUntil||now<t.liveUntil);
        if (simplifiedVision(h,s,e,now)) {
            if (t != null && t.frontlineProbe != Long.MIN_VALUE && now >= t.frontlineProbe && now - t.frontlineProbe < 8) return false;
            return frontlinePoint(h,e,familiar,now) != null;
        }
        var box=e.getBoundingBox();var vision=new VisionFrame(h,e.getVisibilityPercent(h),familiar);long mask=0;int index=0;
        List<Vec3> eligibleSamples=new ArrayList<>();
        for(var sample:BodyVisibilitySamples.of(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ)){
            Vec3 point=new Vec3(sample.x(),sample.y(),sample.z());
            if(vision.contains(point)){eligibleSamples.add(point);mask|=1L<<index;}
            index++;
        }
        Vec3 primary=e.getEyePosition();
        if(t!=null&&t.bodySample!=null){var f=t.bodySample;
            primary=new Vec3(box.minX+box.getXsize()*f.x(),box.minY+box.getYsize()*f.y(),box.minZ+box.getZsize()*f.z());}
        if(vision.contains(primary)){
            if(!eligibleSamples.contains(primary))eligibleSamples.add(primary);
            mask|=1L<<BodyVisibilitySamples.MAX_POINTS;
        }
        if(eligibleSamples.isEmpty()){
            s.geometrySkipped++;if(t!=null){t.visible=false;t.evidence.miss(now);t.denied="身体未进入有效视野";}
            s.pendingBodies.remove(e.getUUID());PerceptionBudget.cancelBodyRays(h,e.getUUID());return false;
        }
        boolean freshHurt=s.focusHurt&&Objects.equals(s.focus,e.getUUID())
                &&(h.hurtTime>0||now-s.focusStarted<4);
        var retry=VisualOcclusionMemory.prepare(h,e,now,freshHurt,eligibleSamples,mask);
        if(retry.reset()){
            NegativeObservationSweep sweep=s.bodySweeps.get(e.getUUID());if(sweep!=null)sweep.reset();
        }
        if(!retry.allowed()){
            if(t!=null){t.visible=false;t.evidence.miss(now);t.denied="完整遮挡缓存：等待复查或已验证实心遮挡";}
            s.occlusionSkipped++;s.pendingBodies.remove(e.getUUID());PerceptionBudget.cancelBodyRays(h,e.getUUID());return false;
        }
        return true;
    }
    private static boolean simplifiedVision(Human h, State s, LivingEntity target, long now) {
        return BattlePerceptionDetail.reduced(h) && now >= s.directionProtectedUntil
                && !(s.focusHurt && Objects.equals(s.focus,target.getUUID()));
    }

    /** Use one of two coarse aim points and never claim a body sweep for this sample. */
    private static Vec3 frontlinePoint(Human h, LivingEntity target, boolean familiar, long now) {
        var eye = target.getEyePosition();
        var center = target.getBoundingBox().getCenter();
        int period = AdaptiveActivityBudget.interval(h,c().integer("budgets.active_interval_ticks"),false);
        boolean preferCenter = (Math.floorDiv(now, period) & 1L) == 0;
        var primary = preferCenter ? center : eye;
        var secondary = preferCenter ? eye : center;
        var vision = new VisionFrame(h, target.getVisibilityPercent(h), familiar);
        if (vision.contains(primary)) return primary;
        return vision.contains(secondary) ? secondary : null;
    }

    private static long candidateRank(UUID id,long epoch){
        long v=id.getMostSignificantBits()^id.getLeastSignificantBits()^(epoch*0x9e3779b97f4a7c15L);
        v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;v=(v^(v>>>27))*0x94d049bb133111ebL;
        return v^(v>>>31);
    }
    private static long recognizing(State s){return s.tracks.values().stream().filter(t->t.evidence.active()).count();}
    private record VisualProbe(Vec3 visiblePoint,boolean complete,String reason) {}
    /** Item discovery shares actual head orientation and the near cylinder, without enemy bonuses. */
    static boolean withinLootVision(Human h,Vec3 point){return new VisionFrame(h,1.0D,false).contains(point);}

    /** Observer geometry and effective range are shared across one body's sample points. */
    private static final class VisionFrame {
        private final Vec3 eye, forward, horizontal;
        private final double x, y, z, maxRangeSquared, near, below, above, nearAngle;
        private final boolean familiar;
        private final double angleMultiplier, angleCap, middle, far, nearFull, middleFull, farFull;

        VisionFrame(Human observer, double visibility, boolean familiar) {
            this.familiar = familiar;
            eye = observer.getEyePosition();
            forward = Vec3.directionFromRotation(observer.getXRot(), observer.getYHeadRot()).normalize();
            horizontal = Vec3.directionFromRotation(0, observer.getYHeadRot());
            AABB body = observer.getBoundingBox();
            x = observer.getX(); y = body.minY; z = observer.getZ();
            double maxRange = Math.max(2, range(observer) * visibility);
            maxRangeSquared = maxRange * maxRange;
            var config = c();
            near = config.number("vision.near_distance");
            below = -config.number("vision.near_below_feet");
            above = body.getYsize() + config.number("vision.near_above_head");
            angleMultiplier = config.number("vision.familiar_angle_multiplier");
            angleCap = config.number("vision.maximum_half_angle");
            middle = config.number("vision.middle_distance");
            far = config.number("vision.far_distance");
            nearFull = config.number("vision.near_full_angle");
            middleFull = config.number("vision.middle_full_angle");
            farFull = config.number("vision.far_full_angle");
            nearAngle = angleAt(near);
        }

        boolean contains(Vec3 point) {
            double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > maxRangeSquared) return false;
            double horizontalX = point.x - x, horizontalZ = point.z - z;
            if (NearVisionPolicy.owns(horizontalX, point.y - y, horizontalZ, near, below, above))
                return NearVisionPolicy.inSector(horizontalX, horizontalZ, horizontal.x, horizontal.z, nearAngle);
            double distance = Math.sqrt(distanceSquared);
            return distance < 1.e-4 || (forward.x * dx + forward.y * dy + forward.z * dz) / distance
                    > Math.cos(angleAt(distance));
        }
        private double angleAt(double distance) {
            return PerceptionPolicy.halfAngle(distance, familiar, angleMultiplier, angleCap,
                    near, middle, far, nearFull, middleFull, farFull);
        }
    }

    private static boolean clearVisualRay(Human h,State s,LivingEntity target,Vec3 point,long now){
        return clearVisualRay(h,s,target,point,now,true);
    }

    private static boolean clearVisualRay(Human h,State s,LivingEntity target,Vec3 point,long now,boolean cacheBlocked){
        Vec3 eye=h.getEyePosition(),towardEye=eye.subtract(point);
        // Surface samples can lie exactly on the floor/adjacent wall. Test the approach side of the surface.
        Vec3 end=towardEye.lengthSqr()>1.e-8?point.add(towardEye.normalize().scale(1.e-4)):point;
        if (!cacheBlocked && h.level() instanceof ServerLevel level)
            BattlePerceptionMetrics.add(level, BattlePerceptionMetrics.Work.VISION_CLIP, 1);
        BlockHitResult hit=h.level().clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,h));
        boolean clear=hit.getType()==HitResult.Type.MISS;
        if(!clear&&cacheBlocked)VisualOcclusionMemory.blockedRay(h,target,hit,point);
        s.rays.addLast(new Ray(h.getEyePosition(),clear?point:hit.getLocation(),clear,now));
        while(s.rays.size()>8)s.rays.removeFirst();
        return clear;
    }
    private static VisualProbe bodyVisibility(Human h,State s,LivingEntity e,Track track,boolean familiar,long now,boolean fast){
        NegativeObservationSweep sweep=s.bodySweeps.computeIfAbsent(e.getUUID(),k->new NegativeObservationSweep());
        while(s.bodySweeps.size()>c().integer("budgets.candidates")+c().integer("memory.maximum_targets"))s.bodySweeps.remove(s.bodySweeps.keySet().iterator().next());
        AABB box=e.getBoundingBox();var vision=new VisionFrame(h,e.getVisibilityPercent(h),familiar);
        Vec3 primary=e.getEyePosition();
        if(track!=null&&track.bodySample!=null){
            var f=track.bodySample;
            primary=new Vec3(box.minX+box.getXsize()*f.x(),box.minY+box.getYsize()*f.y(),box.minZ+box.getZsize()*f.z());
        }
        // The ordinary candidate reservation already pays for this one fast ray.
        if(fast&&vision.contains(primary)&&clearVisualRay(h,s,e,primary,now)){
            s.pendingBodies.remove(e.getUUID());
            PerceptionBudget.cancelBodyRays(h,e.getUUID());
            sweep.reset();return new VisualProbe(primary,true,"身体部位无遮挡");
        }
        List<Vec3> samples=new ArrayList<>();long sampleMask=0;int sampleIndex=0;
        for(var point:BodyVisibilitySamples.of(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ)){
            Vec3 candidate=new Vec3(point.x(),point.y(),point.z());
            if(vision.contains(candidate)){samples.add(candidate);sampleMask|=1L<<sampleIndex;}
            sampleIndex++;
        }
        if(samples.isEmpty()){
            sweep.reset();
            s.pendingBodies.remove(e.getUUID());
            PerceptionBudget.cancelBodyRays(h,e.getUUID());
            return new VisualProbe(null,true,"身体未进入有效视野或已采样部位被遮挡");
        }
        boolean hitProbe=s.focusHurt&&e.getUUID().equals(s.focus)||now<s.directionProtectedUntil;
        int bodyLimit=hitProbe?c().integer("budgets.body_rays_per_target"):AdaptiveActivityBudget.bodyRays(h,c().integer("budgets.body_rays_per_target"));
        int count=Math.min(samples.size(), Math.min(bodyLimit, PerceptionBudget.maxRayBatch(h)));
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
            if(clearVisualRay(h,s,e,point,now)){sweep.reset();return new VisualProbe(point,true,"身体部位无遮挡");}
        }
        boolean complete=sweep.negative(samples.size(),start,count,sampleMask);
        if(complete){
            List<Vec3> cacheSamples=new ArrayList<>(samples);long cacheMask=sampleMask;
            if(vision.contains(primary)){
                if(!cacheSamples.contains(primary))cacheSamples.add(primary);
                cacheMask|=1L<<BodyVisibilitySamples.MAX_POINTS;
            }
            VisualOcclusionMemory.negative(h,e,now,cacheMask,cacheSamples);
        }
        return new VisualProbe(null,complete,complete?"身体视野采样均被方块遮挡":"身体采样尚未完成");
    }
    private static BodyVisibilitySamples.Point fraction(AABB box,Vec3 point){
        return new BodyVisibilitySamples.Point(
                Math.max(0,Math.min(1,(point.x-box.minX)/Math.max(1.e-8,box.getXsize()))),
                Math.max(0,Math.min(1,(point.y-box.minY)/Math.max(1.e-8,box.getYsize()))),
                Math.max(0,Math.min(1,(point.z-box.minZ)/Math.max(1.e-8,box.getZsize()))));
    }
    private static void check(Human h,State s,LivingEntity e,long now,int interval,boolean fast){
        long sample = BattlePerceptionMetrics.startSample(h);
        try {
            checkVisualEvidence(h,s,e,now,interval,fast);
        } finally {
            BattlePerceptionMetrics.finishSample(h, sample,
                    BattlePerceptionMetrics.Work.VISION_SAMPLES, BattlePerceptionMetrics.Work.VISION_NS);
        }
    }

    private static void checkVisualEvidence(Human h,State s,LivingEntity e,long now,int interval,boolean fast){
        if(!enemy(h,e))return;Track t=s.tracks.get(e.getUUID());boolean familiar=t!=null&&t.marked&&(now<t.familiarUntil||now<t.liveUntil);
        boolean simple = simplifiedVision(h,s,e,now);
        VisualProbe probe;
        if (simple) {
            if (!fast) return;
            if (t != null) t.frontlineProbe = now;
            Vec3 point = frontlinePoint(h,e,familiar,now);
            boolean clear = point != null && clearVisualRay(h,s,e,point,now,false);
            probe = new VisualProbe(clear ? point : null, true,
                    clear ? "团战快速目视" : "团战采样点未见");
        } else probe=bodyVisibility(h,s,e,t,familiar,now,fast);
        if(probe.visiblePoint==null){
            if(t!=null){t.denied=probe.reason;if(probe.complete){t.visible=false;t.evidence.miss(now);}}
            if(!probe.complete){s.scanStatus=probe.reason;s.reason=probe.reason;}
            return;
        }
        VisualOcclusionMemory.visible(h,e);
        double distance=probe.visiblePoint.distanceTo(h.getEyePosition());
        if(t==null)t=track(s,e.getUUID());if(t==null){s.reason="目标记忆容量已满";return;}
        t.visible=true;t.denied=probe.reason;t.name=bounded(e.getName().getString(),64);t.position=e.position();t.eye=probe.visiblePoint;t.positionAt=now;
        t.bodySample=fraction(e.getBoundingBox(),probe.visiblePoint);
        t.checked=now;
        if (simple) t.frontlineProbe = now;
        boolean live=t.marked&&PerceptionPolicy.live(now,t.liveUntil);
        if(live){t.visual=now;t.liveUntil=now+c().integer("memory.dynamic_ticks");t.familiarUntil=now+c().integer("memory.familiar_ticks");t.refresh="目视";
            s.alertUntil=now+c().integer("observation.alert_ticks");}
        boolean priority = !live && !s.focusHurt && !s.priorityFocus && now >= s.directionProtectedUntil
                && now >= s.nextPriorityFocus && now >= t.lookAfter && mayInspectPriority(h)
                && HumanThreatAssessment.visiblePriorityInterrupt(h,h.getTarget(),e);
        if (!live && simple && distance <= BattlePerceptionDetail.QUICK_RECOGNITION_RANGE) {
            confirmSight(h,s,t,now,"团战近距离目视确认");
            return;
        }
        if(!live&&!t.evidence.active()&&recognizing(s)>=RecognitionEvidence.MAX_CONCURRENT){
            // Only actual visible evidence may interrupt a distant recognition. Never a radius scan alone.
            Track pause=null;
            for(Track other:s.tracks.values())if(other.evidence.active()&&other.position!=null
                    && !(Objects.equals(other.id,s.focus) && (s.focusHurt || s.priorityFocus))
                    &&(priority || PerceptionDiscoveryPolicy.preemptsRecognition(distance,h.position().distanceTo(other.position),
                    c().number("selection.near_radius"),false))
                    &&(pause==null||h.position().distanceToSqr(other.position)>h.position().distanceToSqr(pause.position)))pause=other;
            if(pause!=null){pause.evidence.pause();if(Objects.equals(pause.id,s.focus))release(s,"已目视近场来源优先识别");}
        }
        if(!live&&!t.evidence.active()&&recognizing(s)<RecognitionEvidence.MAX_CONCURRENT)t.evidence.resume(now);
        if (priority && t.evidence.active()) {
            if (s.focus != null) release(s,"可见高威胁优先识别");
            s.focus=e.getUUID(); s.priorityFocus=true; s.nextPriorityFocus=now+70; s.unfamiliarFocus=!familiar;
            s.focusStarted=now; s.focusLastVisible=now; s.lookOwner="高威胁短时观察";
        }
        if(s.focus==null&&now>=t.lookAfter&&!live&&t.evidence.active()){
            s.focus=e.getUUID();s.unfamiliarFocus=!familiar;s.focusHurt=false;s.focusStarted=now;s.focusLastVisible=now;s.lookOwner="视觉识别主观察";
        }
        if(Objects.equals(s.focus,e.getUUID())){
            s.focusLastVisible=now;s.look=probe.visiblePoint;
        }
        if(!live&&t.evidence.active()){
            int required=s.focusHurt&&Objects.equals(s.focus,e.getUUID())?Math.max(c().integer("recognition.minimum_ticks"),c().integer("recognition.hurt_ticks")):
                    PerceptionPolicy.recognition(distance,h.isAlert,familiar,c().number("recognition.base_ticks"),c().number("recognition.ticks_per_block"),
                        c().integer("recognition.maximum_ticks"),c().integer("recognition.minimum_ticks"),c().number("recognition.alert_factor"),
                        c().number("recognition.familiar_factor"),c().number("recognition.combined_factor"));
            if (simple) required = Math.max(2, (required + 1) / 2);
            if(t.evidence.visible(now,interval,required,c().integer("recognition.break_ticks")))
                confirmSight(h,s,t,now,"首次目视确认");
            s.nextScan=Math.min(s.nextScan,now+AdaptiveActivityBudget.interval(h,c().integer("budgets.active_interval_ticks"),s.focusHurt||now<s.directionProtectedUntil));
        }else if(live&&Objects.equals(s.focus,e.getUUID()))release(s,"已标记来源已回看");
        if(s.focus==null){
            for(Track other:s.tracks.values())if(other.evidence.active()&&other.visible&&now>=other.lookAfter){
                s.focus=other.id;s.look=other.eye;s.unfamiliarFocus=!(other.marked&&now<other.familiarUntil);s.focusHurt=false;
                s.focusStarted=now;s.focusLastVisible=now;s.lookOwner="视觉识别主观察";break;
            }
        }
    }
    private static void confirmSight(Human h, State s, Track track, long now, String reason) {
        track.evidence.finish();
        track.marked = true;
        track.visual = now;
        track.liveUntil = now + c().integer("memory.dynamic_ticks");
        track.familiarUntil = now + c().integer("memory.familiar_ticks");
        track.refresh = reason;
        alert(h,s,"目视确认："+track.name);
        if (Objects.equals(s.focus,track.id)) release(s,"识别完成");
    }

    private static void describe(Human h,State s){
        if(HumanMonitorControl.controls(h)){
            s.decision="玩家接管";s.reason="视觉识别与记忆持续更新；不自主移动、转向或攻击";return;
        }
        if(now(h)<s.directionProtectedUntil){s.decision="回看来袭方向";}
        else if(h.getTarget()!=null){s.decision="处理已确认主目标";s.reason=HumanTargetCoordinator.reason(h);}
        else if(s.focus!=null){s.decision=s.unfamiliarFocus?"识别陌生目标":"回看已标记目标";}
        else if(h.isFleeing){s.decision="撤退与恢复";}
        else if(bestClue(h)!=null){s.decision="调查线索";}
        else{s.decision=h.isAlert?"警觉环顾":"闲置观察";}
    }
    /** Optional tactical reviews can slow down without changing target authorization. */
    public static boolean steadyFrontline(Human h) {
        State s = STATES.get(h);
        return s != null && !s.focusHurt && now(h) >= s.directionProtectedUntil
                && BattlePerceptionDetail.stableEngagement(h);
    }

    public static LivingEntity candidate(Human h){
        if(!(h.level() instanceof ServerLevel level))return null;State s=STATES.get(h);if(s==null)return null;
        LivingEntity current=h.getTarget();
        List<LivingEntity> known=new ArrayList<>();
        for(Track t:s.tracks.values())if(t.marked&&now(h)<t.liveUntil&&level.getEntity(t.id) instanceof LivingEntity e&&valid(h,e))known.add(e);
        for(LivingEntity e:HumanTargetSharing.targets(h))if(!known.contains(e))known.add(e);
        if (known.isEmpty()) return null;
        if (known.size() == 1) {
            LivingEntity only = known.get(0);
            return selectionAllowed(h, only) ? only : null;
        }
        List<RankedTarget> ranked = new ArrayList<>(known.size());
        for (LivingEntity target : known) {
            ranked.add(new RankedTarget(target, HumanThreatAssessment.priorityInterrupt(h, current, target),
                    HumanThreatAssessment.score(h, target), h.distanceToSqr(target)));
        }
        ranked.sort(TARGET_ORDER);
        for (RankedTarget entry : ranked) {
            if (selectionAllowed(h, entry.target())) return entry.target();
        }
        return null;
    }

    private record RankedTarget(LivingEntity target, boolean interrupt, double threat, double distanceSquared) {}

    private static final Comparator<RankedTarget> TARGET_ORDER = Comparator
            .comparing(RankedTarget::interrupt).reversed()
            .thenComparing(Comparator.comparingDouble(RankedTarget::threat).reversed())
            .thenComparingDouble(RankedTarget::distanceSquared)
            .thenComparing(entry -> entry.target().getUUID());

    /** All normal proposals share player crowd admission; identity remains a separate permission. */
    public static boolean selectionAllowed(Human h,LivingEntity e){
        if(!canTarget(h,e))return false;
        return e==h.getTarget()||!(e instanceof Player player)||SoldierCombatMode.hasCombatController(h)
                ||HumanRelations.isForcedHostileTo(h,player)||isSelfAttacker(h,e)||!playerLimit(h,player);
    }
    private static boolean isSelfAttacker(Human h,LivingEntity e){return h.getLastHurtByMob()==e&&h.tickCount-h.getLastHurtByMobTimestamp()<200;}
    private static boolean playerLimit(Human h,Player p){
        int limit=Config.maxTargeting();if(limit<=0)return true;
        State state=state(h);long tick=now(h);
        state.targetCaps.entrySet().removeIf(entry->entry.getValue().tick!=tick);
        TargetCap cached=state.targetCaps.get(p.getUUID());if(cached!=null)return cached.limited;
        if(!PerceptionBudget.claim(h,"queries",1,":target-cap:"+p.getUUID())){
            state.targetCaps.put(p.getUUID(),new TargetCap(tick,true));return true;
        }
        List<Human> others=new ArrayList<>();p.level().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Human.class),p.getBoundingBox().inflate(15),
                e->e!=h&&e.getTarget()==p,others,limit);
        boolean limited=others.size()>=limit;state.targetCaps.put(p.getUUID(),new TargetCap(tick,limited));return limited;
    }
    static Clue bestClue(Human h){return bestClue(h,clue->true);}
    private static Clue bestClue(Human h,java.util.function.Predicate<Clue> eligible){
        State s=STATES.get(h);if(s==null)return null;long now=now(h);
        return s.clues.stream().filter(a->now<a.until&&a.completed<0&&!SoundInvestigationMemory.blocked(h,a.kind,a.position)
                &&detailAllowsClue(h,a)&&allowsCreatureClue(h,a.kind,a.source)&&eligible.test(a)).findFirst().orElse(null);
    }
    static boolean detailAllowsClue(Human h, Clue clue) {
        if (!BattlePerceptionDetail.reduced(h)) return true;
        Entity source = clue.source != null && h.level() instanceof ServerLevel level ? level.getEntity(clue.source) : null;
        return BattlePerceptionDetail.acceptsClue(h, clue.kind, source);
    }

    private static boolean usefulCombatClue(Human h,State s,Clue clue){
        if(!dev.felix.hostilehumans.perception.SoundExclusionMemory.sound(clue.kind))return false;
        if(clue.source!=null){
            LivingEntity current=h.getTarget();
            if(current!=null&&clue.source.equals(current.getUUID()))return false;
            // Known allies firing are not new flank threats. Their actual aid notices remain eligible.
            if(!clue.kind.equals("aid")&&h.level() instanceof ServerLevel level
                    &&level.getEntity(clue.source) instanceof LivingEntity source&&HumanRelations.allied(h,source))return false;
        }
        return (clue!=s.glancedClue||s.glancedPosition==null||s.glancedPosition.distanceToSqr(clue.position)>
                Math.pow(c().number("investigation.repath_distance"),2))
                &&HumanThreatAssessment.maySoundGlance(h,clue.position,clue.priority);
    }
    static void finishClue(Human h,Clue clue,String reason){if(clue!=null)clue.completed=now(h);State s=state(h);s.reason=reason;transition(s,now(h),reason);}
    public static void decision(Human h,String decision,String reason){State s=state(h);s.decision=decision;s.reason=reason;}
    static void lookAtClue(Human h,Clue clue){
        State s=state(h);if(s.focus!=null||h.getTarget()!=null||NavigationDebugService.controls(h))return;
        Vec3 point=clue.position.add(0,h.getEyeHeight(),0);
        if(point.distanceToSqr(h.getEyePosition())>1){s.look=point;s.directionStarted=now(h);s.lookOwner="线索调查";}
    }
    private static void reactToClue(Human h,State s,long now){
        if(s.focusHurt||now<s.directionProtectedUntil||h.isFleeing)return;
        boolean combat=canTarget(h,h.getTarget());
        if(now<s.nextClueGlance)return;
        Clue clue=combat?bestClue(h,candidate->usefulCombatClue(h,s,candidate)):bestClue(h);if(clue==null)return;
        if(combat){
            if(!HumanThreatAssessment.maySoundGlance(h,clue.position,clue.priority))return;
        }else if(s.focus!=null)return;
        boolean moved=s.glancedPosition==null||s.glancedPosition.distanceToSqr(clue.position)>
                Math.pow(c().number("investigation.repath_distance"),2);
        if(clue==s.glancedClue&&!moved)return;
        if(combat){
            s.soundGlancePoint=clue.position.add(0,h.getEyeHeight(),0);
            s.soundGlanceUntil=now+c().integer("attention.sound_glance_ticks");
            s.reason="当前敌人压力较低，辅助回看重要声音："+clue.kind;
        }else {
            // Attention is independent of MOVE: holding-position orders may turn without investigating on foot.
            lookAtClue(h,clue);s.reason="转向线索："+clue.kind;s.lookOwner="线索观察："+clue.kind;
        }
        s.glancedClue=clue;s.glancedPosition=clue.position;
        s.nextClueGlance=now+Math.max(combat?c().integer("attention.sound_glance_cooldown_ticks"):0,
                Math.max(c().integer("observation.look_cooldown_ticks"),c().integer("investigation.hold_ticks")));
    }
    private static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}

    public static String actionName(net.minecraft.world.entity.ai.goal.Goal goal) {
        return switch(goal.getClass().getSimpleName()) {
            case "IncomingHitResponseGoal" -> "真实受击自卫回看";
            case "InvestigateSoundGoal", "PerceptionInvestigationGoal" -> "线索调查与搜索";
            case "AdaptiveCombatGoal" -> "战术/防御/撤退";
            case "ProjectileReactionGoal" -> "可见来袭箭矢防御（未锁定射手）";
            case "PressureReactionGoal" -> "本能高压防御（不依赖锁敌）";
            case "SoldierOrderGoal", "TemporarySupportFollowGoal", "BodyguardMovementGoal" -> "行动指令";
            case "LavaEscapeGoal", "AvoidTNTGoal", "AvoidCreeperGoal", "RunFromTarget" -> "紧急避险";
            case "CombatRecoveryGoal", "CombatConsumableGoal", "IdleRecoveryGoal" -> "恢复用品";
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
        row(rows,"clues","最近听觉分级",s.soundPolicy);
        row(rows,"actions","阵营被动能力",FactionAbilities.describe(h));
        row(rows,"budget","动态活动档位",AdaptiveActivityBudget.describe(h));
        row(rows,"budget","感知细节",BattlePerceptionDetail.reduced(h) ? "团战前线：精简视觉与环境侦查" : "完整");
        row(rows,"overview","警觉剩余",remaining(s.alertUntil,now));row(rows,"actions","观察请求",s.lookOwner);
        row(rows,"actions","实际朝向归属",HumanMonitorControl.controls(h)?"接管玩家视角":HumanAttention.owner(h));
        row(rows,"targets","主攻击目标",h.getTarget()==null?"无":bounded(h.getTarget().getName().getString(),64));
        row(rows,"targets","主目标选择原因",HumanTargetCoordinator.reason(h));
        row(rows,"budgets","候选预筛（最近一轮）","视野外"+s.geometrySkipped+" · 遮挡退避"+s.occlusionSkipped);
        row(rows,"targets","主目标威胁分（最近评估）",HumanThreatAssessment.describe(h,h.getTarget()));
        row(rows,"actions","攻击朝向",h.getTarget()==null?"无主目标":HumanAttention.canAim(h,h.getTarget())?"允许，仍需武器/许可/射界":"移动偏角超过头部限制，暂缓远程出手");
        row(rows,"actions","已有射界采样",RangedShotClearance.debugState(h,h.getTarget()));
        row(rows,"actions","射位路线/记忆",RangedFiringMemory.debugState(h));
        row(rows,"actions","增强路径跟随",HumanPathFollower.describe(h));
        row(rows,"actions","身体/头部角度",String.format(java.util.Locale.ROOT,"%.0f° / %.0f° · 移动单侧限%.0f°",h.yBodyRot,h.getYHeadRot(),HumanAttention.movingHeadLimit(h)));
        row(rows,"actions","匿名来向保护",remaining(s.directionProtectedUntil,now));
        row(rows,"actions","MOVE状态",HumanMonitorControl.controls(h)?"玩家接管":NavigationDebugService.controls(h)?"固定点测试":h.isFleeing?"撤退":h.getTarget()!=null?"战斗":h.getNavigation().isDone()?"无活动路径":"命令/调查路径");
        row(rows,"actions","驻守返岗",GarrisonReturnNavigation.status(h));
        row(rows,"actions","行动指令",SoldierOrder.get(h).name());row(rows,"actions","攻击模式",SoldierCombatMode.get(h).name());
        for(var action:h.hhu$actionSnapshot())row(rows,action.group(),action.label(),action.value());
        row(rows,"vision","基础视觉距离",String.format(java.util.Locale.ROOT,"%.1f 格",r));
        row(rows,"vision","近场柱体",String.format(java.util.Locale.ROOT,"半径 %.1f 格 · 脚下 %.1f / 顶部 %.1f · 不随俯仰",c().number("vision.near_distance"),
                c().number("vision.near_below_feet"),c().number("vision.near_above_head")));
        row(rows,"vision","完整视场角",String.format(java.util.Locale.ROOT,"近 %.0f° / 中 %.0f° / 远 %.0f°",
                c().number("vision.near_full_angle"),c().number("vision.middle_full_angle"),c().number("vision.far_full_angle")));
        row(rows,"vision","本轮检测",s.scanStatus);row(rows,"vision","最近采样年龄",s.scanAt<0?"暂无":(now-s.scanAt)+"tick");
        row(rows,"targets","待观察来源",s.pending.size()+"/"+c().integer("observation.queue_size"));
        row(rows,"targets","并行视觉识别",recognizing(s)+"/"+RecognitionEvidence.MAX_CONCURRENT);
        int pendingIndex=0;
        for(Observe o:s.pending.values())row(rows,"targets","排队来源 "+(++pendingIndex),
                (o.familiar?"已标记回看":"未标记来袭方向")+" · "+remaining(o.until,now));
        for(String kind:List.of("queries","rays","occlusion","notices","sources"))row(rows,"budgets",kind.equals("occlusion")?"遮挡几何复核":kind,PerceptionBudget.usage(h,kind));
        for(Track t:s.tracks.values()){
            if(!t.marked&&!t.evidence.hasEvidence())continue;
            boolean live=t.marked&&now<t.liveUntil;String stage=t.evidence.active()?"识别中":t.evidence.hasEvidence()?"识别暂停，保留证据":live?"动态位置有效":"最后位置/旧敌标签";
            row(rows,"targets",t.name+" ["+t.id.toString().substring(0,8)+"]",stage+(Objects.equals(t.id,h.getTarget()==null?null:h.getTarget().getUUID())?" · 当前目标":"")+String.format(java.util.Locale.ROOT," · %.0f%% · %s",t.evidence.progress()*100,
                    Objects.equals(t.id,s.focus)?"主识别/辅助朝向请求":t.evidence.active()?"视野内并行识别":t.evidence.hasEvidence()?"让位受击或其他识别":"已确认记录"));
            row(rows,"targets","最近视觉结果",t.denied+" · 最近可见"+(t.checked<0?"暂无":(now-t.checked)+"tick前"));
            row(rows,"targets","动态/最后/旧敌",remaining(t.liveUntil,now)+" / "+remaining(t.positionAt+c().integer("memory.last_position_ticks"),now)+" / "+remaining(t.familiarUntil,now)+" · "+t.refresh);
            if(h.level() instanceof ServerLevel level && level.getEntity(t.id) instanceof LivingEntity entity){
                row(rows,"targets","攻击许可",valid(h,entity)?"允许；动作仍需有效位置与射界":"不允许");
                if(live)row(rows,"targets","威胁分（最近评估）",HumanThreatAssessment.describe(h,entity));
            }
            if(t.position!=null&&now<t.positionAt+c().integer("memory.last_position_ticks"))
                marker(markers,t.evidence.active()?"recognition":live?t.visible&&now-t.checked<=c().integer("budgets.active_interval_ticks")?"visible":"dynamic":"last",t.name+" · "+stage,t.position,null,.4);
        }
        row(rows,"clues","声音排除区",SoundInvestigationMemory.describe(h));
        for(Clue clue:s.clues){row(rows,"clues",clue.kind,"优先级"+clue.priority+" · "+remaining(clue.until,now)+(clue.completed>=0?" · 已调查":""));
            marker(markers,clue.kind.equals("beacon")?"beacon":"sound",clue.kind,clue.position,null,clue.kind.equals("beacon")?c().number("beacon.grid_size")*.5:.4);}
        for(Observe o:s.pending.values())marker(markers,"direction","待观察来向",h.getEyePosition(),o.point,0);
        if(s.look!=null)marker(markers,"direction",s.lookOwner,h.getEyePosition(),s.look,0);
        if(now<s.soundGlanceUntil&&s.soundGlancePoint!=null)marker(markers,"direction","重要声音有限回看请求",h.getEyePosition(),s.soundGlancePoint,0);
        for(Ray ray:s.rays)if(now-ray.tick<=20)marker(markers,ray.clear?"los":"blocked",(ray.clear?"可见采样":"遮挡采样")+" · "+(now-ray.tick)+"tick前",ray.from,ray.to,0);
        Vec3 view=Vec3.directionFromRotation(h.getXRot(),h.getYHeadRot()).normalize();
        Vec3 nearHeading=Vec3.directionFromRotation(0,h.getYHeadRot());double near=c().number("vision.near_distance");
        Vec3 base=new Vec3(h.getX(),h.getBoundingBox().minY-c().number("vision.near_below_feet"),h.getZ());
        Vec3 top=new Vec3(h.getX(),h.getBoundingBox().maxY+c().number("vision.near_above_head"),h.getZ());
        for(boolean familiar:new boolean[]{false,true}){
            String kind=familiar?"vision_known_near_":"vision_near_";
            // For near markers only: end=(horizontal heading X, half-angle radians, horizontal heading Z).
            Vec3 shape=new Vec3(nearHeading.x,halfAngle(near,familiar),nearHeading.z);
            marker(markers,kind+"base",familiar?"旧敌近场柱体":"陌生目标近场柱体",base,shape,near);
            marker(markers,kind+"top","近场柱体顶部",top,shape,near);
        }
        for(double d:new LinkedHashSet<>(List.of(c().number("vision.middle_distance"),c().number("vision.far_distance"),r)))if(d>near&&d<=r){
            double a=halfAngle(d,false);
            double b=halfAngle(d,true);
            marker(markers,"vision_new_ring","陌生目标几何视野",h.getEyePosition().add(view.scale(d*Math.cos(a))),view,d*Math.sin(a));
            marker(markers,"vision_known_ring","旧敌几何视野",h.getEyePosition().add(view.scale(d*Math.cos(b))),view,d*Math.sin(b));
        }
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

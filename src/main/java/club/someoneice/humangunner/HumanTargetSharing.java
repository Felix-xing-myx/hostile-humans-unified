package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Bounded, deduplicated report relay; never writes personal recognition or target selection. */
final class HumanTargetSharing {
    static final int COOLDOWN = 200, LOCK_TICKS = 120, LAST_TICKS = 600, MAX_RECEIVERS = 3;
    static final int RELAY_INTERVAL = 20;
    static final double RANGE_SQR = 144;
    private static final String SEND = "hostile_humans_unified:share_send_remaining";
    private static final String CLUE_SEND = "hostile_humans_unified:share_clue_send_remaining";
    private static final String RECEIVE = "hostile_humans_unified:share_receive_remaining";
    private static final Map<Human, LinkedHashMap<UUID, Lease>> INBOX = new WeakHashMap<>();
    private static final Map<Human,Long> RECEIVED_AT = new WeakHashMap<>();
    private static final Map<Human, TargetReportLedger> SEEN = new WeakHashMap<>();
    record Evidence(UUID target, Vec3 position, boolean confirmed) {}
    /** Shared immutable origin and deadline: forwarding never creates a fresh authority. */
    private record Report(UUID origin, Evidence evidence, long created, long positionAt, Threat threat) {}
    record Threat(double intrinsic,double priority) { static final Threat NONE=new Threat(0,0); }
    private static final Map<Human, Report> OUTBOX = new WeakHashMap<>();
    private static final class Lease {
        final UUID sender, target, owner, receiverOwner;
        final String order, mode, receiverOrder, receiverMode;
        final Window window;
        final Report report;
        Vec3 position;long positionAt;
        Lease(Human from, Human to, Report report) {
            this.report=report;Evidence evidence=report.evidence();
            sender=from.getUUID();target=evidence.target();owner=HumanRelations.effectiveOwner(from);
            order=SoldierOrder.get(from).name();mode=SoldierCombatMode.get(from).name();
            receiverOwner=HumanRelations.effectiveOwner(to);receiverOrder=SoldierOrder.get(to).name();receiverMode=SoldierCombatMode.get(to).name();
            position=evidence.position();positionAt=report.positionAt();window=new Window(report.created(),evidence.confirmed());
        }
    }
    /** Single-use lease clock: a broken link freezes permanently, including after range reentry. */
    static final class Window {
        final long until;
        long frozenAt=-1;
        Window(long now,boolean confirmed){until=now+(confirmed?LOCK_TICKS:0);if(!confirmed)frozenAt=now;}
        boolean update(long now,boolean lawfulLink){
            if(frozenAt<0&&(now>=until||!lawfulLink))frozenAt=Math.min(now,until);
            return frozenAt<0;
        }
        boolean retained(long now){return frozenAt<0||now-frozenAt<LAST_TICKS;}
    }
    static int nextCooldown(int remaining){return Math.max(0,Math.min(COOLDOWN,remaining)-1);}
    private HumanTargetSharing() {}
    static boolean available(Human human){
        return human.isAlive()&&!human.isRemoved()&&!human.isNoAi()&&!human.isSleeping()
                &&!NavigationDebugService.controls(human)&&!HumanInventorySession.isEditing(human);
    }
    static boolean peers(Human a, Human b) {
        return a!=b&&available(a)&&available(b)
                &&a.level()==b.level()&&a.distanceToSqr(b)<=RANGE_SQR
                &&HumanFactions.same(a,b)
                &&(!HumanFactions.enabled()||HumanFactions.id(a).equals(HumanFactions.id(b)))
                &&Objects.equals(HumanRelations.effectiveOwner(a),HumanRelations.effectiveOwner(b))
                &&HumanRelations.allied(a,b)&&HumanRelations.allied(b,a);
    }
    /** Counters live in Forge persistent data, decrement only while loaded, and survive unload/reload. */
    static void tick(Human receiver) {
        for(String key:List.of(SEND,RECEIVE,CLUE_SEND)){
            if(key.equals(RECEIVE)&&Objects.equals(RECEIVED_AT.get(receiver),HumanPerception.now(receiver)))continue;
            int remaining=receiver.getPersistentData().getInt(key);
            if(key.equals(SEND))remaining=Math.min(RELAY_INTERVAL,remaining);
            if(remaining>0)receiver.getPersistentData().putInt(key,nextCooldown(remaining));
        }
        if(!available(receiver)){clear(receiver);return;}
        var inbox=INBOX.get(receiver);if(inbox==null)return;
        long now=HumanPerception.now(receiver);
        for(var iterator=inbox.values().iterator();iterator.hasNext();){
            Lease lease=iterator.next();
            LivingEntity target=resolve(receiver,lease.target);
            if(target==null||!receiverContext(receiver,lease)||!HumanPerception.sharingAllowed(receiver,target)){iterator.remove();continue;}
            refresh(receiver,lease,target,now);
            if(!lease.window.retained(now))iterator.remove();
        }
    }
    private static boolean receiverContext(Human receiver,Lease lease){
        return available(receiver)
                &&Objects.equals(lease.receiverOwner,HumanRelations.effectiveOwner(receiver))
                &&lease.receiverOrder.equals(SoldierOrder.get(receiver).name())
                &&lease.receiverMode.equals(SoldierCombatMode.get(receiver).name());
    }
    static void clear(Human human){INBOX.remove(human);OUTBOX.remove(human);} // Intentionally retain cooldowns.
    private static LivingEntity resolve(Human human,UUID id){
        return id!=null&&human.level() instanceof ServerLevel level&&level.getEntity(id) instanceof LivingEntity living
                &&living.isAlive()&&!living.isRemoved()?living:null;
    }
    private static void refresh(Human receiver,Lease lease,LivingEntity target,long now){
        if(lease.window.frozenAt>=0)return; // A failed link cannot thaw itself.
        LivingEntity entity=resolve(receiver,lease.sender);
        boolean legal=entity instanceof Human sender&&peers(sender,receiver)
                &&Objects.equals(lease.owner,HumanRelations.effectiveOwner(sender))
                &&lease.order.equals(SoldierOrder.get(sender).name())&&lease.mode.equals(SoldierCombatMode.get(sender).name())
                &&HumanPerception.sharingAllowed(sender,target);
        if(lease.sender.equals(lease.report.origin()))
            legal=legal&&entity instanceof Human origin&&HumanPerception.personalDynamic(origin,target);
        if(lease.window.update(now,legal)){
            // Only independent personal confirmation permits fresh coordinates; relays keep snapshots.
            if(entity instanceof Human sender&&HumanPerception.personalDynamic(sender,target)){
                lease.position=target.position();lease.positionAt=now;
            }
            return;
        }
        if(lease.window.retained(now))HumanPerception.notice(receiver,"shared",lease.position,lease.target,
                (int)Math.max(0,lease.window.frozenAt+LAST_TICKS-now));
    }
    static boolean canTarget(Human receiver,LivingEntity target){
        Lease lease=lease(receiver,target);return lease!=null&&lease.window.frozenAt<0;
    }
    static Vec3 position(Human receiver,LivingEntity target,long personalPositionAt){
        Lease lease=lease(receiver,target);
        return lease==null||lease.positionAt<=personalPositionAt?null:lease.position;
    }
    private static Lease lease(Human receiver,LivingEntity target){
        if(target==null||!HumanPerception.sharingAllowed(receiver,target))return null;
        var inbox=INBOX.get(receiver);Lease lease=inbox==null?null:inbox.get(target.getUUID());
        if(lease==null)return null;
        if(!receiverContext(receiver,lease)){inbox.remove(target.getUUID());return null;}
        long now=HumanPerception.now(receiver);refresh(receiver,lease,target,now);
        return !lease.window.retained(now)?null:lease;
    }
    static List<LivingEntity> targets(Human receiver){
        var inbox=INBOX.get(receiver);if(inbox==null)return List.of();
        List<LivingEntity> result=new ArrayList<>();
        for(UUID id:List.copyOf(inbox.keySet())){LivingEntity target=resolve(receiver,id);if(canTarget(receiver,target))result.add(target);}
        return result;
    }
    static boolean seen(Human human,UUID target) {
        TargetReportLedger ledger=SEEN.get(human);
        return ledger!=null&&ledger.contains(target,HumanPerception.now(human));
    }
    private static boolean admit(Human human,Report report,long now) {
        return SEEN.computeIfAbsent(human,k->new TargetReportLedger()).admit(
                report.evidence().target(),now,report.created()+COOLDOWN);
    }
    /** A recipient's own distance, recent damage and orders still determine the final score. */
    static Threat threat(Human human,LivingEntity target) {
        Lease lease=lease(human,target);
        return lease!=null&&lease.window.frozenAt<0
                ?lease.report.threat():Threat.NONE;
    }
    private static double rank(Lease lease) { return lease.window.frozenAt<0?rank(lease.report):Double.NEGATIVE_INFINITY; }
    private static double rank(Report report) { return report.threat().intrinsic()+report.threat().priority(); }
    private static boolean hasRecipient(Human sender,List<Human> nearby,Report report) {
        for(Human receiver:nearby)if(peers(sender,receiver)&&!seen(receiver,report.evidence().target()))return true;
        return false;
    }
    private static Report outgoing(Human sender,List<Human> nearby,long now) {
        Report best=OUTBOX.get(sender);
        if(best!=null&&(now>=best.created()+LOCK_TICKS||!sender.getUUID().equals(best.origin())
                ||!HumanPerception.personalDynamic(sender,resolve(sender,best.evidence().target()))))best=null;
        if(best!=null&&!hasRecipient(sender,nearby,best))best=null;
        var inbox=INBOX.get(sender);
        if(inbox!=null)for(Lease lease:inbox.values()){
            LivingEntity target=resolve(sender,lease.target);
            if(target==null||!receiverContext(sender,lease)||!HumanPerception.sharingAllowed(sender,target))continue;
            refresh(sender,lease,target,now);
            if(lease.window.frozenAt<0&&hasRecipient(sender,nearby,lease.report)
                    &&(best==null||rank(lease.report)>rank(best)))
                best=new Report(lease.report.origin(),new Evidence(lease.target,lease.position,true),
                        lease.report.created(),lease.positionAt,lease.report.threat());
        }
        Evidence evidence=HumanPerception.sharingEvidence(sender);
        if(evidence!=null&&evidence.confirmed()){
            LivingEntity target=resolve(sender,evidence.target());
            if(target!=null){
                Report personal=new Report(sender.getUUID(),evidence,now,now,
                        new Threat(HumanThreatAssessment.intrinsic(sender,target),HumanThreatAssessment.priority(sender,target)));
                if(hasRecipient(sender,nearby,personal)&&(best==null||rank(personal)>rank(best))&&admit(sender,personal,now)){
                    OUTBOX.put(sender,personal);best=personal;
                }
            }
        }
        return best;
    }
    /** Invoked only with the existing budgeted candidate query's bounded nearest friendly list. */
    static void broadcast(Human sender,List<Human> nearby){
        if(sender.getPersistentData().getInt(SEND)>0||!available(sender))return;
        long now=HumanPerception.now(sender);Report report=outgoing(sender,nearby,now);
        Evidence evidence=report==null?HumanPerception.sharingEvidence(sender):report.evidence();
        if(evidence==null)return;
        if(report==null&&(sender.getPersistentData().getInt(CLUE_SEND)>0
                ||evidence.target()!=null&&seen(sender,evidence.target())))return;
        int delivered=0;
        for(Human receiver:nearby){
            if(delivered>=MAX_RECEIVERS)break;
            if(!peers(sender,receiver))continue;
            LivingEntity target=resolve(receiver,evidence.target());
            if(target!=null&&!HumanPerception.sharingAllowed(receiver,target))continue;
            if(report!=null){
                if(target==null||now>=report.created()+LOCK_TICKS)continue;
                var inbox=INBOX.computeIfAbsent(receiver,k->new LinkedHashMap<>());
                Lease weakest=null;
                if(inbox.size()>=8&&!inbox.containsKey(evidence.target())){
                    for(Lease entry:inbox.values())
                        if(weakest==null||rank(entry)<rank(weakest))weakest=entry;
                    if(weakest!=null&&rank(report)<=rank(weakest))continue;
                }
                if(!admit(receiver,report,now))continue;
                if(weakest!=null)inbox.remove(weakest.target);
                inbox.put(evidence.target(),new Lease(sender,receiver,report));
            }else{
                // Unconfirmed directions never propagate or grant attack permission.
                if(evidence.confirmed()||receiver.getPersistentData().getInt(RECEIVE)>0)continue;
                receiver.getPersistentData().putInt(RECEIVE,COOLDOWN);RECEIVED_AT.put(receiver,now);
                HumanPerception.notice(receiver,"shared",evidence.position(),evidence.target(),LAST_TICKS);
            }
            delivered++;
        }
        // Polling is bounded even when every neighbour already knows this target.
        sender.getPersistentData().putInt(SEND,RELAY_INTERVAL);
        if(report==null&&delivered>0)sender.getPersistentData().putInt(CLUE_SEND,COOLDOWN);
    }
}

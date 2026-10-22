package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Bounded, one-hop knowledge leases; never writes personal recognition or target selection. */
final class HumanTargetSharing {
    static final int COOLDOWN = 200, LOCK_TICKS = 120, LAST_TICKS = 600, MAX_RECEIVERS = 3;
    static final double RANGE_SQR = 144;
    private static final String SEND = "hostile_humans_unified:share_send_remaining";
    private static final String RECEIVE = "hostile_humans_unified:share_receive_remaining";
    private static final Map<Human, LinkedHashMap<UUID, Lease>> INBOX = new WeakHashMap<>();
    private static final Map<Human,Long> RECEIVED_AT = new WeakHashMap<>();
    record Evidence(UUID target, Vec3 position, boolean confirmed) {}
    private static final class Lease {
        final UUID sender, target, owner, receiverOwner;
        final String order, mode, receiverOrder, receiverMode;
        final Window window;
        Vec3 position;long positionAt;
        Lease(Human from, Human to, Evidence evidence, long now) {
            sender=from.getUUID();target=evidence.target();owner=HumanRelations.effectiveOwner(from);
            order=SoldierOrder.get(from).name();mode=SoldierCombatMode.get(from).name();
            receiverOwner=HumanRelations.effectiveOwner(to);receiverOrder=SoldierOrder.get(to).name();receiverMode=SoldierCombatMode.get(to).name();
            position=evidence.position();positionAt=now;window=new Window(now,evidence.confirmed());
        }
    }
    /** Single-use lease clock: a broken link freezes permanently, including after range reentry. */
    static final class Window {
        final long until;
        long frozenAt=-1;
        Window(long now,boolean confirmed){until=now+(confirmed?LOCK_TICKS:0);if(!confirmed)frozenAt=now;}
        boolean update(long now,boolean lawfulLink){
            if(frozenAt<0&&(now>=until||!lawfulLink))frozenAt=now;
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
        for(String key:List.of(SEND,RECEIVE)){
            if(key.equals(RECEIVE)&&Objects.equals(RECEIVED_AT.get(receiver),HumanPerception.now(receiver)))continue;
            int remaining=receiver.getPersistentData().getInt(key);
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
    static void clear(Human human){INBOX.remove(human);} // Intentionally retain cooldowns.
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
                &&HumanPerception.personalDynamic(sender,target);
        if(lease.window.update(now,legal)){
            lease.position=target.position();lease.positionAt=now;
            return;
        }
        HumanPerception.notice(receiver,"shared",lease.position,lease.target,LAST_TICKS);
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
    /** Invoked only with the existing budgeted candidate query's bounded nearest friendly list. */
    static void broadcast(Human sender,List<Human> nearby){
        if(sender.getPersistentData().getInt(SEND)>0||!available(sender))return;
        Evidence evidence=HumanPerception.sharingEvidence(sender);if(evidence==null)return;
        int delivered=0;long now=HumanPerception.now(sender);
        for(Human receiver:nearby){
            if(delivered>=MAX_RECEIVERS)break;
            if(!peers(sender,receiver)||receiver.getPersistentData().getInt(RECEIVE)>0)continue;
            LivingEntity target=resolve(receiver,evidence.target());
            if(target!=null&&!HumanPerception.sharingAllowed(receiver,target))continue;
            // An unidentified hurt direction has no confirmed identity to validate or attack.
            if(evidence.confirmed()&&target==null)continue;
            receiver.getPersistentData().putInt(RECEIVE,COOLDOWN);RECEIVED_AT.put(receiver,now);delivered++;
            if(evidence.confirmed()){
                var inbox=INBOX.computeIfAbsent(receiver,k->new LinkedHashMap<>());
                inbox.put(evidence.target(),new Lease(sender,receiver,evidence,now));
                while(inbox.size()>8)inbox.remove(inbox.keySet().iterator().next());
            }else HumanPerception.notice(receiver,"shared",evidence.position(),evidence.target(),LAST_TICKS);
        }
        if(delivered>0)sender.getPersistentData().putInt(SEND,COOLDOWN);
    }
}

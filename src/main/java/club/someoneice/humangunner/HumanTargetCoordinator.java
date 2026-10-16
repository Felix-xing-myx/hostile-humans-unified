package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;

/** Sole normal target-selection boundary; cancellation still belongs to commands/safety. */
public final class HumanTargetCoordinator {
    private static final Map<Human,State> STATES=new WeakHashMap<>();
    private static final class State { UUID selected,locked;long lockUntil=-1,changed=-1,nextReview;String reason="尚未选敌"; }
    private HumanTargetCoordinator() {}
    public static void lock(Human h,LivingEntity target,long until){State s=STATES.computeIfAbsent(h,k->new State());s.locked=target.getUUID();s.lockUntil=until;}
    public static void unlock(Human h){State s=STATES.get(h);if(s!=null){s.locked=null;s.lockUntil=-1;}}
    public static void clear(Human human){STATES.remove(human);}
    public static String reason(Human human){State state=STATES.get(human);return state==null?"尚未选敌":state.reason;}
    public static boolean suspended(Human h){
        return h.isNoAi()||h.isSleeping()||NavigationDebugService.controls(h)||HumanGunner.isManualInventoryOpen(h)
                ||h.level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL
                ||SoldierOrder.isReturningFromRetreat(h)||h.guardCombatState.returning();
    }
    /** Explicit survival counterfire may restore its own known opponent, never generic acquisition. */
    public static LivingEntity counterfire(Human h,LivingEntity target){
        if(suspended(h)||!h.isFleeing||h.explosionThreat!=null||!HumanPerception.canTarget(h,target))return h.getTarget();
        if(h.getTarget()!=null)return h.getTarget();
        if(BodyguardProtection.isBodyguard(h)&&BodyguardProtection.owner(h)!=null){
            BodyguardProtection.update(h);if(BodyguardProtection.attack(h)!=target)return null;
        }
        h.setTarget(target);
        State state=STATES.computeIfAbsent(h,k->new State());state.selected=h.getTarget()==null?null:h.getTarget().getUUID();
        state.changed=HumanPerception.now(h);state.reason="明确的撤退反击请求";
        return h.getTarget();
    }
    public static LivingEntity select(Human h,LivingEntity proposal,String why){return select(h,proposal,why,false);}
    public static LivingEntity select(Human h,LivingEntity proposal,String why,boolean urgent){
        if(suspended(h))return h.getTarget();
        State state=STATES.computeIfAbsent(h,k->new State());long now=HumanPerception.now(h);
        LivingEntity current=h.getTarget();
        if(!Objects.equals(state.selected,current==null?null:current.getUUID())){state.selected=current==null?null:current.getUUID();state.changed=now;}
        LivingEntity desired=HumanPerception.canTarget(h,current)?current:null;
        boolean role=BodyguardProtection.isBodyguard(h)&&BodyguardProtection.owner(h)!=null&&!h.isFleeing;
        if(role){BodyguardProtection.update(h);desired=BodyguardProtection.attack(h);why="贴身保护优先级";}
        else if(!h.isFleeing){
            LivingEntity locked=null;
            if(state.locked!=null&&now<state.lockUntil&&h.level() instanceof net.minecraft.server.level.ServerLevel level
                    &&level.getEntity(state.locked) instanceof LivingEntity found&&HumanPerception.canTarget(h,found))locked=found;
            if(locked!=null){desired=locked;proposal=locked;why="多来源压力集中处理";}
            else {state.locked=null;state.lockUntil=-1;}

            if(!HumanPerception.canTarget(h,proposal))proposal=null;
            if(desired==null){desired=proposal!=null?proposal:HumanPerception.candidate(h);why="已确认记忆接替";}
            else if(proposal!=null&&proposal!=desired){
                boolean close=h.distanceToSqr(proposal)<=36&&h.distanceToSqr(desired)>36;
                if(dev.felix.hostilehumans.core.PerceptionCoordinationPolicy.switchAllowed(now,state.changed,
                        PerceptionConfig.get().integer("selection.minimum_hold_ticks"),close,urgent))desired=proposal;
            }
        }
        if(!role&&!h.isFleeing&&state.locked==null&&desired==current&&desired!=null&&proposal==null&&now>=state.nextReview){
            state.nextReview=now+PerceptionConfig.get().integer("budgets.active_interval_ticks");
            LivingEntity nearby=HumanPerception.candidate(h);
            if(nearby!=null&&nearby!=current&&CombatPressurePolicy.shouldPrioritizeCloserThreat(
                    h.distanceToSqr(current),h.distanceToSqr(nearby),
                    RecentDamageTracker.recentDamageFrom(h,current,CombatPressurePolicy.NEARBY_THREAT_DAMAGE_WINDOW_TICKS),h.getMaxHealth())){
                desired=nearby;why="已确认的近处威胁";
            }
        }
        // Retreat owns its private survival threat; ordinary acquisition must not undo escape cancellation.
        if(h.isFleeing&&current==null)return null;
        if(desired!=null&&!HumanPerception.canTarget(h,desired))desired=null;
        if(current!=desired){h.setTarget(desired);state.selected=h.getTarget()==null?null:h.getTarget().getUUID();state.changed=now;state.reason=h.getTarget()==desired?why:"最终许可或事件拒绝目标请求";}
        if(SoldierOrder.get(h)==SoldierOrder.GUARD&&h.getTarget()!=null)h.guardCombatState.retainTarget(h.getTarget().getUUID());
        return h.getTarget();
    }
}

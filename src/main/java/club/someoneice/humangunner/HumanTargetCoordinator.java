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
    static boolean lockActive(Human h,LivingEntity target){
        State s=STATES.get(h);return s!=null&&target!=null&&target.getUUID().equals(s.locked)&&HumanPerception.now(h)<s.lockUntil;
    }
    public static void clear(Human human){STATES.remove(human);}
    public static String reason(Human human){State state=STATES.get(human);return state==null?"尚未选敌":state.reason;}
    public static boolean suspended(Human h){
        return h.isNoAi()||h.isSleeping()||NavigationDebugService.controls(h)||HumanGunner.isManualInventoryOpen(h)
                ||h.level().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL
                ||SoldierOrder.isReturningFromRetreat(h)&&!SoldierOrder.isGarrison(h);
    }
    /** Explicit survival counterfire may restore its own known opponent, never generic acquisition. */
    public static LivingEntity counterfire(Human h,LivingEntity target){
        // Null is rejection, even when the old Mob target happens to equal this request.
        if(suspended(h)||!h.isFleeing||h.explosionThreat!=null||!HumanPerception.canTarget(h,target))return null;
        if(h.getTarget()!=null)return h.getTarget()==target?target:null;
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
            if(!HumanPerception.selectionAllowed(h,proposal))proposal=null;
            LivingEntity best=proposal;
            if(desired==null||now>=state.nextReview){
                state.nextReview=now+AdaptiveActivityBudget.interval(h,PerceptionConfig.get().integer("budgets.active_interval_ticks"),urgent);
                LivingEntity known=HumanPerception.candidate(h);
                if(best==null||known!=null&&HumanThreatAssessment.score(h,known)>HumanThreatAssessment.score(h,best))best=known;
            }
            LivingEntity locked=null;
            if(state.locked!=null&&now<state.lockUntil&&h.level() instanceof net.minecraft.server.level.ServerLevel level
                    &&level.getEntity(state.locked) instanceof LivingEntity found&&HumanPerception.selectionAllowed(h,found))locked=found;
            if(locked!=null&&best!=null&&best!=locked&&(HumanThreatAssessment.nearOverride(h,locked,best)
                    ||urgent&&HumanThreatAssessment.shouldSwitch(h,locked,best))){
                unlock(h);locked=null;why="威胁评分打破远敌或压力偏好";
            }
            if(locked!=null){desired=locked;best=locked;why="多来源压力集中处理";}
            else {state.locked=null;state.lockUntil=-1;}
            if(desired==null){desired=best;why="已确认记忆按威胁分接替";}
            else if(best!=null&&best!=desired&&HumanThreatAssessment.shouldSwitch(h,desired,best)){
                boolean close=HumanThreatAssessment.nearOverride(h,desired,best);
                if(dev.felix.hostilehumans.core.PerceptionCoordinationPolicy.switchAllowed(now,state.changed,
                        PerceptionConfig.get().integer("selection.minimum_hold_ticks"),close,urgent)){
                    desired=best;why=close?"已确认的近处威胁评分优先":"动态威胁评分超过切换余量";
                }
            }
        }
        // Retreat owns its private survival threat; ordinary acquisition must not undo escape cancellation.
        if(h.isFleeing&&current==null)return null;
        if(desired!=null&&(!HumanPerception.canTarget(h,desired)||!role&&!HumanPerception.selectionAllowed(h,desired)))desired=null;
        if(current!=desired){h.setTarget(desired);state.selected=h.getTarget()==null?null:h.getTarget().getUUID();state.changed=now;state.reason=h.getTarget()==desired?why:"最终许可或事件拒绝目标请求";}
        if(SoldierOrder.isGarrison(h)&&h.getTarget()!=null)h.guardCombatState.retainTarget(h.getTarget().getUUID());
        return h.getTarget();
    }
}

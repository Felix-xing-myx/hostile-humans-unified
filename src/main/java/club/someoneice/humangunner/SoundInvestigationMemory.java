package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import dev.felix.hostilehumans.core.SoundExclusionMemory;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.phys.Vec3;

/** Acoustic-only negative memory; never rejects sight, direct hurt, owner or beacon reports. */
final class SoundInvestigationMemory {
    private static final Map<Human,SoundExclusionMemory> STATES=new WeakHashMap<>();
    private SoundInvestigationMemory() {}
    private static String scope(Human h){return h.level().dimension().location()+":"+SoldierOrder.get(h)+":"+h.getOwnerUUID();}
    static boolean blocked(Human h,String kind,Vec3 point){
        if(!SoundExclusionMemory.sound(kind)||point==null)return false;
        SoundExclusionMemory s=STATES.get(h);
        return s!=null&&s.blocked(scope(h),HumanPerception.now(h),
                h.getX(),h.getY(),h.getZ(),point.x,point.y,point.z);
    }
    static void failed(Human h,HumanPerception.Clue clue){
        if(clue==null||!SoundExclusionMemory.sound(clue.kind))return;
        var c=PerceptionConfig.get();Vec3 p=clue.position;
        STATES.computeIfAbsent(h,k->new SoundExclusionMemory()).fail(scope(h),
                HumanPerception.now(h),h.getX(),h.getY(),h.getZ(),p.x,p.y,p.z,
                c.integer("investigation.exclusion_ticks"),c.integer("investigation.exclusion_max_ticks"),
                c.integer("investigation.exclusion_history_ticks"),c.number("investigation.exclusion_radius"),
                c.number("investigation.exclusion_vertical"),c.number("investigation.exclusion_reset_distance"),
                c.integer("investigation.exclusion_maximum"));
        HumanPerception.finishClue(h,clue,"声音区域不可达，暂时忽略附近动静");
    }
    static String describe(Human h){
        var s=STATES.get(h);if(s==null)return "无排除区";
        long now=HumanPerception.now(h);s.prune(scope(h),now,h.getX(),h.getY(),h.getZ());
        return s.regions().stream().filter(r->now<r.until()).map(r->String.format(java.util.Locale.ROOT,"%.0f,%.0f,%.0f · R%.0f · %.1f秒",r.x(),r.y(),r.z(),r.radius(),(r.until()-now)/20.))
                .collect(java.util.stream.Collectors.joining("；"));
    }
}

package dev.felix.hostilehumans.perception;

import dev.felix.hostilehumans.entity.Human;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Source importance is sensory filtering, never attack permission or live-position knowledge. */
final class HearingRelevance {
    record Result(int importance,boolean deferred,String reason) {}
    private HearingRelevance() {}
    static boolean step(String kind){return kind.equals("walk")||kind.equals("run")||kind.equals("swim");}
    static int importance(Entity actor,String kind) {
        if(kind.equals("aid")||kind.equals("explosion"))return 4;
        if(actor instanceof Player)return 3;
        if(actor instanceof Mob mob&&(mob.getTarget() instanceof Human||mob.getTarget() instanceof Player))return 4;
        if(actor instanceof Human)return 3;
        return step(kind)?0:2;
    }
    static Result evaluate(Human listener,Entity source,String kind,Vec3 point,double radius,int seed) {
        PerceptionConfig c=PerceptionConfig.get();
        double distance=listener.position().distanceTo(point),vertical=Math.abs(listener.getY()-point.y);
        if(seed==0&&(distance>c.number("hearing.background_step_radius")||vertical>c.number("hearing.background_vertical")))
            return new Result(0,false,"背景怪物脚步，不调查");
        double weighted=Math.sqrt(listener.position().distanceToSqr(point)+3*vertical*vertical);
        if(weighted>radius)return new Result(0,false,"距离或高差衰减");
        String work=":hearing:"+kind+":"+(source==null?"world":source.getUUID());
        if(!PerceptionBudget.claim(listener,"rays",1,work))return new Result(0,true,"声音遮蔽检查等待");
        Vec3 endpoint=point.add(0,.4,0);
        var hit=listener.level().clip(new ClipContext(listener.getEyePosition(),endpoint,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,listener));
        boolean clear=hit.getType()==HitResult.Type.MISS
                ||hit.getBlockPos().equals(BlockPos.containing(endpoint));
        if(seed==0)return new Result(clear?2:0,false,clear?"近场无遮挡怪物脚步":"背景脚步被遮蔽");
        double attenuation=seed>=4?.75:c.number("hearing.wall_radius_factor");
        if(!clear&&weighted>radius*attenuation)return new Result(0,false,"墙体衰减后不可听");
        return new Result(seed,false,clear?"重要声音无遮挡":"重要声音经过墙体衰减");
    }
}

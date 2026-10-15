package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** Selection only consumes confirmed knowledge; it never performs a second visual scan. */
public final class PerceptionTargetGoal extends Goal {
    private final Human human;
    public PerceptionTargetGoal(Human human){this.human=human;setFlags(EnumSet.of(Flag.TARGET));}
    @Override public boolean canUse(){return !human.isNoAi()&&!human.isSleeping()&&!NavigationDebugService.controls(human);}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void tick(){
        var desired=HumanPerception.candidate(human);
        if(human.getTarget()!=desired)human.setTarget(desired);
    }
    @Override public void start(){tick();}
}

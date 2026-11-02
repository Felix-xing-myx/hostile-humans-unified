package dev.felix.hostilehumans.perception;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.HumanTargetCoordinator;
import dev.felix.hostilehumans.observer.NavigationDebugService;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** Selection only consumes confirmed knowledge; it never performs a second visual scan. */
public final class PerceptionTargetGoal extends Goal {
    private final Human human;
    public PerceptionTargetGoal(Human human){this.human=human;setFlags(EnumSet.of(Flag.TARGET));}
    @Override public boolean canUse(){return !human.isNoAi()&&!human.isSleeping()&&!NavigationDebugService.controls(human);}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void tick(){
        HumanTargetCoordinator.select(human,null,"共同目标选择");
    }
    @Override public void start(){tick();}
}

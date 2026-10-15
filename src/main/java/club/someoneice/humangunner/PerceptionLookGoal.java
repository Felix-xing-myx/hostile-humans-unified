package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** Protected attention owns LOOK only; movement can still evade or obey orders. */
public final class PerceptionLookGoal extends Goal {
    private final Human human;
    public PerceptionLookGoal(Human human){this.human=human;setFlags(EnumSet.of(Flag.LOOK));}
    @Override public boolean canUse(){return HumanPerception.observing(human);}
    @Override public boolean canContinueToUse(){return canUse();}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void tick(){HumanPerception.applyLook(human);}
}

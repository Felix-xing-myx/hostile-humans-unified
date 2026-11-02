package dev.felix.hostilehumans.perception;

import dev.felix.hostilehumans.entity.Human;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import dev.felix.hostilehumans.perception.HumanAttention;
import dev.felix.hostilehumans.perception.PerceptionBudget;
import java.util.UUID;

/** Idle glances use vanilla selection/timing; combat goals own target tracking. */
public final class HumanLookAtPlayerGoal extends LookAtPlayerGoal {
    private final Mob human;
    private long nextGlanceTick;
    private UUID lastLook;
    private long rayUntil;
    private boolean visible;

    @Override
    public void tick() {
        super.tick();
        if(!(human instanceof Human h)||lookAt==null)return;
        long now=human.level().getGameTime();
        if(!lookAt.getUUID().equals(lastLook)||now>=rayUntil){
            visible=false;
            if(!PerceptionBudget.claim(h,"rays",1,":idle-player"))return;
            lastLook=lookAt.getUUID();rayUntil=now+5;visible=h.hasLineOfSight(lookAt);
        }
        if(visible)HumanAttention.idleGlance(h,lookAt.getEyePosition());
    }

    public HumanLookAtPlayerGoal(Mob human) {
        super(human, Player.class, 4.0F, 0.02F);
        this.human = human;
    }

    @Override
    public boolean canUse() {
        return human.getTarget() == null
                && human.level().getGameTime() >= nextGlanceTick
                && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return human.getTarget() == null && super.canContinueToUse();
    }

    @Override
    public void stop() {
        super.stop();
        // Absolute server ticks: not shortened by the goal scheduler's tick rate.
        nextGlanceTick = human.level().getGameTime() + 100 + human.getRandom().nextInt(101);
    }
}

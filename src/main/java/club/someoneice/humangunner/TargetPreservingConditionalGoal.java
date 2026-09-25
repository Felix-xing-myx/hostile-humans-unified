package club.someoneice.humangunner;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.function.BooleanSupplier;

/** Conditional delegate which can preserve a valid target across weapon handoff. */
public final class TargetPreservingConditionalGoal extends Goal {
    private final Mob owner;
    private final Goal delegate;
    private final BooleanSupplier condition;
    private final BooleanSupplier preserveTargetOnStop;

    public TargetPreservingConditionalGoal(
            Mob owner,
            Goal delegate,
            BooleanSupplier condition,
            BooleanSupplier preserveTargetOnStop
    ) {
        this.owner = owner;
        this.delegate = delegate;
        this.condition = condition;
        this.preserveTargetOnStop = preserveTargetOnStop;
        setFlags(delegate.getFlags());
    }

    @Override
    public boolean canUse() {
        return condition.getAsBoolean() && delegate.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return condition.getAsBoolean() && delegate.canContinueToUse();
    }

    @Override
    public boolean isInterruptable() {
        return delegate.isInterruptable();
    }

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void stop() {
        LivingEntity remembered = owner.getTarget();
        delegate.stop();
        if (preserveTargetOnStop.getAsBoolean()
                && remembered != null
                && remembered.isAlive()
                && owner.canAttack(remembered)) {
            owner.setTarget(remembered);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return delegate.requiresUpdateEveryTick();
    }

    @Override
    public void tick() {
        delegate.tick();
    }
}

package club.someoneice.humangunner;

import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;

/**
 * Keeps a combat goal registered for the entity's lifetime while enabling it
 * only for the currently held weapon. This avoids mutating GoalSelector from
 * entity ticks, which is unsafe with optimized goal collections.
 */
public final class ConditionalGoal extends Goal {
    private final Goal delegate;
    private final BooleanSupplier condition;

    public ConditionalGoal(Goal delegate, BooleanSupplier condition) {
        this.delegate = delegate;
        this.condition = condition;
        setFlags(delegate.getFlags());
    }

    @Override
    public EnumSet<Flag> getFlags() {
        return delegate.getFlags();
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
        delegate.stop();
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

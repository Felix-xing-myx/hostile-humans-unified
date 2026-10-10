package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ForgeEventFactory;

/** Server-owned food action. Never acquires movement or native weapon-use state. */
final class OffhandFoodUse {
    private static final Map<Human, State> ACTIONS = new WeakHashMap<>();
    private static final class State {
        final RecoverySupplies.UseSession session;
        int remaining, lastTick = Integer.MIN_VALUE;
        boolean ended, consumed;
        State(RecoverySupplies.UseSession session, int remaining) {
            this.session = session;
            this.remaining = remaining;
        }
    }
    private OffhandFoodUse() {}

    static boolean canStart(Human human) {
        return !human.isUsingItem() || human.getUsedItemHand() == InteractionHand.MAIN_HAND;
    }

    static boolean start(Human human, RecoverySupplies.UseSession session) {
        if (human.level().isClientSide || !canStart(human)) return false;
        int duration = ForgeEventFactory.onItemUseStart(human, human.getOffhandItem(),
                Math.max(1, session.expectedCompletionTick() - human.tickCount));
        if (duration <= 0) return false;
        ACTIONS.put(human, new State(session, duration));
        human.setNpcOffhandEating(true);
        return true;
    }

    static boolean owns(Human human, RecoverySupplies.UseSession session) {
        State state = ACTIONS.get(human);
        return state != null && state.session == session;
    }
    static boolean running(Human human, RecoverySupplies.UseSession session) {
        State state = ACTIONS.get(human);
        return state != null && state.session == session && !state.ended;
    }
    static boolean completed(Human human, RecoverySupplies.UseSession session) {
        State state = ACTIONS.get(human);
        return state != null && state.session == session && state.consumed;
    }
    static void clear(Human human) {
        ACTIONS.remove(human);
        human.setNpcOffhandEating(false);
    }

    static void tick(Human human) {
        State state = ACTIONS.get(human);
        if (state == null || state.ended || state.lastTick == human.tickCount) return;
        state.lastTick = human.tickCount;
        ItemStack stack = human.getOffhandItem();
        if (!human.isAlive() || !ItemStack.isSameItemSameTags(stack, state.session.itemAtStart())
                || stack.getCount() != state.session.itemAtStart().getCount()) {
            state.ended = true;
            human.setNpcOffhandEating(false);
            return;
        }
        if (--state.remaining > 0) {
            if (state.remaining % 4 == 0 && human.level() instanceof ServerLevel level) {
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack),
                        human.getX(), human.getEyeY() - 0.25D, human.getZ(), 2, 0.1D, 0.08D, 0.1D, 0.0D);
                human.playSound(stack.getEatingSound(), 0.3F, 1.0F);
            }
            return;
        }
        // Mark terminal before callbacks: reentrant item hooks cannot consume a second meal.
        state.ended = true;
        human.setNpcOffhandEating(false);
        ItemStack before = stack.copy();
        ItemStack result = stack.finishUsingItem(human.level(), human);
        result = ForgeEventFactory.onItemUseFinish(human, before, 0, result);
        if (ACTIONS.get(human) != state) return;
        human.setItemInHand(InteractionHand.OFF_HAND, result);
        // A non-consuming custom item must not grant our additional food healing.
        state.consumed = !ItemStack.isSameItemSameTags(before, result)
                || result.getCount() < before.getCount();
    }
}

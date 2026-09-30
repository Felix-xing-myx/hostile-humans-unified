package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import dev.felix.hostilehumans.core.TickValueGate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Shared non-combat awareness; actual damage and hostility stay in their own handlers. */
public final class HumanAwareness {
    private static final Map<Entity, ActorState> ACTORS = new WeakHashMap<>();
    private record Neighborhood(long tick, ResourceKey<Level> dimension, AABB area,
                                List<WeakReference<Human>> humans) {}
    private record SoundNotice(ResourceKey<Level> dimension, AABB area, BlockPos position) {}
    private record AlertNotice(ResourceKey<Level> dimension, AABB area, boolean excludeActor) {}
    private static final class ActorState {
        Neighborhood soundArea;
        Neighborhood alertArea;
        final TickValueGate<SoundNotice> sounds = new TickValueGate<>();
        final TickValueGate<AlertNotice> alerts = new TickValueGate<>();
    }

    private HumanAwareness() {}

    public static void investigate(Entity actor, BlockPos position) {
        if (actor.level().isClientSide) return;
        ActorState state = ACTORS.computeIfAbsent(actor, ignored -> new ActorState());
        AABB area = actor.getBoundingBox().inflate(16.0D);
        long tick = actor.level().getGameTime();
        BlockPos sound = position.immutable();
        if (!state.sounds.accept(tick, new SoundNotice(actor.level().dimension(), area, sound))) return;
        state.soundArea = nearby(actor, area, tick, state.soundArea);
        for (WeakReference<Human> reference : state.soundArea.humans()) {
            Human human = reference.get();
            if (isPresent(human, actor, area)) human.setInvestigateSound(sound);
        }
    }

    public static void alert(Entity actor, boolean excludeActor) {
        if (actor.level().isClientSide) return;
        ActorState state = ACTORS.computeIfAbsent(actor, ignored -> new ActorState());
        AABB area = actor.getBoundingBox().inflate(10.0D);
        long tick = actor.level().getGameTime();
        if (!state.alerts.accept(tick, new AlertNotice(actor.level().dimension(), area, excludeActor))) return;
        state.alertArea = nearby(actor, area, tick, state.alertArea);
        for (WeakReference<Human> reference : state.alertArea.humans()) {
            Human human = reference.get();
            if ((!excludeActor || human != actor) && isPresent(human, actor, area)) human.isAlert = true;
        }
    }

    private static Neighborhood nearby(Entity actor, AABB area, long tick, Neighborhood cached) {
        if (cached != null && cached.tick() == tick && cached.area().equals(area)
                && cached.dimension().equals(actor.level().dimension())) return cached;
        List<WeakReference<Human>> humans = new ArrayList<>();
        for (Human human : actor.level().getEntitiesOfClass(Human.class, area)) humans.add(new WeakReference<>(human));
        return new Neighborhood(tick, actor.level().dimension(), area, humans);
    }

    private static boolean isPresent(Human human, Entity actor, AABB area) {
        return human != null && human.isAlive() && !human.isRemoved()
                && human.level() == actor.level() && human.getBoundingBox().intersects(area);
    }
}

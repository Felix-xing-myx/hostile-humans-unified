package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.Comparator;
import dev.felix.hostilehumans.core.BudgetedSearch;

/** Emergency movement that gets humans out of lava before combat or orders resume. */
public final class LavaEscapeGoal extends Goal {
    private static final int SEARCH_RADIUS = 32;
    private static final int SEARCH_STEP = 4;
    private static final int ANGULAR_STEPS = 8;
    private static final int SEARCH_INTERVAL_TICKS = 20;
    private static final double NAVIGATION_SPEED = 1.0D;
    private static final double MINIMUM_UPWARD_SPEED = 0.12D;

    private final Human human;
    private int nextSearchTick;
    private BudgetedSearch<BlockPos> pendingShoreCandidates;
    private Path selectedShorePath;

    public LavaEscapeGoal(Human human) {
        this.human = human;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return human.isAlive() && !human.isPassenger() && human.isInLava();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        nextSearchTick = 0;
        pendingShoreCandidates = null;
        MovementSpeedController.retreat(human, true);
        human.stopUsingItem();
        human.setSprinting(true);
        human.getPersistentData().putString("humangunner:ai_phase", "escaping_lava");
        findAndFollowShorePath();
    }

    @Override
    public void tick() {
        Vec3 motion = human.getDeltaMovement();
        if (motion.y < MINIMUM_UPWARD_SPEED) {
            human.setDeltaMovement(motion.x, MINIMUM_UPWARD_SPEED, motion.z);
        }
        if (human.onGround()) {
            human.getJumpControl().jump();
        }

        if (human.tickCount >= nextSearchTick
                && (human.getNavigation().isDone() || human.getNavigation().getPath() == null)) {
            findAndFollowShorePath();
        }
    }

    @Override
    public void stop() {
        OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.LAVA);
        pendingShoreCandidates = null;
        selectedShorePath = null;
        nextSearchTick = human.tickCount + SEARCH_INTERVAL_TICKS;
        if (!human.isInLava()) {
            human.setSprinting(false);
            if (human.getTarget() == null) {
                MovementSpeedController.normal(human);
                human.getPersistentData().putString("humangunner:ai_phase", "idle");
            } else {
                MovementSpeedController.combat(human, false);
            }
        }
    }

    private void findAndFollowShorePath() {
        if (pendingShoreCandidates == null) {
            pendingShoreCandidates = new BudgetedSearch<>(collectShoreCandidates().iterator());
        }
        selectedShorePath = null;
        // Keep the full candidate set, but spread failed A* requests over
        // subsequent ticks rather than running up to 64 in one entity tick.
        int budget = OptionalPathBudget.claim(human, OptionalPathBudget.Kind.LAVA, 4);
        if (budget == 0) {
            nextSearchTick = human.tickCount + 1;
            return;
        }
        BlockPos found = pendingShoreCandidates.firstMatching(budget, candidate -> {
            if (!human.level().hasChunkAt(candidate) || !isSafeShore(candidate)) return false;
            Path path = human.getNavigation().createPath(candidate, 0);
            if (path == null || !path.canReach()) return false;
            selectedShorePath = path;
            return true;
        });
        if (found != null) {
            human.getNavigation().moveTo(selectedShorePath, NAVIGATION_SPEED);
            selectedShorePath = null;
            pendingShoreCandidates = null;
            OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.LAVA);
            nextSearchTick = human.tickCount + SEARCH_INTERVAL_TICKS;
        } else if (pendingShoreCandidates.hasRemaining()) {
            nextSearchTick = human.tickCount + 1;
        } else {
            pendingShoreCandidates = null;
            OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.LAVA);
            nextSearchTick = human.tickCount + SEARCH_INTERVAL_TICKS + Math.floorMod(human.getId(), 10);
        }
    }

    private ArrayList<BlockPos> collectShoreCandidates() {
        BlockPos origin = human.blockPosition();
        ArrayList<BlockPos> candidates = new ArrayList<>();
        double angleOffset = human.getRandom().nextDouble() * Math.PI * 2.0D;

        for (int radius = SEARCH_STEP; radius <= SEARCH_RADIUS; radius += SEARCH_STEP) {
            for (int step = 0; step < ANGULAR_STEPS; step++) {
                double angle = angleOffset + Math.PI * 2.0D * step / ANGULAR_STEPS;
                int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);
                if (!human.level().hasChunkAt(new BlockPos(x, origin.getY(), z))) {
                    continue;
                }

                int y = human.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos candidate = new BlockPos(x, y, z);
                if (!isSafeShore(candidate)) {
                    continue;
                }

                candidates.add(candidate);
            }
        }

        // First reachable in this order is the same nearest-destination
        // criterion as the old exhaustive bestDistance comparison.
        candidates.sort(Comparator.comparingDouble(candidate -> candidate.distSqr(origin)));
        return candidates;
    }

    private boolean isSafeShore(BlockPos pos) {
        BlockPos floor = pos.below();
        return human.level().getFluidState(pos).isEmpty()
                && human.level().getFluidState(pos.above()).isEmpty()
                && human.level().getFluidState(floor).isEmpty()
                && human.level().getBlockState(pos).getCollisionShape(human.level(), pos).isEmpty()
                && human.level().getBlockState(pos.above()).getCollisionShape(human.level(), pos.above()).isEmpty()
                && human.level().getBlockState(floor).isFaceSturdy(human.level(), floor, Direction.UP);
    }
}

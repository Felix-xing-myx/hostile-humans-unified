package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ThrowableProjectile;

/** Shared inventory-backed tactical planner (legacy class name); no MOVE/LOOK goal or extra AI. */
final class ScgunsThrowables {
    private static final String READY = HumanGunner.MOD_ID + ":scguns_throw_ready";
    private ScgunsThrowables() {}
    static boolean supported(ItemStack stack) { return TacticalThrowables.supported(stack); }
    static void tick(Human human) {
        var settings = UnifiedConfig.get().scguns();
        if (human.level().isClientSide || !settings.throwablesEnabled() || human.isFleeing || human.isUsingItem()
                || human.getData() == null || !human.isAlive() || human.getTarget() == null
                || (human.tickCount + Math.floorMod(human.getId(), 20)) % 20 != 0) return;
        long now = human.level().getGameTime();
        if (now < human.getPersistentData().getLong(READY)) return;
        // Failed preflight is also throttled; large armies do not rescan every tick.
        human.getPersistentData().putLong(READY, now + 40);
        LivingEntity target = human.getTarget();
        if (!target.isAlive() || !human.canAttack(target) || !SoldierOrder.allowsTarget(human, target)
                || HumanRelations.allied(human, target) || !human.getSensing().hasLineOfSight(target)) return;
        double distance = human.distanceTo(target);
        if (distance < settings.throwMinRange() || distance > settings.throwMaxRange()) return;
        boolean hasThrowable = false;
        for (int slot = 0; slot < human.getData().getInventoryItemsSize(); slot++) {
            ItemStack stack = human.getData().getInventoryItem(slot);
            if (supported(stack) && settings.allowedThrowables().contains(
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
                hasThrowable = true;
                break;
            }
        }
        if (!hasThrowable) return;
        ClusterAim cluster = clusterAim(human, target, settings);
        target = cluster.target();
        for (int slot = 0; slot < human.getData().getInventoryItemsSize(); slot++) {
            ItemStack stack = human.getData().getInventoryItem(slot);
            if (!supported(stack) || !settings.allowedThrowables().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) continue;
            // Gas/lingering hazards need a larger exclusion zone than a simple blast.
            double safety = TacticalThrowables.safetyRadius(stack, settings.friendlySafetyRadius());
            // Leave the owner's body before launch, but never move the origin through a wall.
            Vec3 forward = target.position().subtract(human.position()).multiply(1, 0, 1).normalize();
            Vec3 eye = human.getEyePosition().add(0, -.1, 0);
            Vec3 origin = eye.add(forward.scale(human.getBbWidth() * .5 + .2));
            if (human.level().clip(new ClipContext(eye, origin, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, human)).getType() != HitResult.Type.MISS) continue;
            FlightProfile physics = flightProfile(stack, settings.fuseTicks());
            ThrowPlan plan = plan(human, target, origin, physics.gravity(), physics.bouncing(),
                    physics.horizon(), settings.throwSpeed());
            if (plan == null || !safeArea(human, plan.impact(), safety)) continue;
            ThrowableProjectile projectile = TacticalThrowables.create(human, stack, settings.fuseTicks());
            if (projectile == null) continue;
            projectile.setPos(origin);
            Vec3 velocity = plan.velocity();
            // Native Projectile.shoot initializes both movement and rotation; no random spread.
            projectile.shoot(velocity.x, velocity.y, velocity.z, (float) velocity.length(), 0);
            // Settle the real stack only after the server accepted the native projectile.
            if (!human.level().addFreshEntity(projectile)) return;
            ItemStack remainder = stack.copy(); remainder.shrink(1);
            human.getData().setInventoryItem(slot, remainder);
            int cooldown = cluster.count() >= settings.clusterMinimumTargets()
                    ? Math.min(settings.throwCooldownTicks(), settings.clusterCooldownTicks())
                    : settings.throwCooldownTicks();
            human.getPersistentData().putLong(READY, now + cooldown);
            human.level().playSound(null, human.blockPosition(), net.minecraft.sounds.SoundEvents.SNOWBALL_THROW,
                    net.minecraft.sounds.SoundSource.HOSTILE, 1, .8F);
            return;
        }
    }
    /** Constructor profiles mirror native public factories; no private field reflection or fake ticks.
     * All grenade-derived bombs inherit the owner constructor's .05F gravity, not base .03F.
     * Beacon bounces too; Shotball effects already trigger on first impact, so aim there. */
    record FlightProfile(double gravity, boolean bouncing, int horizon) { }
    static FlightProfile flightProfile(ItemStack stack, int fuse) {
        return TacticalThrowables.flightProfile(stack, fuse);
    }

    private record ClusterAim(LivingEntity target, int count) { }
    /** At most 16 local candidates, once per throttled preflight. Choose an actual enemy
     * position rather than a centroid that could lie inside a wall or over a cliff. */
    private static ClusterAim clusterAim(Human human, LivingEntity primary, ScgunsSettings settings) {
        double radius = settings.clusterRadius();
        java.util.List<LivingEntity> candidates = new java.util.ArrayList<>();
        candidates.add(primary);
        for (LivingEntity enemy : human.level().getEntitiesOfClass(LivingEntity.class,
                primary.getBoundingBox().inflate(radius * 2), entity -> entity != primary
                        && entity.isAlive() && !entity.isSpectator() && human.canAttack(entity)
                        && (human.isAngryAt(entity)
                        || SoldierCombatMode.isExplicitlyAuthorizedAgainst(human, entity)
                        || HumanTargeting.isAutonomousPlayerEnemy(human, entity)
                        || entity instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == human
                        || BodyguardProtection.protectingFromExplosion(human, entity))
                        && !HumanRelations.allied(human, entity)
                        && SoldierOrder.allowsTarget(human, entity))) {
            double range = human.distanceTo(enemy);
            if (range < settings.throwMinRange() || range > settings.throwMaxRange()
                    || !human.getSensing().hasLineOfSight(enemy)) continue;
            candidates.add(enemy);
            if (candidates.size() >= 16) break;
        }
        LivingEntity best = primary;
        int bestCount = 1;
        for (LivingEntity candidate : candidates) {
            int count = 0;
            for (LivingEntity neighbor : candidates)
                if (candidate.distanceToSqr(neighbor) <= radius * radius) count++;
            if (count > bestCount) { best = candidate; bestCount = count; }
        }
        // Isolated enemies must not displace the current AI target.
        return bestCount >= settings.clusterMinimumTargets()
                ? new ClusterAim(best, bestCount) : new ClusterAim(primary, 1);
    }

    private static boolean safeArea(Human human, Vec3 aim, double radius) {
        if (human.position().distanceToSqr(aim) < radius * radius) return false;
        for (LivingEntity entity : human.level().getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(aim, aim).inflate(radius))) {
            if (HumanRelations.allied(human, entity)) return false;
        }
        return true;
    }
    private static final double DRAG = ThrowableBallistics.DRAG;
    private record ThrowPlan(Vec3 velocity, Vec3 impact) { }
    private record Prediction(Vec3 point, int ticks) { }
    /** Actual configured speed, including the sub-tick part of the first reachable flight time. */
    private static Vec3 trajectory(Vec3 origin, Vec3 aim, int horizon, double gravity, double speed) {
        Vec3 delta = aim.subtract(origin);
        var launch = ThrowableBallistics.solve(delta.x, delta.y, delta.z, gravity, speed, horizon);
        return launch == null ? null : new Vec3(launch.x(), launch.y(), launch.z());
    }
    private static ThrowPlan plan(Human human, LivingEntity target, Vec3 origin, double gravity,
                                  boolean bouncing, int horizon, double speed) {
        Vec3 targetMotion = target.getDeltaMovement();
        // Ignore falling velocity and cap extrapolation: a target can change direction after launch.
        targetMotion = new Vec3(targetMotion.x, 0, targetMotion.z);
        if (targetMotion.lengthSqr() > .16) targetMotion = targetMotion.normalize().scale(.4);
        if (bouncing) return bouncePlan(human, origin, target.position().add(
                lead(targetMotion, horizon)), gravity, horizon, speed);
        Vec3 desired = target.position(), correction = Vec3.ZERO;
        // At most four bounded simulations per preflight, never a per-tick entity scan.
        for (int attempt = 0; attempt < 4; attempt++) {
            Vec3 velocity = trajectory(origin, desired.add(correction), horizon, gravity, speed);
            if (velocity == null) return null;
            Prediction prediction = predict(human, origin, velocity, gravity, bouncing, horizon);
            if (prediction == null) return null;
            desired = target.position().add(lead(targetMotion, prediction.ticks()));
            Vec3 error = desired.subtract(prediction.point());
            if (error.lengthSqr() <= 2.25) return new ThrowPlan(velocity, prediction.point());
            correction = correction.add(error.scale(.8));
            if (correction.lengthSqr() > 144) return null;
        }
        return null;
    }
    /** Timed grenades lead until their actual effect time, not only the first second.
     * Limit displacement to 12 blocks so long fuses cannot aim arbitrarily far away. */
    private static Vec3 lead(Vec3 motion, int ticks) {
        Vec3 offset = motion.scale(Math.min(60, ticks));
        return offset.lengthSqr() > 144 ? offset.normalize().scale(12) : offset;
    }
    private static ThrowPlan bouncePlan(Human human, Vec3 origin, Vec3 desired,
                                        double gravity, int horizon, double speed) {
        Vec3 direction = new Vec3(desired.x - origin.x, 0, desired.z - origin.z).normalize();
        double low = 0, high = 1.2;
        // Search the first landing point so the native bounce ends near the desired effect point.
        // Bisection avoids oscillating between an overshoot and throwing behind the soldier.
        for (int attempt = 0; attempt < 12; attempt++) {
            double fraction = (low + high) * .5;
            Vec3 landing = new Vec3(origin.x + (desired.x - origin.x) * fraction, desired.y,
                    origin.z + (desired.z - origin.z) * fraction);
            Vec3 velocity = trajectory(origin, landing, horizon, gravity, speed);
            if (velocity == null) return null;
            Prediction prediction = predict(human, origin, velocity, gravity, true, horizon);
            if (prediction == null) return null;
            Vec3 error = prediction.point().subtract(desired);
            if (error.lengthSqr() <= 2.25) return new ThrowPlan(velocity, prediction.point());
            if (error.dot(direction) > 0) high = fraction;
            else low = fraction;
        }
        return null;
    }
    /** Mirror native block bounce and tick ordering without spawning prediction entities. */
    private static Prediction predict(Human human, Vec3 origin, Vec3 velocity, double gravity,
                                      boolean bouncing, int horizon) {
        Vec3 pos = origin, motion = velocity;
        for (int step = 0; step < horizon; step++) {
            Vec3 next = pos.add(motion);
            var hit = human.level().clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, human));
            if (hit.getType() != HitResult.Type.MISS) {
                // Walls are not a viable shortcut to a target on the other side.
                if (hit.getDirection() != net.minecraft.core.Direction.UP) return null;
                if (!bouncing) return new Prediction(hit.getLocation(), step + 1);
                motion = motion.multiply(.75, -.25, .75);
                if (motion.y < gravity) motion = new Vec3(motion.x, 0, motion.z);
                next = pos.add(motion);
            }
            if (!human.level().getFluidState(net.minecraft.core.BlockPos.containing(next)).isEmpty()) return null;
            pos = next; motion = motion.scale(DRAG).add(0, -gravity, 0);
        }
        return bouncing ? new Prediction(pos, horizon) : null;
    }
}

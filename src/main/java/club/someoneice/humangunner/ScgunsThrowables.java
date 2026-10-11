package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import top.ribs.scguns.entity.throwable.ThrowableItemEntity;
import top.ribs.scguns.item.*;

/** Inventory-backed tactical ability, no MOVE/LOOK goal and no separate target selector. */
final class ScgunsThrowables {
    private static final String READY = HumanGunner.MOD_ID + ":scguns_throw_ready";
    private ScgunsThrowables() {}
    static boolean supported(ItemStack stack) {
        var item = stack.getItem();
        return item instanceof GrenadeItem || item instanceof GasGrenadeItem || item instanceof ChokeBombItem
                || item instanceof MolotovCocktailItem || item instanceof NailBombItem || item instanceof HellfireBombItem
                || item instanceof SwarmBombItem || item instanceof BeaconGrenadeItem;
    }
    /** Public factory dispatch preserves native entity ownership, detonation and effects. */
    private static ThrowableItemEntity create(Human human, ItemStack stack, int fuse) {
        var item = stack.getItem();
        if (item instanceof GrenadeItem grenade) return grenade.create(human.level(), human, fuse);
        if (item instanceof GasGrenadeItem grenade) return grenade.create(human.level(), human, fuse, 10);
        if (item instanceof ChokeBombItem grenade) return grenade.create(human.level(), human, fuse, 10);
        if (item instanceof MolotovCocktailItem grenade) return grenade.create(human.level(), human, fuse);
        if (item instanceof NailBombItem grenade) return grenade.create(human.level(), human, fuse);
        if (item instanceof HellfireBombItem grenade) return grenade.create(human.level(), human, fuse);
        if (item instanceof SwarmBombItem grenade) return grenade.create(human.level(), human, fuse);
        if (item instanceof BeaconGrenadeItem grenade) return grenade.create(human.level(), human, fuse);
        return null;
    }
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
        for (int slot = 0; slot < human.getData().getInventoryItemsSize(); slot++) {
            ItemStack stack = human.getData().getInventoryItem(slot);
            if (!supported(stack) || !settings.allowedThrowables().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) continue;
            // Gas/lingering hazards need a larger exclusion zone than a simple blast.
            double safety = stack.getItem() instanceof GasGrenadeItem || stack.getItem() instanceof ChokeBombItem
                    ? Math.max(16, settings.friendlySafetyRadius()) : settings.friendlySafetyRadius();
            Vec3 aim = target.position().add(target.getDeltaMovement().scale(4));
            if (!safeArea(human, aim, safety)) return;
            Vec3 origin = human.getEyePosition().add(0, -.1, 0);
            Vec3 velocity = trajectory(origin, aim, settings.fuseTicks());
            if (velocity == null || !clearArc(human, origin, velocity, aim)) return;
            ThrowableItemEntity projectile = create(human, stack, settings.fuseTicks());
            if (projectile == null) return;
            projectile.setItem(stack.copyWithCount(1));
            projectile.setPos(origin); projectile.setDeltaMovement(velocity);
            // Settle the real stack only after the server accepted the native projectile.
            if (!human.level().addFreshEntity(projectile)) return;
            ItemStack remainder = stack.copy(); remainder.shrink(1);
            human.getData().setInventoryItem(slot, remainder);
            human.getPersistentData().putLong(READY, now + settings.throwCooldownTicks());
            human.level().playSound(null, human.blockPosition(), net.minecraft.sounds.SoundEvents.SNOWBALL_THROW,
                    net.minecraft.sounds.SoundSource.HOSTILE, 1, .8F);
            return;
        }
    }
    private static boolean safeArea(Human human, Vec3 aim, double radius) {
        if (human.position().distanceToSqr(aim) < radius * radius) return false;
        for (LivingEntity entity : human.level().getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(aim, aim).inflate(radius))) {
            if (HumanRelations.allied(human, entity)) return false;
        }
        return true;
    }
    /** Native throwable uses .99 drag and .03 gravity. Solve a bounded flight-time candidate. */
    private static Vec3 trajectory(Vec3 origin, Vec3 aim, int fuse) {
        for (int ticks = 12; ticks < Math.min(45, fuse - 5); ticks += 3) {
            double sum = (1 - Math.pow(.99, ticks)) / .01;
            double fall = .03 / .01 * (ticks - sum);
            Vec3 velocity = aim.subtract(origin).add(0, fall, 0).scale(1 / sum);
            if (velocity.lengthSqr() <= 1.0) return velocity;
        }
        return null;
    }
    private static boolean clearArc(Human human, Vec3 origin, Vec3 velocity, Vec3 aim) {
        Vec3 pos = origin, motion = velocity;
        for (int step = 0; step < 45; step++) {
            Vec3 next = pos.add(motion);
            HitResult hit = human.level().clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, human));
            if (hit.getType() != HitResult.Type.MISS) return hit.getLocation().distanceToSqr(aim) < 4;
            if (next.distanceToSqr(aim) < 1) return true;
            pos = next; motion = motion.scale(.99).add(0, -.03, 0);
        }
        return false;
    }
}

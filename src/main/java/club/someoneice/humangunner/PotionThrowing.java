package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.ThrownPotion;

/** Shared distance and trajectory for hand-held and inventory splash potions. */
public final class PotionThrowing {
    public static final double MIN_DISTANCE_SQR = 4.0D * 4.0D;
    public static final double MAX_DISTANCE_SQR = 32.0D * 32.0D;
    private static final float THROW_SPEED = 1.8F;
    private static final String READY = HumanGunner.MOD_ID + ":potion_throw_ready";

    private PotionThrowing() {
    }

    public static boolean inRange(LivingEntity thrower, LivingEntity target) {
        if (target == null || target.level() != thrower.level()) return false;
        double distanceSqr = thrower.distanceToSqr(target);
        return distanceSqr >= MIN_DISTANCE_SQR && distanceSqr <= MAX_DISTANCE_SQR;
    }

    /** Background potion decisions are independent of buff/food/shield/weapon item use. */
    static void tick(Human human) {
        if (human.level().isClientSide || human.getData() == null || !human.isAlive()) return;
        long now = human.level().getGameTime();
        if (now < human.getPersistentData().getLong(READY)) return;
        human.getPersistentData().putLong(READY, now + human.getRandom().nextInt(50, 101));
        LivingEntity target = human.getTarget();
        if (!ThrowableTransactions.available(human, target) || !inRange(human, target)
                || RecoverySupplies.findSplashDebuffSlot(human) < 0
                || human.getRandom().nextFloat() >= .55F) return;
        if (launchDebuff(human, target))
            human.getPersistentData().putLong(READY, now + human.getRandom().nextInt(120, 201));
    }

    /** Authoritative replacement for the legacy Human hand-potion entrypoint. */
    public static boolean throwDebuff(Human human, LivingEntity target) {
        if (human.level().isClientSide) return false;
        long now = human.level().getGameTime();
        if (now < human.getPersistentData().getLong(READY)) return false;
        human.getPersistentData().putLong(READY, now + 40);
        boolean thrown = launchDebuff(human, target);
        if (thrown) human.getPersistentData().putLong(READY, now + human.getRandom().nextInt(120, 201));
        return thrown;
    }
    private static boolean launchDebuff(Human human, LivingEntity target) {
        if (human.getTarget() != target || !ThrowableTransactions.available(human, target) || !inRange(human, target)
                || !noAllyNearTarget(human, target)) return false;
        int slot = RecoverySupplies.findSplashDebuffSlot(human);
        if (slot < 0) return false;
        ItemStack selected = human.getData().getInventoryItem(slot);
        boolean thrown = ThrowableTransactions.submit(human, target, target, slot, actual -> actual == selected,
                one -> {
                    ThrownPotion projectile = new ThrownPotion(human.level(), human);
                    projectile.setItem(one);
                    return prepareShot(projectile, target) ? projectile : null;
                });
        if (thrown) human.level().playSound(null, human.getX(), human.getY(), human.getZ(),
                net.minecraft.sounds.SoundEvents.SPLASH_POTION_THROW, net.minecraft.sounds.SoundSource.HOSTILE,
                .8F, .9F + human.getRandom().nextFloat() * .2F);
        return thrown;
    }
    private static boolean noAllyNearTarget(Human human, LivingEntity target) {
        return human.level().getEntitiesOfClass(Human.class, target.getBoundingBox().inflate(3),
                other -> other != human && HumanRelations.allied(human, other)).isEmpty();
    }
    public static void shoot(ThrownPotion projectile, LivingEntity target) { prepareShot(projectile, target); }
    static boolean prepareShot(ThrownPotion projectile, LivingEntity target) {
        var aim = target.position().add(0, target.getBbHeight() * .45, 0).subtract(projectile.position());
        var launch = ThrowableBallistics.solve(aim.x, aim.y, aim.z, .05, THROW_SPEED, 120);
        if (launch == null) return false;
        projectile.shoot(launch.x(), launch.y(), launch.z(), THROW_SPEED, 3);
        return true;
    }
}

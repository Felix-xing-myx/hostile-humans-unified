package dev.felix.hostilehumans.compat.spartan;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.ranged.HumanArrowCombat;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.equipment.EquipmentBreakSounds;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional reflection boundary for Spartan Weaponry's non-vanilla bolt API. */
public final class SpartanRangedCompat {
    public static final String NPC_BOLT = "hostile_humans_unified:spartan_heavy_crossbow_bolt";
    private static final AtomicBoolean WARNED_BOLT_FAILURE = new AtomicBoolean();
    private static volatile Method createBoltMethod;
    private static volatile boolean createBoltLookupComplete;

    private SpartanRangedCompat() {
    }

    public static boolean isHeavyCrossbow(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("spartanweaponry")
                && id.getPath().endsWith("_heavy_crossbow");
    }

    public static void markChargedForNpc(ItemStack crossbow) {
        if (isHeavyCrossbow(crossbow)) {
            CrossbowItem.setCharged(crossbow, true);
        }
    }

    public static boolean fire(Human human, LivingEntity target) {
        InteractionHand hand = isHeavyCrossbow(human.getMainHandItem())
                ? InteractionHand.MAIN_HAND
                : isHeavyCrossbow(human.getOffhandItem())
                ? InteractionHand.OFF_HAND : null;
        if (hand == null || !HumanArrowCombat.canShoot(human, target)) {
            return false;
        }
        ItemStack crossbow = human.getItemInHand(hand);
        if (!CrossbowItem.isCharged(crossbow)) return false;
        int inserted = HumanArrowCombat.fireVolley(human, target, crossbow, () -> {
            AbstractArrow projectile = createBolt(human);
        projectile.setOwner(human);
        projectile.pickup = AbstractArrow.Pickup.DISALLOWED;
        projectile.getPersistentData().putBoolean(NPC_BOLT, true);

        if (target == null) {
            dev.felix.hostilehumans.behavior.combat.ranged.ManualArrowBallistics.aim(human, projectile, 3.15F, true);
        } else {
        Vec3 origin = projectile.position();
        Vec3 targetPoint = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D);
        double travelTicks = Mth.clamp(origin.distanceTo(targetPoint) / 3.15D, 0.0D, 10.0D);
        Vec3 aim = targetPoint.add(target.getDeltaMovement().scale(travelTicks));
        Vec3 delta = aim.subtract(origin);
        projectile.shoot(
                delta.x, delta.y + 0.020D * travelTicks * travelTicks, delta.z,
                3.15F, 0.75F
        );
        // Apply tier accuracy before entity insertion so the client spawn
        // packet and server simulation begin with the same bolt trajectory.
        HumanRuntime.applyFirstTickArrowBallistics(human, projectile, target);
        }
            return projectile;
        });
        if (inserted == 0) return false;

        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        ItemStack crossbowBeforeDamage = crossbow.copy();
        crossbow.hurtAndBreak(1, human,
                broken -> EquipmentBreakSounds.playNow(human, slot, crossbowBeforeDamage));
        human.level().playSound(
                null, human.getX(), human.getY(), human.getZ(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.0F,
                0.95F + human.getRandom().nextFloat() * 0.10F
        );
        CrossbowItem.setCharged(crossbow, false);
        if (crossbow.hasTag()) {
            crossbow.getTag().remove("ChargedProjectiles");
            crossbow.getTag().remove("Projectile");
        }
        return true;
    }

    private static AbstractArrow createBolt(Human human) {
        Item boltItem = BuiltInRegistries.ITEM.getOptional(
                new ResourceLocation("spartanweaponry", "bolt")
        ).orElse(Items.AIR);
        if (boltItem != Items.AIR) {
            try {
                Method method = resolveCreateBolt(boltItem);
                if (method != null) {
                    Object result = method.invoke(
                            boltItem, human.level(), new ItemStack(boltItem), human
                    );
                    if (result instanceof AbstractArrow arrow) {
                        return arrow;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                if (WARNED_BOLT_FAILURE.compareAndSet(false, true)) {
                    HumanRuntime.LOGGER.error(
                            "Failed to create optional Spartan Weaponry bolt; using vanilla arrow fallback",
                            exception
                    );
                }
            }
        }
        return new Arrow(human.level(), human);
    }

    private static Method resolveCreateBolt(Item boltItem) throws NoSuchMethodException {
        if (!createBoltLookupComplete) {
            synchronized (SpartanRangedCompat.class) {
                if (!createBoltLookupComplete) {
                    createBoltMethod = boltItem.getClass().getMethod(
                            "createBolt", Level.class, ItemStack.class, LivingEntity.class
                    );
                    createBoltMethod.setAccessible(true);
                    createBoltLookupComplete = true;
                }
            }
        }
        return createBoltMethod;
    }
}

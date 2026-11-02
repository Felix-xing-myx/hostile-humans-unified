package dev.felix.hostilehumans.monitor.client;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.mixin.MonitorPredictionEntityAccess;
import dev.felix.hostilehumans.monitor.HumanMonitorNetwork;
import dev.felix.hostilehumans.monitor.MonitorMotionSnapshot;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

/** Unregistered physics shell. Never ticks AI, attacks, receives damage or enters the entity manager.
 * Native travel and collide own acceleration, drag, gravity, fluids, climbing and stepping.
 * Only move's physical result and jump's motion are bridged; world callbacks are excluded. */
public final class MonitorPredictionBody extends LivingEntity {
    private final Human tracked;
    private final Map<MobEffect, MobEffectInstance> effects = new HashMap<>();
    private MonitorMotionSnapshot.Physics physics;
    private int jumpCooldown;
    private MonitorPredictionEnvironment.Reason reason = MonitorPredictionEnvironment.Reason.WAITING_FOR_AUTHORITY;

    MonitorPredictionBody(Human tracked) {
        super(physicsType(tracked), tracked.level());
        this.tracked = tracked;
        setSilent(true);
    }
    @SuppressWarnings("unchecked")
    private static EntityType<? extends LivingEntity> physicsType(Human tracked) {
        // Human's registered type supplies attributes, never a factory call or a spawned Human.
        return (EntityType<? extends LivingEntity>) tracked.getType();
    }
    void reset(MonitorMotionSnapshot snapshot) {
        physics = snapshot.physics();
        var access = (MonitorPredictionEntityAccess) (Object) this;
        access.hostileHumans$dimensions(EntityDimensions.scalable(physics.width(), physics.height()));
        access.hostileHumans$eyeHeight(physics.eyeHeight());
        getAttribute(Attributes.MOVEMENT_SPEED).removeModifiers();
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(physics.movementSpeed());
        getAttribute(ForgeMod.ENTITY_GRAVITY.get()).removeModifiers();
        getAttribute(ForgeMod.ENTITY_GRAVITY.get()).setBaseValue(physics.gravity());
        getAttribute(ForgeMod.SWIM_SPEED.get()).removeModifiers();
        getAttribute(ForgeMod.SWIM_SPEED.get()).setBaseValue(physics.swimSpeed());
        // Synchronize sprint's flag and native transient modifier after replacing the attribute map.
        super.setSprinting(false); super.setSprinting(physics.sprinting());
        setNoGravity(physics.noGravity());
        effects.clear();
        effect(MobEffects.JUMP, physics.jumpBoost()); effect(MobEffects.LEVITATION, physics.levitation());
        effect(MobEffects.SLOW_FALLING, physics.slowFalling() ? 0 : -1);
        effect(MobEffects.DOLPHINS_GRACE, physics.dolphinsGrace() ? 0 : -1);
        setPos(snapshot.position().x, snapshot.position().y, snapshot.position().z);
        setDeltaMovement(snapshot.velocity()); setOnGround(snapshot.onGround());
        horizontalCollision = verticalCollision = verticalCollisionBelow = false;
        jumpCooldown = snapshot.jumpCooldown();
        reason = physics.supported() ? MonitorPredictionEnvironment.Reason.PREDICTING : MonitorPredictionEnvironment.Reason.SERVER_STATE;
    }
    private void effect(MobEffect effect, int amplifier) {
        if (amplifier >= 0) effects.put(effect, new MobEffectInstance(effect, Integer.MAX_VALUE, amplifier));
    }
    boolean available() {
        if (!physics.supported()) { reason = MonitorPredictionEnvironment.Reason.SERVER_STATE; return false; }
        // Native enchantment inspection only reads the real synchronized gear. Never manufacture a
        // prediction ItemStack: Forge may attach capabilities/events even to an unregistered copy.
        if (EnchantmentHelper.getDepthStrider(tracked) != physics.depthStrider()) {
            reason = MonitorPredictionEnvironment.Reason.EQUIPMENT_SYNC; return false;
        }
        reason = MonitorPredictionEnvironment.inspect(level(), getBoundingBox().inflate(0.001).expandTowards(0, -0.1, 0));
        return reason == MonitorPredictionEnvironment.Reason.PREDICTING;
    }
    boolean step(HumanMonitorNetwork.Control input) {
        if (!available()) return false;
        // Entity baseTick's fluid update is isolated from tick, effects and all Human behavior.
        updateInWaterStateAndDoFluidPushing();
        ((MonitorPredictionEntityAccess) (Object) this).hostileHumans$updateFluidEyes();
        if (jumpCooldown > 0) jumpCooldown--;
        Vec3 velocity = getDeltaMovement();
        setDeltaMovement(small(velocity.x), small(velocity.y), small(velocity.z));
        setYRot(input.yaw()); setXRot(input.pitch());
        setSprinting(MonitorPredictionIntent.sprinting(input.active(), input.sprint(), input.forward(),
                input.sneak(), input.attack(), input.use()));
        setJumping(input.jump()); // Native travel also reads this flag for climbing, independently of the jump impulse.
        setShiftKeyDown(input.sneak());
        setSpeed((float) getAttributeValue(Attributes.MOVEMENT_SPEED));
        if (input.jump()) {
            double depth = isInLava() ? getFluidHeight(net.minecraft.tags.FluidTags.LAVA)
                    : getFluidHeight(net.minecraft.tags.FluidTags.WATER);
            if ((isInWater() || isInLava()) && (!onGround() || depth > getFluidJumpThreshold())) {
                jumpInFluid(isInLava() ? ForgeMod.LAVA_TYPE.get() : ForgeMod.WATER_TYPE.get());
            } else if (onGround() && jumpCooldown == 0) { jumpFromGround(); jumpCooldown = 10; }
        } else jumpCooldown = 0;
        float factor = input.sneak() ? .3F : 1;
        try {
            super.travel(new Vec3(input.sideways() * factor * .98F, 0, input.forward() * factor * .98F));
            return true;
        } catch (UnsafeEnvironment ignored) { return false; }
    }
    private static double small(double value) { return Math.abs(value) < .003 ? 0 : value; }
    MonitorPredictionEnvironment.Reason reason() { return reason; }

    @Override public void move(MoverType type, Vec3 requested) {
        reason = MonitorPredictionEnvironment.inspect(level(), getBoundingBox().expandTowards(requested)
                .expandTowards(0, getStepHeight(), 0).inflate(0.001).expandTowards(0, -0.1, 0));
        if (reason != MonitorPredictionEnvironment.Reason.PREDICTING) throw UnsafeEnvironment.INSTANCE;
        Vec3 moved = ((MonitorPredictionEntityAccess) (Object) this).hostileHumans$collide(requested);
        var result = MonitorPhysicalMotion.collision(requested, moved, getDeltaMovement());
        horizontalCollision = result.horizontal();
        verticalCollision = result.vertical();
        verticalCollisionBelow = result.below();
        minorHorizontalCollision = horizontalCollision && isHorizontalCollisionMinor(moved);
        setPos(getX() + moved.x, getY() + moved.y, getZ() + moved.z);
        setOnGroundWithKnownMovement(verticalCollisionBelow, moved);
        float factor = getBlockSpeedFactor();
        setDeltaMovement(result.velocity().multiply(factor, 1, factor));
        // No Block.stepOn/updateEntityAfterFallOn/checkInsideBlocks, fall damage or game events.
    }
    /** Vanilla jumpFromGround movement exactly, excluding ForgeHooks.onLivingJump's game event. */
    @Override protected void jumpFromGround() {
        setDeltaMovement(MonitorPhysicalMotion.jump(getDeltaMovement(), getJumpPower(), isSprinting(), getYRot()));
        hasImpulse = true;
    }
    @Override public boolean isControlledByLocalInstance() { return true; }
    @Override public boolean isEffectiveAi() { return false; }
    @Override public float getStepHeight() { return physics == null ? 0 : physics.stepHeight(); }
    @Override public boolean canCollideWith(Entity other) { return other != tracked && super.canCollideWith(other); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hasEffect(MobEffect effect) { return effects != null && effects.containsKey(effect); }
    @Override public MobEffectInstance getEffect(MobEffect effect) { return effects == null ? null : effects.get(effect); }
    @Override public Iterable<ItemStack> getArmorSlots() { return tracked == null ? java.util.List.of() : tracked.getArmorSlots(); }
    @Override public ItemStack getItemBySlot(EquipmentSlot slot) { return tracked == null ? ItemStack.EMPTY : tracked.getItemBySlot(slot); }
    @Override public void setItemSlot(EquipmentSlot slot, ItemStack stack) { }
    @Override public HumanoidArm getMainArm() { return HumanoidArm.RIGHT; }
    @Override public void tick() { }
    @Override public void baseTick() { }
    @Override public void aiStep() { }
    @Override protected void updateWalkAnimation(float speed) { }
    @Override protected void doWaterSplashEffect() { }
    @Override protected MovementEmission getMovementEmission() { return MovementEmission.NONE; }
    @Override protected void checkInsideBlocks() { }
    @Override protected void checkFallDamage(double delta, boolean grounded, BlockState state, BlockPos pos) { }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public void playSound(SoundEvent sound, float volume, float pitch) { }
    @Override public void playSound(SoundEvent sound) { }
    @Override public void gameEvent(GameEvent event) { }
    @Override public void gameEvent(GameEvent event, Entity source) { }
    private static final class UnsafeEnvironment extends RuntimeException {
        static final UnsafeEnvironment INSTANCE = new UnsafeEnvironment();
        private UnsafeEnvironment() { super(null, null, false, false); }
    }
}

package com.craftix.hostile_humans.entity.ai.control;

import com.craftix.hostile_humans.Config;
import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HumanEntityWalkControl
extends MoveControl {
    int skipTicks;
    Human human;

    public HumanEntityWalkControl(Mob mob) {
        super(mob);
        this.human = (Human)mob;
    }

    public void tick() {
        // STRAFE is the only operation that owns lateral input. Navigation,
        // jumping and idle ticks must clear the previous frame's xxa value;
        // otherwise a bow orbit leaves a permanent sideways walk behind.
        if (this.operation != MoveControl.Operation.STRAFE) {
            this.mob.setXxa(0.0f);
        }
        if (this.skipTicks > 0) {
            --this.skipTicks;
            if (this.mob.getTarget() != null) {
                this.mob.lookAt((Entity)this.mob.getTarget(), 0.0f, 0.0f);
            }
            return;
        }
        if (this.mob.onGround() && this.skipTicks == 0) {
            this.skipTicks = -1;
            this.mob.getNavigation().timeLastRecompute = 0L;
            this.mob.getNavigation().recomputePath();
            if (this.mob.getTarget() != null) {
                this.mob.lookAt((Entity)this.mob.getTarget(), 0.0f, 0.0f);
            }
            return;
        }
        if (this.operation == MoveControl.Operation.STRAFE) {
            float f9;
            float f = (float)this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
            float f1 = (float)this.speedModifier * f;
            float f2 = this.strafeForwards;
            float f3 = this.strafeRight;
            float f4 = Mth.sqrt((float)(f2 * f2 + f3 * f3));
            if (f4 < 1.0f) {
                f4 = 1.0f;
            }
            f4 = f1 / f4;
            float f5 = Mth.sin((float)(this.mob.getYRot() * ((float)Math.PI / 180)));
            float f6 = Mth.cos((float)(this.mob.getYRot() * ((float)Math.PI / 180)));
            float f7 = (f2 *= f4) * f6 - (f3 *= f4) * f5;
            if (!this.isWalkable(f7, f9 = f3 * f6 + f2 * f5)) {
                this.strafeForwards = 1.0f;
                this.strafeRight = 0.0f;
            }
            this.mob.setSpeed(f1);
            this.mob.setZza(this.strafeForwards);
            this.mob.setXxa(this.strafeRight);
            this.operation = MoveControl.Operation.WAIT;
        } else if (this.operation == MoveControl.Operation.MOVE_TO) {
            this.operation = MoveControl.Operation.WAIT;
            double d0 = this.wantedX - this.mob.getX();
            double d1 = this.wantedZ - this.mob.getZ();
            double d2 = this.wantedY - this.mob.getY();
            double d3 = d0 * d0 + d2 * d2 + d1 * d1;
            if (d3 < 2.500000277905201E-7) {
                this.mob.setZza(0.0f);
                this.mob.setXxa(0.0f);
                return;
            }
            float f9 = (float)(Mth.atan2((double)d1, (double)d0) * 57.2957763671875) - 90.0f;
            if (!this.human.isFleeing || this.human.onGround()) {
                this.mob.setYRot(this.rotlerp(this.mob.getYRot(), f9, 90.0f));
            }
            this.mob.setSpeed((float)(this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
            BlockPos blockpos = this.mob.blockPosition();
            BlockState blockstate = this.mob.level().getBlockState(blockpos);
            VoxelShape voxelshape = blockstate.getCollisionShape((BlockGetter)this.mob.level(), blockpos);
            if (d2 > (double)this.mob.getStepHeight() && d0 * d0 + d1 * d1 < (double)Math.max(1.0f, this.mob.getBbWidth()) || !voxelshape.isEmpty() && this.mob.getY() < voxelshape.max(Direction.Axis.Y) + (double)blockpos.getY() && !blockstate.is(BlockTags.DOORS) && !blockstate.is(BlockTags.FENCES)) {
                this.mob.getJumpControl().jump();
                this.operation = MoveControl.Operation.JUMPING;
            }
            if (((Boolean)Config.runJump.get()).booleanValue() && this.human.onPlayerJumpCoolDown == 0) {
                if (this.human.isFleeing && (double)this.human.getRandom().nextFloat() < 0.1 && this.human.toAvoid != null) {
                    if (this.mob.onGround()) {
                        this.mob.getJumpControl().jump();
                        this.operation = MoveControl.Operation.JUMPING;
                        this.addVelocityToMobTowardsPosition((LivingEntity)this.mob, this.human.toAvoid.getX(), this.human.toAvoid.getY(), this.human.toAvoid.getZ(), -0.8);
                        this.mob.setYRot((float)Math.toDegrees(Math.atan2(this.mob.getDeltaMovement().z, this.mob.getDeltaMovement().x)) - 90.0f);
                    }
                } else if (!this.human.isFleeing && this.human.getTarget() != null && this.mob.distanceTo((Entity)this.human.getTarget()) >= 5.0f && HumanUtil.isLookingAtTarget((LivingEntity)this.mob, (Entity)this.human.getTarget()) && this.mob.onGround() && !HumanUtil.isRangedWeapon(this.human.getMainHandItem()) && !HumanUtil.isTrident(this.human.getMainHandItem())) {
                    this.mob.getJumpControl().jump();
                    this.operation = MoveControl.Operation.JUMPING;
                    this.addVelocityToMobTowardsPosition((LivingEntity)this.mob, this.human.getTarget().getX(), this.human.getTarget().getY(), this.human.getTarget().getZ(), 0.8);
                    this.mob.lookAt((Entity)this.human.getTarget(), 0.0f, 0.0f);
                    this.mob.getNavigation().recomputePath();
                    this.skipTicks = 1;
                }
            }
            if (((Boolean)Config.attackJump.get()).booleanValue() && this.human.meleeFlurryHitsRemaining <= 0 && this.human.meleeFlurryDamageTicks <= 0 && this.human.getRandom().nextFloat() < 0.1f && this.human.onGround() && this.human.getTarget() != null && this.human.getTarget().distanceTo((Entity)this.human) < 2.0f && HumanUtil.isMeleeWeapon(this.human.getMainHandItem())) {
                this.mob.getJumpControl().jump();
            }
        } else if (this.operation == MoveControl.Operation.JUMPING) {
            this.mob.setSpeed((float)(this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
            if (this.mob.onGround()) {
                this.operation = MoveControl.Operation.WAIT;
            }
        } else {
            this.mob.setZza(0.0f);
        }
    }

    public void addVelocityToMobTowardsPosition(LivingEntity entity, double x, double y, double z, double speed) {
        double d0 = x - entity.getX();
        double d1 = y - entity.getY();
        double d2 = z - entity.getZ();
        double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
        entity.setDeltaMovement(d0 / d3 * speed, 0.0, d2 / d3 * speed);
    }
}


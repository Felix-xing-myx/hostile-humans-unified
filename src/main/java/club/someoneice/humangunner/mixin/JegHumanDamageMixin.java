package club.someoneice.humangunner.mixin;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

/** Undo only JEG's NPC divisor before its normal damage pipeline; configured scaling runs once later. */
@Pseudo
@Mixin(targets = "ttv.migami.jeg.entity.projectile.ProjectileEntity", remap = false)
public abstract class JegHumanDamageMixin {
    @Shadow public abstract LivingEntity getShooter();
    /** Native NPCs randomly ignore their own entity type. HHU uses explicit faction filtering instead. */
    @ModifyConstant(method = "ignoreEntity", constant = @Constant(doubleValue = .9D), require = 1, allow = 1)
    private double hostileHumans$explicitFactions(double original) {
        return getShooter() instanceof Human ? 0D : original;
    }
    @ModifyArg(method = "onHitEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
            index = 1, require = 1, allow = 1)
    private float hostileHumans$restorePlayerDamage(float amount) {
        LivingEntity shooter = getShooter();
        return shooter instanceof Human ? amount * (shooter.level().getDifficulty() == Difficulty.HARD ? 1.5F : 2F) : amount;
    }
}

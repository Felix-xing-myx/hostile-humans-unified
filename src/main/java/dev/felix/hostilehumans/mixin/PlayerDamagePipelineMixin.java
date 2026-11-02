package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.damage.HumanDamagePipeline;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Player overrides actuallyHurt, so its call site must join the same armor seam. */
@Mixin(Player.class)
public abstract class PlayerDamagePipelineMixin extends LivingEntity {
    protected PlayerDamagePipelineMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }
    @Redirect(method = "m_6475_(Lnet/minecraft/world/damagesource/DamageSource;F)V", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;m_21161_(Lnet/minecraft/world/damagesource/DamageSource;F)F"), require = 1, allow = 1)
    private float hostileHumansUnified$partitionPlayerArmor(Player player, DamageSource source, float amount) {
        // The armor method is declared by LivingEntity, not Player. A Player @Shadow cannot bind it.
        return HumanDamagePipeline.armor(player, source, amount,
                normal -> getDamageAfterArmorAbsorb(source, (float) normal));
    }
}

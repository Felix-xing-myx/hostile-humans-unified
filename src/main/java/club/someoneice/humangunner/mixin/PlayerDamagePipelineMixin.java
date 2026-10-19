package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.HumanDamagePipeline;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Player overrides actuallyHurt, so its call site must join the same armor seam. */
@Mixin(Player.class)
public abstract class PlayerDamagePipelineMixin {
    @Shadow(remap = false) protected abstract float m_21161_(DamageSource source, float amount);
    @Redirect(method = "m_6475_(Lnet/minecraft/world/damagesource/DamageSource;F)V", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;m_21161_(Lnet/minecraft/world/damagesource/DamageSource;F)F"), require = 1, allow = 1)
    private float hostileHumansUnified$partitionPlayerArmor(Player player, DamageSource source, float amount) {
        return HumanDamagePipeline.armor(player, source, amount, normal -> m_21161_(source, (float) normal));
    }
}

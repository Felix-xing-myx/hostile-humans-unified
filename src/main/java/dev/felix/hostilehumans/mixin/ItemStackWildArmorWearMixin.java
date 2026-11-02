package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.inventory.WildArmorWear;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Limit only the native damage transaction; preserve Forge item hooks and outer break handling. */
@Mixin(ItemStack.class)
public abstract class ItemStackWildArmorWearMixin {
    // This source set has no generated refmap. Match the production SRG seam explicitly.
    @Redirect(method = "m_41622_(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V", remap = false,
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;m_220157_(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z"),
            require = 1, allow = 1)
    private boolean hhu$limitWildArmor(ItemStack stack, int amount, RandomSource random, ServerPlayer player,
            int requested, LivingEntity owner, Consumer<LivingEntity> onBreak) {
        return WildArmorWear.hurt(stack, amount, random, player, owner);
    }
}

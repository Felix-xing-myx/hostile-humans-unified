package dev.felix.hostilehumans.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/** Filter the native candidate list before allocating entries for unrelated items. */
@Mixin(ItemEntity.class)
public abstract class DroppedItemMergeQueryMixin {
    @ModifyArg(method = "m_32069_()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            index = 2, remap = false, require = 0)
    private Predicate<ItemEntity> hhu$matchingItemCandidates(Predicate<ItemEntity> vanilla) {
        var type = ((ItemEntity) (Object) this).getItem().getItem();
        return candidate -> candidate.getItem().is(type) && vanilla.test(candidate);
    }
}

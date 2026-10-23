package club.someoneice.humangunner.mixin;

import com.craftix.hostile_humans.client.renderer.HumanShieldPresentation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Scoped native model-property bridge; never installs a global replacement for players or other mobs. */
@Mixin(ItemOverrides.class)
public abstract class HumanShieldItemPropertyMixin {
    @Redirect(method = "resolve", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/item/ItemProperties;getProperty(Lnet/minecraft/world/item/Item;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/item/ItemPropertyFunction;"), require = 0)
    private ItemPropertyFunction hostileHumans$shieldProperty(Item item, ResourceLocation property,
            BakedModel model, ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
        return HumanShieldPresentation.blockingProperty(ItemProperties.getProperty(item, property), property, stack, entity);
    }
}

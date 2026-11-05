package dev.felix.hostilehumans.developer;

import net.minecraft.world.item.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Read-only player documentation; grants no authoring privileges. */
public final class DeveloperGuideItem extends Item {
    public DeveloperGuideItem(Properties properties){super(properties);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(level.isClientSide)net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                ()->()->dev.felix.hostilehumans.developer.client.DeveloperGuideScreen.open());
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
}

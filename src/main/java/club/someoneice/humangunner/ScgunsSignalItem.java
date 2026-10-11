package club.someoneice.humangunner;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Stable HHU registry ID; inert without the optional integration. */
final class ScgunsSignalItem extends Item {
    private static final String READY = HumanGunner.MOD_ID + ":scguns_flare_ready";
    ScgunsSignalItem(Properties properties) { super(properties); }
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        if (!(player instanceof ServerPlayer serverPlayer) || !ScgunsCompat.enabled()) return InteractionResultHolder.fail(stack);
        long now = level.getGameTime();
        if (now < player.getPersistentData().getLong(READY) || !ScgunsCompat.launchFlare(serverPlayer)) return InteractionResultHolder.fail(stack);
        player.getPersistentData().putLong(READY, now + UnifiedConfig.get().scguns().flareCooldownTicks());
        player.getCooldowns().addCooldown(this, UnifiedConfig.get().scguns().flareCooldownTicks());
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }
}

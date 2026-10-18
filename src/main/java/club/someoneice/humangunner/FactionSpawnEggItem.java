package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeSpawnEggItem;
import java.util.function.Supplier;

/** Vanilla placement adapter: fixes the type and explicit faction before any initialization observes the new resident. */
public final class FactionSpawnEggItem extends ForgeSpawnEggItem {
    private final Supplier<? extends EntityType<? extends Human>> humanType;
    private final String faction;
    private final boolean boss;
    FactionSpawnEggItem(Supplier<? extends EntityType<? extends Human>> type, String faction, boolean boss,
                        int background, int highlight, Properties properties) {
        super(type, background, highlight, properties);
        this.humanType = type; this.faction = faction; this.boss = boss;
    }
    @Override public EntityType<?> getType(CompoundTag tag) { return humanType.get(); }
    private void identity(ItemStack stack) {
        CompoundTag entity = stack.getOrCreateTagElement("EntityTag");
        CompoundTag forge = entity.getCompound("ForgeData");
        forge.putString(HumanFactions.FACTION, FactionDefinitions.id(faction).toString());
        forge.putBoolean(HumanFactions.BOSS, boss);
        entity.put("ForgeData", forge);
    }
    private boolean allowed(Player player) {
        boolean enabled = !boss || UnifiedConfig.get().featureEnabled("boss", true);
        if (!enabled && player != null && !player.level().isClientSide)
            player.displayClientMessage(Component.translatable("message.hostile_humans_unified.boss.disabled"), true);
        return enabled;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide && !allowed(context.getPlayer())) return InteractionResult.FAIL;
        if (!context.getLevel().isClientSide) identity(context.getItemInHand());
        return super.useOn(context);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && !allowed(player)) return InteractionResultHolder.fail(player.getItemInHand(hand));
        if (!level.isClientSide) identity(player.getItemInHand(hand));
        return super.use(level, player, hand);
    }
}

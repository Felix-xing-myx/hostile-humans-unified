package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.ModEntityType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.List;

public final class HumanSignalItem extends Item {
    private final int tier;
    private final boolean hostile;

    HumanSignalItem(int tier, boolean hostile, Properties properties) {
        super(properties);
        this.tier = tier;
        this.hostile = hostile;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(hostile ? "item.humangunner.hostile_beacon.tooltip"
                : "item.humangunner.signal_flare.tooltip", RecruitmentPolicy.waveSize(tier)).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer))
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        String key = SignalPolicy.cooldownKey(hostile, tier);
        long now = server.getGameTime();
        long ready = player.getPersistentData().getLong(key);
        if (now < ready) {
            player.displayClientMessage(Component.translatable("message.humangunner.signal.cooldown",
                    (ready - now + 19) / 20).withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        int spawned = spawnWave(server, serverPlayer);
        if (spawned == 0) {
            player.displayClientMessage(Component.translatable("message.humangunner.signal.no_space")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        player.getPersistentData().putLong(key, now + SignalPolicy.cooldownTicks(hostile));
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.displayClientMessage(Component.translatable(hostile
                ? "message.humangunner.hostile_wave" : "message.humangunner.reinforcements", spawned), true);
        return InteractionResultHolder.consume(stack);
    }

    @SuppressWarnings("unchecked")
    private int spawnWave(ServerLevel level, ServerPlayer player) {
        EntityType<? extends Human> type = (EntityType<? extends Human>) switch (tier) {
            case 0 -> ModEntityType.ROAMER.get();
            case 1 -> ModEntityType.HUMAN1.get();
            case 2 -> ModEntityType.HUMAN2.get();
            default -> HumanGunnerRegistries.TIER_THREE_HUMAN.get();
        };
        int count = 0;
        for (int member = 0; member < RecruitmentPolicy.waveSize(tier); member++) {
            BlockPos pos = findPosition(level, player, SignalPolicy.minimumDistance(hostile),
                    SignalPolicy.maximumDistance(hostile), hostile);
            if (pos == null) continue;
            Human human = type.create(level);
            if (human == null) continue;
            human.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(human)) continue;
            human.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            if (!hostile) {
                human.getPersistentData().putUUID(HumanRelations.TEMP_OWNER, player.getUUID());
                human.getPersistentData().putLong(HumanRelations.TEMP_UNTIL,
                        level.getGameTime() + SignalPolicy.SUPPORT_LIFETIME_TICKS);
                human.getPersistentData().putBoolean(HumanRelations.NO_DROPS, true);
                human.setTarget(null);
            } else {
                human.getPersistentData().putUUID(HumanRelations.FORCED_HOSTILE_TARGET, player.getUUID());
                human.setTarget(player);
            }
            if (level.addFreshEntity(human)) count++;
        }
        return count;
    }

    private static BlockPos findPosition(ServerLevel level, ServerPlayer owner, int min, int max,
                                         boolean hostile) {
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            int distance = min + level.random.nextInt(max - min + 1);
            int x = owner.blockPosition().getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = owner.blockPosition().getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            BlockPos pos = hostile
                    ? new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z)
                    : SafePositions.nearFloor(level, x, z, owner.getBlockY());
            if (pos == null) continue;
            if (SafePositions.isSafe(level, pos)) return pos;
        }
        return null;
    }

}

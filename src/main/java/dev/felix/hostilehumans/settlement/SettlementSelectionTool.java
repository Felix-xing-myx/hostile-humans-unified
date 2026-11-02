package dev.felix.hostilehumans.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Server-held binding and two inclusive block corners; held item data never becomes authority. */
public final class SettlementSelectionTool extends Item {
    private record Binding(
            ResourceLocation dimension,
            BlockPos information,
            String region,
            BlockPos first,
            boolean exclude,
            int revision) {}

    private static final Map<ServerPlayer, Binding> BINDINGS = new WeakHashMap<>();

    public SettlementSelectionTool(Properties properties) {
        super(properties);
    }

    public static String mode(
            ServerPlayer player, BlockPos information, String region, boolean exclude) {
        var info = SettlementAuthoring.information(player, information);
        if (info == null) return "author unavailable";
        BINDINGS.put(
                player,
                new Binding(
                        player.level().dimension().location(),
                        information.immutable(),
                        region,
                        null,
                        exclude,
                        info.revision()));
        return "selection mode updated";
    }

    /**
     * Initial binding is local; subsequent block clicks may span a large loaded author template.
     */
    static SettlementInformationBlockEntity boundInformation(
            ServerPlayer player, BlockPos information) {
        var binding = BINDINGS.get(player);
        if (!SettlementAccess.author(player)
                || binding == null
                || !binding.information().equals(information)
                || !binding.dimension().equals(player.level().dimension().location())
                || !player.serverLevel().hasChunkAt(information)) return null;
        var entity = player.serverLevel().getBlockEntity(information);
        return entity instanceof SettlementInformationBlockEntity info
                        && info.revision() == binding.revision()
                ? info
                : null;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return useOn(context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        if (!SettlementAccess.author(player)) return InteractionResult.FAIL;
        BlockPos point = context.getClickedPos();
        if (player.serverLevel().getBlockEntity(point)
                instanceof SettlementInformationBlockEntity) {
            SettlementAuthoring.initialize(player, point);
            mode(player, point, "outer", false);
            SettlementNetwork.openAuthor(player, point);
            player.displayClientMessage(
                    Component.literal(
                            "Bound information block. Choose region/include or exclude in editor,"
                                + " then click two block cells."),
                    true);
            return InteractionResult.CONSUME;
        }
        Binding binding = BINDINGS.get(player);
        if (binding == null || !binding.dimension().equals(player.level().dimension().location())) {
            BINDINGS.remove(player);
            player.displayClientMessage(
                    Component.literal("Bind an information block first."), true);
            return InteractionResult.FAIL;
        }
        var info = boundInformation(player, binding.information());
        if (info == null || info.revision() != binding.revision()) {
            BINDINGS.remove(player);
            player.displayClientMessage(Component.literal("Binding stale; reopen editor."), true);
            return InteractionResult.FAIL;
        }
        if (binding.first() == null) {
            BINDINGS.put(
                    player,
                    new Binding(
                            binding.dimension(),
                            binding.information(),
                            binding.region(),
                            point.immutable(),
                            binding.exclude(),
                            binding.revision()));
            player.displayClientMessage(
                    Component.literal("First corner: " + point.toShortString()), true);
        } else {
            String result =
                    SettlementAuthoring.addBox(
                            player,
                            binding.information(),
                            binding.region(),
                            binding.exclude(),
                            binding.first(),
                            point);
            BINDINGS.put(
                    player,
                    new Binding(
                            binding.dimension(),
                            binding.information(),
                            binding.region(),
                            null,
                            binding.exclude(),
                            info.revision()));
            player.displayClientMessage(Component.literal(result), true);
            if (player.distanceToSqr(
                            binding.information().getX() + .5,
                            binding.information().getY() + .5,
                            binding.information().getZ() + .5)
                    <= 4096) SettlementNetwork.openAuthor(player, binding.information());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(
            ItemStack stack, Level level, List<Component> lines, TooltipFlag flags) {
        lines.add(
                Component.literal(
                        "Author: bind information block; editor chooses include/exclude; click"
                            + " first and second block cells."));
        lines.add(
                Component.literal(
                        "Coordinates are inclusive. Unsubmitted UI edits can be discarded;"
                            + " submitted boxes persist in the information block."));
    }
}

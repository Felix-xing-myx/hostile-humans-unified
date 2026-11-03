package dev.felix.hostilehumans.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Authoritative corners live in a revision-bound server session. */
public final class SettlementSelectionTool extends Item {
    enum Mode { ADD, REPLACE, EDIT, BOUNDS, FACILITY, MOVE_FACILITY, PLACE, VIEW }

    private record Binding(ResourceLocation dimension, BlockPos information,
            SettlementInformationBlockEntity carrier, String region, boolean exclude, boolean currentOnly, int boxIndex, Mode mode,
            int revision, BlockPos first, BlockPos second) {
        Binding corners(BlockPos a, BlockPos b) {
            return new Binding(dimension, information, carrier, region, exclude, currentOnly, boxIndex, mode, revision, a, b);
        }
    }

    private static final Map<ServerPlayer, Binding> BINDINGS = new WeakHashMap<>();

    public SettlementSelectionTool(Properties properties) {
        super(properties);
    }

    static String mode(ServerPlayer player, BlockPos position, String region, boolean exclude,
            boolean currentOnly, int boxIndex, Mode mode, int revision) {
        var info = SettlementAuthoring.information(player, position);
        if (info == null || info.revision() != revision) return "author unavailable or stale";
        if (mode == Mode.PLACE && (!player.canUseGameMasterBlocks()
                || ResourceLocation.tryParse(region) == null))
            return "template placement requires a valid ID and creative operator permission";
        var binding = new Binding(player.level().dimension().location(), position.immutable(),
                info, region, exclude, currentOnly, boxIndex, mode, revision, null, null);
        SettlementTemplateFiles.cancel(player);
        publish(player, binding);
        BINDINGS.put(player, binding);
        if (mode == Mode.PLACE) tell(player, "pick_placement");
        else tell(player, (mode == Mode.FACILITY || mode == Mode.MOVE_FACILITY) ? "pick" : "first_corner");
        return SettlementNetwork.SELECTION_READY;
    }

    static SettlementInformationBlockEntity boundInformation(ServerPlayer player, BlockPos position) {
        var binding = BINDINGS.get(player);
        if (!SettlementAccess.author(player) || binding == null
                || !binding.information().equals(position)
                || !binding.dimension().equals(player.level().dimension().location())
                || !player.serverLevel().hasChunkAt(position)) return null;
        return player.serverLevel().getBlockEntity(position) == binding.carrier()
                && binding.carrier().revision() == binding.revision() ? binding.carrier() : null;
    }

    private static void publish(ServerPlayer player, Binding binding) {
        var info = binding.carrier();
        var author = SettlementCodec.template(info.author());
        var transform = SettlementAuthoring.currentTransform(player, info);
        var boxes = new ArrayList<SettlementPreview.Box>();
        var size = author.size();
        boxes.add(new SettlementPreview.Box(new RegionBox(0, 0, 0,
                size.x() - 1, size.y() - 1, size.z() - 1).transform(transform), 0xffffff, "NBT"));
        for (var region : author.regions()) {
            if (binding.currentOnly() && !binding.region().isEmpty() && !region.id().equals(binding.region())) continue;
            int color = region.layer() == SettlementRegion.Layer.INNER ? 0x37d5ff : 0xffbd57;
            for (var box : region.includes())
                boxes.add(new SettlementPreview.Box(box.transform(transform), color, region.name()));
            for (var box : region.excludes())
                boxes.add(new SettlementPreview.Box(box.transform(transform), 0xff5362, "− " + region.name()));
        }
        for (var facility : author.facilities()) {
            if (binding.currentOnly() && !binding.region().isEmpty() && !facility.region().equals(binding.region())) continue;
            var p = transform.apply(facility.position());
            boxes.add(new SettlementPreview.Box(RegionBox.between(p, p), 0xb78cff, facility.type().name()));
        }
        SettlementNetwork.preview(player, new SettlementPreview(binding.dimension().toString(),
                boxes, binding.first(), binding.second(), true));
    }

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(
                "item.hostile_humans_unified.settlement_selection_tool." + key, args), true);
    }

    static void clear(ServerPlayer player) {
        if (BINDINGS.remove(player) != null) SettlementNetwork.preview(player, SettlementPreview.EMPTY);
    }

    static void tick(ServerPlayer player) {
        var binding = BINDINGS.get(player);
        if (binding != null && boundInformation(player, binding.information()) == null) clear(player);
    }

    private static boolean active(ServerPlayer player, Binding binding) {
        if (binding != null && boundInformation(player, binding.information()) != null) return true;
        BINDINGS.remove(player);
        SettlementNetwork.preview(player, SettlementPreview.EMPTY);
        tell(player, "bind_first");
        return false;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return useOn(context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        if (!SettlementAccess.author(player)) return InteractionResult.FAIL;
        var binding = BINDINGS.get(player);
        if (SettlementTemplateFiles.hasPreview(player)) {
            if (player.isShiftKeyDown()) finishPlacement(player);
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown() && binding != null) return confirm(player, binding);
        BlockPos point = context.getClickedPos().immutable();
        if (player.serverLevel().getBlockEntity(point) instanceof SettlementInformationBlockEntity
                && (binding == null || binding.mode() != Mode.BOUNDS)) {
            SettlementAuthoring.initialize(player, point);
            SettlementNetwork.openAuthor(player, point);
            return InteractionResult.CONSUME;
        }
        if (!active(player, binding)) return InteractionResult.FAIL;
        if (binding.mode() == Mode.VIEW) {
            tell(player, "bind_first");
            return InteractionResult.CONSUME;
        }
        try {
            if (binding.mode() == Mode.PLACE) {
                var corner = point.relative(context.getClickedFace());
                var result = SettlementTemplateFiles.preview(player, new ResourceLocation(binding.region()), corner,
                        net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE,
                        binding.exclude());
                player.sendSystemMessage(Component.literal(result));
                tell(player, "confirm_placement");
            } else if (binding.mode() == Mode.FACILITY || binding.mode() == Mode.MOVE_FACILITY) {
                String result = SettlementAuthoring.bindFacility(player, binding.carrier(), point,
                        binding.region(), binding.mode() == Mode.MOVE_FACILITY ? binding.boxIndex() : -1);
                finish(player, binding, result);
            } else {
                Binding next = binding.first() == null || binding.second() != null
                        ? binding.corners(point, null) : binding.corners(binding.first(), point);
                BINDINGS.put(player, next);
                publish(player, next);
                if (next.second() == null) tell(player, "second_corner");
                else {
                    var box = RegionBox.between(SettlementAuthoring.cell(next.first()),
                            SettlementAuthoring.cell(next.second()));
                    tell(player, "confirm", box.sizeX(), box.sizeY(), box.sizeZ());
                }
            }
        } catch (IllegalArgumentException bad) {
            player.displayClientMessage(Component.literal(bad.getMessage()), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            if (SettlementTemplateFiles.hasPreview(server)) {
                if (server.isShiftKeyDown()) finishPlacement(server);
                else {
                    SettlementTemplateFiles.cancel(server);
                    tell(server, "cancelled");
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
            var binding = BINDINGS.get(server);
            if (binding != null && server.isShiftKeyDown()) confirm(server, binding);
            else {
                BINDINGS.remove(server);
                SettlementNetwork.preview(server, SettlementPreview.EMPTY);
                tell(server, "cancelled");
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void finishPlacement(ServerPlayer player) {
        try {
            player.sendSystemMessage(Component.literal(SettlementTemplateFiles.confirm(player)));
        } catch (IllegalArgumentException bad) {
            player.sendSystemMessage(Component.literal(bad.getMessage()));
        }
    }

    private static InteractionResult confirm(ServerPlayer player, Binding binding) {
        if (!active(player, binding)) return InteractionResult.FAIL;
        if (binding.second() == null) {
            tell(player, "second_corner");
            return InteractionResult.CONSUME;
        }
        try {
            String result = binding.mode() == Mode.BOUNDS
                    ? SettlementAuthoring.setBounds(player, binding.carrier(), binding.first(), binding.second())
                    : SettlementAuthoring.addBox(player, binding.information(), binding.region(),
                            binding.exclude(), binding.mode() == Mode.REPLACE,
                            binding.mode() == Mode.EDIT ? binding.boxIndex() : -1, binding.first(), binding.second());
            finish(player, binding, result);
        } catch (IllegalArgumentException bad) {
            player.displayClientMessage(Component.literal(bad.getMessage()), true);
        }
        return InteractionResult.CONSUME;
    }

    private static void finish(ServerPlayer player, Binding binding, String result) {
        if (!result.startsWith("saved author revision ")) {
            player.displayClientMessage(Component.literal(result), true);
            return;
        }
        var info = binding.carrier();
        Mode mode = binding.mode() == Mode.ADD || binding.mode() == Mode.FACILITY
                ? binding.mode() : Mode.VIEW;
        var next = new Binding(binding.dimension(), binding.information(), info, binding.region(),
                binding.exclude(), binding.currentOnly(), binding.boxIndex(), mode, info.revision(), null, null);
        BINDINGS.put(player, next);
        publish(player, next);
        tell(player, "saved");
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flags) {
        lines.add(Component.translatable("item.hostile_humans_unified.settlement_selection_tool.help"));
    }
}

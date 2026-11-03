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
    enum Mode { ADD, REPLACE, EDIT, BOUNDS, FACILITY, MOVE_FACILITY, VIEW, NEW_OUTER, NEW_INNER }

    private record Binding(ResourceLocation dimension, BlockPos information,
            SettlementInformationBlockEntity carrier, String region, boolean exclude, boolean currentOnly, int boxIndex, Mode mode,
            int revision, int visible, BlockPos first, BlockPos second) {
        Binding corners(BlockPos a, BlockPos b) {
            return new Binding(dimension, information, carrier, region, exclude, currentOnly, boxIndex, mode, revision, visible, a, b);
        }
    }

    static final String NEED_TOOL = "settlement selection tool required";

    static boolean hasTool(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++)
            if (inventory.getItem(i).is(SettlementContent.SELECTION_TOOL.get())) return true;
        return false;
    }

    private static final Map<ServerPlayer, Binding> BINDINGS = new WeakHashMap<>();

    public SettlementSelectionTool(Properties properties) {
        super(properties);
    }

    static String mode(ServerPlayer player, BlockPos position, String region, boolean exclude,
            boolean currentOnly, int boxIndex, Mode mode, int revision) {
        var info = SettlementAuthoring.information(player, position);
        if (info == null || info.revision() != revision) return "author unavailable or stale";
        if (region.length() > 256) return "selection identifier too long";
        var author = SettlementCodec.template(info.author());
        if (mode == Mode.BOUNDS && new ResourceLocation(author.template()).getPath().equals("untitled"))
            return "template name required before selection";
        var binding = new Binding(player.level().dimension().location(), position.immutable(),
                info, region, exclude, currentOnly, boxIndex, mode, revision, visibility(player, info), null, null);
        info.setChanged(); // Persist the carrier identity before its chunk can unload.
        publish(player, binding);
        BINDINGS.put(player, binding);
        tell(player, "bound_mode", Component.translatable(
                "item.hostile_humans_unified.settlement_selection_tool.mode." + mode.name().toLowerCase(java.util.Locale.ROOT)),
                region.isEmpty() ? "" : " · " + region);
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
        var carrier = SettlementAuthoring.cell(binding.information());
        boxes.add(new SettlementPreview.Box(RegionBox.between(carrier, carrier), 0x6affaa, ""));
        var size = author.size();
        if ((binding.visible() & 1) != 0) boxes.add(new SettlementPreview.Box(new RegionBox(0, 0, 0,
                size.x() - 1, size.y() - 1, size.z() - 1).transform(transform), 0xffffff, "NBT"));
        for (var region : author.regions()) {
            if (!SettlementAuthoring.allowsLayer(author, region.layer())) continue;
            if (binding.currentOnly() && !binding.region().isEmpty() && !region.id().equals(binding.region())) continue;
            int color = region.layer() == SettlementRegion.Layer.INNER ? 0x37d5ff : 0xffbd57;
            String label = region.name() + " [" + region.id() + "]";
            if ((binding.visible() & (region.layer() == SettlementRegion.Layer.INNER ? 4 : 2)) != 0) {
                for (int i = 0; i < region.includes().size(); i++)
                    boxes.add(new SettlementPreview.Box(region.includes().get(i).transform(transform),
                            color, label + " #" + (i + 1)));
            }
            if ((binding.visible() & 8) != 0) {
                for (int i = 0; i < region.excludes().size(); i++)
                    boxes.add(new SettlementPreview.Box(region.excludes().get(i).transform(transform),
                            0xff5362, "− " + label + " #" + (i + 1)));
            }
        }
        if ((binding.visible() & 16) != 0) for (var facility : author.facilities()) {
            if (!SettlementAuthoring.allowsFacility(author, facility.type())) continue;
            if (binding.currentOnly() && !binding.region().isEmpty() && !facility.region().equals(binding.region())) continue;
            var p = transform.apply(facility.position());
            boxes.add(new SettlementPreview.Box(RegionBox.between(p, p), 0xb78cff, facility.type().name()));
        }
        SettlementNetwork.preview(player, new SettlementPreview(binding.dimension().toString(),
                boxes, binding.first(), binding.second(), true, List.of(),
                binding.mode().name().toLowerCase(java.util.Locale.ROOT), binding.region()));
    }

    private static int visibility(ServerPlayer player, SettlementInformationBlockEntity info) {
        var prior = BINDINGS.get(player);
        return prior != null && prior.carrier() == info ? prior.visible() : 31;
    }

    private static boolean holding(ServerPlayer player) {
        var item = SettlementContent.SELECTION_TOOL.get();
        return player.getMainHandItem().is(item) || player.getOffhandItem().is(item);
    }

    private static void openPanel(ServerPlayer player) {
        var binding = BINDINGS.get(player);
        if (!active(player, binding)) return;
        var data = new net.minecraft.nbt.CompoundTag();
        data.putLong("anchor", binding.information().asLong());
        data.putInt("revision", binding.revision());
        var author = SettlementCodec.template(binding.carrier().author());
        data.putString("template", author.template());
        data.putString("kind", author.kind().name());
        data.putInt("tier", author.tier());
        data.putBoolean("ready", named(author));
        data.putBoolean("outer", SettlementAuthoring.allowsLayer(author, SettlementRegion.Layer.OUTER));
        data.putBoolean("inner", SettlementAuthoring.allowsLayer(author, SettlementRegion.Layer.INNER));
        data.putString("mode", binding.mode().name());
        data.putString("region", binding.region());
        data.putBoolean("exclude", binding.exclude());
        data.putInt("visible", binding.visible());
        var regions = new net.minecraft.nbt.ListTag();
        for (var region : SettlementCodec.template(binding.carrier().author()).regions()) {
            var row = new net.minecraft.nbt.CompoundTag();
            row.putString("id", region.id());
            row.putString("name", region.name());
            row.putString("layer", region.layer().name());
            row.putInt("includes", region.includes().size());
            row.putInt("excludes", region.excludes().size());
            regions.add(row);
        }
        data.put("regions", regions);
        var facilities = new net.minecraft.nbt.ListTag();
        for (var facility : author.facilities()) {
            var row = new net.minecraft.nbt.CompoundTag();
            row.putString("id", facility.id());
            row.putString("type", facility.type().name());
            row.putString("region", facility.region());
            var point = SettlementAuthoring.currentTransform(player, binding.carrier()).apply(facility.position());
            row.putString("position", point.x() + ", " + point.y() + ", " + point.z());
            facilities.add(row);
        }
        data.put("facilities", facilities);
        var allowed = new net.minecraft.nbt.ListTag();
        for (var type : SettlementFacility.Type.values())
            if (SettlementAuthoring.allowsFacility(author, type)
                    && type != SettlementFacility.Type.ENTRANCE && type != SettlementFacility.Type.POST)
                allowed.add(net.minecraft.nbt.StringTag.valueOf(type.name()));
        data.put("allowedFacilities", allowed);
        SettlementNetwork.selectorView(player, data);
    }

    private static boolean named(SettlementTemplate author) {
        return !new ResourceLocation(author.template()).getPath().equals("untitled");
    }

    private static SettlementRegion.Layer newLayer(Mode mode) {
        return switch (mode) {
            case NEW_OUTER -> SettlementRegion.Layer.OUTER;
            case NEW_INNER -> SettlementRegion.Layer.INNER;
            default -> null;
        };
    }

    static void configure(ServerPlayer player, net.minecraft.nbt.CompoundTag data) {
        if (data.getBoolean("cancelPlacement")) {
            if (SettlementAccess.author(player) && SettlementWandPlacement.holding(player)) {
                SettlementWandPlacement.cancel(player);
                SettlementTemplateFiles.cancel(player);
            }
            return;
        }
        if (!holding(player)) return;
        var binding = BINDINGS.get(player);
        if (!active(player, binding)) return;
        if (data.getBoolean("cancel")) {
            var next = binding.corners(null, null);
            BINDINGS.put(player, next);
            publish(player, next);
            tell(player, "cancelled");
            return;
        }
        if (!data.contains("anchor", net.minecraft.nbt.Tag.TAG_LONG)
                || data.getLong("anchor") != binding.information().asLong()
                || data.getInt("revision") != binding.revision()) return;
        if (data.getBoolean("openExport")) {
            SettlementNetwork.openAuthor(player, binding.information(), SettlementNetwork.AUTHOR_FILE_TAB);
            return;
        }
        if (data.getBoolean("visibilityOnly")) {
            var next = new Binding(binding.dimension(), binding.information(), binding.carrier(),
                    binding.region(), binding.exclude(), binding.currentOnly(), binding.boxIndex(), binding.mode(),
                    binding.revision(), data.getInt("visible") & 31, binding.first(), binding.second());
            BINDINGS.put(player, next);
            publish(player, next);
            return;
        }
        Mode mode;
        try { mode = Mode.valueOf(data.getString("mode")); }
        catch (IllegalArgumentException bad) { return; }
        String region = data.getString("region");
        var author = SettlementCodec.template(binding.carrier().author());
        if (mode != Mode.VIEW && !named(author)) {
            tell(player, "name_first");
            return;
        }
        if (data.getBoolean("remove")) {
            if (!named(author) || region.length() > 64 || data.getString("facility").length() > 64) return;
            try {
                var editing = new Binding(binding.dimension(), binding.information(), binding.carrier(), region,
                        false, false, -1, Mode.VIEW, binding.revision(), data.getInt("visible") & 31, null, null);
                finish(player, editing, SettlementAuthoring.removeSelection(binding.carrier(), region,
                        data.getString("facility"), data.getBoolean("exclude"), data.getInt("box")));
            } catch (IllegalArgumentException bad) {
                player.displayClientMessage(Component.literal(bad.getMessage()), true);
            }
            openPanel(player);
            return;
        }
        var layer = newLayer(mode);
        if (layer != null && !SettlementAuthoring.allowsLayer(author, layer)
                || mode == Mode.FACILITY && !SettlementAuthoring.allowsFacility(author, SettlementFacility.Type.CONTROL))
            return;
        if ((mode == Mode.ADD || mode == Mode.REPLACE || mode == Mode.EDIT)
                && author.regions().stream().noneMatch(r -> r.id().equals(region) && SettlementAuthoring.allowsLayer(author, r.layer()))) {
            tell(player, "region_first");
            return;
        }
        if (mode == Mode.MOVE_FACILITY || region.length() > 64) return;
        int box = mode == Mode.EDIT ? data.getInt("box") : -1;
        if (mode == Mode.EDIT && (box < 0 || author.regions().stream().noneMatch(r -> r.id().equals(region)
                && box < (data.getBoolean("exclude") ? r.excludes() : r.includes()).size()))) return;
        boolean exclude = data.getBoolean("exclude");
        boolean sameSelection = mode == binding.mode() && region.equals(binding.region()) && exclude == binding.exclude() && box == binding.boxIndex();
        var next = new Binding(binding.dimension(), binding.information(), binding.carrier(), region,
                exclude, false, box, mode, binding.revision(), data.getInt("visible") & 31,
                sameSelection ? binding.first() : null, sameSelection ? binding.second() : null);
        publish(player, next);
        BINDINGS.put(player, next);
        tell(player, "bound_mode", Component.translatable(
                "item.hostile_humans_unified.settlement_selection_tool.mode." + mode.name().toLowerCase(java.util.Locale.ROOT)),
                region.isEmpty() ? "" : " · " + region);
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
        if (binding == null || validBinding(player, binding)) return;
        if (!SettlementAccess.author(player)) {
            clear(player);
            return;
        }
        if (!binding.dimension().equals(player.level().dimension().location())
                || !player.serverLevel().hasChunkAt(binding.information())) return;
        if (player.serverLevel().getBlockEntity(binding.information()) instanceof SettlementInformationBlockEntity info
                && info.selectionIdentity().equals(binding.carrier().selectionIdentity())) {
            boolean unchanged = info.revision() == binding.revision();
            var next = unchanged
                    ? new Binding(binding.dimension(), binding.information(), info, binding.region(), binding.exclude(),
                            binding.currentOnly(), binding.boxIndex(), binding.mode(), binding.revision(),
                            binding.visible(), binding.first(), binding.second())
                    : new Binding(binding.dimension(), binding.information(), info, "", false,
                            false, -1, Mode.VIEW, info.revision(), binding.visible(), null, null);
            try {
                publish(player, next);
                BINDINGS.put(player, next);
                return;
            } catch (IllegalArgumentException invalid) {
                // Unknown or invalid draft cannot be drawn safely.
            }
        }
        clear(player);
    }

    private static boolean validBinding(ServerPlayer player, Binding binding) {
        return boundInformation(player, binding.information()) != null;
    }

    private static boolean active(ServerPlayer player, Binding binding) {
        if (binding != null && validBinding(player, binding)) return true;
        if (binding != null) {
            player.displayClientMessage(Component.translatable(
                    "item.hostile_humans_unified.settlement_selection_tool.carrier_unavailable"), true);
            return false;
        }
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
        BlockPos point = context.getClickedPos().immutable();
        if (!player.serverLevel().hasChunkAt(point)) return InteractionResult.FAIL;
        if (player.isShiftKeyDown()) {
            if (player.serverLevel().getBlockEntity(point) instanceof SettlementInformationBlockEntity) {
                SettlementAuthoring.initialize(player, point);
                var info = SettlementAuthoring.information(player, point);
                if (info != null) mode(player, point, "", false, false, -1, Mode.VIEW, info.revision());
            } else openPanel(player);
            return InteractionResult.CONSUME;
        }
        if (!active(player, binding)) return InteractionResult.FAIL;
        if (binding.mode() == Mode.VIEW) {
            tell(player, "choose_mode");
            return InteractionResult.CONSUME;
        }
        try {
            if (binding.mode() == Mode.FACILITY || binding.mode() == Mode.MOVE_FACILITY) {
                String result = SettlementAuthoring.bindFacility(player, binding.carrier(), point,
                        binding.region(), binding.mode() == Mode.MOVE_FACILITY ? binding.boxIndex() : -1);
                finish(player, binding, result);
            } else {
                if (binding.second() != null) return confirm(player, binding);
                Binding next = binding.first() == null
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
            var binding = BINDINGS.get(server);
            if (server.isShiftKeyDown()) openPanel(server);
            else if (binding != null && binding.second() != null) confirm(server, binding);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
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
                            binding.mode() == Mode.EDIT ? binding.boxIndex() : -1, binding.first(), binding.second(), newLayer(binding.mode()));
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
                binding.exclude(), binding.currentOnly(), binding.boxIndex(), mode, info.revision(), binding.visible(), null, null);
        BINDINGS.put(player, next);
        publish(player, next);
        tell(player, "saved");
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flags) {
        lines.add(Component.translatable("item.hostile_humans_unified.settlement_selection_tool.help"));
    }
}

package dev.felix.hostilehumans.settlement;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class SettlementFacilityBindingTool extends Item {
    private record Binding(ResourceKey<Level> dimension, BlockPos position, UUID identity,
                           String region, int facilityIndex, int revision) {}
    static final String NEED_TOOL = "settlement facility binding tool required";

    static final class Problem extends IllegalArgumentException {
        final Component message;
        Problem(String key, Object... args) { message = SettlementFacilityBindingTool.message(key, args); }
    }

    private static final Map<ServerPlayer, Binding> BINDINGS = new WeakHashMap<>();

    public SettlementFacilityBindingTool(Properties properties) {
        super(properties);
    }

    static boolean hasTool(ServerPlayer player) {
        return player.getInventory().contains(new ItemStack(SettlementContent.FACILITY_TOOL.get()));
    }

    static String bind(ServerPlayer player, BlockPos point, String region, int facilityIndex, int revision) {
        var info = SettlementAuthoring.information(player, point);
        if (info == null || info.revision() != revision) return "author unavailable or stale";
        var author = SettlementCodec.template(info.author());
        if (region.length() > 64 || facilityIndex < -1 || facilityIndex >= author.facilities().size())
            return "invalid facility selection";
        if (new ResourceLocation(author.template()).getPath().equals("untitled"))
            return "template name required before facility binding";
        if (author.kind() == SettlementTemplate.Kind.ORDINARY) return "ordinary buildings do not support facilities";
        info.setChanged();
        BINDINGS.put(player, new Binding(player.level().dimension(), point.immutable(),
                info.selectionIdentity(), region, facilityIndex, revision));
        boolean hasOuter = author.regions().stream().anyMatch(
                candidate -> candidate.layer() == SettlementRegion.Layer.OUTER && !candidate.includes().isEmpty());
        tell(player, hasOuter ? "ready" : "prepare_outer");
        return SettlementNetwork.SELECTION_READY;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(context.getPlayer() instanceof ServerPlayer player))
            return context.getPlayer() != null && (context.getPlayer().isCreative() || context.getPlayer().hasPermissions(2))
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        if (!SettlementAccess.author(player)) return InteractionResult.PASS;
        var point = context.getClickedPos();
        if (!player.serverLevel().hasChunkAt(point)) return InteractionResult.FAIL;
        try {
            if (player.serverLevel().getBlockEntity(point) instanceof SettlementInformationBlockEntity info) {
                SettlementAuthoring.initialize(player, point);
                String result = bind(player, point, "", -1, info.revision());
                if (!SettlementNetwork.SELECTION_READY.equals(result)) player.displayClientMessage(localize(result), true);
                return InteractionResult.CONSUME;
            }
            var binding = BINDINGS.get(player);
            var info = binding == null || !binding.dimension().equals(player.level().dimension()) ? null
                    : SettlementAuthoring.information(player, binding.position());
            if (info == null || !info.selectionIdentity().equals(binding.identity())
                    || (binding.facilityIndex() >= 0 || !binding.region().isEmpty()) && info.revision() != binding.revision()) {
                tell(player, "bind_first");
                return InteractionResult.CONSUME;
            }
            if (player.serverLevel().getBlockState(point).is(dev.felix.hostilehumans.economy.EconomyContent.TASK_BOARD.get())) {
                if (binding.facilityIndex() >= 0) throw new Problem("wrong_type");
                publishTaskBoard(player, info, point);
                return InteractionResult.CONSUME;
            }
            String result = SettlementAuthoring.bindFacility(player, info, point, binding.region(), binding.facilityIndex());
            if (!result.startsWith("saved author revision ")) {
                player.displayClientMessage(localize(result), true);
                return InteractionResult.CONSUME;
            }
            BINDINGS.put(player, new Binding(binding.dimension(), binding.position(), binding.identity(),
                    binding.region(), -1, info.revision()));
            var published = publish(player, info);
            if (published.changed()) tell(player, "registered");
            else tell(player, "draft_only", localize(published.reason()));
        } catch (Problem bad) {
            player.displayClientMessage(bad.message, true);
        } catch (IllegalArgumentException bad) {
            player.displayClientMessage(localize(bad.getMessage()), true);
        }
        return InteractionResult.CONSUME;
    }

    private static SettlementIndex.Result publish(ServerPlayer player, SettlementInformationBlockEntity info) {
        var options = new CompoundTag();
        if (info.instance() == null)
            return SettlementAuthoring.importNew(player, info.getBlockPos(), info.revision(), options);
        var instance = SettlementIndex.get(player.serverLevel()).find(info.instance());
        if (instance == null) return new SettlementIndex.Result(false, "linked instance unavailable", null);
        return SettlementAuthoring.update(player, info.getBlockPos(), info.revision(), instance.revision(), options);
    }

    private static void publishTaskBoard(ServerPlayer player, SettlementInformationBlockEntity info, BlockPos point) {
        var author = SettlementCodec.template(info.author());
        var local = SettlementAuthoring.currentTransform(player, info).unapply(SettlementAuthoring.cell(point));
        boolean outer = false;
        for (var region : author.regions()) {
            if (!region.contains(local.x() + .5, local.y(), local.z() + .5)) continue;
            if (region.layer() == SettlementRegion.Layer.INNER) throw new Problem("task_board_inner");
            outer = true;
        }
        if (!outer) throw new Problem("task_board_draft");
        var index = SettlementIndex.get(player.serverLevel());
        var current = index.query(point.getX() + .5, point.getY(), point.getZ() + .5);
        if (current.unknown()) throw new IllegalArgumentException("unknown region");
        if (current.hits().stream().anyMatch(hit -> !hit.instance().equals(info.instance())))
            throw new Problem("task_board_overlap");
        var published = publish(player, info);
        if (!published.changed() && !"already imported".equals(published.reason())) {
            tell(player, "task_board_publish_failed", localize(published.reason()));
            return;
        }
        var site = index.taskBoardSite(point);
        if (!site.id().equals(info.instance())) throw new Problem("task_board_overlap");
        tell(player, "task_board_ready");
    }

    static Component message(String key, Object... args) {
        return Component.translatable("item.hostile_humans_unified.settlement_facility_tool." + key, args);
    }

    public static Component localize(String reason) {
        if (reason == null) return message("rejected");
        if (reason.startsWith("rejected: ")) return localize(reason.substring("rejected: ".length()));
        String emptyRegion = "add an include box or remove the empty region: ";
        if (reason.startsWith(emptyRegion)) return message("empty_region", reason.substring(emptyRegion.length()));
        String outsideCapture = "facility outside structure capture: ";
        if (reason.startsWith(outsideCapture)) return message("outside_capture", reason.substring(outsideCapture.length()));
        return switch (reason) {
            case "author unavailable or stale", "missing or stale author", "missing or stale author link",
                    "author revision changed" -> message("bind_first");
            case "invalid facility selection" -> message("missing_facility");
            case "template name required before facility binding" -> message("name_first");
            case "ordinary buildings do not support facilities", "ordinary building has no protected instance" -> message("ordinary");
            case "only a protected ROOT creates an instance", "missing root instance" -> message("root_first");
            case "linked instance unavailable", "module reference mismatch", "bound module unavailable",
                    "bound instance unavailable; restore it or use a fresh author block" -> message("instance_missing");
            case "define an outer region with a capture box before publishing the settlement" -> message("no_region", message("layer_outer"));
            case "instance revision changed", "already linked; explicitly update or export this instance" -> message("refresh_author");
            case "instance has conflicting control facilities", "duplicate template control" -> message("duplicate_control");
            case "dimension instance limit", "instance spatial index limit", "template total box/extent limit" -> message("index_limit");
            case "unknown index schema; retained read-only", "unknown existing author schema; preserve payload" -> message("unknown_schema");
            case "task board has no registered outer courtyard" -> message("task_board_unregistered");
            case "task board inside inner courtyard" -> message("task_board_inner");
            case "task board overlaps settlements" -> message("task_board_overlap");
            case "task board settlement changed" -> message("task_board_changed");
            case "task board missing" -> message("task_board_missing");
            case "tasks require true standing 50 or a valid cautious permit" -> message("task_board_standing");
            case "protected region permission denied" -> message("region_permission");
            case "service out of range or unloaded" -> message("service_range");
            case "unknown region", "unknown task board instance" -> message("task_board_unknown");
            case "no typed facility at this block", "facility not registered" -> message("unregistered");
            default -> Component.literal(reason);
        };
    }

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(message(key, args), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, java.util.List<Component> lines, TooltipFlag flags) {
        lines.add(Component.translatable("item.hostile_humans_unified.settlement_facility_tool.help"));
    }
}

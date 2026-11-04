package dev.felix.hostilehumans.settlement.client;

import dev.felix.hostilehumans.client.ui.framework.InputValue;
import dev.felix.hostilehumans.client.ui.framework.PanelButton;
import dev.felix.hostilehumans.client.ui.framework.PanelGeometry;
import dev.felix.hostilehumans.client.ui.framework.PanelScreen;
import dev.felix.hostilehumans.client.ui.framework.UiLayout;
import dev.felix.hostilehumans.settlement.SettlementNetwork;
import dev.felix.hostilehumans.settlement.SettlementWandPlacement;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;

public final class SettlementWandScreen extends PanelScreen {
    private record Options(String id, int turns, Mirror mirror, boolean entities) {}
    private static final WeakHashMap<ClientLevel, Options> LAST = new WeakHashMap<>();

    private final SettlementWandPlacement.View view;
    private final ClientLevel level;
    private final InputValue<String> template;
    private int turns;
    private Mirror mirror;
    private boolean entities;
    private boolean listOpen;
    private final List<InputValue<Integer>> coordinates;
    private boolean awaiting;
    private String error = "";

    private SettlementWandScreen(SettlementWandPlacement.View view, ClientLevel level) {
        super(text(view.adjustment() == null ? "wand_title" : "wand_adjust_title"), PanelGeometry.Size.L);
        this.view = view;
        this.level = level;
        var saved = LAST.getOrDefault(level, new Options("", 0, Mirror.NONE, true));
        var adjustment = view.adjustment();
        template = InputValue.text(adjustment == null ? saved.id() : adjustment.id(), 256, true);
        turns = adjustment == null ? saved.turns() : adjustment.turns();
        mirror = adjustment == null ? saved.mirror() : adjustment.mirror();
        entities = adjustment == null ? saved.entities() : adjustment.entities();
        listOpen = saved.id().isEmpty();
        var corner = adjustment == null ? null : adjustment.corner();
        coordinates = corner == null ? List.of() : List.of(
                InputValue.integer(corner.getX(), -30_000_000, 30_000_000),
                InputValue.integer(corner.getY(), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1),
                InputValue.integer(corner.getZ(), -30_000_000, 30_000_000));
    }

    public static void show(SettlementWandPlacement.View update) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.screen instanceof SettlementWandScreen screen && screen.view.token().equals(update.token())) {
            if (update.armed()) screen.closeScreen();
            else {
                screen.awaiting = false;
                screen.error = update.reason();
                screen.rebuildWidgets();
            }
        } else if (!update.armed() && update.reason().isEmpty()) {
            mc.setScreen(new SettlementWandScreen(update, mc.level));
        }
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.hostile_humans_unified.settlement." + key, args);
    }

    private UiLayout.Node button(Component label, boolean selected, Runnable callback) {
        var button = body(new PanelButton(label, ignored -> callback.run(), this::theme).compact(true));
        button.selected(selected);
        return node(button);
    }

    private UiLayout.Node note(Component message) {
        return text(message, () -> theme().muted());
    }

    @Override protected UiLayout.Node buildContent() {
        List<UiLayout.Node> nodes = new ArrayList<>();
        nodes.add(note(text("wand_workflow")));
        if (!error.isEmpty()) nodes.add(text(text(error), () -> theme().danger()));
        if (view.adjustment() != null) return adjustmentContent(nodes);
        nodes.add(field(text("placement_template"), template));
        nodes.add(button(text(listOpen ? "hide_templates" : "show_templates"), listOpen,
                () -> { listOpen = !listOpen; rebuildWidgets(); }));
        if (listOpen) {
            if (view.choices().isEmpty()) nodes.add(note(text("templates_empty")));
            for (int i = 0; i < view.choices().size(); i++) {
                var row = view.choices().getCompound(i);
                String id = row.getString("id");
                nodes.add(button(Component.literal(id).append(" ? ")
                                .append(text(row.getString("source").equals("instance") ? "template_instance" : row.getBoolean("world") ? "template_world" : "template_pack")),
                        id.equals(template.text()), () -> { template.set(id); listOpen = false; rebuildWidgets(); }));
            }
        }
        for (int i = 0; i < view.choices().size(); i++) {
            var row = view.choices().getCompound(i);
            if (!row.getBoolean("world") || !row.getString("id").equals(template.text())) continue;
            var id = new ResourceLocation(template.text());
            String root = row.getString("source").equals("instance")
                    ? "config/hostile_humans_unified/building_library/" : "generated/";
            nodes.add(note(Component.literal(root + id.getNamespace()
                    + "/structures/" + id.getPath() + ".nbt")));
            break;
        }
        nodes.add(button(text(entities ? "place_entities_on" : "place_entities_off"),
                entities, () -> { entities = !entities; rebuildWidgets(); }));
        nodes.add(note(text("place_entities_help")));
        return UiLayout.column(6, nodes);
    }

    private UiLayout.Node adjustmentContent(List<UiLayout.Node> nodes) {
        nodes.set(0, note(text("wand_adjust_help")));
        nodes.add(note(Component.literal(template.text())));
        for (int i = 0; i < coordinates.size(); i++) {
            var value = coordinates.get(i);
            String axis = List.of("X", "Y", "Z").get(i);
            nodes.add(field(Component.literal(axis), value));
            nodes.add(UiLayout.grid(2, 4, 40, List.of(
                    button(Component.literal(axis + " −1"), false, () -> step(value, -1)),
                    button(Component.literal(axis + " +1"), false, () -> step(value, 1)))));
        }
        nodes.add(button(text("rotation", turns * 90), false, () -> { turns = (turns + 1) % 4; rebuildWidgets(); }));
        List<UiLayout.Node> mirrors = new ArrayList<>();
        for (var option : Mirror.values())
            mirrors.add(button(text("mirror." + option.name()), option == mirror,
                    () -> { mirror = option; rebuildWidgets(); }));
        nodes.add(UiLayout.grid(3, 4, 40, mirrors));
        return UiLayout.column(6, nodes);
    }

    private void step(InputValue<Integer> value, int delta) {
        if (awaiting) return;
        var previous = value.text();
        value.value().ifPresent(number -> {
            value.set(Integer.toString(number + delta));
            if (!value.valid()) value.set(previous);
        });
        rebuildWidgets();
    }

    private void submitAdjustment(boolean confirm, boolean closeImmediately) {
        if (awaiting) return;
        if (coordinates.stream().anyMatch(value -> !value.valid())) {
            if (closeImmediately) closeScreen();
            else { error = "invalid_input"; rebuildWidgets(); }
            return;
        }
        var corner = new BlockPos(coordinates.get(0).value().orElseThrow(),
                coordinates.get(1).value().orElseThrow(), coordinates.get(2).value().orElseThrow());
        awaiting = true;
        SettlementNetwork.armWand(new SettlementWandPlacement.Request(
                view.token(), template.text(), turns, mirror, entities, corner, confirm));
        if (closeImmediately) closeScreen();
    }

    @Override public void onClose() {
        if (view.adjustment() == null || awaiting) closeScreen();
        else submitAdjustment(false, true);
    }

    private void closeScreen() {
        super.onClose();
    }

    @Override protected void buildFooter() {
        if (view.adjustment() != null) {
            footer(text("wand_apply_preview"), ignored -> submitAdjustment(false, false));
            footer(text("wand_confirm_placement"), ignored -> submitAdjustment(true, false));
            return;
        }
        footer(Component.translatable("gui.cancel"), ignored -> onClose());
        footer(text("place_template"), ignored -> {
            if (!template.valid() || template.text().isBlank() || ResourceLocation.tryParse(template.text()) == null) {
                error = "invalid_input";
                rebuildWidgets();
                return;
            }
            SettlementNetwork.armWand(new SettlementWandPlacement.Request(
                    view.token(), template.text(), turns, mirror, entities));
        });
    }

    @Override public void removed() {
        if (view.adjustment() == null) LAST.put(level, new Options(template.text(), turns, mirror, entities));
        super.removed();
    }
}

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
    private String error = "";

    private SettlementWandScreen(SettlementWandPlacement.View view, ClientLevel level) {
        super(text("wand_title"), PanelGeometry.Size.L);
        this.view = view;
        this.level = level;
        var saved = LAST.getOrDefault(level, new Options("", 0, Mirror.NONE, false));
        template = InputValue.text(saved.id(), 256, true);
        turns = saved.turns();
        mirror = saved.mirror();
        entities = saved.entities();
        listOpen = saved.id().isEmpty();
    }

    public static void show(SettlementWandPlacement.View update) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.screen instanceof SettlementWandScreen screen && screen.view.token().equals(update.token())) {
            if (update.armed()) screen.onClose();
            else {
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
        nodes.add(field(text("placement_template"), template));
        nodes.add(button(text(listOpen ? "hide_templates" : "show_templates"), listOpen,
                () -> { listOpen = !listOpen; rebuildWidgets(); }));
        if (listOpen) {
            if (view.choices().isEmpty()) nodes.add(note(text("templates_empty")));
            for (int i = 0; i < view.choices().size(); i++) {
                var row = view.choices().getCompound(i);
                String id = row.getString("id");
                nodes.add(button(Component.literal(id).append(" ? ")
                                .append(text(row.getBoolean("world") ? "template_world" : "template_pack")),
                        id.equals(template.text()), () -> { template.set(id); listOpen = false; rebuildWidgets(); }));
            }
        }
        for (int i = 0; i < view.choices().size(); i++) {
            var row = view.choices().getCompound(i);
            if (!row.getBoolean("world") || !row.getString("id").equals(template.text())) continue;
            var id = new ResourceLocation(template.text());
            nodes.add(note(Component.literal("generated/" + id.getNamespace()
                    + "/structures/" + id.getPath() + ".nbt")));
            break;
        }
        nodes.add(button(text("rotation", turns * 90), false, () -> { turns = (turns + 1) % 4; rebuildWidgets(); }));
        List<UiLayout.Node> mirrors = new ArrayList<>();
        for (var option : Mirror.values())
            mirrors.add(button(text("mirror." + option.name()), option == mirror,
                    () -> { mirror = option; rebuildWidgets(); }));
        nodes.add(UiLayout.grid(3, 4, 40, mirrors));
        nodes.add(button(text(entities ? "template_entities_on" : "template_entities_off"),
                entities, () -> { entities = !entities; rebuildWidgets(); }));
        return UiLayout.column(6, nodes);
    }

    @Override protected void buildFooter() {
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
        LAST.put(level, new Options(template.text(), turns, mirror, entities));
        super.removed();
    }
}

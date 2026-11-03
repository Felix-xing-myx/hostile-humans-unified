package dev.felix.hostilehumans.settlement.client;

import dev.felix.hostilehumans.client.ui.framework.PanelButton;
import dev.felix.hostilehumans.client.ui.framework.PanelGeometry;
import dev.felix.hostilehumans.client.ui.framework.PanelScreen;
import dev.felix.hostilehumans.client.ui.framework.UiLayout;
import dev.felix.hostilehumans.settlement.SettlementNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

public final class SettlementSelectorScreen extends PanelScreen {
    private static final String[] VISIBILITY = {"bounds", "outer", "inner", "exclude", "facilities"};
    private final CompoundTag binding;
    private String mode, region;
    private boolean exclude;
    private int visible;

    private SettlementSelectorScreen(CompoundTag data) {
        super(text("selector_title"), PanelGeometry.Size.L);
        binding = data;
        mode = data.getString("mode");
        if (mode.equals("EDIT") || mode.equals("MOVE_FACILITY")) mode = "VIEW";
        region = data.getString("region");
        exclude = data.getBoolean("exclude");
        visible = data.getInt("visible");
    }

    public static void show(CompoundTag data) {
        var client = Minecraft.getInstance();
        if (client.level != null) client.setScreen(new SettlementSelectorScreen(data));
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.hostile_humans_unified.settlement." + key, args);
    }

    private UiLayout.Node option(Component caption, boolean selected, Runnable action) {
        var button = body(new PanelButton(caption, ignored -> action.run(), this::theme).compact(true));
        button.selected(selected);
        return node(button);
    }

    @Override protected UiLayout.Node buildContent() {
        List<UiLayout.Node> nodes = new ArrayList<>();
        nodes.add(text(text("selector_bound", binding.getString("template")), () -> theme().muted()));
        nodes.add(text(text("selector_workflow"), () -> theme().muted()));
        for (String value : List.of("BOUNDS", "ADD", "REPLACE", "FACILITY", "VIEW")) {
            nodes.add(option(Component.translatable("item.hostile_humans_unified.settlement_selection_tool.mode."
                            + value.toLowerCase(java.util.Locale.ROOT)), value.equals(mode),
                    () -> { mode = value; rebuildWidgets(); }));
        }
        if (mode.equals("ADD") || mode.equals("REPLACE")) {
            nodes.add(option(text(exclude ? "excludes" : "includes"), exclude,
                    () -> { exclude = !exclude; rebuildWidgets(); }));
        }
        if (mode.equals("FACILITY"))
            nodes.add(option(text("selector_auto_region"), region.isEmpty(),
                    () -> { region = ""; rebuildWidgets(); }));
        if (mode.equals("ADD") || mode.equals("REPLACE") || mode.equals("FACILITY")) {
            var regions = binding.getList("regions", Tag.TAG_COMPOUND);
            if (regions.isEmpty()) nodes.add(text(text("selector_no_region"), () -> theme().muted()));
            for (int i = 0; i < regions.size(); i++) {
                var row = regions.getCompound(i);
                String id = row.getString("id");
                nodes.add(option(text("layer." + row.getString("layer")).append(" ? ")
                                .append(row.getString("name")).append(" [" + id + "]"),
                        region.equals(id), () -> { region = id; rebuildWidgets(); }));
            }
        }
        for (int i = 0; i < VISIBILITY.length; i++) {
            int mask = 1 << i;
            nodes.add(option(text("selector_show." + VISIBILITY[i], text((visible & mask) != 0
                            ? "selector_on" : "selector_off")), (visible & mask) != 0,
                    () -> { visible ^= mask; rebuildWidgets(); }));
        }
        return UiLayout.column(6, nodes);
    }

    @Override protected void buildFooter() {
        footer(Component.translatable("gui.cancel"), ignored -> onClose());
        var apply = footer(text("selector_apply"), ignored -> {
            var data = new CompoundTag();
            data.putLong("anchor", binding.getLong("anchor"));
            data.putInt("revision", binding.getInt("revision"));
            data.putString("mode", mode);
            data.putString("region", region);
            data.putBoolean("exclude", exclude);
            data.putInt("visible", visible);
            SettlementNetwork.configureSelector(data);
            onClose();
        });
        apply.active = !(mode.equals("ADD") || mode.equals("REPLACE")) || !region.isEmpty();
    }
}

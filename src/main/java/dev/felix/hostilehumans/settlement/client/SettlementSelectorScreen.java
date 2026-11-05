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
    private String region = "";
    private String pendingDelete = "";
    private int visible;

    private SettlementSelectorScreen(CompoundTag data) {
        super(text("selector_title"), PanelGeometry.Size.L);
        binding = data;
        region = data.getString("region");
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
        String kind = binding.getString("kind");
        var identity = text("kind." + kind).copy();
        if (!kind.equals("ORDINARY")) identity.append(" · ").append(text("tier_choice." + binding.getInt("tier")));
        nodes.add(text(identity, () -> theme().text()));
        if (!binding.getBoolean("ready")) {
            nodes.add(text(text("selector_setup"), () -> theme().muted()));
            return UiLayout.column(6, nodes);
        }
        nodes.add(option(text("selector_persistent", text(SettlementPreviewClient.selectionVisible?"selector_on":"selector_off")),
                SettlementPreviewClient.selectionVisible,()->{SettlementPreviewClient.selectionVisible=!SettlementPreviewClient.selectionVisible;rebuildWidgets();}));
        nodes.add(text(text("selector_workflow"), () -> theme().muted()));
        nodes.add(action("selector_capture", "BOUNDS", "", false, -1));
        if (binding.getBoolean("outer")) {
            nodes.add(action("selector_new_outer", "NEW_OUTER", "", false, -1));
            if (binding.getBoolean("inner")) nodes.add(action("selector_new_inner", "NEW_INNER", "", false, -1));
            addRegions(nodes);
            addFacilities(nodes);
        }
        for (int i = 0; i < VISIBILITY.length; i++) {
            if (i >= 1 && !binding.getBoolean("outer") || i == 2 && !binding.getBoolean("inner")) continue;
            int mask = 1 << i;
            nodes.add(option(text("selector_show." + VISIBILITY[i], text((visible & mask) != 0
                            ? "selector_on" : "selector_off")), (visible & mask) != 0,
                    () -> { visible ^= mask; rebuildWidgets(); }));
        }
        return UiLayout.column(6, nodes);
    }

    private void addRegions(List<UiLayout.Node> nodes) {
        var regions = binding.getList("regions", Tag.TAG_COMPOUND);
        for (int i = 0; i < regions.size(); i++) {
            var row = regions.getCompound(i);
            String id = row.getString("id");
            if (row.getString("layer").equals("INNER") && !binding.getBoolean("inner")) continue;
            nodes.add(option(text("selector_region_row", text("layer." + row.getString("layer")),
                            row.getString("name"), id, row.getInt("includes"), row.getInt("excludes")),
                    region.equals(id), () -> {
                        region = region.equals(id) ? "" : id;
                        pendingDelete = "";
                        rebuildWidgets();
                    }));
            if (!region.equals(id)) continue;
            nodes.add(action("selector_add_include", "ADD", id, false, -1));
            nodes.add(action("selector_add_exclude", "ADD", id, true, -1));
            for (boolean exclude : List.of(false, true)) {
                int count = row.getInt(exclude ? "excludes" : "includes");
                for (int j = 0; j < count; j++) {
                    int index = j;
                    var label = text(exclude ? "excludes" : "includes").copy().append(" #" + (j + 1));
                    nodes.add(option(text("selector_redraw", label), false, () -> start("EDIT", id, exclude, index)));
                    nodes.add(remove(label, id, "", exclude, index));
                }
            }
            nodes.add(remove(text("selector_whole_region"), id, "", false, -1));
        }
    }

    private void addFacilities(List<UiLayout.Node> nodes) {
        if (binding.getList("regions", Tag.TAG_COMPOUND).size() > 0) {
            nodes.add(text(text("selector_facility_help"), () -> theme().muted()));
            var allowed = binding.getList("allowedFacilities", Tag.TAG_STRING);
            for (int i = 0; i < allowed.size(); i++)
                nodes.add(text(text("help.facility." + allowed.getString(i)), () -> theme().muted()));
        }
        var facilities = binding.getList("facilities", Tag.TAG_COMPOUND);
        for (int i = 0; i < facilities.size(); i++) {
            var row = facilities.getCompound(i);
            var label = text("facility_type." + row.getString("type")).copy()
                    .append(" · " + row.getString("region") + " · " + row.getString("position"));
            nodes.add(remove(label, "", row.getString("id"), false, -1));
        }
    }

    private UiLayout.Node action(String caption, String mode, String id, boolean exclude, int box) {
        return option(text(caption), false, () -> start(mode, id, exclude, box));
    }

    private UiLayout.Node remove(Component label, String id, String facility, boolean exclude, int box) {
        String key = id + ":" + facility + ":" + exclude + ":" + box;
        boolean armed = pendingDelete.equals(key);
        return option(text(armed ? "selector_confirm_remove" : "selector_remove", label), armed, () -> {
            if (!armed) {
                pendingDelete = key;
                rebuildWidgets();
                return;
            }
            var request = request("VIEW", id, exclude, box);
            request.putBoolean("remove", true);
            request.putString("facility", facility);
            pendingDelete = "";
            SettlementNetwork.configureSelector(request);
        });
    }

    private CompoundTag request(String mode, String id, boolean exclude, int box) {
        var data = new CompoundTag();
        data.putLong("anchor", binding.getLong("anchor"));
        data.putInt("revision", binding.getInt("revision"));
        data.putString("mode", mode);
        data.putString("region", id);
        data.putBoolean("exclude", exclude);
        data.putInt("box", box);
        data.putInt("visible", visible);
        return data;
    }

    private void start(String mode, String id, boolean exclude, int box) {
        SettlementNetwork.configureSelector(request(mode, id, exclude, box));
        onClose();
    }

    @Override protected void buildFooter() {
        footer(Component.translatable("gui.cancel"), ignored -> onClose());
        if (binding.getBoolean("ready"))
            footer(text("selector_open_export"), ignored -> {
                var data = request("VIEW", "", false, -1);
                data.putBoolean("openExport", true);
                SettlementNetwork.configureSelector(data);
            });
        if (binding.getBoolean("ready"))
            footer(text("selector_apply"), ignored -> {
                var data = request("VIEW", "", false, -1);
                data.putBoolean("visibilityOnly", true);
                SettlementNetwork.configureSelector(data);
                onClose();
            });
    }
}

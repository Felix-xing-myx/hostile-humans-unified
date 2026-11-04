package dev.felix.hostilehumans.settlement.client;

import dev.felix.hostilehumans.client.ui.framework.InputValue;
import dev.felix.hostilehumans.client.ui.framework.PanelButton;
import dev.felix.hostilehumans.client.ui.framework.UiThemes;
import dev.felix.hostilehumans.client.ui.framework.PanelDisclosure;
import dev.felix.hostilehumans.client.ui.framework.PanelGeometry;
import dev.felix.hostilehumans.client.ui.framework.PanelGroup;
import dev.felix.hostilehumans.client.ui.framework.PanelScreen;
import dev.felix.hostilehumans.client.ui.framework.PendingAction;
import dev.felix.hostilehumans.client.ui.framework.UiLayout;
import dev.felix.hostilehumans.settlement.SettlementNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Structured author forms and indexed read-only management; no world/entity queries on the client.
 */
public final class SettlementScreen extends PanelScreen {
    private static final UUID NONE = new UUID(0, 0);
    private static final LinkedHashSet<UUID> RETIRED = new LinkedHashSet<>();

    private record Submission(
            SettlementNetwork.Snapshot base, SettlementNetwork.Action action, CompoundTag data) {}

    private final PendingAction<Submission> pending = new PendingAction<>();
    private final Map<String, AbstractWidget> controls = new HashMap<>();
    private SettlementNetwork.Snapshot snapshot, editBase;
    private CompoundTag author;
    private static final List<String> GENERATION_TEXT =
            List.of("group", "dimension", "allowed_biomes", "excluded_biomes");
    private static final List<String> GENERATION_NUMBERS =
            List.of("weight", "min_y", "max_y", "max_slope", "minimum_day", "spacing", "separation");
    private List<InputValue<String>> generationText = List.of();
    private List<InputValue<Integer>> generationNumbers = List.of();
    private CompoundTag generation = new CompoundTag();

    private InputValue<String> template,
            module,
            faction,
            regionId,
            regionName,
            facilityId,
            facilityRegion,
            displayName,
            restoreModule;
    private InputValue<Integer> tier, targetRevision;
    private InputValue<UUID> target, postHuman;
    private List<InputValue<Integer>> size,
            offset,
            pivot,
            box = List.of(),
            facilityPosition = List.of();
    private String kind = "ROOT",
            layer = "OUTER",
            facilityType = "POST",
            mirror = "NONE",
            tab = "template",
            expanded = "",
            error = "",
            confirmation = "";
    private int regionIndex, boxIndex, facilityIndex, turns;
    private int keepAliveTicks;
    private record AuthorView(String tab, boolean entities) {}
    private static final java.util.Map<net.minecraft.client.multiplayer.ClientLevel,
            java.util.LinkedHashMap<net.minecraft.core.BlockPos, AuthorView>> AUTHOR_VIEWS =
            new java.util.WeakHashMap<>();
    private final net.minecraft.client.multiplayer.ClientLevel authorLevel;
    private boolean exclude, localChanged, serverClosed, transformDirty, templateEntities, currentRegionOnly, advancedAuthor;
    private long confirmUntil;

    private SettlementScreen(SettlementNetwork.Snapshot snapshot) {
        super(text(snapshot.author() ? "author_title" : "title"), PanelGeometry.Size.L);
        this.snapshot = snapshot;
        authorLevel = Minecraft.getInstance().level;
        resetDraft(snapshot);
        var views = AUTHOR_VIEWS.get(authorLevel);
        var previous = snapshot.author() && views != null ? views.get(snapshot.anchor()) : null;
        if (previous != null) {
            tab = previous.tab();
            templateEntities = previous.entities();
        }
        if (snapshot.author() && snapshot.reason().equals(SettlementNetwork.AUTHOR_FILE_TAB)) tab = "transfer";
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.hostile_humans_unified.settlement." + key, args);
    }

    public static void show(SettlementNetwork.Snapshot value) {
        var mc = Minecraft.getInstance();
        if (RETIRED.contains(value.token())) return;
        if (mc.screen instanceof SettlementScreen s && s.snapshot.token().equals(value.token())) {
            if (!value.valid()) {
                s.pending.acknowledge(value.acknowledgedRequest());
                s.serverClosed = true;
                if (mc.player != null)
                    mc.player.displayClientMessage(text("invalid", value.reason()), false);
                s.onClose();
                return;
            }
            s.accept(value);
        } else if (value.valid() && value.acknowledgedRequest().equals(NONE))
            mc.setScreen(new SettlementScreen(value));
    }

    private void accept(SettlementNetwork.Snapshot value) {
        Submission request = pending.request();
        boolean ack = pending.acknowledge(value.acknowledgedRequest());
        if (!value.author() && !value.data().contains("map_terrain")
                && value.instanceRevision() == snapshot.instanceRevision()
                && snapshot.data().contains("map_terrain"))
            value.data().put("map_terrain", snapshot.data().getCompound("map_terrain"));
        snapshot = value;
        if (!value.author()) return;
        if (ack && request != null) {
            boolean explicitReload =
                    request.action() == SettlementNetwork.Action.EXPORT_AUTHOR
                            || request.action() == SettlementNetwork.Action.RESTORE_AUTHOR;
            CompoundTag returned = value.data().getCompound("author").copy();
            returned.remove("revision");
            CompoundTag submitted = request.data().getCompound("author").copy();
            submitted.remove("revision");
            if (explicitReload && value.authorRevision() > request.base().authorRevision()
                    || (request.action() == SettlementNetwork.Action.SAVE_AUTHOR
                            || request.action() == SettlementNetwork.Action.SELECTION_MODE)
                            && request.data().contains("author", Tag.TAG_COMPOUND)
                            && returned.equals(submitted)
                            && value.authorRevision() > request.base().authorRevision()
                    || request.action() == SettlementNetwork.Action.BOUNDS_AS_OUTER
                            && value.authorRevision() > request.base().authorRevision()) resetDraft(value);
        } else if (!dirty() && value.authorRevision() != editBase.authorRevision())
            resetDraft(value);
        if (ack
                && request != null
                && (request.action() == SettlementNetwork.Action.UPDATE_INSTANCE
                        || request.action() == SettlementNetwork.Action.IMPORT_NEW
                        || request.action() == SettlementNetwork.Action.JOIN_INSTANCE)
                && matchesTransform(value.data().getCompound("author_transform"), request.data()))
            transformDirty = false;
        if (!transformDirty) readTransform(value);
        if (ack && request != null && request.action() == SettlementNetwork.Action.SELECTION_MODE
                && value.reason().equals(SettlementNetwork.SELECTION_READY)) {
            onClose();
            return;
        }
        refresh();
    }

    private static boolean matchesTransform(CompoundTag actual, CompoundTag sent) {
        return actual.contains("turns")
                && sent.contains("turns")
                && actual.getInt("turns") == sent.getInt("turns")
                && actual.getString("mirror").equals(sent.getString("mirror"))
                && Arrays.equals(actual.getIntArray("pivot"), sent.getIntArray("pivot"));
    }

    private void readTransform(SettlementNetwork.Snapshot value) {
        CompoundTag transform = value.data().getCompound("author_transform");
        if (value.instance().equals(NONE) && transform.isEmpty()) {
            turns = 0;
            mirror = "NONE";
            pivot = coordinates(new int[] {0, 0, 0}, 3, 0, -30000000, 30000000);
            return;
        }
        if (!transform.contains("turns")
                || transform.getIntArray("pivot").length != 3
                || !List.of("NONE", "LEFT_RIGHT", "FRONT_BACK")
                        .contains(transform.getString("mirror"))) return;
        int rotation = transform.getInt("turns");
        if (rotation < 0 || rotation > 3) return;
        turns = rotation;
        mirror = transform.getString("mirror");
        pivot = coordinates(transform.getIntArray("pivot"), 3, 0, -30000000, 30000000);
    }

    private static InputValue<String> string(String value, int max, boolean required) {
        return InputValue.text(value, max, required);
    }

    private static List<InputValue<Integer>> coordinates(
            int[] data, int count, int fallback, int min, int max) {
        List<InputValue<Integer>> out = new ArrayList<>();
        for (int i = 0; i < count; i++)
            out.add(InputValue.integer(data.length == count ? data[i] : fallback, min, max));
        return out;
    }

    private void resetDraft(SettlementNetwork.Snapshot value) {
        boolean retainTransform =
                transformDirty && editBase != null && editBase.token().equals(value.token());
        editBase = value;
        author = value.data().getCompound("author").copy();
        resetGenerationDraft();
        localChanged = false;
        error = "";
        template = string(author.getString("template"), 256, true);
        module = string(author.getString("module"), 64, true);
        faction = string(author.getString("faction"), 256, true);
        tier = InputValue.integer(author.getInt("tier"), 1, 3);
        kind = author.getString("kind");
        size = coordinates(author.getIntArray("size"), 3, 1, 1, 512);
        offset = coordinates(author.getIntArray("origin_offset"), 3, 0, -30000000, 30000000);
        if (!retainTransform) {
            transformDirty = false;
            readTransform(value);
        }
        if (pivot == null) pivot = coordinates(new int[] {0, 0, 0}, 3, 0, -30000000, 30000000);
        target = InputValue.uuid(value.instance().equals(NONE) ? null : value.instance());
        targetRevision = InputValue.integer(value.instanceRevision(), 0, Integer.MAX_VALUE);
        displayName = string(value.data().getString("name"), 64, false);
        restoreModule = string(value.data().getString("module"), 64, true);
        postHuman = InputValue.uuid(null);
        regionIndex = 0;
        boxIndex = 0;
        facilityIndex = 0;
        loadRegion();
        loadFacility();
    }

    private ListTag regions() {
        return author.getList("regions", Tag.TAG_COMPOUND);
    }

    private ListTag facilities() {
        return author.getList("facilities", Tag.TAG_COMPOUND);
    }

    private CompoundTag region() {
        return regions().isEmpty()
                ? null
                : regions().getCompound(Math.min(regionIndex, regions().size() - 1));
    }

    private ListTag boxes() {
        var region = region();
        return region == null
                ? new ListTag()
                : region.getList(exclude ? "exclude" : "include", Tag.TAG_INT_ARRAY);
    }

    private void loadRegion() {
        var row = region();
        if (row == null) {
            regionId = string("", 64, true);
            regionName = string("", 96, false);
            box = List.of();
            return;
        }
        regionIndex = Math.min(regionIndex, regions().size() - 1);
        regionId = string(row.getString("id"), 64, true);
        regionName = string(row.getString("name"), 96, false);
        layer = row.getString("layer");
        boxIndex = 0;
        loadBox();
    }

    private void loadBox() {
        ListTag list = boxes();
        if (list.isEmpty()) {
            box = List.of();
            return;
        }
        boxIndex = Math.max(0, Math.min(boxIndex, list.size() - 1));
        box = coordinates(list.getIntArray(boxIndex), 6, 0, -30000000, 30000000);
    }

    private void loadFacility() {
        if (facilities().isEmpty()) {
            facilityId = string("", 64, true);
            facilityRegion = string("", 64, true);
            facilityPosition = List.of();
            return;
        }
        facilityIndex = Math.max(0, Math.min(facilityIndex, facilities().size() - 1));
        var row = facilities().getCompound(facilityIndex);
        facilityId = string(row.getString("id"), 64, true);
        facilityRegion = string(row.getString("region"), 64, true);
        facilityType = row.getString("type");
        facilityPosition = coordinates(row.getIntArray("position"), 3, 0, -30000000, 30000000);
    }

    private static boolean valid(List<? extends InputValue<?>> values) {
        return values.stream().allMatch(InputValue::valid);
    }

    private static boolean changed(List<? extends InputValue<?>> values) {
        return values.stream().anyMatch(InputValue::dirty);
    }

    private boolean dirty() {
        return localChanged
                || changed(generationText)
                || changed(generationNumbers)
                || template.dirty()
                || module.dirty()
                || faction.dirty()
                || tier.dirty()
                || changed(size)
                || changed(offset)
                || regionId.dirty()
                || regionName.dirty()
                || changed(box)
                || facilityId.dirty()
                || facilityRegion.dirty()
                || changed(facilityPosition);
    }

    private boolean knownSchema() {
        return author.getInt("schema") == 1;
    }

    private boolean editable() {
        return snapshot.author() && knownSchema() && !pending.busy();
    }

    private void refresh() {
        String focus =
                controls.entrySet().stream()
                        .filter(e -> e.getValue() == getFocused())
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse("");
        rebuildWidgets();
        AbstractWidget next = controls.get(focus);
        if (next != null) {
            setFocused(next);
            next.setFocused(true);
            revealFocus();
        }
    }

    /** Section band inside the author form, matching the grouped layout of the command panels. */
    private UiLayout.Node heading(Component title) {
        return node(body(new PanelGroup(title, UiThemes.MARKER_RANGE, this::theme)));
    }

    private UiLayout.Node muted(Component label) {
        return text(label, () -> theme().muted());
    }

    private UiLayout.Node action(
            String id, Component label, boolean active, boolean selected, Runnable callback) {
        if (id.equals(confirmation) && System.currentTimeMillis() < confirmUntil)
            label = text("confirm", label);
        PanelButton b = body(new PanelButton(label, button -> callback.run(), this::theme));
        b.active = active;
        b.selected(selected);
        controls.put(id, b);
        return node(b);
    }

    private UiLayout.Node input(String key, InputValue<?> value) {
        var control = field(text(key), value);
        var help = text("help." + key);
        return net.minecraft.client.resources.language.I18n.exists(
                        "gui.hostile_humans_unified.settlement.help." + key)
                ? UiLayout.column(2, List.of(control, muted(help))) : control;
    }

    private UiLayout.Node coords(String prefix, List<InputValue<Integer>> values) {
        List<UiLayout.Node> nodes = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) nodes.add(input(prefix + "." + i, values.get(i)));
        return UiLayout.grid(3, 6, 80, nodes);
    }

    private UiLayout.Node choices(
            String id,
            List<String> values,
            String current,
            java.util.function.Consumer<String> setter) {
        List<UiLayout.Node> nodes = new ArrayList<>();
        for (String value : values) {
            // Compact chips: the same visual as the faction presets, and far shorter than a
            // three column grid in a form that now carries a help line per field.
            PanelButton chip =
                    body(
                            new PanelButton(
                                    text(id + "." + value),
                                    b -> {
                                        setter.accept(value);
                                        localChanged = true;
                                        refresh();
                                    },
                                    this::theme)
                                    .compact(true));
            chip.active = editable();
            chip.selected(value.equals(current));
            controls.put(id + "." + value, chip);
            nodes.add(node(chip));
        }
        return UiLayout.grid(values.size(), 4, 40, nodes);
    }

    private void confirm(String key, Runnable callback) {
        if (key.equals(confirmation) && System.currentTimeMillis() < confirmUntil) {
            confirmation = "";
            confirmUntil = 0;
            callback.run();
        } else {
            confirmation = key;
            confirmUntil = System.currentTimeMillis() + 4000;
            refresh();
        }
    }

    private boolean fail(String reason) {
        error = reason;
        refresh();
        return false;
    }

    private static int[] integers(List<InputValue<Integer>> values) {
        int[] a = new int[values.size()];
        for (int i = 0; i < a.length; i++) a[i] = values.get(i).value().orElseThrow();
        return a;
    }

    private boolean applyBox() {
        if (box.isEmpty()) return true;
        if (!valid(box)) return fail("invalid_input");
        int[] a = integers(box);
        if (a[0] > a[3] || a[1] > a[4] || a[2] > a[5]) return fail("box_order");
        ListTag list = boxes();
        if (boxIndex < list.size()) {
            if (changed(box)) {
                list.set(boxIndex, new IntArrayTag(a));
                localChanged = true;
                box.forEach(InputValue::accepted);
            }
        }
        return true;
    }

    private boolean applyRegion() {
        if (region() == null) return true;
        if (!regionId.valid() || !regionName.valid()) return fail("invalid_input");
        if (!applyBox()) return false;
        var row = region();
        String prior = row.getString("id"), next = regionId.value().orElseThrow();
        if (regionId.dirty() || regionName.dirty()) {
            row.putString("id", next);
            row.putString("name", regionName.value().orElseThrow());
            for (Tag tag : facilities()) {
                CompoundTag f = (CompoundTag) tag;
                if (f.getString("region").equals(prior)) f.putString("region", next);
            }
            if (facilityRegion != null
                    && !facilityRegion.dirty()
                    && facilityRegion.value().orElse("").equals(prior))
                facilityRegion = string(next, 64, true);
            localChanged = true;
            regionId.accepted();
            regionName.accepted();
        }
        row.putString("layer", layer);
        return true;
    }

    private boolean applyFacility() {
        if (facilities().isEmpty()) return true;
        if (!facilityId.valid() || !facilityRegion.valid() || !valid(facilityPosition))
            return fail("invalid_input");
        var row = facilities().getCompound(facilityIndex);
        if (facilityId.dirty() || facilityRegion.dirty() || changed(facilityPosition)) {
            row.putString("id", facilityId.value().orElseThrow());
            row.putString("region", facilityRegion.value().orElseThrow());
            row.putIntArray("position", integers(facilityPosition));
            localChanged = true;
            facilityId.accepted();
            facilityRegion.accepted();
            facilityPosition.forEach(InputValue::accepted);
        }
        row.putString("type", facilityType);
        return true;
    }

    private CompoundTag gather() {
        if (!knownSchema()
                || !template.valid()
                || !module.valid()
                || !faction.valid()
                || !tier.valid()
                || !valid(size)
                || !valid(offset)
                || !applyRegion()
                || !applyFacility()) return null;
        CompoundTag payload = author.copy();
        payload.putString("template", template.value().orElseThrow());
        payload.putString("module", module.value().orElseThrow());
        payload.putString("faction", faction.value().orElseThrow());
        payload.putInt("tier", tier.value().orElseThrow());
        payload.putString("kind", kind);
        if (kind.equals("BROKER_STATION")) {
            if (payload.getList("broker_factions", Tag.TAG_STRING).isEmpty()) return null;
        } else payload.remove("broker_factions");
        payload.putIntArray("size", integers(size));
        payload.putIntArray("origin_offset", integers(offset));
        if (!valid(generationText) || !valid(generationNumbers)) return null;
        var rules = generation.copy();
        for (int i = 0; i < GENERATION_TEXT.size(); i++)
            rules.putString(GENERATION_TEXT.get(i), generationText.get(i).value().orElse(""));
        for (int i = 0; i < GENERATION_NUMBERS.size(); i++)
            rules.putInt(GENERATION_NUMBERS.get(i), generationNumbers.get(i).value().orElseThrow());
        try {
            var parsed = dev.felix.hostilehumans.settlement.SettlementGenerationRules.read(rules);
            if (parsed.equals(dev.felix.hostilehumans.settlement.SettlementGenerationRules.DEFAULT))
                payload.remove("generation");
            else payload.put("generation", parsed.write());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        return payload;
    }

    private void send(
            SettlementNetwork.Snapshot base, SettlementNetwork.Action action, CompoundTag data) {
        if (pending.busy()) return;
        if ((action == SettlementNetwork.Action.IMPORT_NEW
                        || action == SettlementNetwork.Action.JOIN_INSTANCE
                        || action == SettlementNetwork.Action.UPDATE_INSTANCE)
                && dirty()) {
            fail("save_first");
            return;
        }
        CompoundTag payload = data.copy();
        UUID id = SettlementNetwork.request(base, action, payload);
        pending.begin(id, new Submission(base, action, payload));
        refresh();
    }

    @Override
    protected boolean fieldsEnabled() {
        return !pending.busy() && (!snapshot.author() || knownSchema());
    }

    @Override
    protected void formChanged() {
        if (changed(pivot)) transformDirty = true;
        if (snapshot.author())
            for (String key : List.of("import", "join", "update", "save_template_file",
                    "replace_template_file", "bounds_as_outer", "pick_facility")) {
                var button = controls.get(key);
                if (button != null)
                    button.active =
                            editable()
                                    && !dirty()
                                    && (!key.equals("update") || !snapshot.instance().equals(NONE));
            }
        var capture = controls.get("capture_bounds");
        if (capture != null) capture.active = editable() && namedTemplate();
        relayout();
    }

    @Override
    protected String faction() {
        return snapshot.data().getString("faction");
    }

    private static final List<String> PAGES =
            List.of("template", "regions", "facilities", "generation", "transfer", "management");

    @Override
    protected void buildBand() {
        if (!snapshot.author() || !knownSchema()) return;
        for (String page : PAGES) {
            PanelButton button =
                    band(
                            new PanelButton(
                                    text("tab." + page),
                                    b -> {
                                        tab = page;
                                        refresh();
                                    },
                                    this::theme));
            button.selected(tab.equals(page));
            button.active = !pending.busy();
            controls.put("tab." + page, button);
        }
    }

    @Override
    protected UiLayout.Node buildContent() {
        controls.clear();
        if (!snapshot.author()) return UiLayout.column(0, List.of());
        List<UiLayout.Node> root = new ArrayList<>();
        root.add(muted(text("revisions", snapshot.authorRevision(), snapshot.instanceRevision())));
        if (!snapshot.reason().equals(SettlementNetwork.AUTHOR_FILE_TAB)) root.add(muted(snapshot.reason().equals("settlement selection tool required")
                ? text("need_selection_tool")
                : snapshot.reason().equals("settlement facility binding tool required")
                        ? text("need_facility_tool") : dev.felix.hostilehumans.settlement.SettlementFacilityBindingTool.localize(snapshot.reason())));
        if (pending.busy()) root.add(text(text("pending"), () -> theme().accent()));
        if (!error.isEmpty()) root.add(text(text(error), () -> theme().danger()));
        if (snapshot.author()) {
            if (!knownSchema()) {
                root.add(text(text("unknown_schema"), () -> theme().danger()));
                return UiLayout.column(6, root);
            }
            if (snapshot.authorRevision() != editBase.authorRevision())
                root.add(text(text("stale"), () -> theme().danger()));
            root.add(
                    switch (tab) {
                        case "regions" -> regionEditor();
                        case "facilities" -> facilityEditor();
                        case "generation" -> generationEditor();
                        case "transfer" -> transfer();
                        case "management" -> management();
                        default -> identity();
                    });
        }
        return UiLayout.column(6, root);
    }

    private void resetGenerationDraft() {
        generation = author.getCompound("generation").copy();
        if (generation.isEmpty())
            generation = dev.felix.hostilehumans.settlement.SettlementGenerationRules.DEFAULT.write();
        else if (generation.getInt("spacing") == 0 && generation.getInt("separation") == 0) {
            generation.putInt("spacing", 32);
            generation.putInt("separation", 8);
        }
        generationText = GENERATION_TEXT.stream()
                .map(key -> string(generation.getString(key), key.endsWith("_biomes") ? 4096 : 128,
                        !key.endsWith("_biomes")))
                .toList();
        generationNumbers = List.of(
                InputValue.integer(generation.getInt("weight"), 1, 1000),
                InputValue.integer(generation.getInt("min_y"), -2048, 2047),
                InputValue.integer(generation.getInt("max_y"), -2048, 2047),
                InputValue.integer(generation.getInt("max_slope"), 0, 64),
                InputValue.integer(generation.getInt("minimum_day"), 0, Integer.MAX_VALUE),
                InputValue.integer(generation.getInt("spacing"), 2, 4096),
                InputValue.integer(generation.getInt("separation"), 1, 4095));
    }

    private UiLayout.Node generationEditor() {
        List<UiLayout.Node> nodes = new ArrayList<>();
        nodes.add(muted(text("generation.help")));
        nodes.add(muted(text("building_pack.help")));
        nodes.add(action("validate_generation", text("validate_generation"), editable() && !dirty(), false,
                () -> send(snapshot, SettlementNetwork.Action.VALIDATE_GENERATION, new CompoundTag())));
        nodes.add(action("export_building_pack", text("export_building_pack"), editable() && !dirty(), false,
                () -> exportBuildingPack(false)));
        nodes.add(action("replace_building_pack", text("replace_building_pack"), editable() && !dirty(), false,
                () -> confirm("replace_building_pack", () -> exportBuildingPack(true))));
        nodes.add(choices("generation.enabled", List.of("false", "true"),
                Boolean.toString(generation.getBoolean("enabled")),
                value -> generation.putBoolean("enabled", Boolean.parseBoolean(value))));
        for (int i = 0; i < GENERATION_TEXT.size(); i++)
            nodes.add(input("generation." + GENERATION_TEXT.get(i), generationText.get(i)));
        nodes.add(muted(text("generation.group_scope")));
        String group = generationText.get(0).text();
        var peers = snapshot.data().getList("generation_groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < peers.size(); i++) {
            var peer = peers.getCompound(i);
            if (peer.getString("group").equals(group))
                nodes.add(muted(text("generation.group_source", peer.getString("id"),
                        peer.getInt("spacing"), peer.getInt("separation"))));
        }
        nodes.add(generationChoices("placement", List.of("SURFACE", "UNDERGROUND")));
        nodes.add(generationChoices("water", List.of("ANY", "DRY", "WATER")));
        nodes.add(generationChoices("terrain", List.of("NONE", "BEARD_THIN", "BEARD_BOX")));
        for (int i = 0; i < GENERATION_NUMBERS.size(); i++)
            nodes.add(input("generation." + GENERATION_NUMBERS.get(i), generationNumbers.get(i)));
        return UiLayout.column(6, nodes);
    }

    private UiLayout.Node generationChoices(String key, List<String> values) {
        return choices("generation." + key, values, generation.getString(key),
                value -> generation.putString(key, value));
    }

    private boolean namedTemplate() {
        var id = net.minecraft.resources.ResourceLocation.tryParse(template.text());
        return template.valid() && id != null && !id.getPath().equals("untitled");
    }

    private void beginWorldEdit(String mode, String regionId) {
        if (mode.equals("BOUNDS") && !namedTemplate()) { fail("name_before_bounds"); return; }
        var payload = new CompoundTag();
        payload.putString("mode", mode);
        payload.putString("region", regionId);
        payload.putBoolean("exclude", exclude);
        payload.putBoolean("current_only", currentRegionOnly);
        payload.putInt("box_index", mode.equals("MOVE_FACILITY") ? facilityIndex : boxIndex);
        var base = snapshot;
        if (dirty()) {
            var draft = gather();
            if (draft == null) { fail("invalid_input"); return; }
            if (!regionId.isEmpty() && !mode.equals("MOVE_FACILITY") && region() != null)
                payload.putString("region", region().getString("id"));
            if (mode.equals("MOVE_FACILITY") && facilityIndex < facilities().size())
                payload.putString("region", facilities().getCompound(facilityIndex).getString("region"));
            payload.put("author", draft);
            base = editBase;
        }
        send(base, SettlementNetwork.Action.SELECTION_MODE, payload);
    }

    private void exportBuildingPack(boolean overwrite) {
        if (dirty()) { fail("save_first"); return; }
        var payload = new CompoundTag();
        payload.putBoolean("overwrite", overwrite);
        send(snapshot, SettlementNetwork.Action.EXPORT_BUILDING_PACK, payload);
    }

    private void saveTemplateFile(boolean overwrite) {
        var payload = new CompoundTag();
        payload.putBoolean("entities", templateEntities);
        payload.putBoolean("overwrite", overwrite);
        send(snapshot, SettlementNetwork.Action.SAVE_TEMPLATE_FILE, payload);
    }

    private UiLayout.Node identity() {
        List<UiLayout.Node> nodes = new ArrayList<>();
        nodes.add(choices("kind", List.of("ORDINARY", "ROOT", "ATTACHMENT", "BROKER_STATION"), kind, v -> kind = v));
        nodes.add(muted(text("help.kind." + kind)));
        nodes.add(input("template_id", template));
        nodes.add(muted(text("bind_bounds_help")));
        nodes.add(action("capture_bounds", text("capture_bounds"), editable() && namedTemplate(), false,
                () -> beginWorldEdit("BOUNDS", "")));
        if (!kind.equals("ORDINARY")) {
            var registeredFactions = snapshot.data().getList("faction_ids", Tag.TAG_STRING)
                    .stream().map(Tag::getAsString).toList();
            nodes.add(field(text("faction"), faction, registeredFactions, UiThemes::factionName));
            nodes.add(muted(text("help.faction")));
            nodes.add(muted(text("help.tier")));
            nodes.add(choices("tier_choice", List.of("1", "2", "3"), tier.text(), tier::set));
        } else {
            addFileSaveControls(nodes);
        }
        if (kind.equals("BROKER_STATION")) {
            nodes.add(muted(text("broker_factions_help")));
            var factions = author.getList("broker_factions", Tag.TAG_STRING);
            for (var currency : dev.felix.hostilehumans.economy.Currency.values()) {
                String id = currency.faction();
                boolean selected = factions.stream().anyMatch(value -> value.getAsString().equals(id));
                nodes.add(action("broker_faction." + id, UiThemes.factionName(id), editable(), selected, () -> {
                    var chosen = new java.util.TreeSet<String>();
                    factions.forEach(value -> chosen.add(value.getAsString()));
                    if (selected) chosen.remove(id);
                    else chosen.add(id);
                    var list = new ListTag();
                    chosen.forEach(value -> list.add(net.minecraft.nbt.StringTag.valueOf(value)));
                    author.put("broker_factions", list);
                    localChanged = true;
                    refresh();
                }));
            }
        }
        nodes.add(action("advanced_author", text("advanced_author"), editable(), advancedAuthor,
                () -> { advancedAuthor = !advancedAuthor; refresh(); }));
        if (advancedAuthor) {
            nodes.add(input("module_id", module));
            nodes.add(heading(text("size")));
            nodes.add(coords("size", size));
            nodes.add(heading(text("origin")));
            nodes.add(coords("origin", offset));
        }
        return UiLayout.column(6, nodes);
    }

    private UiLayout.Node regionEditor() {
        List<UiLayout.Node> list = new ArrayList<>();
        list.add(muted(text("region_tool_help")));
        for (int i = 0; i < regions().size(); i++) {
            final int index = i;
            var r = regions().getCompound(i);
            list.add(
                    action(
                            "region." + i,
                            Component.literal(r.getString("id") + " · " + r.getString("name")),
                            editable(),
                            regionIndex == i,
                            () -> {
                                if (applyRegion()) {
                                    regionIndex = index;
                                    loadRegion();
                                    refresh();
                                }
                            }));
        }
        list.add(
                action(
                        "addRegion",
                        text("add_region"),
                        editable() && regions().size() < 64,
                        false,
                        () -> {
                            if (!applyRegion()) return;
                            CompoundTag r = new CompoundTag();
                            int number = regions().size();
                            var existingIds = regions().stream().map(CompoundTag.class::cast)
                                    .map(region -> region.getString("id")).collect(java.util.stream.Collectors.toSet());
                            while (existingIds.contains("region_" + number)) number++;
                            r.putString("id", "region_" + number);
                            r.putString("name", "Region " + (number + 1));
                            r.putString("layer", "OUTER");
                            r.put("include", new ListTag());
                            r.put("exclude", new ListTag());
                            ListTag all = regions();
                            all.add(r);
                            author.put("regions", all);
                            regionIndex = all.size() - 1;
                            localChanged = true;
                            loadRegion();
                            refresh();
                        }));
        List<UiLayout.Node> detail = new ArrayList<>();
        if (region() == null) detail.add(muted(text("create_outer_help")));
        else {
            detail.add(
                    action(
                            "selection",
                            text("world_selection"),
                            editable(),
                            false,
                            () -> beginWorldEdit("ADD", region().getString("id"))));
            detail.add(input("region_id", regionId));
            detail.add(input("region_name", regionName));
            if (layer.equals("OUTER") && !kind.equals("ORDINARY"))
                detail.add(action("bounds_as_outer", text("bounds_as_outer"), editable() && !dirty(), false,
                        () -> confirm("bounds_as_outer", () -> {
                            var data = new CompoundTag();
                            data.putString("region", region().getString("id"));
                            send(snapshot, SettlementNetwork.Action.BOUNDS_AS_OUTER, data);
                        })));
            detail.add(muted(text("help.layer")));
            detail.add(choices("layer", List.of("OUTER", "INNER"), layer, v -> layer = v));
            detail.add(muted(text("coordinates_hint")));
            detail.add(
                    UiLayout.grid(
                            2,
                            6,
                            List.of(
                                    action(
                                            "includes",
                                            text("includes"),
                                            editable(),
                                            !exclude,
                                            () -> {
                                                if (applyBox()) {
                                                    exclude = false;
                                                    boxIndex = 0;
                                                    loadBox();
                                                    refresh();
                                                }
                                            }),
                                    action(
                                            "excludes",
                                            text("excludes"),
                                            editable(),
                                            exclude,
                                            () -> {
                                                if (applyBox()) {
                                                    exclude = true;
                                                    boxIndex = 0;
                                                    loadBox();
                                                    refresh();
                                                }
                                            }))));
            List<UiLayout.Node> boxList = new ArrayList<>();
            for (int i = 0; i < boxes().size(); i++) {
                final int index = i;
                int[] a = boxes().getIntArray(i);
                boxList.add(
                        action(
                                "box." + i,
                                text("box", i + 1, Arrays.toString(a)),
                                editable(),
                                boxIndex == i,
                                () -> {
                                    if (applyBox()) {
                                        boxIndex = index;
                                        loadBox();
                                        refresh();
                                    }
                                }));
            }
            detail.add(UiLayout.column(4, boxList));
            if (!box.isEmpty()) detail.add(coords("box_coord", box));
            detail.add(
                    action(
                            "addBox",
                            text("add_box"),
                            editable()
                                    && region().getList("include", Tag.TAG_INT_ARRAY).size()
                                                    + region().getList("exclude", Tag.TAG_INT_ARRAY)
                                                            .size()
                                            < 64,
                            false,
                            () -> {
                                if (!applyBox()) return;
                                ListTag all = boxes();
                                all.add(new IntArrayTag(new int[] {0, 0, 0, 0, 0, 0}));
                                region().put(exclude ? "exclude" : "include", all);
                                boxIndex = all.size() - 1;
                                localChanged = true;
                                loadBox();
                                refresh();
                            }));
            detail.add(
                    action(
                            "removeBox",
                            text("remove_box"),
                            editable() && !boxes().isEmpty() && (exclude || boxes().size() > 1),
                            false,
                            () -> {
                                boxes().remove(boxIndex);
                                boxIndex = 0;
                                localChanged = true;
                                loadBox();
                                refresh();
                            }));
            detail.add(
                    action(
                            "applyRegion",
                            text("apply_local"),
                            editable(),
                            false,
                            () -> {
                                if (applyRegion()) refresh();
                            }));
            String selectedId = region().getString("id");
            String removeId = "removeRegion." + selectedId;
            var references = new ArrayList<String>();
            for (Tag tag : facilities()) {
                var facility = (CompoundTag) tag;
                if (facility.getString("region").equals(selectedId))
                    references.add(facility.getString("id"));
            }
            if (!references.isEmpty())
                detail.add(muted(text("region_remove_facilities", String.join(", ", references))));
            detail.add(action(removeId, text("remove_region"),
                    editable() && references.isEmpty(), false,
                    () -> confirm(removeId, () -> {
                        regions().remove(regionIndex);
                        regionIndex = 0;
                        localChanged = true;
                        loadRegion();
                        refresh();
                    })));
            detail.add(action("region_preview_scope", text(currentRegionOnly ? "preview_current" : "preview_all"),
                    editable(), currentRegionOnly, () -> { currentRegionOnly = !currentRegionOnly; refresh(); }));
            detail.add(action("region_preview_depth", text(SettlementPreviewClient.throughWalls
                            ? "preview_through" : "preview_depth"),
                    editable(), SettlementPreviewClient.throughWalls,
                    () -> { SettlementPreviewClient.throughWalls = !SettlementPreviewClient.throughWalls; refresh(); }));
            detail.add(action("replace_world_selection", text("replace_world_selection"),
                    editable(), false,
                    () -> beginWorldEdit("REPLACE", region().getString("id"))));
            detail.add(action("edit_world_box", text("edit_world_box"),
                    editable() && boxIndex < boxes().size(), false,
                    () -> beginWorldEdit("EDIT", region().getString("id"))));
            detail.add(action("bind_world_facility", text("bind_world_facility"),
                    editable(), false,
                    () -> beginWorldEdit("FACILITY", region().getString("id"))));
        }
        return UiLayout.row(12, List.of(UiLayout.column(6, list), UiLayout.column(6, detail)));
    }

    private UiLayout.Node facilityEditor() {
        List<UiLayout.Node> list = new ArrayList<>();
        list.add(muted(text("facility_tool_help")));
        list.add(action("pick_facility", text("pick_facility"), editable(), false,
                () -> beginWorldEdit("FACILITY", "")));
        for (int i = 0; i < facilities().size(); i++) {
            final int index = i;
            var f = facilities().getCompound(i);
            list.add(
                    action(
                            "facility." + i,
                            Component.literal(f.getString("id") + " · " + f.getString("type")),
                            editable(),
                            facilityIndex == i,
                            () -> {
                                if (applyFacility()) {
                                    facilityIndex = index;
                                    loadFacility();
                                    refresh();
                                }
                            }));
        }
        list.add(
                action(
                        "addFacility",
                        text("add_facility"),
                        editable() && facilities().size() < 128 && region() != null,
                        false,
                        () -> {
                            if (!applyFacility()) return;
                            CompoundTag f = new CompoundTag();
                            f.putString("id", "facility_" + facilities().size());
                            f.putString("type", "POST");
                            f.putString("region", region().getString("id"));
                            f.putIntArray("position", new int[] {0, 0, 0});
                            ListTag all = facilities();
                            all.add(f);
                            author.put("facilities", all);
                            facilityIndex = all.size() - 1;
                            localChanged = true;
                            loadFacility();
                            refresh();
                        }));
        List<UiLayout.Node> detail = new ArrayList<>();
        if (facilities().isEmpty()) detail.add(muted(text("no_facility")));
        else {
            detail.add(input("facility_id", facilityId));
            detail.add(input("facility_region", facilityRegion));
            detail.add(muted(text("help.facility." + facilityType)));
            detail.add(
                    choices(
                            "facility_type",
                            List.of("CONTROL", "ENTRANCE", "SUPPLY", "TRADER", "POST", "TRAINING"),
                            facilityType,
                            v -> facilityType = v));
            detail.add(coords("position", facilityPosition));
            detail.add(action("move_facility", text("move_facility"), editable(), false,
                    () -> beginWorldEdit("MOVE_FACILITY", facilities().getCompound(facilityIndex).getString("region"))));
            detail.add(
                    action(
                            "applyFacility",
                            text("apply_local"),
                            editable(),
                            false,
                            () -> {
                                if (applyFacility()) refresh();
                            }));
            detail.add(
                    action(
                            "removeFacility",
                            text("remove_facility"),
                            editable(),
                            false,
                            () ->
                                    confirm(
                                            "removeFacility",
                                            () -> {
                                                facilities().remove(facilityIndex);
                                                facilityIndex = 0;
                                                localChanged = true;
                                                loadFacility();
                                                refresh();
                                            })));
        }
        return UiLayout.row(12, List.of(UiLayout.column(6, list), UiLayout.column(6, detail)));
    }

    private CompoundTag transformOptions() {
        return transformOptions(true);
    }

    private CompoundTag transformOptions(boolean includeTransform) {
        if (includeTransform && !valid(pivot)) return null;
        CompoundTag p = new CompoundTag();
        if (includeTransform) {
            p.putInt("turns", turns);
            p.putString("mirror", mirror);
            p.putIntArray("pivot", integers(pivot));
        }
        p.putString("name", displayName.value().orElse(""));
        return p;
    }

    private void addFileSaveControls(List<UiLayout.Node> nodes) {
        if (dirty()) nodes.add(muted(text("export_save_draft_first")));
        nodes.add(muted(text("export_entities_help")));
        nodes.add(action("template_entities", text(templateEntities ? "export_entities_on" : "export_entities_off"),
                editable(), templateEntities, () -> { templateEntities = !templateEntities; refresh(); }));
        nodes.add(action("save_template_file", text("save_template_file"), editable() && !dirty(), false,
                () -> saveTemplateFile(false)));
        nodes.add(action("replace_template_file", text("replace_template_file"), editable() && !dirty(), false,
                () -> confirm("replace_template_file", () -> saveTemplateFile(true))));
    }

    private UiLayout.Node transfer() {
        List<UiLayout.Node> nodes = new ArrayList<>();
        addFileSaveControls(nodes);
        if (kind.equals("ORDINARY") && snapshot.instance().equals(NONE)) return UiLayout.column(6, nodes);
        nodes.add(muted(text("transfer_hint")));
        nodes.add(input("instance_name", displayName));
        nodes.add(
                action(
                        "rotation",
                        text("rotation", turns * 90),
                        editable(),
                        false,
                        () -> {
                            turns = (turns + 1) % 4;
                            transformDirty = true;
                            refresh();
                        }));
        List<UiLayout.Node> mirrors = new ArrayList<>();
        for (String value : List.of("NONE", "LEFT_RIGHT", "FRONT_BACK"))
            mirrors.add(
                    action(
                            "mirror." + value,
                            text("mirror." + value),
                            editable(),
                            mirror.equals(value),
                            () -> {
                                mirror = value;
                                transformDirty = true;
                                refresh();
                            }));
        nodes.add(UiLayout.grid(3, 4, 80, mirrors));
        nodes.add(coords("pivot", pivot));
        nodes.add(
                action(
                        "import",
                        text("import_new"),
                        editable() && !dirty(),
                        false,
                        () -> {
                            var payload = transformOptions();
                            if (payload != null)
                                send(snapshot, SettlementNetwork.Action.IMPORT_NEW, payload);
                            else fail("invalid_input");
                        }));
        nodes.add(input("target", target));
        nodes.add(input("target_revision", targetRevision));
        nodes.add(input("restore_module", restoreModule));
        nodes.add(
                action(
                        "join",
                        text("join"),
                        editable() && !dirty(),
                        false,
                        () -> {
                            var payload = transformOptions();
                            if (payload == null || !target.valid() || !targetRevision.valid()) {
                                fail("invalid_input");
                                return;
                            }
                            payload.putUUID("target", target.value().orElseThrow());
                            payload.putInt("target_revision", targetRevision.value().orElseThrow());
                            send(snapshot, SettlementNetwork.Action.JOIN_INSTANCE, payload);
                        }));
        nodes.add(
                action(
                        "update",
                        text("update"),
                        editable() && !dirty() && !snapshot.instance().equals(NONE),
                        false,
                        () ->
                                confirm(
                                        "update",
                                        () -> {
                                            var payload = transformOptions(transformDirty);
                                            if (payload != null)
                                                send(
                                                        snapshot,
                                                        SettlementNetwork.Action.UPDATE_INSTANCE,
                                                        payload);
                                        })));
        nodes.add(
                action(
                        "export",
                        text("export"),
                        editable() && !snapshot.instance().equals(NONE),
                        false,
                        () ->
                                confirm(
                                        "export",
                                        () ->
                                                send(
                                                        snapshot,
                                                        SettlementNetwork.Action.EXPORT_AUTHOR,
                                                        new CompoundTag()))));
        nodes.add(
                action(
                        "restore",
                        text("restore"),
                        editable(),
                        false,
                        () ->
                                confirm(
                                        "restore",
                                        () -> {
                                            if (!target.valid()
                                                    || !targetRevision.valid()
                                                    || !restoreModule.valid()) {
                                                fail("invalid_input");
                                                return;
                                            }
                                            CompoundTag payload = new CompoundTag();
                                            payload.putUUID("target", target.value().orElseThrow());
                                            payload.putInt(
                                                    "target_revision",
                                                    targetRevision.value().orElseThrow());
                                            payload.putString(
                                                    "module", restoreModule.value().orElseThrow());
                                            send(
                                                    snapshot,
                                                    SettlementNetwork.Action.RESTORE_AUTHOR,
                                                    payload);
                                        })));
        nodes.add(
                action(
                        "delete",
                        text("delete"),
                        editable() && !snapshot.instance().equals(NONE),
                        false,
                        () ->
                                confirm(
                                        "delete",
                                        () ->
                                                send(
                                                        snapshot,
                                                        SettlementNetwork.Action.DELETE_INSTANCE,
                                                        new CompoundTag()))));
        nodes.add(
                action(
                        "reload",
                        text("reload_draft"),
                        !pending.busy(),
                        false,
                        () ->
                                confirm(
                                        "reload",
                                        () -> {
                                            transformDirty = false;
                                            resetDraft(snapshot);
                                            refresh();
                                        })));
        return UiLayout.column(6, nodes);
    }

    private UiLayout.Node management() {
        CompoundTag data = snapshot.data();
        List<UiLayout.Node> nodes = new ArrayList<>();
        nodes.add(text(Component.literal(data.getString("name")), () -> theme().accent()));
        nodes.add(
                muted(
                        text(
                                "identity",
                                data.getString("faction"),
                                data.getInt("tier"),
                                snapshot.instance())));
        nodes.add(muted(text("residents_unavailable")));
        if (!snapshot.author()) {
            nodes.add(input("instance_name", displayName));
            nodes.add(
                    action(
                            "rename",
                            text("rename"),
                            !pending.busy(),
                            false,
                            () -> {
                                if (!displayName.valid()
                                        || displayName.value().orElse("").isBlank()) {
                                    fail("invalid_input");
                                    return;
                                }
                                CompoundTag payload = new CompoundTag();
                                payload.putString("name", displayName.value().orElseThrow());
                                send(snapshot, SettlementNetwork.Action.RENAME, payload);
                            }));
            nodes.add(muted(text("permission_hint")));
            nodes.add(muted(text("choose_owned")));
            ListTag owned = data.getList("owned_soldiers", Tag.TAG_COMPOUND);
            List<UiLayout.Node> soldiers = new ArrayList<>();
            for (int i = 0; i < Math.min(32, owned.size()); i++) {
                var person = owned.getCompound(i);
                if (!person.hasUUID("id")) continue;
                UUID id = person.getUUID("id");
                soldiers.add(
                        action(
                                "owned." + id,
                                Component.literal(person.getString("name")),
                                !pending.busy(),
                                postHuman.value().filter(id::equals).isPresent(),
                                () -> {
                                    postHuman.set(id.toString());
                                    refresh();
                                }));
            }
            nodes.add(UiLayout.grid(2, 6, soldiers));
            if (owned.isEmpty()) nodes.add(muted(text("no_owned")));
            nodes.add(input("post_human", postHuman));
        }
        int modulePage = Math.max(0, data.getInt("module_page")),
                modulePages = Math.max(1, data.getInt("module_pages"));
        if (data.contains("module_pages")) {
            nodes.add(
                    muted(
                            text(
                                    "module_page",
                                    modulePage + 1,
                                    modulePages,
                                    data.getInt("module_total"))));
            UiLayout.Node previous =
                    action(
                            "modulePrevious",
                            text("module_previous"),
                            !pending.busy() && modulePage > 0,
                            false,
                            () -> {
                                CompoundTag page = new CompoundTag();
                                page.putInt("module_page", modulePage - 1);
                                send(snapshot, SettlementNetwork.Action.REFRESH, page);
                            });
            UiLayout.Node next =
                    action(
                            "moduleNext",
                            text("module_next"),
                            !pending.busy() && modulePage + 1 < modulePages,
                            false,
                            () -> {
                                CompoundTag page = new CompoundTag();
                                page.putInt("module_page", modulePage + 1);
                                send(snapshot, SettlementNetwork.Action.REFRESH, page);
                            });
            nodes.add(UiLayout.grid(2, 6, List.of(previous, next)));
        }
        ListTag modules = data.getList("modules", Tag.TAG_COMPOUND);
        for (int m = 0; m < modules.size(); m++) {
            var module = modules.getCompound(m);
            String moduleKey = module.getString("key");
            nodes.add(muted(text("module", moduleKey)));
            ListTag regions = module.getList("regions", Tag.TAG_COMPOUND);
            for (int i = 0; i < regions.size(); i++) {
                var region = regions.getCompound(i);
                String key = moduleKey + "/" + region.getString("id");
                List<Component> lines = new ArrayList<>();
                for (String type : List.of("include", "exclude")) {
                    ListTag boxes = region.getList(type, Tag.TAG_INT_ARRAY);
                    for (int b = 0; b < boxes.size(); b++)
                        lines.add(text(type, Arrays.toString(boxes.getIntArray(b))));
                }
                PanelDisclosure row =
                        body(
                                new PanelDisclosure(
                                        button -> {
                                            expanded = expanded.equals(key) ? "" : key;
                                            refresh();
                                        },
                                        this::theme));
                row.update(
                        Component.literal(region.getString("name")),
                        text("layer." + region.getString("layer")),
                        lines,
                        expanded.equals(key),
                        false,
                        theme().text());
                controls.put("view." + key, row);
                nodes.add(node(row));
            }
            ListTag facilities = module.getList("facilities", Tag.TAG_COMPOUND);
            for (int i = 0; i < facilities.size(); i++) {
                var f = facilities.getCompound(i);
                nodes.add(
                        muted(
                                text(
                                        "facility_status",
                                        f.getString("stable_id"),
                                        f.getString("type"),
                                        Arrays.toString(f.getIntArray("position")),
                                        f.getString("status"),
                                        f.getString("reason"))));
                if (!snapshot.author() && f.getString("type").equals("POST"))
                    nodes.add(
                            action(
                                    "assign." + f.getString("stable_id"),
                                    text("assign_post"),
                                    !pending.busy(),
                                    false,
                                    () -> {
                                        if (!postHuman.valid()) {
                                            fail("invalid_input");
                                            return;
                                        }
                                        UUID id =
                                                SettlementNetwork.assignPost(
                                                        snapshot.instance(),
                                                        f.getString("stable_id"),
                                                        postHuman.value().orElseThrow(),
                                                        snapshot.instanceRevision());
                                        pending.begin(
                                                id,
                                                new Submission(
                                                        snapshot,
                                                        SettlementNetwork.Action.REFRESH,
                                                        new CompoundTag()));
                                        refresh();
                                    }));
            }
        }
        if (modules.isEmpty()) nodes.add(muted(text("no_modules")));
        return UiLayout.column(6, nodes);
    }

    @Override
    protected void buildFooter() {
        footer(Component.translatable("gui.done"), b -> onClose());
        if (!snapshot.author()) return;
        if (snapshot.author())
            footer(
                                    text("save_author"),
                                    b -> {
                                        if (!editable()) return;
                                        CompoundTag draft = gather();
                                        if (draft == null) {
                                            fail("invalid_input");
                                            return;
                                        }
                                        CompoundTag payload = new CompoundTag();
                                        payload.put("author", draft);
                                        send(
                                                editBase,
                                                SettlementNetwork.Action.SAVE_AUTHOR,
                                                payload);
                                    })
                            .active =
                    editable();
        footer(
                                text("refresh"),
                                b ->
                                        send(
                                                snapshot,
                                                SettlementNetwork.Action.REFRESH,
                                                new CompoundTag()))
                        .active =
                !pending.busy();
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.render(graphics, mouseX, mouseY, partial);
        if (!snapshot.author())
            SettlementMap.render(graphics, font, bodyRect, snapshot.data(), mouseX, mouseY);
    }

    @Override
    public void tick() {
        super.tick();
        if (++keepAliveTicks >= (snapshot.author() ? 300 : 20)) {
            keepAliveTicks = 0;
            SettlementNetwork.request(snapshot, SettlementNetwork.Action.KEEP_ALIVE, new CompoundTag());
        }
        if (confirmUntil > 0 && System.currentTimeMillis() >= confirmUntil) {
            confirmUntil = 0;
            confirmation = "";
            refresh();
        }
    }

    @Override
    public void removed() {
        if (snapshot.author() && authorLevel != null) {
            var views = AUTHOR_VIEWS.computeIfAbsent(authorLevel, ignored -> new java.util.LinkedHashMap<>());
            views.remove(snapshot.anchor());
            views.put(snapshot.anchor(), new AuthorView(tab, templateEntities));
            while (views.size() > 64) views.remove(views.keySet().iterator().next());
        }
        RETIRED.add(snapshot.token());
        while (RETIRED.size() > 32) RETIRED.remove(RETIRED.iterator().next());
        if (!serverClosed && Minecraft.getInstance().getConnection() != null)
            SettlementNetwork.request(snapshot, SettlementNetwork.Action.CLOSE, new CompoundTag());
        super.removed();
    }
}

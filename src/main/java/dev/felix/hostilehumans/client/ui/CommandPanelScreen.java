package dev.felix.hostilehumans.client.ui;

import dev.felix.hostilehumans.behavior.combat.CombatPreference;
import dev.felix.hostilehumans.client.ui.framework.PanelButton;
import dev.felix.hostilehumans.client.ui.framework.PanelCheck;
import dev.felix.hostilehumans.client.ui.framework.PanelGeometry;
import dev.felix.hostilehumans.client.ui.framework.PanelGroup;
import dev.felix.hostilehumans.client.ui.framework.PanelHealth;
import dev.felix.hostilehumans.client.ui.framework.PanelMeter;
import dev.felix.hostilehumans.client.ui.framework.PanelScreen;
import dev.felix.hostilehumans.client.ui.framework.PanelSlider;
import dev.felix.hostilehumans.client.ui.framework.PanelText;
import dev.felix.hostilehumans.client.ui.framework.UiLayout;
import dev.felix.hostilehumans.client.ui.framework.UiThemes;
import dev.felix.hostilehumans.progression.GrowthSnapshot;
import dev.felix.hostilehumans.soldier.CommandHealthSnapshot;
import dev.felix.hostilehumans.soldier.CommandView;
import dev.felix.hostilehumans.soldier.SoldierCombatMode;
import dev.felix.hostilehumans.soldier.SoldierOrder;
import dev.felix.hostilehumans.soldier.SquadCommands;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.*;

/** Shared native command composition. Domain updates still pass unchanged to the owning channel. */
public abstract class CommandPanelScreen extends PanelScreen {
    protected CommandView state;
    protected GrowthSnapshot growth;
    private final List<Runnable> bindings = new ArrayList<>();
    private final Map<String, AbstractWidget> identities = new LinkedHashMap<>();
    private String structure = "";
    private boolean deferredStructure;
    private PanelButton close, secondary;

    protected CommandPanelScreen(Component title, CommandView state, GrowthSnapshot growth) {
        super(title, PanelGeometry.Size.S);
        this.state = state;
        this.growth = growth;
    }

    protected CommandHealthSnapshot healthSnapshot() {
        return CommandHealthSnapshot.empty();
    }

    protected boolean representativeHealth() {
        return false;
    }

    protected abstract Component heading();

    protected abstract Component status();

    protected abstract void submit(SquadCommands.Update update);

    protected abstract void secondary();

    protected abstract Component secondaryLabel();

    protected boolean secondaryDanger() {
        return false;
    }

    protected abstract void reanchor();

    protected Component closeLabel() {
        return tr("close");
    }

    protected Component additionalStatus() {
        return Component.empty();
    }

    protected boolean hasContextWarning() {
        return !List.of("ready", "free", "empty").contains(state.reason());
    }

    protected Component contextReason() {
        return tr("context." + state.reason());
    }

    protected void details() {}

    protected boolean hasDetails() {
        return false;
    }

    protected boolean hasPatrolSettings() {
        return false;
    }

    protected void patrolSettings() {}

    protected int panelWidth() {
        return frame.width();
    }

    protected int panelHeight() {
        return frame.height();
    }

    protected int accent() {
        return theme().accent();
    }

    protected String headerFaction() {
        return "";
    }

    @Override
    protected String faction() {
        return headerFaction();
    }

    protected static Component tr(String key, Object... args) {
        return Component.translatable("screen.hostile_humans_unified.command." + key, args);
    }

    protected static Component ui(String key, Object... args) {
        return Component.translatable("gui.hostile_humans_unified.command." + key, args);
    }

    static Component orderName(SoldierOrder order) {
        return Component.translatable(
                "screen.hostile_humans_unified.soldier." + order.name().toLowerCase(Locale.ROOT));
    }

    static Component combatName(SoldierCombatMode mode) {
        return Component.translatable(
                "screen.hostile_humans_unified.soldier.combat."
                        + mode.name().toLowerCase(Locale.ROOT));
    }

    private boolean mixed(CommandView.Field field) {
        return field != null && state.mixed().contains(field);
    }

    private Component fieldLabel(Component label, CommandView.Field field) {
        return mixed(field) ? label.copy().append(" · ").append(tr("mixed")) : label;
    }

    private String structure() {
        return state.order()
                + ":"
                + mixed(CommandView.Field.ORDER)
                + ":"
                + hasPatrolSettings()
                + ":"
                + (growth != null && growth.enabled())
                + ":"
                + healthSnapshot().available();
    }

    protected void refreshSnapshot() {
        if (close == null) return;
        if (!structure.equals(structure())
                && getFocused() instanceof PanelSlider slider
                && slider.editing()) {
            deferredStructure = true;
            for (Runnable bind : bindings) bind.run();
            updateFooter();
            relayout();
            return;
        }
        if (!structure.equals(structure())) {
            String key =
                    identities.entrySet().stream()
                            .filter(e -> e.getValue() == getFocused())
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .orElse("");
            rebuildWidgets();
            AbstractWidget focus = identities.get(key);
            if (focus != null) {
                setFocused(focus);
                focus.setFocused(true);
                revealFocus();
            }
        } else {
            for (Runnable bind : bindings) bind.run();
            updateFooter();
            relayout();
        }
    }

    private UiLayout.Node label(Supplier<Component> text, boolean accent) {
        PanelText widget =
                body(new PanelText(text.get(), () -> accent ? theme().accent() : theme().muted()));
        bindings.add(() -> widget.setMessage(text.get()));
        return node(widget);
    }

    private UiLayout.Node action(
            String key,
            Supplier<Component> message,
            CommandView.Field field,
            BooleanSupplier selected,
            Runnable action) {
        PanelButton button =
                body(new PanelButton(fieldLabel(message.get(), field), b -> action.run(), this::theme));
        identities.put(key, button);
        Runnable bind =
                () -> {
                    button.setMessage(fieldLabel(message.get(), field));
                    button.selected(selected.getAsBoolean() && !mixed(field));
                };
        bindings.add(bind);
        bind.run();
        return node(button);
    }

    /** Checkbox row bound to a boolean field; keeps the mixed label rule of ordinary buttons. */
    private UiLayout.Node toggle(
            String key,
            Supplier<Component> message,
            CommandView.Field field,
            BooleanSupplier checked,
            Runnable action) {
        PanelCheck button =
                body(new PanelCheck(fieldLabel(message.get(), field), checked.getAsBoolean(), b -> action.run(), this::theme));
        identities.put(key, button);
        Runnable bind =
                () -> {
                    button.setMessage(fieldLabel(message.get(), field));
                    button.checked(checked.getAsBoolean());
                    button.active = true;
                };
        bindings.add(bind);
        bind.run();
        return node(button);
    }

    private UiLayout.Node group(Component heading, ResourceLocation marker, List<UiLayout.Node> content) {
        List<UiLayout.Node> nodes = new ArrayList<>();
        nodes.add(node(body(new PanelGroup(heading, marker, this::theme))));
        nodes.addAll(content);
        return UiLayout.column(4, List.copyOf(nodes));
    }

    private UiLayout.Node movement() {
        List<UiLayout.Node> orders = new ArrayList<>();
        for (var order : CommandView.ORDERS)
            orders.add(
                    action(
                            "order." + order,
                            () -> orderName(order),
                            CommandView.Field.ORDER,
                            () -> state.order() == order,
                            () -> submit(CommandView.order(order))));
        return group(tr("movement"), UiThemes.MARKER_MOVE, List.of(UiLayout.grid(3, 4, 96, orders)));
    }

    private UiLayout.Node garrison() {
        List<UiLayout.Node> nodes = new ArrayList<>();
        nodes.add(
                toggle(
                        "stay",
                        () -> tr(state.garrison().stay() ? "stay_on" : "stay_off"),
                        CommandView.Field.STAY,
                        () -> state.garrison().stay(),
                        () ->
                                submit(
                                        CommandView.stay(
                                                mixed(CommandView.Field.STAY)
                                                        || !state.garrison().stay()))));
        nodes.add(
                toggle(
                        "chase",
                        () -> tr(state.garrison().chase() ? "chase_on" : "chase_off"),
                        CommandView.Field.CHASE,
                        () -> state.garrison().chase(),
                        () ->
                                submit(
                                        CommandView.chase(
                                                mixed(CommandView.Field.CHASE)
                                                        || !state.garrison().chase()))));
        nodes.add(
                action(
                        "reanchor",
                        () -> tr("reanchor"),
                        CommandView.Field.ORDER,
                        () -> false,
                        this::reanchor));
        return group(
                Component.translatable("screen.hostile_humans_unified.soldier.garrison"),
                UiThemes.MARKER_GUARD,
                nodes);
    }

    private UiLayout.Node combat() {
        List<UiLayout.Node> modes = new ArrayList<>(), preferences = new ArrayList<>();
        for (var mode : CommandView.COMBAT)
            modes.add(
                    action(
                            "combat." + mode,
                            () -> combatName(mode),
                            CommandView.Field.COMBAT,
                            () -> state.combat() == mode,
                            () -> submit(CommandView.combat(mode))));
        for (var preference : CombatPreference.values())
            preferences.add(
                    action(
                            "preference." + preference,
                            () -> tr("preference." + preference.name().toLowerCase(Locale.ROOT)),
                            CommandView.Field.PREFERENCE,
                            () -> state.preference() == preference,
                            () -> submit(CommandView.preference(preference))));
        List<UiLayout.Node> groups = new ArrayList<>();
        groups.add(group(tr("combat"), UiThemes.MARKER_FIGHT, List.of(UiLayout.grid(2, 4, 100, modes))));
        groups.add(
                group(
                        tr("preference"),
                        UiThemes.MARKER_PREFER,
                        List.of(UiLayout.grid(2, 4, 100, preferences))));
        if (hasDetails())
            groups.add(
                    action("attributes", () -> tr("attributes"), null, () -> false, this::details));
        if (hasPatrolSettings())
            groups.add(
                    action(
                            "patrol",
                            () -> Component.translatable("patrol_wait.open"),
                            null,
                            () -> false,
                            this::patrolSettings));
        return UiLayout.column(9, groups);
    }

    private UiLayout.Node distance(boolean garrison) {
        CommandView.Field field = garrison ? CommandView.Field.RADIUS : CommandView.Field.FOLLOW;
        IntSupplier value = () -> garrison ? state.garrison().radius() : state.follow();
        PanelSlider slider =
                body(
                        new PanelSlider(
                                value.getAsInt(),
                                v -> tr(garrison ? "garrison_activity" : "owner_activity"),
                                v ->
                                        submit(
                                                garrison
                                                        ? CommandView.radius(v)
                                                        : CommandView.follow(v)),
                                this::theme)
                                .value(v -> tr("distance", v)));
        identities.put(garrison ? "radius" : "follow", slider);
        bindings.add(() -> slider.accept(value.getAsInt(), mixed(field)));
        return node(slider);
    }

    private UiLayout.Node settings() {
        List<UiLayout.Node> nodes = new ArrayList<>();
        nodes.add(
                toggle(
                        "pickup",
                        () -> tr(state.pickup() ? "pickup_on" : "pickup_off"),
                        CommandView.Field.PICKUP,
                        () -> state.pickup(),
                        () ->
                                submit(
                                        CommandView.pickup(
                                                mixed(CommandView.Field.PICKUP)
                                                        || !state.pickup()))));
        nodes.add(distance(true));
        nodes.add(distance(false));
        return group(tr("settings"), UiThemes.MARKER_RANGE, nodes);
    }

    /** Short faction caption in the title band; pages may replace it with a count. */
    @Override
    protected Component headerStatus() {
        return headerFaction().isBlank()
                ? Component.empty()
                : CommandPanelChrome.factionName(headerFaction());
    }

    @Override
    protected UiLayout.Node buildContent() {
        bindings.clear();
        identities.clear();
        structure = structure();
        List<UiLayout.Node> nodes = new ArrayList<>();
        nodes.add(label(this::heading, true));
        nodes.add(label(this::status, false));
        if (healthSnapshot().available()) {
            PanelHealth health = body(new PanelHealth().heading(true));
            Runnable bind =
                    () -> health.update(healthSnapshot().current(), healthSnapshot().maximum());
            bindings.add(bind);
            bind.run();
            nodes.add(node(health));
            nodes.add(label(this::healthLine, false));
        }
        nodes.add(label(this::additionalStatus, false));
        if (growth != null && growth.enabled()) {
            String p = "screen.hostile_humans_unified.soldier.growth.";
            nodes.add(label(() -> Component.translatable(p + "level", growth.level()), true));
            nodes.add(
                    label(
                            () ->
                                    growth.level() >= 5
                                            ? Component.translatable(p + "max_level")
                                            : Component.translatable(
                                                    p + "stage",
                                                    growth.stagePercent(),
                                                    amount(growth.stageProgress()),
                                                    amount(growth.stageRequired())),
                            false));
            nodes.add(
                    node(
                            body(
                                    new PanelMeter(
                                            () -> growth.level() >= 5 ? 1 : growth.stageProgress(),
                                            () -> growth.level() >= 5 ? 1 : growth.stageRequired(),
                                            () -> theme().accent()))));
            nodes.add(
                    label(
                            () ->
                                    growth.experiencePercent() >= growth.experienceCapPercent()
                                            ? Component.translatable(p + "experience_capped")
                                            : Component.translatable(
                                                    p + "experience",
                                                    growth.experiencePercent(),
                                                    growth.experienceMinPercent(),
                                                    growth.experienceCapPercent()),
                            false));
            nodes.add(
                    node(
                            body(
                                    new PanelMeter(
                                            () ->
                                                    growth.experiencePercent()
                                                            - growth.experienceMinPercent(),
                                            () ->
                                                    growth.experienceCapPercent()
                                                            - growth.experienceMinPercent(),
                                            () -> 0xFF8BB6BC))));
            if (growth.experiencePercent() < growth.experienceCapPercent())
                nodes.add(
                        label(
                                () ->
                                        Component.translatable(
                                                p + "next_point",
                                                amount(growth.experienceProgress()),
                                                amount(growth.experienceRequired())),
                                false));
        }
        nodes.add(movement());
        if (state.order() == SoldierOrder.GARRISON || mixed(CommandView.Field.ORDER))
            nodes.add(garrison());
        nodes.add(settings());
        nodes.add(combat());
        return UiLayout.column(6, nodes);
    }

    /** Health line under the ticks; the squad view keeps the representative wording. */
    private Component healthLine() {
        var snapshot = healthSnapshot();
        if (!representativeHealth())
            return snapshot.mixed()
                    ? ui("health_line", number(snapshot.current()), number(snapshot.maximum()))
                            .copy()
                            .append(" · ")
                            .append(ui("health_mixed"))
                    : ui("health_line", number(snapshot.current()), number(snapshot.maximum()));
        return Component.translatable(
                "gui.hostile_humans_unified.command.health_representative",
                snapshot.name(),
                number(snapshot.current()),
                number(snapshot.maximum()),
                Component.translatable(
                        snapshot.mixed()
                                ? "gui.hostile_humans_unified.command.health_mixed"
                                : "gui.hostile_humans_unified.command.health_sample"));
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private static String amount(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    protected void buildFooter() {
        close = footer(closeLabel(), b -> onClose());
        footerSpacer();
        secondary =
                footer(
                                secondaryLabel(),
                                b -> {
                                    secondary();
                                    updateFooter();
                                })
                        .danger(secondaryDanger());
    }

    private void updateFooter() {
        if (close != null) {
            close.setMessage(closeLabel());
            secondary.setMessage(secondaryLabel());
            secondary.danger(secondaryDanger());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (deferredStructure
                && !(getFocused() instanceof PanelSlider slider && slider.editing())) {
            deferredStructure = false;
            refreshSnapshot();
        }
        if (secondary != null && !secondary.getMessage().equals(secondaryLabel())) {
            updateFooter();
            relayout();
        }
    }
}
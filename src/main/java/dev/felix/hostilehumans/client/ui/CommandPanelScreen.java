package dev.felix.hostilehumans.client.ui;

import dev.felix.hostilehumans.behavior.combat.CombatPreference;
import dev.felix.hostilehumans.client.ui.framework.PanelButton;
import dev.felix.hostilehumans.client.ui.framework.PanelGeometry;
import dev.felix.hostilehumans.client.ui.framework.PanelHealth;
import dev.felix.hostilehumans.client.ui.framework.PanelMeter;
import dev.felix.hostilehumans.client.ui.framework.PanelScreen;
import dev.felix.hostilehumans.client.ui.framework.PanelSlider;
import dev.felix.hostilehumans.client.ui.framework.PanelText;
import dev.felix.hostilehumans.client.ui.framework.UiLayout;
import dev.felix.hostilehumans.progression.GrowthSnapshot;
import dev.felix.hostilehumans.soldier.CommandHealthSnapshot;
import dev.felix.hostilehumans.soldier.CommandView;
import dev.felix.hostilehumans.soldier.SoldierCombatMode;
import dev.felix.hostilehumans.soldier.SoldierOrder;
import dev.felix.hostilehumans.soldier.SquadCommands;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.*;

/** Shared native command composition. Domain updates still pass unchanged to the owning channel. */
public abstract class CommandPanelScreen extends PanelScreen {
    protected static final int PANEL = 0xF0101820,
            SURFACE = 0xFF202B36,
            HOVER = 0xFF2A3947,
            EDGE = 0xFF394653,
            TEXT = 0xFFE6EBEF,
            MUTED = 0xFF929FA9,
            GOLD = 0xFFF2C66D,
            DISABLED = 0xFF5F707C;
    protected CommandView state;
    protected GrowthSnapshot growth;
    private final List<Runnable> bindings = new ArrayList<>();
    private final Map<String, AbstractWidget> identities = new HashMap<>();
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

    protected float panelScale() {
        return 1F;
    }

    protected int left() {
        return frame.x();
    }

    protected int top() {
        return frame.y();
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

    protected static Component tr(String key) {
        return Component.translatable("screen.hostile_humans_unified.command." + key);
    }

    protected static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
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

    protected void button(
            GuiGraphics g,
            Component label,
            int x,
            int y,
            int w,
            int h,
            int mx,
            int my,
            boolean active,
            boolean chosen,
            boolean danger) {
        CommandPanelChrome.control(
                g,
                font,
                label,
                x,
                y,
                w,
                h,
                hit(mx, my, x, y, w, h),
                active,
                chosen,
                danger,
                accent());
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
                body(
                        new PanelButton(
                                fieldLabel(message.get(), field), b -> action.run(), this::theme));
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

    private UiLayout.Node group(Component heading, List<UiLayout.Node> content) {
        List<UiLayout.Node> nodes = new ArrayList<>();
        nodes.add(label(() -> heading, false));
        nodes.addAll(content);
        return UiLayout.column(6, List.copyOf(nodes));
    }

    private UiLayout.Node movement() {
        List<UiLayout.Node> nodes = new ArrayList<>();
        for (var order : CommandView.ORDERS)
            nodes.add(
                    action(
                            "order." + order,
                            () -> orderName(order),
                            CommandView.Field.ORDER,
                            () -> state.order() == order,
                            () -> submit(CommandView.order(order))));
        if (state.order() == SoldierOrder.GARRISON || mixed(CommandView.Field.ORDER)) {
            nodes.add(
                    action(
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
                    action(
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
        }
        return group(tr("movement"), nodes);
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
        List<UiLayout.Node> nodes = new ArrayList<>();
        nodes.add(group(tr("combat"), List.of(UiLayout.grid(2, 6, modes))));
        nodes.add(group(tr("preference"), List.of(UiLayout.grid(2, 6, preferences))));
        if (hasDetails())
            nodes.add(
                    action("attributes", () -> tr("attributes"), null, () -> false, this::details));
        if (hasPatrolSettings())
            nodes.add(
                    action(
                            "patrol",
                            () -> Component.translatable("patrol_wait.open"),
                            null,
                            () -> false,
                            this::patrolSettings));
        return UiLayout.column(9, nodes);
    }

    private UiLayout.Node distance(boolean garrison) {
        CommandView.Field field = garrison ? CommandView.Field.RADIUS : CommandView.Field.FOLLOW;
        IntSupplier value = () -> garrison ? state.garrison().radius() : state.follow();
        PanelSlider slider =
                body(
                        new PanelSlider(
                                value.getAsInt(),
                                v ->
                                        tr(garrison ? "garrison_activity" : "owner_activity")
                                                .copy()
                                                .append(" · ")
                                                .append(
                                                        Component.translatable(
                                                                "screen.hostile_humans_unified.command.distance",
                                                                v)),
                                v ->
                                        submit(
                                                garrison
                                                        ? CommandView.radius(v)
                                                        : CommandView.follow(v)),
                                this::theme));
        identities.put(garrison ? "radius" : "follow", slider);
        bindings.add(() -> slider.accept(value.getAsInt(), mixed(field)));
        return node(slider);
    }

    private UiLayout.Node settings() {
        return group(
                tr("settings"),
                List.of(
                        action(
                                "pickup",
                                () -> tr(state.pickup() ? "pickup_on" : "pickup_off"),
                                CommandView.Field.PICKUP,
                                () -> state.pickup(),
                                () ->
                                        submit(
                                                CommandView.pickup(
                                                        mixed(CommandView.Field.PICKUP)
                                                                || !state.pickup()))),
                        distance(true),
                        distance(false)));
    }

    @Override
    protected UiLayout.Node buildContent() {
        bindings.clear();
        identities.clear();
        structure = structure();
        List<UiLayout.Node> nodes = new ArrayList<>();
        nodes.add(label(this::heading, true));
        if (healthSnapshot().available()) {
            PanelHealth health = body(new PanelHealth());
            Runnable bind =
                    () -> health.update(healthSnapshot().current(), healthSnapshot().maximum());
            bindings.add(bind);
            bind.run();
            nodes.add(UiLayout.leaf(health::measureHeight, health::place));
            nodes.add(
                    label(
                            () ->
                                    Component.translatable(
                                            representativeHealth()
                                                    ? "gui.hostile_humans_unified.command.health_representative"
                                                    : "gui.hostile_humans_unified.command.health",
                                            healthSnapshot().name(),
                                            String.format(
                                                    Locale.ROOT,
                                                    "%.1f",
                                                    healthSnapshot().current()),
                                            String.format(
                                                    Locale.ROOT,
                                                    "%.1f",
                                                    healthSnapshot().maximum()),
                                            Component.translatable(
                                                    healthSnapshot().mixed()
                                                            ? "gui.hostile_humans_unified.command.health_mixed"
                                                            : "gui.hostile_humans_unified.command.health_sample")),
                            false));
        }
        nodes.add(label(this::status, false));
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
                                    Component.translatable(
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
            nodes.add(
                    label(
                            () ->
                                    growth.experiencePercent() >= growth.experienceCapPercent()
                                            ? Component.translatable(p + "experience_capped")
                                            : Component.translatable(
                                                    p + "next_point",
                                                    amount(growth.experienceProgress()),
                                                    amount(growth.experienceRequired())),
                            false));
        }
        nodes.add(
                UiLayout.row(
                        8, List.of(UiLayout.column(9, List.of(movement(), settings())), combat())));
        return UiLayout.column(9, nodes);
    }

    private static String amount(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    protected void buildFooter() {
        close = footer(closeLabel(), b -> onClose());
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

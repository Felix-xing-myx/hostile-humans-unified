package dev.felix.hostilehumans.cultivation.client;

import dev.felix.hostilehumans.client.ui.framework.InputValue;
import dev.felix.hostilehumans.client.ui.framework.PanelButton;
import dev.felix.hostilehumans.client.ui.framework.PanelDisclosure;
import dev.felix.hostilehumans.client.ui.framework.PanelGeometry;
import dev.felix.hostilehumans.client.ui.framework.PanelMeter;
import dev.felix.hostilehumans.client.ui.framework.PanelGroup;
import dev.felix.hostilehumans.client.ui.framework.PanelListRow;
import dev.felix.hostilehumans.client.ui.framework.UiThemes;
import dev.felix.hostilehumans.client.ui.framework.PanelScreen;
import dev.felix.hostilehumans.client.ui.framework.PendingAction;
import dev.felix.hostilehumans.client.ui.framework.UiLayout;
import dev.felix.hostilehumans.cultivation.CultivationNetwork;
import dev.felix.hostilehumans.cultivation.CultivationSnapshot;
import dev.felix.hostilehumans.progression.SpecialtyAttributes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.*;

/** Real server training data, composed from the same widgets as command/attribute panels. */
public final class CultivationScreen extends PanelScreen {
    private static final UUID NONE = new UUID(0, 0);
    private static final LinkedHashSet<UUID> RETIRED = new LinkedHashSet<>();
    private CultivationSnapshot snapshot;
    private UUID selected;
    private final PendingAction<CultivationNetwork.Request> pending = new PendingAction<>();
    private final Map<String, AbstractWidget> controls = new HashMap<>();
    private final Set<String> primary = new LinkedHashSet<>();
    private InputValue<Integer> amount = InputValue.integer(1, 1, 4096);
    private CultivationNetwork.Mode mode = CultivationNetwork.Mode.GROW;
    private CultivationNetwork.Mode previewMode;
    private UUID previewTarget = NONE;
    private int candidate;
    private boolean automatic, primaryDirty;
    private String expanded = "", factionFilter = "";
    private int tierFilter = -1;
    private long cancelUntil, breakthroughUntil, primaryUntil;
    private boolean resourcePage, advanced;

    private CultivationScreen(CultivationSnapshot snapshot) {
        super(text("title"), PanelGeometry.Size.L);
        this.snapshot = snapshot;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.hostile_humans_unified.cultivation." + key, args);
    }

    public static void show(CultivationSnapshot snapshot) {
        Minecraft mc = Minecraft.getInstance();
        if (RETIRED.contains(snapshot.token())) return;
        if (mc.screen instanceof CultivationScreen s
                && s.snapshot.token().equals(snapshot.token())) {
            s.accept(snapshot);
            return;
        }
        // A transaction reply must never reopen a page after Escape or replace a different active
        // panel.
        if (snapshot.acknowledgedRequest().equals(NONE))
            mc.setScreen(new CultivationScreen(snapshot));
    }

    private void accept(CultivationSnapshot next) {
        CultivationNetwork.Request ack = pending.request();
        boolean matched = pending.acknowledge(next.acknowledgedRequest());
        var before = selected();
        if (next.revision() >= snapshot.revision()) snapshot = next;
        var soldier = selected();
        if (before != null && (soldier == null || before.revision() != soldier.revision())) {
            breakthroughUntil = primaryUntil = 0;
            previewTarget = NONE;
            previewMode = null;
        }
        if (matched && ack != null && ack.target().equals(selected)) {
            if (ack.action() == CultivationNetwork.Action.PREVIEW && next.reason().isEmpty()) {
                previewTarget = selected;
                previewMode = ack.mode();
            }
            if (ack.action() == CultivationNetwork.Action.SET_PRIMARY
                    && soldier != null
                    && new HashSet<>(soldier.primary()).equals(new HashSet<>(ack.primary()))) {
                primary.clear();
                primary.addAll(soldier.primary());
                primaryDirty = false;
                previewTarget = NONE;
                previewMode = null;
            }
        }
        if (soldier != null && !primaryDirty) {
            primary.clear();
            primary.addAll(soldier.primary());
        }
        refresh();
    }

    private CultivationSnapshot.SoldierView selected() {
        return snapshot.soldiers().stream()
                .filter(s -> s.id().equals(selected))
                .findFirst()
                .orElse(null);
    }

    private void select(CultivationSnapshot.SoldierView soldier) {
        selected = soldier.id();
        primary.clear();
        primary.addAll(soldier.primary());
        primaryDirty = false;
        candidate = 0;
        expanded = "";
        cancelUntil = breakthroughUntil = primaryUntil = 0;
        previewTarget = NONE;
        previewMode = null;
    }

    private void refresh() {
        String key =
                controls.entrySet().stream()
                        .filter(e -> e.getValue() == getFocused())
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse("");
        rebuildWidgets();
        AbstractWidget focus = controls.get(key);
        if (focus != null) {
            setFocused(focus);
            focus.setFocused(true);
            revealFocus();
        }
    }

    private UiLayout.Node muted(Component label) {
        return text(label, () -> theme().muted());
    }

    private UiLayout.Node action(
            String key, Component label, boolean enabled, boolean chosen, Runnable run) {
        PanelButton button = body(new PanelButton(label, b -> run.run(), this::theme));
        button.active = enabled;
        button.selected(chosen);
        controls.put(key, button);
        return node(button);
    }

    private UiLayout.Node request(
            String key, Component label, CultivationNetwork.Action action, boolean enabled) {
        return action(key, label, !pending.busy() && enabled, false, () -> send(action));
    }

    private void send(CultivationNetwork.Action action) {
        if (pending.busy()) return;
        boolean account =
                action == CultivationNetwork.Action.DEPOSIT
                        || action == CultivationNetwork.Action.WITHDRAW;
        if (account && !amount.valid()) return;
        UUID target =
                account || action == CultivationNetwork.Action.REFRESH || selected == null
                        ? NONE
                        : selected;
        long signature = 0;
        var soldier = selected();
        if (soldier != null)
            for (var c : soldier.candidates())
                if (c.index() == candidate) signature = c.signature();
        var request =
                new CultivationNetwork.Request(
                        snapshot.token(),
                        UUID.randomUUID(),
                        snapshot.revision(),
                        target,
                        action,
                        candidate,
                        signature,
                        mode,
                        automatic,
                        action == CultivationNetwork.Action.SET_PRIMARY
                                ? List.copyOf(primary)
                                : List.of(),
                        amount.value().orElse(0));
        pending.begin(request.requestId(), request);
        CultivationNetwork.send(request);
        refresh();
    }

    @Override
    protected boolean fieldsEnabled() {
        return !pending.busy();
    }

    @Override
    protected void formChanged() {
        for (String key : List.of("deposit", "withdraw")) {
            var button = controls.get(key);
            if (button != null) button.active = !pending.busy() && amount.valid();
        }
        relayout();
    }

    @Override
    protected int splitWidth() {
        return Math.max(1, frame.width() / 3);
    }

    @Override
    protected void buildLeft(List<UiLayout.Node> list) {
        controls.clear();
        list.add(group("roster"));
        var factions = new ArrayList<String>();
        factions.add("");
        snapshot.soldiers().stream().map(CultivationSnapshot.SoldierView::faction)
                .filter(faction -> !faction.isEmpty()).distinct().sorted().forEach(factions::add);
        var tiers = new ArrayList<Integer>();
        tiers.add(-1);
        snapshot.soldiers().stream().map(CultivationSnapshot.SoldierView::tier)
                .distinct().sorted().forEach(tiers::add);
        list.add(action("tier_filter", text("filter_tier", tierLabel(tierFilter)), true, false, () -> {
            tierFilter = tiers.get((tiers.indexOf(tierFilter) + 1) % tiers.size());
            refresh();
        }));
        list.add(action("faction_filter", text("filter_faction", factionFilter.isEmpty()
                ? text("filter_all") : UiThemes.factionName(factionFilter)), true, false, () -> {
            factionFilter = factions.get((factions.indexOf(factionFilter) + 1) % factions.size());
            refresh();
        }));
        var rows = snapshot.soldiers().stream()
                .filter(soldier -> tierFilter < 0 || tierFilter == soldier.tier())
                .filter(soldier -> factionFilter.isEmpty() || factionFilter.equals(soldier.faction()))
                .toList();
        for (var soldier : rows) {
            String name = soldier.name().isBlank()
                    ? soldier.id().toString().substring(0, 8) : soldier.name();
            var metadata = Component.empty().append(tierLabel(soldier.tier()));
            if (!soldier.faction().isEmpty())
                metadata.append(" · ")
                        .append(UiThemes.factionName(soldier.faction()));
            var button = body(new PanelListRow(name, metadata.getString(), "", 0, 0,
                    UiThemes.faction(soldier.faction()).accent(),
                    Component.literal(name).append(" · ").append(metadata), ignored -> {
                        select(soldier);
                        refresh();
                    }, () -> UiThemes.faction(soldier.faction())));
            button.selected(soldier.id().equals(selected));
            button.active = !pending.busy();
            controls.put("soldier." + soldier.id(), button);
            list.add(node(button));
        }
        if (rows.isEmpty()) list.add(muted(text("empty")));
    }

    private static Component tierLabel(int tier) {
        return tier < 0 ? text("filter_all")
                : Component.translatable("screen.hostile_humans_unified.roster.tier." + tier);
    }

    private UiLayout.Node group(String key) {
        return node(body(new PanelGroup(text(key), null, this::theme)));
    }

    @Override
    protected void buildBand() {
        var training = band(new PanelButton(text("training_tab"), b -> {
            resourcePage = false;
            refresh();
        }, this::theme));
        training.selected(!resourcePage);
        var resources = band(new PanelButton(text("resources_tab"), b -> {
            resourcePage = true;
            refresh();
        }, this::theme));
        resources.selected(resourcePage);
    }

    @Override
    protected void buildRight(List<UiLayout.Node> content) {
        if (!resourcePage) {
            if (snapshot.testMode()) content.add(text(text("test_mode"), () -> theme().danger()));
            if (!snapshot.reason().isEmpty())
                content.add(text(Component.literal(snapshot.reason()), () -> theme().danger()));
            if (pending.busy()) content.add(muted(text("pending")));
            content.add(detail());
            return;
        }
        if (snapshot.testMode()) content.add(text(text("test_mode"), () -> theme().danger()));
        content.add(
                muted(
                        Component.literal(
                                snapshot.dimension()
                                        + " · "
                                        + snapshot.x()
                                        + ", "
                                        + snapshot.y()
                                        + ", "
                                        + snapshot.z()
                                        + " · r"
                                        + snapshot.revision())));
        content.add(muted(Component.literal(snapshot.reason())));
        if (pending.busy()) content.add(text(text("pending"), () -> theme().accent()));
        content.add(group("resources"));
        var account = snapshot.account();
        content.add(
                muted(
                        text(
                                "account",
                                account.stock(),
                                number(account.creditMicro() / 1000000.0),
                                number(account.reservedMicro() / 1000000.0))));
        content.add(field(text("amount"), amount));
        content.add(
                UiLayout.grid(
                        2,
                        6,
                        List.of(
                                request(
                                        "deposit",
                                        text("deposit"),
                                        CultivationNetwork.Action.DEPOSIT,
                                        amount.valid()),
                                request(
                                        "withdraw",
                                        text("withdraw"),
                                        CultivationNetwork.Action.WITHDRAW,
                                        amount.valid()))));
        content.add(group("seats"));
        for (var seat : snapshot.seats()) {
            String name = seat.soldier() == null ? "—" : snapshot.soldiers().stream()
                    .filter(soldier -> soldier.id().equals(seat.soldier()))
                    .map(CultivationSnapshot.SoldierView::name).filter(value -> !value.isBlank())
                    .findFirst().orElse(seat.soldier().toString().substring(0, 8));
            content.add(muted(text("seat", seat.index() + 1, name, text("status." + seat.status()))));
        }
        content.add(muted(text("resource_help")));
    }

    private UiLayout.Node detail() {
        var soldier = selected();
        if (soldier == null) return muted(text("select_soldier"));
        List<UiLayout.Node> content = new ArrayList<>();
        content.add(group("training"));
        content.add(text(Component.literal(soldier.name()), () -> UiThemes.faction(soldier.faction()).accent()));
        content.add(
                muted(
                        text(
                                "budget",
                                number(soldier.baseBudget()),
                                number(soldier.budget()),
                                number(soldier.potential()),
                                number(soldier.used()))));
        if (!soldier.reason().isEmpty())
            content.add(text(Component.literal(soldier.reason()), () -> theme().danger()));
        if (soldier.breakthroughReady()) {
            content.add(group("breakthrough_node"));
            content.add(muted(text("breakthrough_requirements")));
            content.add(muted(text("breakthrough_cost")));
            if (!canPay(1)) content.add(text(text("supplies_missing"), () -> theme().danger()));
            content.add(muted(text("breakthrough_footer")));
        }
        if (cycleLocked(soldier)) {
            var cycle = soldier.cycle();
            content.add(
                    muted(
                            text(
                                    "cycle",
                                    text("status." + cycle.status()),
                                    cycle.elapsedMillis() / 1000,
                                    cycle.durationMillis() / 1000,
                                    cycle.reason())));
            content.add(
                    node(
                            body(
                                    new PanelMeter(
                                            () -> cycle.elapsedMillis(),
                                            () -> cycle.durationMillis(),
                                            () -> theme().accent()))));
            boolean paused = cycle.status().equals("PAUSED");
            if (paused || cycle.status().equals("ACTIVE") || cycle.status().equals("MOVING"))
                content.add(request(paused ? "resume" : "pause", text(paused ? "resume" : "pause"),
                        paused ? CultivationNetwork.Action.RESUME : CultivationNetwork.Action.PAUSE, true));
            content.add(
                    action(
                            "cancel",
                            text(
                                    System.currentTimeMillis() < cancelUntil
                                            ? "cancel_confirm"
                                            : "cancel"),
                            !pending.busy(),
                            false,
                            () -> {
                                if (System.currentTimeMillis() < cancelUntil) {
                                    cancelUntil = 0;
                                    send(CultivationNetwork.Action.CANCEL);
                                } else {
                                    cancelUntil = System.currentTimeMillis() + 4000;
                                    refresh();
                                }
                            }));
            return UiLayout.column(6, content);
        }
        content.add(group("allocation"));
        content.add(muted(text("selection_help")));
        content.add(muted(text("primary", primary.size(), soldier.expanded() ? 5 : 3)));
        List<UiLayout.Node> choices = new ArrayList<>();
        for (var dimension : SpecialtyAttributes.Dimension.values()) {
            String id = dimension.key();
            choices.add(
                    action(
                            "primary." + id,
                            Component.translatable("attribute_detail.dimension." + id),
                            !pending.busy(),
                            primary.contains(id),
                            () -> {
                                if (primary.remove(id)) {
                                    primaryDirty = true;
                                } else if (primary.size() < (soldier.expanded() ? 5 : 3)) {
                                    primary.add(id);
                                    primaryDirty = true;
                                }
                                previewMode = null;
                                previewTarget = NONE;
                                primaryUntil = 0;
                                refresh();
                            }));
        }
        content.add(UiLayout.grid(3, 4, 65, choices));
        content.add(muted(text("primary_cost")));
        boolean dirty = primaryChanged(soldier);
        if (dirty) {
            content.add(muted(text("selection_footer")));
            if (!canPay(1)) content.add(text(text("supplies_missing"), () -> theme().danger()));
        }
        content.add(
                UiLayout.grid(
                        2,
                        6,
                        List.of(
                                action(
                                        "grow",
                                        text("grow"),
                                        !pending.busy(),
                                        mode == CultivationNetwork.Mode.GROW,
                                        () -> {
                                            mode = CultivationNetwork.Mode.GROW;
                                            breakthroughUntil = primaryUntil = 0;
                                            refresh();
                                        }),
                                action(
                                        "redistribute",
                                        text("redistribute"),
                                        !pending.busy(),
                                        mode == CultivationNetwork.Mode.REDISTRIBUTE,
                                        () -> {
                                            mode = CultivationNetwork.Mode.REDISTRIBUTE;
                                            breakthroughUntil = primaryUntil = 0;
                                            refresh();
                                        }))));
        content.add(muted(text(previewReady() ? "candidate_help" : "preview_footer")));
        if (previewReady() && soldier.candidates().isEmpty()) content.add(muted(text("no_candidates")));
        List<UiLayout.Node> candidates = new ArrayList<>();
        for (var c : previewReady() ? soldier.candidates() : List.<CultivationSnapshot.CandidateView>of())
            candidates.add(
                    action(
                            "candidate." + c.index(),
                            text(
                                    "candidate",
                                    c.index() + 1,
                                    Component.translatable(
                                            "gui.hostile_humans_unified.cultivation.style."
                                                    + c.style())),
                            !pending.busy(),
                            candidate == c.index(),
                            () -> {
                                candidate = c.index();
                                refresh();
                            }));
        content.add(UiLayout.grid(3, 4, 65, candidates));
        CultivationSnapshot.CandidateView chosen = null;
        if (previewReady())
            for (var c : soldier.candidates()) if (c.index() == candidate) chosen = c;
        if (chosen != null)
            content.add(
                    muted(
                            text(
                                    "cost",
                                    number(chosen.nominalStep()),
                                    number(chosen.potentialCost()),
                                    number(chosen.materialCost()),
                                    chosen.durationMillis() / 1000,
                                    number(chosen.successBudget()),
                                    number(chosen.failureBudget()))));
        if (chosen != null && !canPay(chosen.materialCost()))
            content.add(text(text("supplies_missing"), () -> theme().danger()));
        content.add(action("advanced", text(advanced ? "advanced_close" : "advanced_open"), true, advanced, () -> {
            advanced = !advanced;
            refresh();
        }));
        if (!advanced) return UiLayout.column(6, content);
        content.add(muted(text("advanced_help")));
        content.add(group("execution"));
        content.add(
                action(
                        "automatic",
                        text(automatic ? "automatic_on" : "automatic_off"),
                        !pending.busy(),
                        automatic,
                        () -> {
                            automatic = !automatic;
                            refresh();
                        }));
        content.add(
                UiLayout.grid(
                        2,
                        6,
                        List.of(
                                request(
                                        "potential",
                                        text("use_potential"),
                                        CultivationNetwork.Action.USE_POTENTIAL,
                                        true),
                                request(
                                        "expand",
                                        text("expand_primary"),
                                        CultivationNetwork.Action.EXPAND_PRIMARY,
                                        !soldier.expanded()))));
        content.add(group("attributes"));
        double[] values = soldier.values(), lower = soldier.lower(), upper = soldier.upper();
        double[] success = chosen == null ? null : chosen.successValues(),
                failure = chosen == null ? null : chosen.failureValues();
        var dimensions = SpecialtyAttributes.Dimension.values();
        for (int i = 0; i < Math.min(dimensions.length, values.length); i++) {
            String key = dimensions[i].key();
            List<Component> explanation = new ArrayList<>();
            explanation.add(text("bounds", number(lower[i]), number(upper[i])));
            if (success != null) {
                explanation.add(text("success_value", number(success[i])));
                explanation.add(text("failure_value", number(failure[i])));
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
                    Component.translatable("attribute_detail.dimension." + key),
                    Component.literal(number(values[i])),
                    explanation,
                    expanded.equals(key),
                    primary.contains(key),
                    theme().text());
            controls.put("dimension." + key, row);
            content.add(node(row));
        }
        return UiLayout.column(6, content);
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.6f", value)
                .replaceFirst("0+$", "")
                .replaceFirst("\\.$", "");
    }

    private boolean primaryChanged(CultivationSnapshot.SoldierView soldier) {
        return !new HashSet<>(primary).equals(new HashSet<>(soldier.primary()));
    }

    private boolean previewReady() {
        var soldier = selected();
        return soldier != null && !primaryChanged(soldier)
                && previewTarget.equals(selected) && previewMode == mode;
    }

    private static boolean cycleLocked(CultivationSnapshot.SoldierView soldier) {
        return soldier.cycle() != null && !soldier.cycle().status().equals("COMPLETED")
                && !soldier.cycle().status().equals("CANCELLED");
    }

    private boolean canPay(double cost) {
        return snapshot.account().stock() + snapshot.account().creditMicro() / 1000000.0 >= cost;
    }

    private void confirm(CultivationNetwork.Action action) {
        long now = System.currentTimeMillis();
        long until = action == CultivationNetwork.Action.CONFIRM_BREAKTHROUGH ? breakthroughUntil : primaryUntil;
        if (now < until) {
            breakthroughUntil = primaryUntil = 0;
            send(action);
        } else {
            if (action == CultivationNetwork.Action.CONFIRM_BREAKTHROUGH) breakthroughUntil = now + 5000;
            else primaryUntil = now + 5000;
            refresh();
        }
    }

    @Override
    protected void buildFooter() {
        footer(Component.translatable("gui.done"), b -> onClose());
        footerSpacer();
        if (pending.busy()) {
            footer(text("retry"), b -> CultivationNetwork.send(pending.request()));
            return;
        }
        var soldier = selected();
        if (!resourcePage && soldier != null && !cycleLocked(soldier)) {
            if (soldier.breakthroughReady() && mode == CultivationNetwork.Mode.GROW) {
                footer(text(System.currentTimeMillis() < breakthroughUntil ? "breakthrough_confirm" : "breakthrough"),
                        b -> confirm(CultivationNetwork.Action.CONFIRM_BREAKTHROUGH)).selected(true).active = canPay(1);
            } else if (primaryChanged(soldier)) {
                footer(text(System.currentTimeMillis() < primaryUntil ? "primary_confirm" : "set_primary"),
                        b -> confirm(CultivationNetwork.Action.SET_PRIMARY)).selected(true).active = !primary.isEmpty() && canPay(1);
            } else if (!previewReady()) {
                footer(text("preview"), b -> send(CultivationNetwork.Action.PREVIEW)).selected(true);
            } else {
                CultivationSnapshot.CandidateView chosen = soldier.candidates().stream()
                        .filter(c -> c.index() == candidate).findFirst().orElse(null);
                footer(text("start"), b -> send(CultivationNetwork.Action.START)).selected(true).active = chosen != null
                        && canPay(chosen.materialCost());
            }
        }
        footer(text("refresh"), b -> send(CultivationNetwork.Action.REFRESH));
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (cancelUntil > 0 && now >= cancelUntil || breakthroughUntil > 0 && now >= breakthroughUntil
                || primaryUntil > 0 && now >= primaryUntil) {
            if (now >= cancelUntil) cancelUntil = 0;
            if (now >= breakthroughUntil) breakthroughUntil = 0;
            if (now >= primaryUntil) primaryUntil = 0;
            refresh();
        }
    }

    @Override
    public void removed() {
        RETIRED.add(snapshot.token());
        while (RETIRED.size() > 32) RETIRED.remove(RETIRED.iterator().next());
        super.removed();
    }
}

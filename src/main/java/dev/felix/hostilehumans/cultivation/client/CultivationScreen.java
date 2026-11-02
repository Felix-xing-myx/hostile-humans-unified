package dev.felix.hostilehumans.cultivation.client;

import dev.felix.hostilehumans.client.ui.framework.InputValue;
import dev.felix.hostilehumans.client.ui.framework.PanelButton;
import dev.felix.hostilehumans.client.ui.framework.PanelDisclosure;
import dev.felix.hostilehumans.client.ui.framework.PanelGeometry;
import dev.felix.hostilehumans.client.ui.framework.PanelMeter;
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
    private String expanded = "";
    private long cancelUntil;

    private CultivationScreen(CultivationSnapshot snapshot) {
        super(text("title"), PanelGeometry.Size.L);
        this.snapshot = snapshot;
        if (!snapshot.soldiers().isEmpty()) select(snapshot.soldiers().get(0));
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
        if (next.revision() >= snapshot.revision()) snapshot = next;
        if (selected == null && !snapshot.soldiers().isEmpty()) select(snapshot.soldiers().get(0));
        var soldier = selected();
        if (matched && ack != null && ack.target().equals(selected)) {
            if (ack.action() == CultivationNetwork.Action.PREVIEW) {
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
        cancelUntil = 0;
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
    protected UiLayout.Node buildContent() {
        controls.clear();
        List<UiLayout.Node> content = new ArrayList<>();
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
        List<UiLayout.Node> list = new ArrayList<>();
        for (var seat : snapshot.seats())
            list.add(
                    muted(
                            text(
                                    "seat",
                                    seat.index() + 1,
                                    seat.soldier() == null
                                            ? "—"
                                            : seat.soldier().toString().substring(0, 8),
                                    seat.status())));
        for (var soldier : snapshot.soldiers())
            list.add(
                    action(
                            "soldier." + soldier.id(),
                            Component.literal(soldier.name()),
                            true,
                            soldier.id().equals(selected),
                            () -> {
                                select(soldier);
                                refresh();
                            }));
        if (snapshot.soldiers().isEmpty()) list.add(muted(text("empty")));
        content.add(UiLayout.row(12, List.of(UiLayout.column(6, list), detail())));
        return UiLayout.column(6, content);
    }

    private UiLayout.Node detail() {
        var soldier = selected();
        if (soldier == null) return muted(text("empty"));
        List<UiLayout.Node> content = new ArrayList<>();
        content.add(text(Component.literal(soldier.name()), () -> theme().accent()));
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
        if (soldier.cycle() != null) {
            var cycle = soldier.cycle();
            content.add(
                    muted(
                            text(
                                    "cycle",
                                    cycle.status(),
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
            content.add(
                    UiLayout.grid(
                            2,
                            6,
                            List.of(
                                    request(
                                            "pause",
                                            text("pause"),
                                            CultivationNetwork.Action.PAUSE,
                                            true),
                                    request(
                                            "resume",
                                            text("resume"),
                                            CultivationNetwork.Action.RESUME,
                                            true))));
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
        }
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
                                refresh();
                            }));
        }
        content.add(UiLayout.grid(3, 4, 65, choices));
        content.add(muted(text("primary_cost")));
        boolean dirty = !new HashSet<>(primary).equals(new HashSet<>(soldier.primary()));
        content.add(
                request(
                        "setPrimary",
                        text("set_primary"),
                        CultivationNetwork.Action.SET_PRIMARY,
                        dirty && !primary.isEmpty()));
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
                                            refresh();
                                        }),
                                action(
                                        "redistribute",
                                        text("redistribute"),
                                        !pending.busy(),
                                        mode == CultivationNetwork.Mode.REDISTRIBUTE,
                                        () -> {
                                            mode = CultivationNetwork.Mode.REDISTRIBUTE;
                                            refresh();
                                        }))));
        content.add(request("preview", text("preview"), CultivationNetwork.Action.PREVIEW, !dirty));
        List<UiLayout.Node> candidates = new ArrayList<>();
        for (var c : soldier.candidates())
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
                request(
                        "start",
                        text("start"),
                        CultivationNetwork.Action.START,
                        chosen != null
                                && !dirty
                                && previewTarget.equals(selected)
                                && previewMode == mode));
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
        content.add(
                request(
                        "breakthrough",
                        text("breakthrough"),
                        CultivationNetwork.Action.CONFIRM_BREAKTHROUGH,
                        !soldier.breakthrough()));
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

    @Override
    protected void buildFooter() {
        footer(Component.translatable("gui.done"), b -> onClose());
        footer(
                text(pending.busy() ? "retry" : "refresh"),
                b -> {
                    if (pending.busy()) CultivationNetwork.send(pending.request());
                    else send(CultivationNetwork.Action.REFRESH);
                });
    }

    @Override
    public void tick() {
        super.tick();
        if (cancelUntil > 0 && System.currentTimeMillis() >= cancelUntil) {
            cancelUntil = 0;
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

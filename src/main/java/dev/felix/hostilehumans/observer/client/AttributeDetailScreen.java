package dev.felix.hostilehumans.observer.client;

import dev.felix.hostilehumans.client.ui.SoldierCommandScreen;
import dev.felix.hostilehumans.client.ui.SoldierRosterScreen;
import dev.felix.hostilehumans.client.ui.framework.PanelButton;
import dev.felix.hostilehumans.client.ui.framework.PanelDisclosure;
import dev.felix.hostilehumans.client.ui.framework.PanelGeometry;
import dev.felix.hostilehumans.client.ui.framework.PanelHealth;
import dev.felix.hostilehumans.client.ui.framework.PanelScreen;
import dev.felix.hostilehumans.client.ui.framework.PanelSessionView;
import dev.felix.hostilehumans.client.ui.framework.PanelText;
import dev.felix.hostilehumans.client.ui.framework.UiLayout;
import dev.felix.hostilehumans.observer.AttributeDetailNetwork;
import dev.felix.hostilehumans.observer.AttributeDetailSnapshot;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/** Server-backed attribute view composed from persistent native widgets and inline disclosures. */
public final class AttributeDetailScreen extends PanelScreen {
    private final Screen parent;
    private final PanelSessionView session;
    private AttributeDetailSnapshot snapshot;
    private final Map<String, PanelDisclosure> rows = new LinkedHashMap<>();
    private final List<PanelButton> labels = new ArrayList<>();
    private PanelText identity, budget, sample;
    private PanelHealth health;
    private Set<String> highlighted = Set.of();
    private String expanded = "";

    public AttributeDetailScreen(Screen parent, UUID token, AttributeDetailSnapshot snapshot) {
        super(text("title"), PanelGeometry.Size.S);
        this.parent = parent;
        this.snapshot = snapshot;
        session =
                new PanelSessionView(
                        token,
                        20,
                        () -> AttributeDetailNetwork.keepAlive(token),
                        () -> AttributeDetailNetwork.close(token));
    }

    public Screen parent() {
        return parent;
    }

    public UUID token() {
        return session.token();
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("attribute_detail." + key, args);
    }

    private static Component dimension(String key) {
        return text("dimension." + key);
    }

    private boolean managedParent() {
        return parent instanceof SoldierCommandScreen || parent instanceof SoldierRosterScreen;
    }

    @Override
    protected String faction() {
        return snapshot.faction();
    }

    @Override
    protected Component headerStatus() {
        return text(snapshot.boss() ? "boss" : "ordinary");
    }

    public void update(AttributeDetailSnapshot next) {
        if (!next.target().equals(snapshot.target())
                || next.entityId() != snapshot.entityId()
                || next.tick() < snapshot.tick()) return;
        boolean structure =
                !next.labels().equals(snapshot.labels())
                        || !next.rows().stream()
                                .map(AttributeDetailSnapshot.Row::dimension)
                                .toList()
                                .equals(
                                        snapshot.rows().stream()
                                                .map(AttributeDetailSnapshot.Row::dimension)
                                                .toList());
        snapshot = next;
        if (identity != null) {
            if (structure) rebuildWidgets();
            else refresh();
        }
    }

    public void serverClosed() {
        session.suppressClose();
        minecraft.setScreen(managedParent() ? null : parent);
    }

    public void disconnect() {
        session.suppressClose();
    }

    @Override
    public void tick() {
        session.tick();
    }

    @Override
    public void removed() {
        session.removed();
    }

    @Override
    public void onClose() {
        if (managedParent()) {
            if (!session.suppressed() && minecraft.getConnection() != null)
                AttributeDetailNetwork.returnToParent(
                        token(), parent instanceof SoldierRosterScreen);
            minecraft.setScreen(null);
        } else minecraft.setScreen(parent);
    }

    @Override
    protected UiLayout.Node buildContent() {
        rows.clear();
        labels.clear();
        List<UiLayout.Node> content = new ArrayList<>();
        identity = body(new PanelText(Component.empty(), () -> theme().accent()));
        content.add(node(identity));
        health = body(new PanelHealth().heading(true));
        content.add(UiLayout.leaf(health::measureHeight, health::place));
        budget = body(new PanelText(Component.empty(), () -> theme().muted()));
        content.add(node(budget));
        sample = body(new PanelText(Component.empty(), () -> theme().muted()));
        content.add(node(sample));
        if (snapshot.labels().isEmpty())
            content.add(text(text("no_labels"), () -> theme().muted()));
        else {
            List<UiLayout.Node> tags = new ArrayList<>();
            for (var label : snapshot.labels()) {
                PanelButton button =
                        body(
                                new PanelButton(
                                        text("label." + label.key()),
                                        b -> {
                                            Set<String> selected = Set.copyOf(label.dimensions());
                                            highlighted =
                                                    highlighted.equals(selected)
                                                            ? Set.of()
                                                            : selected;
                                            refresh();
                                        },
                                        this::theme)
                                .compact(true));
                labels.add(button);
                tags.add(node(button));
            }
            content.add(UiLayout.row(4, tags));
        }
        content.add(text(text("hint"), () -> theme().muted()));
        for (var row : snapshot.rows()) {
            String key = row.dimension();
            PanelDisclosure widget =
                    body(
                            new PanelDisclosure(
                                    b -> {
                                        expanded = expanded.equals(key) ? "" : key;
                                        refresh();
                                        revealFocus();
                                    },
                                    this::theme));
            rows.put(key, widget);
            content.add(node(widget));
        }
        bind();
        return UiLayout.column(4, List.copyOf(content));
    }

    private void refresh() {
        bind();
        relayout();
    }

    private void bind() {
        identity.setMessage(Component.literal(snapshot.name()));
        budget.setMessage(
                text(
                        "health_budget",
                        String.format(Locale.ROOT, "%.2f", snapshot.currentHealth()),
                        String.format(Locale.ROOT, "%.3f", snapshot.budget())));
        sample.setMessage(
                text("sample", snapshot.tick(), text(snapshot.enabled() ? "enabled" : "disabled")));
        double maximum =
                snapshot.rows().stream()
                        .filter(row -> row.dimension().equals("health") && row.known())
                        .mapToDouble(AttributeDetailSnapshot.Row::value)
                        .findFirst()
                        .orElse(snapshot.currentHealth());
        health.update(snapshot.currentHealth(), maximum);
        for (int i = 0; i < labels.size(); i++)
            labels.get(i)
                    .selected(
                            highlighted.equals(Set.copyOf(snapshot.labels().get(i).dimensions())));
        for (var row : snapshot.rows())
            rows.get(row.dimension())
                    .update(
                            dimension(row.dimension()),
                            Component.literal(arrow(row) + " " + displayed(row)),
                            detail(row),
                            expanded.equals(row.dimension()),
                            highlighted.contains(row.dimension()),
                            0xFF000000 | color(row));
    }

    @Override
    public Component getNarrationMessage() {
        return title.copy()
                .append(". ")
                .append(
                        text(
                                "header",
                                snapshot.name(),
                                snapshot.boss() ? text("boss") : text("ordinary")))
                .append(". ")
                .append(budget == null ? Component.empty() : budget.getMessage());
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.4f", value)
                .replaceFirst("0+$", "")
                .replaceFirst("\\.$", "");
    }

    private static String displayed(AttributeDetailSnapshot.Row row) {
        if (!row.known()) return text("unknown").getString();
        return switch (row.unit()) {
            case "percent" -> String.format(Locale.ROOT, "%.2f%%", row.value() * 100);
            case "hp" -> String.format(Locale.ROOT, "%.2f HP", row.value());
            case "factor" -> "×" + number(row.value());
            default -> number(row.value()) + " " + text("unit." + row.unit()).getString();
        };
    }

    private static boolean comparable(AttributeDetailSnapshot.Row row) {
        return row.known()
                && row.notes().stream().noneMatch(note -> note.key().equals("reference_unknown"));
    }

    private static String arrow(AttributeDetailSnapshot.Row row) {
        return !comparable(row)
                ? "?"
                : row.value() > row.baseline() + 1e-6
                        ? "↑"
                        : row.value() < row.baseline() - 1e-6 ? "↓" : "≈";
    }

    private static int color(AttributeDetailSnapshot.Row row) {
        return !comparable(row)
                ? 0xB9C1CA
                : row.value() > row.baseline() + 1e-6
                        ? 0x95D7AB
                        : row.value() < row.baseline() - 1e-6 ? 0xE59A94 : 0xD7DFE7;
    }

    private List<Component> detail(AttributeDetailSnapshot.Row row) {
        List<Component> result = new ArrayList<>();
        result.add(text("stored_effective", number(row.stored()), number(row.effective())));
        result.add(text("base", number(row.base())));
        for (var layer : row.layers())
            result.add(
                    text(
                            "layer_line",
                            text("layer." + layer.key()),
                            text("operation." + layer.operation()),
                            number(layer.value())));
        result.add(text("final", displayed(row)));
        result.add(
                text(
                        !comparable(row)
                                ? "comparison.unknown"
                                : row.value() > row.baseline() + 1e-6
                                        ? "comparison.higher"
                                        : row.value() < row.baseline() - 1e-6
                                                ? "comparison.lower"
                                                : "comparison.similar",
                        number(row.baseline())));
        for (var note : row.notes()) {
            if (note.key().startsWith("gun_category_"))
                result.add(
                        text(
                                "gun_category_value",
                                text(
                                        "gun_category."
                                                + note.key().substring("gun_category_".length())),
                                note.detail()));
            else result.add(text("note." + note.key(), note.detail()));
        }
        return result;
    }
}

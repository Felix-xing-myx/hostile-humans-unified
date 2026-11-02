package dev.felix.hostilehumans.client.ui;

import dev.felix.hostilehumans.client.ui.framework.PanelButton;
import dev.felix.hostilehumans.client.ui.framework.PanelGeometry;
import dev.felix.hostilehumans.client.ui.framework.PanelScreen;
import dev.felix.hostilehumans.client.ui.framework.UiLayout;
import dev.felix.hostilehumans.soldier.PatrolToolNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared native composition; all route mutations remain server acknowledged. */
public final class PatrolWaitScreen extends PanelScreen {
    private PatrolToolNetwork.Snapshot state;
    private int page;
    private boolean pending;

    private PatrolWaitScreen(PatrolToolNetwork.Snapshot snapshot) {
        super(
                text(snapshot.control() != null ? "controller_title" : "title"),
                PanelGeometry.Size.S);
        state = snapshot;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("patrol_wait." + key, args);
    }

    public static void show(PatrolToolNetwork.Snapshot snapshot) {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof PatrolWaitScreen s && s.state.token().equals(snapshot.token())) {
            if (!snapshot.valid()) {
                s.onClose();
                return;
            }
            s.state = snapshot;
            s.pending = false;
            s.rebuildWidgets();
        } else if (snapshot.opening() && snapshot.valid())
            mc.setScreen(new PatrolWaitScreen(snapshot));
    }

    private boolean controller() {
        return state.control() != null;
    }

    private boolean editable() {
        return !controller() || state.control().mode() != 0;
    }

    private int rowsPerPage() {
        return Math.max(
                1, (frame.height() - (controller() ? 220 : 110)) / (controller() ? 90 : 60));
    }

    private int pages() {
        return Math.max(1, (state.rows().size() + rowsPerPage() - 1) / rowsPerPage());
    }

    private UiLayout.Node action(Component label, boolean enabled, Runnable request) {
        PanelButton button = body(new PanelButton(label, b -> request.run(), this::theme));
        button.active = enabled;
        return node(button);
    }

    private UiLayout.Node muted(Component label) {
        return text(label, () -> theme().muted());
    }

    @Override
    protected UiLayout.Node buildContent() {
        page = Math.min(page, pages() - 1);
        List<UiLayout.Node> content = new ArrayList<>();
        content.add(text(Component.literal(state.name()), () -> theme().accent()));
        if (controller()) {
            var c = state.control();
            List<UiLayout.Node> modes = new ArrayList<>();
            for (int mode = 0; mode < 3; mode++) {
                final int selected = mode;
                modes.add(
                        action(
                                text("mode." + mode),
                                !pending && c.mode() != mode,
                                () -> changeMode(selected)));
            }
            content.add(UiLayout.grid(3, 6, modes));
            content.add(
                    action(
                            text(c.chase() ? "chase_on" : "chase_off"),
                            !pending && editable(),
                            () -> {
                                if (pending) return;
                                pending = true;
                                PatrolToolNetwork.controllerChase(state, !state.control().chase());
                                rebuildWidgets();
                            }));
            content.add(muted(text("radius", c.radius())));
            content.add(
                    UiLayout.grid(
                            2,
                            6,
                            List.of(
                                    action(
                                            Component.literal("−"),
                                            !pending && editable() && c.radius() > 8,
                                            () -> radius(-8)),
                                    action(
                                            Component.literal("+"),
                                            !pending && editable() && c.radius() < 96,
                                            () -> radius(8)))));
            String key = "patrol_wait.phase." + c.phase();
            Component phase = I18n.exists(key) ? Component.translatable(key) : text("phase.idle");
            content.add(
                    muted(
                            text("mode." + c.mode())
                                    .copy()
                                    .append(" · ")
                                    .append(phase)
                                    .append(" · ")
                                    .append(text("pattern." + c.pattern()))));
        }
        if (state.rows().isEmpty()) content.add(muted(text("empty")));
        for (int row = 0;
                row < rowsPerPage() && page * rowsPerPage() + row < state.rows().size();
                row++) {
            int index = page * rowsPerPage() + row;
            var point = state.rows().get(index);
            var pos = point.position();
            content.add(
                    text(
                            Component.literal(
                                    "#"
                                            + (index + 1)
                                            + "  "
                                            + pos.getX()
                                            + ", "
                                            + pos.getY()
                                            + ", "
                                            + pos.getZ()),
                            () ->
                                    controller() && state.control().point() == index
                                            ? theme().accent()
                                            : theme().text()));
            content.add(
                    muted(
                            text(
                                    "seconds",
                                    String.format(Locale.ROOT, "%.2f", point.ticks() / 20.0))));
            List<UiLayout.Node> buttons = new ArrayList<>();
            if (controller()) {
                buttons.add(
                        action(
                                Component.literal("↑"),
                                !pending && editable() && index > 0,
                                () -> edit(index, 0)));
                buttons.add(
                        action(
                                Component.literal("↓"),
                                !pending && editable() && index + 1 < state.rows().size(),
                                () -> edit(index, 1)));
                buttons.add(
                        action(
                                text("remove"),
                                !pending && editable() && state.rows().size() > 2,
                                () -> edit(index, 2)));
            }
            buttons.add(
                    action(
                            Component.literal("−"),
                            !pending && editable() && point.ticks() > 0,
                            () -> step(index, -20)));
            buttons.add(
                    action(
                            Component.literal("+"),
                            !pending && editable() && point.ticks() < 1200,
                            () -> step(index, 20)));
            content.add(UiLayout.grid(5, 4, 40, buttons));
        }
        content.add(
                muted(
                        text("page", page + 1, pages())
                                .copy()
                                .append(" · ")
                                .append(text("count", state.rows().size()))));
        return UiLayout.column(6, content);
    }

    @Override
    protected void buildFooter() {
        footer(
                                Component.literal("◀"),
                                b -> {
                                    page--;
                                    rebuildWidgets();
                                })
                        .active =
                page > 0;
        footer(
                                Component.literal("▶"),
                                b -> {
                                    page++;
                                    rebuildWidgets();
                                })
                        .active =
                page + 1 < pages();
        footer(text("close"), b -> onClose());
    }

    private void changeMode(int mode) {
        if (pending) return;
        pending = true;
        PatrolToolNetwork.controllerMode(state, mode);
        rebuildWidgets();
    }

    private void radius(int delta) {
        if (pending || !editable()) return;
        pending = true;
        PatrolToolNetwork.controllerRadius(state, state.control().radius() + delta);
        rebuildWidgets();
    }

    private void edit(int index, int operation) {
        if (pending || !editable()) return;
        pending = true;
        PatrolToolNetwork.editPoint(state, index, operation);
        rebuildWidgets();
    }

    private void step(int index, int delta) {
        if (pending || !editable()) return;
        pending = true;
        PatrolToolNetwork.setWait(
                state, index, Math.max(0, Math.min(1200, state.rows().get(index).ticks() + delta)));
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (!viewport.contains(x, y)) return super.mouseScrolled(x, y, delta);
        // Keep local page navigation while shared scroll exposes wrapped rows in a small window.
        if (hasOverflow()) return super.mouseScrolled(x, y, delta);
        int next = Math.max(0, Math.min(pages() - 1, page + (delta < 0 ? 1 : -1)));
        if (next != page) {
            page = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void removed() {
        if (Minecraft.getInstance().getConnection() != null) PatrolToolNetwork.close(state.token());
        super.removed();
    }
}

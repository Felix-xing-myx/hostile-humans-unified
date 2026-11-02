package dev.felix.hostilehumans.observer.client;

import dev.felix.hostilehumans.behavior.BehaviorDebugSnapshot;
import dev.felix.hostilehumans.client.ui.framework.PanelButton;
import dev.felix.hostilehumans.client.ui.framework.PanelGeometry;
import dev.felix.hostilehumans.client.ui.framework.PanelScreen;
import dev.felix.hostilehumans.client.ui.framework.UiLayout;
import dev.felix.hostilehumans.monitor.client.HumanMonitorClient;
import dev.felix.hostilehumans.observer.NavigationDebugNetwork;
import dev.felix.hostilehumans.observer.NavigationDebugViewPolicy;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Display layers are local; developer control requests are separately authorized by the server. */
public final class NavigationLegendScreen extends PanelScreen {
    private record Row(int color, Component text) {}

    private static final String[] PAGES = {
        "overview", "memory", "vision", "clues", "action", "budget", "legend"
    };
    private int page, refreshTicks;
    private List<Row> displayRows = List.of();
    private final Map<String, AbstractWidget> controls = new HashMap<>();
    private boolean spectator;

    public NavigationLegendScreen() {
        super(label("title"), PanelGeometry.Size.M);
        page = NavigationDebugClient.detailPage();
    }

    private static Component label(String key, Object... args) {
        return Component.translatable("navigation_debug.detail." + key, args);
    }

    private void refreshRows(boolean reset) {
        displayRows = rows();
        String focus =
                controls.entrySet().stream()
                        .filter(e -> e.getValue() == getFocused())
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse("");
        rebuildWidgets();
        if (reset) scrollTo(0);
        AbstractWidget next = controls.get(focus);
        if (next != null) {
            setFocused(next);
            next.setFocused(true);
            revealFocus();
        }
    }

    @Override
    protected void buildBand() {
        for (int i = 0; i < PAGES.length; i++) {
            final int index = i;
            PanelButton b =
                    band(
                            new PanelButton(
                                    label("page." + PAGES[i]),
                                    button -> {
                                        page = index;
                                        NavigationDebugClient.detailPage(index);
                                        refreshRows(true);
                                    },
                                    this::theme));
            b.selected(page == i);
            controls.put("tab." + i, b);
        }
    }

    @Override
    protected UiLayout.Node buildContent() {
        controls.clear();
        if (displayRows.isEmpty()) displayRows = rows();
        List<UiLayout.Node> content = new ArrayList<>(), layers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final int index = i;
            PanelButton b =
                    body(
                            new PanelButton(
                                            layerLabel(i),
                                            button -> {
                                                NavigationDebugClient.cycleLayer(index);
                                                refreshRows(false);
                                            },
                                            this::theme)
                                    .compact(true));
            controls.put("layer." + i, b);
            layers.add(node(b));
        }
        content.add(UiLayout.grid(4, 4, 90, layers));
        for (Row row : displayRows)
            content.add(
                    text(
                            row.text(),
                            () -> row.color() >= 0 ? 0xFF000000 | row.color() : theme().muted()));
        return UiLayout.column(3, content);
    }

    @Override
    protected void buildFooter() {
        spectator = NavigationDebugClient.spectatorAttached();
        footer(
                                label("entity"),
                                b -> {
                                    if (NavigationDebugClient.visibleEntries().isEmpty()) return;
                                    NavigationDebugNetwork.cycleFocus();
                                    scrollTo(0);
                                })
                        .active =
                !spectator;
        footer(label("stop_test"), b -> NavigationDebugNetwork.stopFocusedTest()).active =
                !spectator;
        footer(Component.translatable("gui.done"), b -> onClose());
    }

    @Override
    public void tick() {
        if (++refreshTicks % 5 != 0) return;
        List<Row> next = rows();
        if (!next.equals(displayRows) || spectator != NavigationDebugClient.spectatorAttached()) {
            displayRows = next;
            refreshRows(false);
        }
    }

    @Override
    public Component getNarrationMessage() {
        Component message = title.copy();
        for (Row row : displayRows) message = message.copy().append(". ").append(row.text());
        return message;
    }

    private Component layerLabel(int i) {
        String scope =
                i == 1 && NavigationDebugClient.LAYERS[i] == 1
                        ? "scope.vision_focus"
                        : "scope." + NavigationDebugClient.LAYERS[i];
        return label("layer", label("layer." + i), label(scope));
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        var entries = NavigationDebugClient.visibleEntries();
        if (entries.isEmpty()) {
            rows.add(new Row(-1, label("stale")));
            return rows;
        }
        var focus =
                entries.stream()
                        .filter(NavigationDebugClient::diagnosticFocus)
                        .findFirst()
                        .orElse(entries.get(0));
        var s = focus.snapshot();
        var p = s.perception();
        rows.add(
                new Row(
                        NavigationDebugClient.slotColor(focus.slot()),
                        label("focus", focus.slot() + 1, s.name())));
        rows.add(new Row(-1, label("readonly")));
        if (HumanMonitorClient.active())
            rows.add(new Row(-1, Component.literal("监视器：仅显示当前目标；无需头盔或指针，退出后恢复原观察名单")));
        else if (NavigationDebugClient.spectatorAttached())
            rows.add(new Row(-1, Component.literal("旁观附身：仅显示指针已标记的人类；显示开关有效，退出后恢复原多选焦点")));
        if (s.capacityTruncated())
            rows.add(new Row(-1, Component.literal("因网络容量截断部分诊断细节；观察实体、焦点与动作权限数据保留")));
        if (page != 6) {
            rows.add(new Row(-1, label("state", p.awareness(), p.decision())));
            rows.add(new Row(-1, label("reason", p.reason())));
            rows.add(new Row(-1, label("sample", p.tick())));
        }
        if (page == 0) {
            for (var e : entries)
                rows.add(
                        new Row(
                                NavigationDebugClient.slotColor(e.slot()),
                                label(
                                        "state",
                                        "#" + (e.slot() + 1) + " " + e.snapshot().name(),
                                        e.snapshot().perception().decision())));
        }
        if (page == 2) {
            rows.add(new Row(-1, label("geometry")));
            rows.add(new Row(-1, label("vision_modes")));
        }
        if (page == 4) behaviorRows(rows, s.behavior());
        if (page == 4 || page == 5) {
            rows.add(
                    new Row(
                            -1,
                            Component.translatable(
                                    "navigation_debug.card_title",
                                    focus.slot() + 1,
                                    s.name(),
                                    Component.translatable("navigation_debug.mode." + s.mode()),
                                    "")));
            rows.add(
                    new Row(
                            -1,
                            Component.translatable(
                                    "navigation_debug.hud_route",
                                    Component.translatable("navigation_debug.result." + s.state()),
                                    s.firstNode(),
                                    s.totalNodes(),
                                    s.complete(),
                                    s.truncated())));
            rows.add(
                    new Row(
                            -1,
                            Component.literal(
                                    s.navigator()
                                            + " | "
                                            + s.budget()
                                            + " | "
                                            + s.searchMillis()
                                            + " ms | "
                                            + s.obstruction())));
            for (var sample : s.samples())
                rows.add(
                        new Row(
                                -1,
                                Component.literal(
                                        sample.source()
                                                + " / "
                                                + sample.result()
                                                + " / "
                                                + sample.position()
                                                + " / "
                                                + sample.score())));
        }
        if (page == 6) {
            for (int i = 0; i < 8; i++)
                rows.add(
                        new Row(
                                NavigationDebugClient.slotColor(i),
                                Component.translatable("navigation_debug.help.entity", i + 1)));
            for (String key : List.of("next", "pending", "accepted", "rejected", "controls"))
                rows.add(new Row(-1, Component.translatable("navigation_debug.help." + key)));
            for (String kind :
                    List.of(
                            "visible",
                            "dynamic",
                            "last",
                            "recognition",
                            "sound",
                            "direction",
                            "beacon",
                            "los",
                            "blocked",
                            "vision_near_base",
                            "vision_known_near_base",
                            "vision_new_ring",
                            "vision_known_ring")) rows.add(new Row(-1, label("marker." + kind)));
        } else {
            for (var row : p.rows())
                if (matches(row.group()))
                    rows.add(new Row(-1, Component.literal(row.label() + ": " + row.value())));
            for (var marker : p.markers())
                if (page == 2 && (marker.kind().equals("los") || marker.kind().equals("blocked")))
                    rows.add(new Row(-1, Component.literal(marker.kind() + ": " + marker.label())));
        }
        return rows;
    }

    private static void behaviorRows(List<Row> rows, BehaviorDebugSnapshot snapshot) {
        rows.add(
                new Row(
                        -1,
                        Component.literal(
                                "服务器动作 · 会话 "
                                        + (snapshot.authorityPresent()
                                                ? snapshot.session()
                                                : "尚无协调器")
                                        + " · 采样tick "
                                        + snapshot.tick())));
        rows.add(new Row(-1, Component.literal("移动执行桥: " + snapshot.bridge())));
        if (snapshot.bridge().equals("unbound"))
            rows.add(new Row(-1, Component.literal("unbound：以下仅显示原GoalSelector运行状态，尚无移动权限归属保证")));
        if (snapshot.actions().isEmpty()) rows.add(new Row(-1, Component.literal("当前协调动作：无")));
        for (var action : snapshot.actions()) {
            String kind =
                    action.lease()
                            ? "租约"
                                    + action.leaseTicks()
                                    + "t / 心跳 "
                                    + (action.lastHeartbeat() == Long.MIN_VALUE
                                            ? "尚无"
                                            : action.lastHeartbeat())
                            : "动作时限";
            rows.add(
                    new Row(
                            -1,
                            Component.literal(
                                    "#"
                                            + action.session()
                                            + "/"
                                            + action.sequence()
                                            + " "
                                            + action.name()
                                            + " · "
                                            + action.status()
                                            + " · p"
                                            + action.priority()
                                            + " · "
                                            + (action.interruptible() ? "可中断" : "不可中断"))));
            rows.add(
                    new Row(
                            -1,
                            Component.literal(
                                    "持有 "
                                            + String.join(" + ", action.heldResources())
                                            + " / 请求 "
                                            + String.join(" + ", action.resources())
                                            + " · 剩余 "
                                            + action.remainingTicks()
                                            + "t · "
                                            + kind)));
        }
        for (var goal : snapshot.goals())
            rows.add(
                    new Row(
                            -1,
                            Component.literal(
                                    "Goal "
                                            + goal.name()
                                            + " · p"
                                            + goal.priority()
                                            + " · "
                                            + String.join("/", goal.flags())
                                            + " · "
                                            + (goal.entered() ? "已启动" : "未启动")
                                            + (goal.executing() ? "/回调中" : "")
                                            + " · "
                                            + goal.state()
                                            + (goal.tokenSequence() == 0
                                                    ? ""
                                                    : " · #"
                                                            + goal.tokenSession()
                                                            + "/"
                                                            + goal.tokenSequence()))));
        for (var progress : snapshot.progress()) {
            rows.add(
                    new Row(
                            -1,
                            Component.literal(
                                    progress.kind()
                                            + " · "
                                            + progress.state()
                                            + " · "
                                            + progress.location()
                                            + (progress.item().isEmpty()
                                                    ? ""
                                                    : " · " + progress.item()))));
            rows.add(
                    new Row(
                            -1,
                            Component.literal(
                                    "工作量 "
                                            + progressNumber(progress.work())
                                            + " / "
                                            + progressNumber(progress.totalWork())
                                            + " · 实测速率 "
                                            + progressNumber(progress.workPerTick())
                                            + "/t · 采样 "
                                            + progress.sampledTick())));
            rows.add(
                    new Row(
                            -1,
                            Component.literal(
                                    (progress.remainingTicks() < 0
                                                    ? "余时未知"
                                                    : "余时约 " + progress.remainingTicks() + "t")
                                            + (progress.elapsedTicks() < 0
                                                    ? ""
                                                    : " · 历时 " + progress.elapsedTicks() + "t")
                                            + " · "
                                            + progress.detail())));
        }
        for (var feedback : snapshot.feedback())
            rows.add(
                    new Row(
                            -1,
                            Component.literal(
                                    "最近反馈 #"
                                            + feedback.session()
                                            + "/"
                                            + feedback.sequence()
                                            + " "
                                            + feedback.name()
                                            + " · "
                                            + feedback.status()
                                            + " · "
                                            + feedback.reason())));
        if (snapshot.truncated()) rows.add(new Row(-1, Component.literal("动作/Goal/进度/反馈已按协议上限截断")));
    }

    private static String progressNumber(double value) {
        return value < 0 ? "未知" : String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private boolean matches(String group) {
        return NavigationDebugViewPolicy.pageContains(page, group);
    }
}

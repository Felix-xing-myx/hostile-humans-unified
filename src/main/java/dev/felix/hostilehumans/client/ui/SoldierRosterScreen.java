package dev.felix.hostilehumans.client.ui;

import dev.felix.hostilehumans.client.ui.framework.PanelButton;
import dev.felix.hostilehumans.client.ui.framework.PanelCheck;
import dev.felix.hostilehumans.client.ui.framework.PanelEditBox;
import dev.felix.hostilehumans.client.ui.framework.PanelGeometry;
import dev.felix.hostilehumans.client.ui.framework.PanelGroup;
import dev.felix.hostilehumans.client.ui.framework.PanelHealth;
import dev.felix.hostilehumans.client.ui.framework.PanelMeter;
import dev.felix.hostilehumans.client.ui.framework.PanelPageSlider;
import dev.felix.hostilehumans.client.ui.framework.PanelRosterRow;
import dev.felix.hostilehumans.client.ui.framework.PanelScreen;
import dev.felix.hostilehumans.client.ui.framework.PanelText;
import dev.felix.hostilehumans.client.ui.framework.RosterPageCache;
import dev.felix.hostilehumans.client.ui.framework.UiLayout;
import dev.felix.hostilehumans.client.ui.framework.UiThemes;
import dev.felix.hostilehumans.observer.AttributeDetailNetwork;
import dev.felix.hostilehumans.soldier.CommandView;
import dev.felix.hostilehumans.soldier.SoldierRosterNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Master/detail roster. Five-row server pages remain authoritative; eight cached pages are
 * view-only. The left column and the detail pane scroll independently.
 */
public final class SoldierRosterScreen extends PanelScreen {
    private static final UUID NONE = new UUID(0, 0);
    private static final int LIST_WIDTH = 280;

    private final UUID token;
    private final RosterPageCache<SoldierRosterNetwork.Entry> cache = new RosterPageCache<>(8);
    private final Set<UUID> marked = new LinkedHashSet<>();
    private final Map<String, AbstractWidget> controls = new HashMap<>();
    private List<SoldierRosterNetwork.Entry> entries;
    private List<SoldierRosterNetwork.SquadEntry> squads = List.of();
    private int[] counts, limits;
    private int tier,
            page,
            total,
            squadFilter = -1,
            editingSquad,
            refreshTicks,
            requestedPage = -1,
            retryTicks,
            retries;
    private UUID selected;
    private SoldierRosterNetwork.Entry selectedEntry;
    private boolean squadPanel, authoritative = true;
    private String result = "", draftName = "";
    private long dismissConfirmUntil, recallConfirmUntil;
    private PanelEditBox squadName;
    private PanelPageSlider pageSlider;

    private SoldierRosterScreen(
            UUID token,
            int tier,
            int page,
            int total,
            List<SoldierRosterNetwork.Entry> entries,
            int[] counts,
            int[] limits) {
        super(roster("title"), PanelGeometry.Size.M);
        this.token = token;
        accept(tier, page, total, entries, counts, limits, -1, List.of(), "");
    }

    public static void show(
            UUID token,
            boolean opening,
            int tier,
            int page,
            int total,
            List<SoldierRosterNetwork.Entry> entries,
            int[] counts,
            int[] limits,
            int squadFilter,
            List<SoldierRosterNetwork.SquadEntry> squads,
            String result) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SoldierRosterScreen s && s.token.equals(token)) {
            s.accept(tier, page, total, entries, counts, limits, squadFilter, squads, result);
            s.refresh(false);
        } else if (opening && (mc.screen == null || mc.screen instanceof SoldierRosterScreen)) {
            var s = new SoldierRosterScreen(token, tier, page, total, entries, counts, limits);
            s.accept(tier, page, total, entries, counts, limits, squadFilter, squads, result);
            mc.setScreen(s);
        }
    }

    private void accept(
            int tier,
            int page,
            int total,
            List<SoldierRosterNetwork.Entry> entries,
            int[] counts,
            int[] limits,
            int squadFilter,
            List<SoldierRosterNetwork.SquadEntry> squads,
            String result) {
        boolean filterChanged = this.tier != tier || this.squadFilter != squadFilter;
        if (filterChanged) {
            requestedPage = -1;
            dismissConfirmUntil = 0;
            recallConfirmUntil = 0;
        }
        cache.filter(tier, squadFilter);
        cache.put(page, entries);
        this.entries = List.copyOf(entries);
        this.tier = tier;
        this.page = page;
        this.total = total;
        this.counts = counts.clone();
        this.limits = limits.clone();
        this.squadFilter = squadFilter;
        this.squads = List.copyOf(squads);
        authoritative = true;
        if (!result.isEmpty()) this.result = result;
        if (requestedPage >= 0) requestedPage = Math.min(requestedPage, maxPage());
        if (requestedPage == page) requestedPage = -1;
        for (var entry : entries) if (entry.id().equals(selected)) selectedEntry = entry;
        // Absence from one page is neither a death nor a deletion. Keep a stale detail until fresh
        // evidence arrives.
        if (filterChanged && squadName != null) scrollTo(-1, 0);
    }

    public static void invalidate(UUID token) {
        if (Minecraft.getInstance().screen instanceof SoldierRosterScreen s
                && s.token.equals(token)) {
            if (Minecraft.getInstance().player != null)
                Minecraft.getInstance()
                        .player
                        .displayClientMessage(
                                Component.translatable(
                                        "message.hostile_humans_unified.command.failed"),
                                false);
            s.onClose();
        }
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static Component cmd(String key, Object... args) {
        return tr("screen.hostile_humans_unified.command." + key, args);
    }

    private static Component roster(String key, Object... args) {
        return tr("screen.hostile_humans_unified.roster." + key, args);
    }

    private static Component ui(String key, Object... args) {
        return tr("gui.hostile_humans_unified.roster." + key, args);
    }

    private int maxPage() {
        return Math.max(0, (total - 1) / SoldierRosterNetwork.PAGE_SIZE);
    }

    private int displayedPage() {
        return requestedPage >= 0 ? requestedPage : page;
    }

    private boolean fresh(SoldierRosterNetwork.Entry entry) {
        return authoritative
                && requestedPage < 0
                && entries.stream().anyMatch(e -> e.id().equals(entry.id()));
    }

    @Override
    protected String faction() {
        return selectedEntry == null ? "" : selectedEntry.faction();
    }

    @Override
    protected int splitWidth() {
        return LIST_WIDTH;
    }

    @Override
    protected Component headerStatus() {
        return Component.literal((displayedPage() + 1) + " / " + (maxPage() + 1));
    }

    @Override
    protected Component bandStatus() {
        return Component.empty();
    }

    private UiLayout.Node muted(Component message) {
        return text(message, () -> theme().muted());
    }

    private UiLayout.Node action(
            String id, Component label, boolean active, boolean selected, Runnable run) {
        return action(id, label, active, selected, false, run);
    }

    private UiLayout.Node action(
            String id,
            Component label,
            boolean active,
            boolean selected,
            boolean danger,
            Runnable run) {
        PanelButton b = body(new PanelButton(label, button -> run.run(), this::theme));
        b.active = active;
        b.selected(selected);
        b.danger(danger);
        controls.put(id, b);
        return node(b);
    }

    private void chip(String id, Component label, boolean selected, Runnable run) {
        PanelButton b = band(new PanelButton(label, button -> run.run(), this::theme));
        b.active = true;
        b.selected(selected);
        controls.put(id, b);
    }

    private UiLayout.Node pair(UiLayout.Node a, UiLayout.Node b) {
        return UiLayout.grid(2, 4, 100, List.of(a, b));
    }

    private void refresh(boolean resetScroll) {
        if (minecraft == null) return;
        String focus =
                controls.entrySet().stream()
                        .filter(e -> e.getValue() == getFocused())
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse("");
        boolean editor = squadName != null && squadName.isFocused();
        if (squadName != null) draftName = squadName.getValue();
        rebuildWidgets();
        if (resetScroll) scrollReset();
        AbstractWidget next = editor ? squadName : controls.get(focus);
        if (next != null) {
            setFocused(next);
            next.setFocused(true);
            revealFocus();
        }
    }

    @Override
    protected void buildBand() {
        for (int filter = -1; filter < 4; filter++) {
            final int f = filter;
            chip(
                    "tier." + f,
                    f < 0 ? roster("all") : roster("tier." + f),
                    tier == f,
                    () -> filterTier(f));
        }
        chip("squads", cmd("squad_manage", marked.size()), squadPanel, () -> {
            if (selected != null && marked.size() < 64) marked.add(selected);
            squadPanel = true;
            editingSquad = 0;
            refresh(false);
        });
    }

    @Override
    protected void buildLeft(List<UiLayout.Node> out) {
        controls.clear();
        squadName = null;
        pageSlider = null;
        List<UiLayout.Node> quotas = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            quotas.add(
                    muted(
                            tr(
                                    "message.hostile_humans_unified.roster.tier." + i,
                                    counts[i],
                                    limits[i])));
        out.add(UiLayout.grid(2, 4, 120, quotas));
        out.add(muted(cmd("squad_filter", squadFilter < 0 ? "*" : squadFilter)));
        List<SoldierRosterNetwork.Entry> shown =
                displayedPage() == page ? entries : cache.get(displayedPage());
        if (requestedPage >= 0) out.add(muted(ui("loading")));
        for (var entry : shown) out.add(node(row(entry)));
        if (shown.isEmpty())
            out.add(muted(requestedPage >= 0 ? ui("loading") : cmd("empty_roster")));
        if (maxPage() > 0) {
            out.add(
                    pair(
                            action(
                                    "previous",
                                    Component.literal("◀"),
                                    displayedPage() > 0,
                                    false,
                                    () -> requestPage(displayedPage() - 1)),
                            action(
                                    "next",
                                    Component.literal("▶"),
                                    displayedPage() < maxPage(),
                                    false,
                                    () -> requestPage(displayedPage() + 1))));
            pageSlider =
                    body(
                            new PanelPageSlider(
                                    displayedPage(), maxPage(), this::requestPage, this::theme));
            controls.put("page", pageSlider);
            out.add(node(pageSlider));
        }
    }

    private PanelRosterRow row(SoldierRosterNetwork.Entry entry) {
        boolean current = fresh(entry) && "online".equals(entry.status());
        String name = entry.name().isBlank() ? roster("unnamed").getString() : entry.name();
        String metadata =
                roster("tier." + entry.tier()).getString()
                        + " · ["
                        + entry.squad()
                        + "] · "
                        + (current
                                        ? roster("status_short." + entry.status())
                                        : roster("last_known_short"))
                                .getString();
        Component narration =
                Component.literal(name + ". " + metadata)
                        .append(". ")
                        .append(
                                current
                                        ? roster("health", entry.health(), entry.maxHealth())
                                        : roster("last_known"));
        PanelRosterRow button =
                body(
                        new PanelRosterRow(
                                name,
                                metadata,
                                entry.health(),
                                entry.maxHealth(),
                                current,
                                marked.contains(entry.id()),
                                narration,
                                b -> {
                                    if (hasShiftDown()) toggleMark(entry.id());
                                    else {
                                        selected = entry.id();
                                        selectedEntry = entry;
                                        dismissConfirmUntil = 0;
                                        refresh(false);
                                    }
                                },
                                this::theme));
        button.selected(entry.id().equals(selected));
        button.tone("online".equals(entry.status()) ? 0xFF8FC7A6
                : "recalling".equals(entry.status()) ? 0xFFF2C66D : 0xFF4A5560);
        controls.put("entry." + entry.id(), button);
        return button;
    }

    @Override
    protected void buildRight(List<UiLayout.Node> out) {
        if (squadPanel) {
            squadManagement(out);
            return;
        }
        if (selectedEntry == null) {
            out.add(muted(ui("select")));
            return;
        }
        var e = selectedEntry;
        boolean live = fresh(e) && "online".equals(e.status());
        out.add(text(Component.literal(e.name()), () -> theme().accent()));
        out.add(
                muted(
                        CommandPanelChrome.factionName(e.faction())
                                .copy()
                                .append(
                                        " · "
                                                + e.dimension()
                                                + " · "
                                                + e.x()
                                                + ", "
                                                + e.y()
                                                + ", "
                                                + e.z())));
        out.add(muted(live ? roster("status." + e.status()) : roster("last_known")));
        if (live) {
            PanelHealth health = body(new PanelHealth().heading(true));
            health.update(e.health(), e.maxHealth());
            out.add(node(health));
            out.add(
                    text(
                            roster("health", e.health(), e.maxHealth()),
                            () -> theme().muted()));
        }
        if (e.growth() != null && e.growth().enabled()) {
            var growth = e.growth();
            String p = "screen.hostile_humans_unified.soldier.growth.";
            out.add(text(tr(p + "level", growth.level()), () -> theme().accent()));
            out.add(
                    node(
                            body(
                                    new PanelMeter(
                                            () ->
                                                    growth.experiencePercent()
                                                            - growth.experienceMinPercent(),
                                            () ->
                                                    growth.experienceCapPercent()
                                                            - growth.experienceMinPercent(),
                                            () -> theme().accent()))));
            out.add(
                    muted(
                            growth.level() >= 5
                                    ? tr(p + "max_level")
                                    : tr(
                                            p + "stage",
                                            growth.stagePercent(),
                                            growth.stageProgress(),
                                            growth.stageRequired())));
            out.add(
                    muted(
                            growth.experiencePercent() >= growth.experienceCapPercent()
                                    ? tr(p + "experience_capped")
                                    : tr(
                                            p + "next_point",
                                            growth.experienceProgress(),
                                            growth.experienceRequired())));
        }
        out.add(node(body(new PanelGroup(cmd("movement"), UiThemes.MARKER_MOVE, this::theme))));
        List<UiLayout.Node> modes = new ArrayList<>();
        for (int i = 0; i < CommandView.ORDERS.size(); i++) {
            var order = CommandView.ORDERS.get(i);
            final int index = i;
            modes.add(
                    action(
                            "order." + order,
                            CommandPanelScreen.orderName(order),
                            live,
                            e.order().equals(order.name()),
                            () -> request(4, e.id(), index, true)));
        }
        out.add(UiLayout.grid(2, 4, 100, modes));
        out.add(node(body(new PanelGroup(cmd("combat"), UiThemes.MARKER_FIGHT, this::theme))));
        modes = new ArrayList<>();
        for (var mode : CommandView.COMBAT)
            modes.add(
                    action(
                            "combat." + mode,
                            CommandPanelScreen.combatName(mode),
                            live,
                            e.combat().equals(mode.name()),
                            () -> request(5, e.id(), mode.ordinal(), true)));
        out.add(UiLayout.grid(2, 4, 100, modes));
        PanelCheck pickup =
                body(
                        new PanelCheck(
                                cmd(e.pickup() ? "pickup_on" : "pickup_off"),
                                e.pickup(),
                                b -> request(6, e.id(), e.pickup() ? 0 : 1, true),
                                this::theme));
        pickup.active = live;
        controls.put("pickup", pickup);
        out.add(node(pickup));
        out.add(
                pair(
                        action(
                                "full",
                                cmd("full_panel"),
                                live,
                                false,
                                () -> request(13, e.id(), 0, false)),
                        action(
                                "attributes",
                                cmd("attributes"),
                                live && e.entityId() >= 0,
                                false,
                                () -> AttributeDetailNetwork.requestOwned(e.entityId(), e.id()))));
        out.add(
                action(
                        "monitor",
                        tr("human_monitor.roster.open"),
                        true,
                        false,
                        () -> request(16, e.id(), 0, false)));
        out.add(
                pair(
                        action(
                                "mark",
                                cmd("mark"),
                                true,
                                marked.contains(e.id()),
                                () -> toggleMark(e.id())),
                        action(
                                "recallOne",
                                roster("recall_one"),
                                true,
                                false,
                                () -> request(3, e.id(), 0, true))));
        out.add(
                pair(
                        action(
                                "earlier",
                                roster("move_earlier"),
                                fresh(e) && e.canMoveEarlier(),
                                false,
                                () -> request(11, e.id(), 0, true)),
                        action(
                                "later",
                                roster("move_later"),
                                fresh(e) && e.canMoveLater(),
                                false,
                                () -> request(12, e.id(), 0, true))));
        out.add(
                action(
                        "dismiss",
                        System.currentTimeMillis() < dismissConfirmUntil
                                ? roster("confirm")
                                : tr("screen.hostile_humans_unified.soldier.dismiss"),
                        true,
                        false,
                        true,
                        () -> {
                            if (System.currentTimeMillis() < dismissConfirmUntil) {
                                dismissConfirmUntil = 0;
                                request(1, e.id(), 0, true);
                            } else {
                                dismissConfirmUntil = System.currentTimeMillis() + 4000;
                                refresh(false);
                            }
                        }));
    }

    private void squadManagement(List<UiLayout.Node> out) {
        out.add(text(cmd("squad_select", marked.size()), () -> theme().accent()));
        if (!result.isEmpty()) out.add(muted(Component.literal(result)));
        for (var squad : squads) {
            out.add(
                    action(
                            "squad." + squad.number(),
                            Component.literal(
                                    squad.number() + " · " + squad.name() + " ("
                                            + squad.members() + ")"),
                            true,
                            editingSquad == squad.number(),
                            () -> {
                                editingSquad = squad.number();
                                draftName = squad.name();
                                squadName = null;
                                refresh(false);
                                if (squadName != null) {
                                    setFocused(squadName);
                                    squadName.setFocused(true);
                                    revealFocus();
                                }
                            }));
            if (editingSquad == squad.number()) {
                squadName = body(new PanelEditBox(font, cmd("squad_name"), this::theme));
                squadName.setValue(draftName);
                controls.put("editor", squadName);
                out.add(node(squadName));
                out.add(action("rename", cmd("rename"), true, false, this::saveSquadName));
            }
            out.add(
                    pair(
                            action(
                                    "assign." + squad.number(),
                                    cmd("assign"),
                                    !marked.isEmpty(),
                                    false,
                                    () -> {
                                        cache.clear();
                                        SoldierRosterNetwork.assign(
                                                token, squad.number(), List.copyOf(marked));
                                    }),
                            action(
                                    "filter." + squad.number(),
                                    cmd("filter"),
                                    true,
                                    false,
                                    () -> filterSquad(squad.number()))));
            out.add(
                    pair(
                            action(
                                    "bind." + squad.number(),
                                    cmd("bind"),
                                    true,
                                    false,
                                    () -> SoldierRosterNetwork.bind(token, squad.number())),
                            action(
                                    "quick." + squad.number(),
                                    cmd("squad_quick"),
                                    true,
                                    false,
                                    () -> request(15, NONE, squad.number(), false))));
        }
        out.add(
                action(
                        "removeSquad",
                        cmd("remove_squad"),
                        !marked.isEmpty(),
                        false,
                        true,
                        () -> {
                            cache.clear();
                            SoldierRosterNetwork.assign(token, 0, List.copyOf(marked));
                        }));
        out.add(
                pair(
                        action("allSquads", cmd("all_squads"), true, false, () -> filterSquad(-1)),
                        action("noSquad", cmd("no_squad"), true, false, () -> filterSquad(0))));
    }

    @Override
    protected void buildFooter() {
        footer(
                selected != null || squadPanel || tier != -1
                        ? roster("back")
                        : cmd("close"),
                b -> back());
        footerSpacer();
        if (squadPanel) {
            footer(cmd("remove_squad"), b -> {
                        cache.clear();
                        SoldierRosterNetwork.assign(token, 0, List.copyOf(marked));
                    })
                    .active = !marked.isEmpty();
            return;
        }
        footer(roster("refresh"), b -> request(0, NONE, 0, false));
        footer(
                System.currentTimeMillis() < recallConfirmUntil
                        ? ui("recall_confirm")
                        : roster("recall"),
                b -> {
                    if (System.currentTimeMillis() < recallConfirmUntil) {
                        recallConfirmUntil = 0;
                        request(2, NONE, 0, true);
                    } else {
                        recallConfirmUntil = System.currentTimeMillis() + 4000;
                        refresh(false);
                    }
                })
                .danger(true);
    }

    private void toggleMark(UUID id) {
        if (!marked.remove(id) && marked.size() < 64) marked.add(id);
        refresh(false);
    }

    private void filterTier(int value) {
        cache.clear();
        requestedPage = -1;
        SoldierRosterNetwork.request(token, 7, NONE, value);
    }

    private void filterSquad(int value) {
        cache.clear();
        requestedPage = -1;
        SoldierRosterNetwork.request(token, 14, NONE, value);
        squadPanel = false;
        editingSquad = 0;
        refresh(true);
    }

    private void request(int action, UUID human, int value, boolean mutation) {
        if (mutation) {
            cache.clear();
            authoritative = false;
        }
        SoldierRosterNetwork.request(token, action, human, value);
        if (mutation) refresh(false);
    }

    private void saveSquadName() {
        if (editingSquad > 0 && squadName != null) {
            draftName = squadName.getValue();
            SoldierRosterNetwork.rename(token, editingSquad, draftName);
        }
        editingSquad = 0;
        squadName = null;
        refresh(false);
    }

    private void back() {
        if (squadPanel) {
            squadPanel = false;
            editingSquad = 0;
            squadName = null;
            refresh(false);
        } else if (selected != null) {
            selected = null;
            selectedEntry = null;
            dismissConfirmUntil = 0;
            refresh(false);
        } else if (tier != -1) filterTier(-1);
        else onClose();
    }

    private void requestPage(int target) {
        target = Math.max(0, Math.min(maxPage(), target));
        if (target == displayedPage()) return;
        requestedPage = target;
        retryTicks = 0;
        retries = 0;
        SoldierRosterNetwork.request(token, 8, NONE, target);
        refresh(true);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (pageSlider != null && pageSlider.isMouseOver(x, y) && delta != 0) {
            if (!pageSlider.editing()) requestPage(displayedPage() + (delta > 0 ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            back();
            return true;
        }
        if (squadPanel && editingSquad > 0 && key == GLFW.GLFW_KEY_ENTER) {
            saveSquadName();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void tick() {
        super.tick();
        if (squadName != null) squadName.tick();
        if (requestedPage >= 0 && page != requestedPage && retries < 3 && ++retryTicks >= 4) {
            retryTicks = 0;
            retries++;
            SoldierRosterNetwork.request(token, 8, NONE, requestedPage);
        }
        if (++refreshTicks >= 100) {
            refreshTicks = 0;
            SoldierRosterNetwork.request(token, 10, NONE);
        }
        long now = System.currentTimeMillis();
        if (dismissConfirmUntil > 0 && now >= dismissConfirmUntil) {
            dismissConfirmUntil = 0;
            refresh(false);
        }
        if (recallConfirmUntil > 0 && now >= recallConfirmUntil) {
            recallConfirmUntil = 0;
            refresh(false);
        }
    }

    @Override
    public void removed() {
        cache.clear();
        if (Minecraft.getInstance().getConnection() != null)
            SoldierRosterNetwork.request(token, 9, NONE);
        super.removed();
    }
}
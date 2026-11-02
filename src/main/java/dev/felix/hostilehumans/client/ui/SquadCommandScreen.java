package dev.felix.hostilehumans.client.ui;

import dev.felix.hostilehumans.client.ui.framework.PanelButton;
import dev.felix.hostilehumans.client.ui.framework.UiLayout;
import dev.felix.hostilehumans.soldier.SoldierOrder;
import dev.felix.hostilehumans.soldier.SquadCommandNetwork;
import dev.felix.hostilehumans.soldier.SquadCommands;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Same controls as one soldier; mixed modes and batch outcomes remain visible. */
public final class SquadCommandScreen extends CommandPanelScreen {
    private SquadCommandNetwork.View view;
    private String result = "";
    private boolean members, suppressReturn;
    private int memberPage;

    private SquadCommandScreen(SquadCommandNetwork.View view) {
        super(
                Component.translatable("screen.hostile_humans_unified.command.squad_title"),
                view.commands(),
                null);
        this.view = view;
        result = view.result().getString();
    }

    public static void show(boolean opening, SquadCommandNetwork.View view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SquadCommandScreen s && s.view.token().equals(view.token())) {
            s.view = view;
            s.state = view.commands();
            if (!view.result().getString().isEmpty()) s.result = view.result().getString();
            s.refreshSnapshot();
        } else if (opening) mc.setScreen(new SquadCommandScreen(view));
    }

    public static void invalidate(UUID token) {
        if (Minecraft.getInstance().screen instanceof SquadCommandScreen s
                && s.view.token().equals(token)) {
            s.suppressReturn = true;
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

    @Override
    protected dev.felix.hostilehumans.soldier.CommandHealthSnapshot healthSnapshot() {
        return view.health();
    }

    @Override
    protected boolean representativeHealth() {
        return true;
    }

    @Override
    protected Component headerStatus() {
        return Component.translatable("gui.hostile_humans_unified.command.squad_total", view.total());
    }

    @Override
    protected Component heading() {
        return Component.translatable(
                "screen.hostile_humans_unified.command.squad_heading",
                view.number(),
                view.name(),
                view.total());
    }

    @Override
    protected Component status() {
        return Component.translatable(
                "screen.hostile_humans_unified.command.squad_modes",
                view.modes().getOrDefault(SoldierOrder.FOLLOW, 0),
                view.modes().getOrDefault(SoldierOrder.BODYGUARD, 0),
                view.modes().getOrDefault(SoldierOrder.GARRISON, 0),
                view.available(),
                view.unloaded());
    }

    @Override
    protected Component additionalStatus() {
        return Component.literal(
                !result.isEmpty()
                        ? result
                        : hasContextWarning()
                                ? contextReason().getString()
                                : tr("squad_representative").getString());
    }

    @Override
    protected void reanchor() {
        SquadCommandNetwork.reanchor(view.token(), view.number());
    }

    @Override
    protected void submit(SquadCommands.Update update) {
        SquadCommandNetwork.submit(view.token(), view.number(), update);
    }

    @Override
    protected Component secondaryLabel() {
        return tr("squad_members");
    }

    @Override
    protected Component closeLabel() {
        return view.fromRoster() ? tr("back_to_roster") : super.closeLabel();
    }

    @Override
    protected void secondary() {
        members = true;
        memberPage = 0;
        rebuildWidgets();
    }

    private int memberRows() {
        return Math.max(1, (panelHeight() - 98) / 26);
    }

    @Override
    protected void refreshSnapshot() {
        if (members) rebuildWidgets();
        else super.refreshSnapshot();
    }

    @Override
    protected UiLayout.Node buildContent() {
        if (!members) return super.buildContent();
        int rows = memberRows(), pages = Math.max(1, (view.members().size() + rows - 1) / rows);
        memberPage = Math.min(memberPage, pages - 1);
        List<UiLayout.Node> content = new ArrayList<>();
        content.add(text(tr("squad_members"), () -> theme().accent()));
        for (int i = 0; i < rows && memberPage * rows + i < view.members().size(); i++) {
            var member = view.members().get(memberPage * rows + i);
            String id = member.id().toString().substring(0, 8);
            Component label =
                    Component.translatable(
                            member.canSubmit()
                                    ? "screen.hostile_humans_unified.command.member_loaded"
                                    : "screen.hostile_humans_unified.command.member_unloaded",
                            id);
            // Read-only native focus target: all member statuses remain keyboard/narrator
            // reachable.
            content.add(node(body(new PanelButton(label, b -> {}, this::theme))));
        }
        content.add(
                text(Component.literal((memberPage + 1) + " / " + pages), () -> theme().muted()));
        return UiLayout.column(6, content);
    }

    @Override
    protected void buildFooter() {
        if (!members) {
            super.buildFooter();
            return;
        }
        int pages = Math.max(1, (view.members().size() + memberRows() - 1) / memberRows());
        footer(
                                Component.literal("◀"),
                                b -> {
                                    memberPage--;
                                    rebuildWidgets();
                                })
                        .active =
                memberPage > 0;
        footer(
                                Component.literal("▶"),
                                b -> {
                                    memberPage++;
                                    rebuildWidgets();
                                })
                        .active =
                memberPage + 1 < pages;
        footer(
                tr("back"),
                b -> {
                    members = false;
                    rebuildWidgets();
                });
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return members || super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (members && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            members = false;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        if (view.fromRoster() && !suppressReturn && Minecraft.getInstance().getConnection() != null)
            SquadCommandNetwork.returnToRoster(view.token());
        super.onClose();
    }

    @Override
    public void removed() {
        if (Minecraft.getInstance().getConnection() != null)
            SquadCommandNetwork.close(view.token());
        super.removed();
    }
}

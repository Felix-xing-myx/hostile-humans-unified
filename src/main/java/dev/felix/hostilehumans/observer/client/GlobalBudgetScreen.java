package dev.felix.hostilehumans.observer.client;

import dev.felix.hostilehumans.client.ui.framework.PanelButton;
import dev.felix.hostilehumans.client.ui.framework.PanelGeometry;
import dev.felix.hostilehumans.client.ui.framework.PanelScreen;
import dev.felix.hostilehumans.client.ui.framework.UiLayout;
import dev.felix.hostilehumans.observer.NavigationDebugNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class GlobalBudgetScreen extends PanelScreen {
    private static final String[] TABS = {"overview", "perception", "navigation"};
    private int page = NavigationDebugClient.globalPage();
    private int ticks;
    private List<String> rows = List.of();

    public GlobalBudgetScreen() { super(label("title"), PanelGeometry.Size.M); }

    public static void accept(List<String> values) {
        if (Minecraft.getInstance().screen instanceof GlobalBudgetScreen screen) {
            screen.rows = values;
            screen.rebuildWidgets();
        }
    }

    private static Component label(String key) {
        return Component.translatable("navigation_debug.global." + key);
    }

    @Override public void tick() {
        super.tick();
        if (ticks++ % 20 == 0) NavigationDebugNetwork.requestGlobalBudget();
    }

    @Override protected void buildBand() {
        for (int i = 0; i < TABS.length; i++) {
            int index = i;
            var tab = band(new PanelButton(label(TABS[i]), ignored -> {
                page = index;
                NavigationDebugClient.globalPage(index);
                rebuildWidgets();
                scrollTo(0);
            }, this::theme));
            tab.selected(page == i);
        }
    }

    @Override protected UiLayout.Node buildContent() {
        List<UiLayout.Node> content = new ArrayList<>();
        if (rows.isEmpty()) content.add(text(label("waiting"), () -> theme().muted()));
        for (String row : rows) {
            int split = row.indexOf('|');
            if (split <= 0) continue;
            String section = row.substring(0, split);
            if (!section.equals(TABS[page]) && !section.equals("all")) continue;
            content.add(text(Component.literal(row.substring(split + 1)), () -> theme().text()));
        }
        return UiLayout.column(5, content);
    }

    @Override protected void buildFooter() {
        footer(Component.translatable("gui.done"), ignored -> onClose());
        footer(label("entity_details"), ignored -> Minecraft.getInstance().setScreen(new NavigationLegendScreen()));
    }
}

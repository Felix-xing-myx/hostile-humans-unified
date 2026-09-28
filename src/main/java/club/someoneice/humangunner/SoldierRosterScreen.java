package club.someoneice.humangunner;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Owner roster with a compact, pixel-style interface. Server validates every action. */
public final class SoldierRosterScreen extends Screen {
    private static final UUID NONE = new UUID(0, 0);
    private static final int WIDTH = 408, HEIGHT = 226;
    private static final int PANEL = 0xF0101820, SURFACE = 0xFF202B36;
    private static final int HOVER = 0xFF2A3947, EDGE = 0xFF394653;
    private static final int TEXT = 0xFFE5EAF0, MUTED = 0xFF9DADBB;
    private static final int GOLD = 0xFFF2C66D, DISABLED = 0xFF65717C;

    private final UUID token;
    private List<SoldierRosterNetwork.Entry> entries;
    private int[] counts, limits;
    private int tier, page, total;
    private UUID selected;
    private UUID previewed;
    private boolean detail;
    private boolean draggingScrollbar;
    private int dragPreviewPage;
    private int dragGrabOffset;
    private int requestedPage = -1;
    private int scrollRetryTicks;
    private int scrollRetryCount;
    private long dismissConfirmUntil;
    private int statusRefreshTicks;

    private SoldierRosterScreen(UUID token, int tier, int page, int total,
                                List<SoldierRosterNetwork.Entry> entries,
                                int[] counts, int[] limits) {
        super(Component.translatable("screen.humangunner.roster.title"));
        this.token = token;
        this.entries = entries;
        this.tier = tier;
        this.page = page;
        this.total = total;
        this.counts = counts;
        this.limits = limits;
    }

    static void show(UUID token, boolean opening, int tier, int page, int total,
                     List<SoldierRosterNetwork.Entry> entries, int[] counts, int[] limits) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SoldierRosterScreen screen && screen.token.equals(token)) {
            boolean filterChanged = screen.tier != tier;
            screen.entries = entries;
            screen.tier = tier;
            screen.page = page;
            screen.total = total;
            if (filterChanged) {
                screen.requestedPage = -1;
                screen.draggingScrollbar = false;
            } else if (screen.requestedPage >= 0) {
                screen.requestedPage = Math.min(screen.requestedPage,
                        Math.max(0, (total - 1) / SoldierRosterNetwork.PAGE_SIZE));
                if (page == screen.requestedPage) screen.requestedPage = -1;
            }
            screen.counts = counts;
            screen.limits = limits;
            if (screen.selected != null && entries.stream().noneMatch(e -> e.id().equals(screen.selected))) {
                screen.selected = null;
                screen.detail = false;
            }
            if (screen.previewed != null && entries.stream().noneMatch(e -> e.id().equals(screen.previewed))) {
                screen.previewed = entries.isEmpty() ? null : entries.get(0).id();
            }
        } else if (opening && (minecraft.screen == null
                || minecraft.screen instanceof SoldierRosterScreen)) {
            minecraft.setScreen(new SoldierRosterScreen(token, tier, page, total, entries, counts, limits));
        }
    }

    private int left() { return width / 2 - WIDTH / 2; }
    private int top() { return height / 2 - HEIGHT / 2; }
    private int maxPage() { return Math.max(0, (total - 1) / SoldierRosterNetwork.PAGE_SIZE); }
    private int thumbHeight() {
        return Math.max(14, 142 / (maxPage() + 1));
    }
    private int thumbTop(int position) {
        int max = maxPage();
        return top() + 54 + (max == 0 ? 0 : (142 - thumbHeight()) * position / max);
    }
    private int pageAtThumbTop(double thumbY) {
        int travel = 142 - thumbHeight();
        if (travel <= 0) return 0;
        double fraction = Math.max(0, Math.min(1, (thumbY - (top() + 54)) / travel));
        return (int) Math.round(fraction * maxPage());
    }
    private void requestPage(int target) {
        target = Math.max(0, Math.min(maxPage(), target));
        if (target == (requestedPage >= 0 ? requestedPage : page)) return;
        requestedPage = target;
        scrollRetryTicks = 0;
        scrollRetryCount = 0;
        SoldierRosterNetwork.request(token, 8, NONE, target);
    }
    private static Component tr(String key) { return Component.translatable(key); }
    private static Component tierName(int tier) { return tr("screen.humangunner.roster.tier." + tier); }
    private static Component statusName(String status) {
        return tr("screen.humangunner.roster.status_short." + status);
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private SoldierRosterNetwork.Entry selectedEntry() {
        if (selected == null) return null;
        return entries.stream().filter(e -> e.id().equals(selected)).findFirst().orElse(null);
    }

    private SoldierRosterNetwork.Entry hoveredEntry(int mx, int my) {
        int x = left() + 8, y = top() + 54;
        for (int i = 0; i < entries.size(); i++) {
            if (hit(mx, my, x, y + i * 29, 242, 26)) return entries.get(i);
        }
        return null;
    }

    private static int statusColor(String status) {
        return switch (status) {
            case "online" -> 0xFF8DCA9E;
            case "recalling" -> GOLD;
            case "unloaded" -> 0xFFAFC9E5;
            default -> 0xFFDF9A8D;
        };
    }

    private static int tierColor(int tier) {
        return switch (tier) {
            case 0 -> 0xFFB2B9BE; // Roamer: neutral stone
            case 1 -> 0xFFD2B47C; // Tier I: bronze
            case 2 -> 0xFF87BED5; // Tier II: blue
            case 3 -> 0xFFC29CD6; // Tier III: violet
            default -> MUTED;
        };
    }

    private static int tierSurface(int tier) {
        return switch (tier) {
            case 0 -> 0xFF303940;
            case 1 -> 0xFF40372B;
            case 2 -> 0xFF263B46;
            case 3 -> 0xFF3C3048;
            default -> SURFACE;
        };
    }

    private void tierBadge(GuiGraphics graphics, int tier, int x, int y) {
        frame(graphics, x, y, 52, 14, tierSurface(tier));
        graphics.fill(x + 2, y + 2, x + 4, y + 12, tierColor(tier));
        graphics.drawCenteredString(font,
                font.plainSubstrByWidth(tierName(tier).getString(), 43),
                x + 27, y + 3, tierColor(tier));
    }

    private void frame(GuiGraphics graphics, int x, int y, int w, int h, int fill) {
        graphics.fill(x, y, x + w, y + h, 0xFF070B10);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, EDGE);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, fill);
    }

    private void button(GuiGraphics graphics, Component label, int x, int y, int w, int h,
                        boolean hovered, boolean active, boolean chosen, boolean danger) {
        frame(graphics, x, y, w, h,
                !active ? 0xFF242C34 : chosen ? 0xFF354754 : hovered ? HOVER : SURFACE);
        if (chosen) graphics.fill(x + 2, y + 2, x + 4, y + h - 2, GOLD);
        int color = !active ? DISABLED : danger ? 0xFFED9B96 : chosen ? GOLD : TEXT;
        graphics.drawCenteredString(font, font.plainSubstrByWidth(label.getString(), w - 7),
                x + w / 2, y + (h - 8) / 2, color);
    }

    private void healthBar(GuiGraphics graphics, SoldierRosterNetwork.Entry entry, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + 3, 0xFF0B1015);
        if (entry.health() < 0 || entry.maxHealth() <= 0) return;
        float fraction = Math.max(0, Math.min(1, (float) entry.health() / entry.maxHealth()));
        int color = fraction <= .25F ? 0xFFDA746B : fraction <= .5F ? GOLD : 0xFF81BE91;
        graphics.fill(x + 1, y + 1, x + 1 + Math.round((w - 2) * fraction), y + 2, color);
    }

    @Override
    public void render(GuiGraphics graphics, int mx, int my, float partialTick) {
        renderBackground(graphics);
        int x = left(), y = top();
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, PANEL);
        graphics.fill(x, y, x + WIDTH, y + 2, EDGE);
        graphics.fill(x, y + HEIGHT - 2, x + WIDTH, y + HEIGHT, EDGE);
        graphics.fill(x, y, x + 2, y + HEIGHT, EDGE);
        graphics.fill(x + WIDTH - 2, y, x + WIDTH, y + HEIGHT, EDGE);
        graphics.drawString(font, title, x + 10, y + 10, TEXT, false);
        graphics.fill(x + 9, y + 24, x + WIDTH - 9, y + 25, EDGE);
        if (detail) renderDetail(graphics, mx, my);
        else renderList(graphics, mx, my);
    }

    private void renderList(GuiGraphics graphics, int mx, int my) {
        int x = left(), y = top();
        for (int i = 0; i < 5; i++) {
            int filter = i - 1, bx = x + 9 + i * 78;
            Component label = filter < 0 ? tr("screen.humangunner.roster.all") : tierName(filter);
            button(graphics, label, bx, y + 31, 76, 18,
                    hit(mx, my, bx, y + 31, 76, 18), true, tier == filter, false);
        }
        for (int i = 0; i < entries.size(); i++) {
            SoldierRosterNetwork.Entry entry = entries.get(i);
            int bx = x + 8, by = y + 54 + i * 29;
            boolean hovered = hit(mx, my, bx, by, 242, 26);
            frame(graphics, bx, by, 242, 26, hovered ? HOVER : SURFACE);
            graphics.fill(bx + 3, by + 3, bx + 5, by + 23, statusColor(entry.status()));
            tierBadge(graphics, entry.tier(), bx + 9, by + 4);
            String name = entry.name().isBlank()
                    ? tr("screen.humangunner.roster.unnamed").getString() : entry.name();
            graphics.drawString(font, font.plainSubstrByWidth(name, 65), bx + 66, by + 4, TEXT, false);
            healthBar(graphics, entry, bx + 9, by + 20, 122);
            if (hovered) {
                button(graphics, tr("screen.humangunner.roster.recall_short"), bx + 177, by + 4,
                        31, 18, hit(mx, my, bx + 177, by + 4, 31, 18), true, false, false);
                button(graphics, Component.literal("▲"), bx + 210, by + 4,
                        14, 18, hit(mx, my, bx + 210, by + 4, 14, 18),
                        entry.canMoveEarlier(), false, false);
                button(graphics, Component.literal("▼"), bx + 226, by + 4,
                        14, 18, hit(mx, my, bx + 226, by + 4, 14, 18),
                        entry.canMoveLater(), false, false);
            } else {
                graphics.drawString(font, font.plainSubstrByWidth(statusName(entry.status()).getString(), 98),
                        bx + 137, by + 4, statusColor(entry.status()), false);
                String health = entry.health() >= 0 ? entry.health() + "/" + entry.maxHealth()
                        : tr("screen.humangunner.roster.last_known_short").getString();
                graphics.drawString(font, health, bx + 234 - font.width(health), by + 15, MUTED, false);
            }
        }
        if (total > SoldierRosterNetwork.PAGE_SIZE) {
            int sx = x + 251, sy = y + 54;
            graphics.fill(sx, sy, sx + 7, sy + 142, 0xFF070B10);
            graphics.fill(sx + 1, sy + 1, sx + 6, sy + 141, EDGE);
            int thumbY = thumbTop(draggingScrollbar ? dragPreviewPage : page);
            graphics.fill(sx + 1, thumbY, sx + 6, thumbY + thumbHeight(), GOLD);
        }
        frame(graphics, x + 259, y + 54, 141, 142, SURFACE);
        SoldierRosterNetwork.Entry hovered = hoveredEntry(mx, my);
        if (hovered != null) previewed = hovered.id();
        SoldierRosterNetwork.Entry focused = entries.stream()
                .filter(entry -> entry.id().equals(previewed)).findFirst().orElse(null);
        if (focused == null) focused = selectedEntry();
        renderOverview(graphics, focused, x + 266, y + 61);
        int maxPage = maxPage();
        button(graphics, Component.literal("◀"), x + 8, y + 202, 23, 18,
                hit(mx, my, x + 8, y + 202, 23, 18), page > 0, false, false);
        graphics.drawCenteredString(font, (page + 1) + " / " + (maxPage + 1), x + 54, y + 207, MUTED);
        button(graphics, Component.literal("▶"), x + 78, y + 202, 23, 18,
                hit(mx, my, x + 78, y + 202, 23, 18), page < maxPage, false, false);
        button(graphics, tr("screen.humangunner.roster.refresh"), x + 110, y + 202, 84, 18,
                hit(mx, my, x + 110, y + 202, 84, 18), true, false, false);
        button(graphics, tr("screen.humangunner.roster.recall"), x + 200, y + 202, 200, 18,
                hit(mx, my, x + 200, y + 202, 200, 18), true, false, false);
        if (hovered != null) {
            int by = y + 58 + entries.indexOf(hovered) * 29;
            Component hint = hit(mx, my, x + 185, by, 31, 18)
                    ? tr("screen.humangunner.roster.recall_one")
                    : hit(mx, my, x + 218, by, 14, 18)
                    ? tr("screen.humangunner.roster.move_earlier")
                    : hit(mx, my, x + 234, by, 14, 18)
                    ? tr("screen.humangunner.roster.move_later") : null;
            if (hint != null) graphics.renderTooltip(font, hint, mx, my);
        }
    }

    private void renderOverview(GuiGraphics graphics, SoldierRosterNetwork.Entry entry, int x, int y) {
        graphics.drawString(font, tr("screen.humangunner.roster.overview"), x, y, GOLD, false);
        if (entry == null) {
            graphics.drawString(font, tr("screen.humangunner.roster.hover_hint"), x, y + 22, MUTED, false);
            for (int i = 0; i < 4; i++) {
                Component count = Component.translatable("message.humangunner.roster.tier." + i,
                        counts[i], limits[i]);
                graphics.drawString(font, font.plainSubstrByWidth(count.getString(), 126),
                        x, y + 44 + i * 18, MUTED, false);
            }
            return;
        }
        String name = entry.name().isBlank()
                ? tr("screen.humangunner.roster.unnamed").getString() : entry.name();
        graphics.drawString(font, font.plainSubstrByWidth(name, 126), x, y + 16, TEXT, false);
        graphics.drawString(font, tierName(entry.tier()), x, y + 30, tierColor(entry.tier()), false);
        graphics.drawString(font, statusName(entry.status()), x, y + 44,
                statusColor(entry.status()), false);
        if (entry.health() >= 0) {
            graphics.drawString(font, entry.health() + " / " + entry.maxHealth(), x, y + 62, TEXT, false);
            healthBar(graphics, entry, x, y + 75, 126);
        } else {
            graphics.drawString(font, tr("screen.humangunner.roster.last_known_short"),
                    x, y + 62, MUTED, false);
        }
        graphics.fill(x, y + 85, x + 126, y + 86, EDGE);
        Component order = tr("screen.humangunner.soldier."
                + entry.order().toLowerCase(Locale.ROOT));
        graphics.drawString(font, font.plainSubstrByWidth(order.getString(), 126), x, y + 93, MUTED, false);
        graphics.drawString(font, font.plainSubstrByWidth(entry.dimension(), 126), x, y + 108, MUTED, false);
        graphics.drawString(font, entry.x() + ", " + entry.y() + ", " + entry.z(),
                x, y + 122, MUTED, false);
    }

    private void renderDetail(GuiGraphics graphics, int mx, int my) {
        SoldierRosterNetwork.Entry entry = selectedEntry();
        if (entry == null) return;
        int x = left(), y = top(), lx = x + 9, rx = x + 209;
        String name = entry.name().isBlank()
                ? tr("screen.humangunner.roster.unnamed").getString() : entry.name();
        tierBadge(graphics, entry.tier(), x + 10, y + 30);
        graphics.drawString(font, font.plainSubstrByWidth(name, 140), x + 67, y + 32, TEXT, false);
        graphics.drawString(font, statusName(entry.status()), x + 218, y + 32,
                statusColor(entry.status()), false);
        graphics.drawString(font, font.plainSubstrByWidth(entry.dimension(), 190),
                x + 10, y + 45, MUTED, false);
        graphics.drawString(font, entry.x() + ", " + entry.y() + ", " + entry.z(),
                x + 218, y + 45, MUTED, false);
        if (entry.health() >= 0) {
            graphics.drawString(font, Component.translatable("screen.humangunner.roster.health",
                    entry.health(), entry.maxHealth()), x + 10, y + 57, TEXT, false);
            healthBar(graphics, entry, x + 218, y + 61, 180);
        } else {
            graphics.drawString(font, tr("screen.humangunner.roster.last_known"),
                    x + 10, y + 57, GOLD, false);
        }
        graphics.drawString(font, tr("screen.humangunner.soldier.movement"), lx + 3, y + 73, GOLD, false);
        graphics.drawString(font, tr("screen.humangunner.soldier.combat"), rx + 3, y + 73, GOLD, false);
        boolean loaded = "online".equals(entry.status());
        SoldierOrder[] orders = SoldierOrder.values();
        for (int i = 0; i < orders.length; i++) {
            SoldierOrder order = orders[i];
            int by = y + 86 + i * 22;
            button(graphics, tr("screen.humangunner.soldier." + order.name().toLowerCase(Locale.ROOT)),
                    lx, by, 190, 19, hit(mx, my, lx, by, 190, 19), loaded,
                    entry.order().equals(order.name()), false);
        }
        SoldierCombatMode[] modes = SoldierCombatMode.values();
        for (int i = 0; i < modes.length; i++) {
            SoldierCombatMode mode = modes[i];
            int by = y + 86 + i * 22;
            button(graphics, tr("screen.humangunner.soldier.combat."
                            + mode.name().toLowerCase(Locale.ROOT)), rx, by, 190, 19,
                    hit(mx, my, rx, by, 190, 19), loaded,
                    entry.combat().equals(mode.name()), false);
        }
        button(graphics, tr(entry.pickup() ? "screen.humangunner.soldier.pickup.enabled"
                        : "screen.humangunner.soldier.pickup.disabled"),
                rx, y + 152, 190, 19, hit(mx, my, rx, y + 152, 190, 19), loaded, false, false);
        button(graphics, tr("screen.humangunner.roster.recall_one"), lx, y + 179, 190, 18,
                hit(mx, my, lx, y + 179, 190, 18), true, false, false);
        button(graphics, tr("screen.humangunner.roster.refresh"), rx, y + 179, 190, 18,
                hit(mx, my, rx, y + 179, 190, 18), true, false, false);
        button(graphics, tr("screen.humangunner.roster.back"), lx, y + 201, 190, 18,
                hit(mx, my, lx, y + 201, 190, 18), true, false, false);
        button(graphics, tr(System.currentTimeMillis() < dismissConfirmUntil
                        ? "screen.humangunner.roster.confirm" : "screen.humangunner.soldier.dismiss"),
                rx, y + 201, 190, 18, hit(mx, my, rx, y + 201, 190, 18), true, false, true);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int mouseButton) {
        if (mouseButton != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mx, my, mouseButton);
        int x = left(), y = top();
        if (detail) return clickDetail(mx, my, x, y);
        for (int i = 0; i < 5; i++) {
            if (hit(mx, my, x + 9 + i * 78, y + 31, 76, 18)) {
                SoldierRosterNetwork.request(token, 7, NONE, i - 1);
                return true;
            }
        }
        for (int i = 0; i < entries.size(); i++) {
            SoldierRosterNetwork.Entry entry = entries.get(i);
            int bx = x + 8, by = y + 54 + i * 29;
            if (!hit(mx, my, bx, by, 242, 26)) continue;
            if (hit(mx, my, bx + 177, by + 4, 31, 18)) {
                SoldierRosterNetwork.request(token, 3, entry.id());
            } else if (hit(mx, my, bx + 210, by + 4, 14, 18)) {
                if (entry.canMoveEarlier()) SoldierRosterNetwork.request(token, 11, entry.id());
            } else if (hit(mx, my, bx + 226, by + 4, 14, 18)) {
                if (entry.canMoveLater()) SoldierRosterNetwork.request(token, 12, entry.id());
            } else {
                selected = entry.id();
                previewed = entry.id();
                detail = true;
                dismissConfirmUntil = 0;
            }
            return true;
        }
        if (total > SoldierRosterNetwork.PAGE_SIZE
                && hit(mx, my, x + 251, y + 54, 7, 142)) {
            int thumbY = thumbTop(page);
            int thumbH = thumbHeight();
            draggingScrollbar = true;
            dragGrabOffset = my >= thumbY && my < thumbY + thumbH
                    ? (int) my - thumbY : thumbH / 2;
            dragPreviewPage = pageAtThumbTop(my - dragGrabOffset);
            return true;
        }
        int maxPage = maxPage();
        if (page > 0 && hit(mx, my, x + 8, y + 202, 23, 18)) {
            requestPage(page - 1);
            return true;
        }
        if (page < maxPage && hit(mx, my, x + 78, y + 202, 23, 18)) {
            requestPage(page + 1);
            return true;
        }
        if (hit(mx, my, x + 110, y + 202, 84, 18)) {
            SoldierRosterNetwork.request(token, 0, NONE);
            return true;
        }
        if (hit(mx, my, x + 200, y + 202, 200, 18)) {
            SoldierRosterNetwork.request(token, 2, NONE);
            return true;
        }
        // Empty space (including the gaps between cards) explicitly restores
        // the overall recruitment summary. Mere pointer travel does not.
        previewed = null;
        selected = null;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int mouseButton, double dragX, double dragY) {
        if (draggingScrollbar && mouseButton == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragPreviewPage = pageAtThumbTop(my - dragGrabOffset);
            return true;
        }
        return super.mouseDragged(mx, my, mouseButton, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int mouseButton) {
        if (draggingScrollbar && mouseButton == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragPreviewPage = pageAtThumbTop(my - dragGrabOffset);
            draggingScrollbar = false;
            requestPage(dragPreviewPage);
            return true;
        }
        return super.mouseReleased(mx, my, mouseButton);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!detail && maxPage() > 0 && delta != 0
                && hit(mx, my, left() + 8, top() + 54, 250, 142)) {
            int current = requestedPage >= 0 ? requestedPage : page;
            requestPage(current + (delta > 0 ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private boolean clickDetail(double mx, double my, int x, int y) {
        SoldierRosterNetwork.Entry entry = selectedEntry();
        if (entry == null) return true;
        int lx = x + 9, rx = x + 209;
        if ("online".equals(entry.status())) {
            SoldierOrder[] orders = SoldierOrder.values();
            for (int i = 0; i < orders.length; i++) {
                if (hit(mx, my, lx, y + 86 + i * 22, 190, 19)) {
                    SoldierRosterNetwork.request(token, 4, entry.id(), orders[i].ordinal());
                    return true;
                }
            }
            SoldierCombatMode[] modes = SoldierCombatMode.values();
            for (int i = 0; i < modes.length; i++) {
                if (hit(mx, my, rx, y + 86 + i * 22, 190, 19)) {
                    SoldierRosterNetwork.request(token, 5, entry.id(), modes[i].ordinal());
                    return true;
                }
            }
            if (hit(mx, my, rx, y + 152, 190, 19)) {
                SoldierRosterNetwork.request(token, 6, entry.id(), entry.pickup() ? 0 : 1);
                return true;
            }
        }
        if (hit(mx, my, lx, y + 179, 190, 18)) {
            SoldierRosterNetwork.request(token, 3, entry.id());
            return true;
        }
        if (hit(mx, my, rx, y + 179, 190, 18)) {
            SoldierRosterNetwork.request(token, 0, NONE);
            return true;
        }
        if (hit(mx, my, lx, y + 201, 190, 18)) {
            detail = false;
            dismissConfirmUntil = 0;
            return true;
        }
        if (hit(mx, my, rx, y + 201, 190, 18)) {
            if (System.currentTimeMillis() < dismissConfirmUntil) {
                SoldierRosterNetwork.request(token, 1, entry.id());
                dismissConfirmUntil = 0;
            } else {
                dismissConfirmUntil = System.currentTimeMillis() + 4000;
            }
            return true;
        }
        return false;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (detail) {
                detail = false;
                dismissConfirmUntil = 0;
                return true;
            }
            if (tier != -1) {
                SoldierRosterNetwork.request(token, 7, NONE, -1);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (requestedPage >= 0 && page != requestedPage && scrollRetryCount < 3
                && ++scrollRetryTicks >= 4) {
            scrollRetryTicks = 0;
            scrollRetryCount++;
            SoldierRosterNetwork.request(token, 8, NONE, requestedPage);
        }
        // Lightweight status refresh; only the explicit refresh button runs the full audit.
        if (++statusRefreshTicks >= 100) {
            statusRefreshTicks = 0;
            SoldierRosterNetwork.request(token, 10, NONE);
        }
    }

    @Override
    public void onClose() {
        SoldierRosterNetwork.request(token, 9, NONE);
        super.onClose();
    }
}

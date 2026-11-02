package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared panel shell: header band, optional filter band, recessed shelf, optional master/detail
 * split with independent scrolling, fixed action bar, bounded scroll and automatic focus reveal.
 *
 * <p>Pages only compose layout nodes and bind server state; every piece of chrome geometry lives
 * here so a single edit restyles every screen.
 */
public abstract class PanelScreen extends Screen {
    public static final int MARGIN = PanelGeometry.MARGIN,
            PADDING_X = 8,
            PADDING_Y = 6,
            ACTION_HEIGHT = 20,
            TITLE_HEIGHT = PanelGeometry.TITLE,
            BAND_HEIGHT = PanelGeometry.BAND;

    private final PanelGeometry.Size size;
    private final List<AbstractWidget> body = new ArrayList<>(), visibleBody = new ArrayList<>();
    private final List<AbstractWidget> band = new ArrayList<>();
    private final List<PanelButton> footer = new ArrayList<>();
    private int footerSpacer = -1;
    private final Map<InputValue<?>, PanelEditBox> inputs = new IdentityHashMap<>();
    private final Set<InputValue<?>> usedInputs =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<AbstractWidget> leftSide =
            Collections.newSetFromMap(new IdentityHashMap<>());
    protected UiRect frame = new UiRect(0, 0, 1, 1), viewport = frame, bodyRect = frame;
    private UiRect leftViewport = frame, rightViewport = frame, bandRect = frame;
    private UiRect actionRect = frame;
    private UiLayout.Node content, leftContent, rightContent;
    private int scroll, contentHeight, scrollLeft, leftHeight, scrollRight, rightHeight;
    private int buildSide;
    private Component clippedTitle = Component.empty(),
            headerText = Component.empty(),
            bandText = Component.empty(),
            overviewNarration = Component.empty();

    protected PanelScreen(Component title, PanelGeometry.Size size) {
        super(title);
        this.size = size;
    }

    /**
     * Single column body. Split pages implement {@link #buildLeft}/{@link #buildRight} instead.
     */
    protected UiLayout.Node buildContent() {
        return UiLayout.column(0, List.of());
    }

    protected String faction() {
        return "";
    }

    protected UiTheme theme() {
        return UiThemes.faction(faction());
    }

    /** Right-aligned text in the title band (counts, totals, level). */
    protected Component headerStatus() {
        return Component.empty();
    }

    /** Filter band above the shelf; the page adds its own chips through {@link #band}. */
    protected void buildBand() {}

    /** Right-aligned text inside the filter band. */
    protected Component bandStatus() {
        return Component.empty();
    }

    /** Fixed width of the left column; 0 selects a single column layout. */
    protected int splitWidth() {
        return 0;
    }

    protected void buildLeft(List<UiLayout.Node> out) {}

    protected void buildRight(List<UiLayout.Node> out) {}

    protected void buildFooter() {
        footer(Component.translatable("gui.done"), b -> onClose());
    }

    /** Splits the action bar: buttons registered before stay left, later ones align right. */
    protected void footerSpacer() {
        footerSpacer = footer.size();
    }

    protected PanelButton footer(
            Component label, net.minecraft.client.gui.components.Button.OnPress action) {
        PanelButton b = new PanelButton(label, action, this::theme);
        footer.add(addWidget(b));
        return b;
    }

    /** Registers a widget in the fixed filter band; {@code buildBand} calls this. */
    protected <T extends AbstractWidget> T band(T widget) {
        band.add(addWidget(widget));
        return widget;
    }

    protected <T extends AbstractWidget> T body(T widget) {
        body.add(addWidget(widget));
        if (buildSide < 0) leftSide.add(widget);
        if (widget instanceof PanelButton button) button.viewport(() -> viewportOf(widget));
        if (widget instanceof PanelSlider slider) slider.viewport(() -> viewportOf(widget));
        if (widget instanceof PanelPageSlider slider) slider.viewport(() -> viewportOf(widget));
        if (widget instanceof PanelEditBox edit) edit.viewport(() -> viewportOf(widget));
        return widget;
    }

    private UiRect viewportOf(AbstractWidget widget) {
        if (splitWidth() <= 0) return viewport;
        return leftSide.contains(widget) ? leftViewport : rightViewport;
    }

    protected UiLayout.Node node(PanelButton b) {
        return UiLayout.leaf(b::measureHeight, b::place);
    }

    protected UiLayout.Node node(PanelEditBox edit) {
        return UiLayout.leaf(edit::measureHeight, edit::place);
    }

    protected UiLayout.Node node(PanelPageSlider slider) {
        return UiLayout.leaf(slider::measureHeight, slider::place);
    }

    protected UiLayout.Node node(PanelMeter meter) {
        return UiLayout.leaf(meter::measureHeight, meter::place);
    }

    protected UiLayout.Node node(PanelSlider slider) {
        return UiLayout.leaf(slider::measureHeight, slider::place);
    }

    protected UiLayout.Node node(PanelText text) {
        return UiLayout.leaf(text::measureHeight, text::place);
    }

    protected UiLayout.Node node(PanelGroup group) {
        return UiLayout.leaf(group::measureHeight, group::place);
    }

    protected UiLayout.Node node(PanelHealth health) {
        return UiLayout.leaf(health::measureHeight, health::place);
    }

    protected UiLayout.Node node(PanelCheck check) {
        return UiLayout.leaf(check::measureHeight, check::place);
    }

    protected UiLayout.Node node(PanelDisclosure row) {
        return UiLayout.leaf(row::measureHeight, row::place);
    }

    protected UiLayout.Node node(PanelRosterRow row) {
        return UiLayout.leaf(row::measureHeight, row::place);
    }

    protected UiLayout.Node node(PanelKeyValue row) {
        return UiLayout.leaf(row::measureHeight, row::place);
    }

    protected boolean fieldsEnabled() {
        return true;
    }

    protected void formChanged() {
        relayout();
    }

    protected UiLayout.Node field(Component label, InputValue<?> draft) {
        PanelText caption =
                body(
                        new PanelText(
                                label, () -> draft.valid() ? theme().muted() : theme().danger()));
        PanelEditBox input =
                inputs.computeIfAbsent(draft, key -> new PanelEditBox(font, label, this::theme));
        usedInputs.add(draft);
        body(input);
        input.setMaxLength(draft.maximumLength());
        if (!input.getValue().equals(draft.text())) input.setValue(draft.text());
        input.active = fieldsEnabled();
        input.validation(draft::valid);
        input.setResponder(
                value -> {
                    draft.set(value);
                    caption.setMessage(
                            draft.valid()
                                    ? label
                                    : label.copy()
                                            .append(" · ")
                                            .append(
                                                    Component.translatable(
                                                            "gui.hostile_humans_unified.input.invalid")));
                    formChanged();
                });
        return UiLayout.column(2, List.of(node(caption), node(input)));
    }

    protected UiLayout.Node text(Component message, java.util.function.IntSupplier color) {
        return node(body(new PanelText(message, color)));
    }

    @Override
    protected void init() {
        InputValue<?> focused = null;
        for (var entry : inputs.entrySet())
            if (entry.getValue() == getFocused()) {
                focused = entry.getKey();
                break;
            }
        body.clear();
        visibleBody.clear();
        band.clear();
        footer.clear();
        footerSpacer = -1;
        usedInputs.clear();
        leftSide.clear();
        content = null;
        leftContent = null;
        rightContent = null;
        frame = PanelGeometry.frame(size, width, height);
        if (splitWidth() > 0) {
            buildSide = -1;
            List<UiLayout.Node> left = new ArrayList<>();
            buildLeft(left);
            leftContent = UiLayout.column(6, left);
            buildSide = 1;
            List<UiLayout.Node> right = new ArrayList<>();
            buildRight(right);
            rightContent = UiLayout.column(6, right);
        } else {
            buildSide = 0;
            content = buildContent();
        }
        buildSide = 0;
        buildBand();
        buildFooter();
        inputs.keySet().retainAll(usedInputs);
        relayout();
        PanelEditBox input = inputs.get(focused);
        if (input != null && input.active) {
            setFocused(input);
            input.setFocused(true);
            revealFocus();
        }
    }

    protected final void relayout() {
        frame = PanelGeometry.frame(size, width, height);
        headerText = headerStatus();
        bandText = bandStatus();
        boolean hasBand = !band.isEmpty() || !bandText.getString().isBlank();
        bodyRect = PanelGeometry.body(frame, hasBand);
        bandRect = hasBand ? PanelGeometry.band(frame) : new UiRect(frame.x(), frame.y(), 0, 0);
        viewport = bodyRect.inset(PADDING_X, PADDING_Y);
        int split = splitWidth();
        if (split > 0) {
            UiRect[] columns = PanelGeometry.split(bodyRect, split);
            leftViewport = columns[0].inset(PADDING_X, PADDING_Y);
            rightViewport = columns[1].inset(PADDING_X, PADDING_Y);
            leftHeight = leftContent == null ? 0 : leftContent.measure(leftViewport.width());
            rightHeight = rightContent == null ? 0 : rightContent.measure(rightViewport.width());
            scrollLeft =
                    PanelGeometry.clampScroll(scrollLeft, leftHeight, leftViewport.height());
            scrollRight =
                    PanelGeometry.clampScroll(scrollRight, rightHeight, rightViewport.height());
            if (leftContent != null)
                leftContent.place(
                        new UiRect(
                                leftViewport.x(),
                                leftViewport.y() - scrollLeft,
                                leftViewport.width(),
                                leftHeight));
            if (rightContent != null)
                rightContent.place(
                        new UiRect(
                                rightViewport.x(),
                                rightViewport.y() - scrollRight,
                                rightViewport.width(),
                                rightHeight));
        } else {
            leftViewport = frame;
            rightViewport = frame;
            contentHeight = content == null ? 0 : content.measure(viewport.width());
            scroll = PanelGeometry.clampScroll(scroll, contentHeight, viewport.height());
            if (content != null)
                content.place(
                        new UiRect(
                                viewport.x(),
                                viewport.y() - scroll,
                                viewport.width(),
                                contentHeight));
        }
        clippedTitle =
                Component.literal(font.plainSubstrByWidth(title.getString(), titleWidth()));
        var overview = title.copy();
        for (AbstractWidget widget : body)
            if (widget instanceof PanelText txt && !txt.getMessage().getString().isBlank())
                overview.append(". ").append(txt.getMessage());
        if (!headerText.getString().isBlank()) overview.append(". ").append(headerText);
        overviewNarration = overview;
        visibleBody.clear();
        for (AbstractWidget w : body) {
            UiRect clip = viewportOf(w);
            if (w.visible && clip.intersects(w.getY(), w.getY() + w.getHeight()))
                visibleBody.add(w);
        }
        if (getFocused() instanceof PanelEditBox edit && !edit.fullyVisible()) {
            edit.setFocused(false);
            setFocused(null);
        }
        layoutBand();
        layoutFooter();
    }

    private int titleWidth() {
        int reserved = MARGIN + 20;
        if (!headerText.getString().isBlank()) reserved += font.width(headerText) + 12;
        return Math.max(1, frame.width() - reserved);
    }

    private void layoutBand() {
        if (bandRect.height() <= 0) return;
        int x = bandRect.x() + 6;
        int limit =
                bandRect.right()
                        - 6
                        - (bandText.getString().isBlank() ? 0 : font.width(bandText) + 8);
        for (AbstractWidget widget : band) {
            int preferred =
                    widget instanceof PanelButton button
                            ? font.width(button.getMessage()) + 14
                            : widget.getWidth();
            int w = Math.max(1, Math.min(preferred, Math.max(1, limit - x)));
            widget.setX(x);
            widget.setY(bandRect.y() + (BAND_HEIGHT - 20) / 2);
            widget.setWidth(w);
            widget.setHeight(20);
            x += w + 4;
        }
    }

    private void layoutFooter() {
        int inner = Math.max(1, bodyRect.width()), count = footer.size(), gap = 6;
        int height = ACTION_HEIGHT;
        if (count == 0) {
            actionRect = new UiRect(bodyRect.x(), frame.bottom() - 10 - height, inner, height);
            return;
        }
        int[] widths = new int[count];
        if (footerSpacer < 0) {
            int bw = Math.max(1, (inner - gap * (count - 1)) / count);
            for (int i = 0; i < count; i++) widths[i] = bw;
        } else {
            for (int i = 0; i < count; i++)
                widths[i] = naturalWidth(footer.get(i), inner / Math.max(1, count));
        }
        for (int i = 0; i < count; i++) height = Math.max(height, footer.get(i).measureHeight(widths[i]));
        int top = Math.max(frame.y(), frame.bottom() - 10 - height);
        actionRect = new UiRect(bodyRect.x(), top, inner, height);
        if (footerSpacer < 0) {
            for (int i = 0; i < count; i++)
                footer.get(i).place(new UiRect(bodyRect.x() + i * (widths[0] + gap), top, widths[0], height));
        } else {
            int x = bodyRect.x();
            for (int i = 0; i < footerSpacer; i++) {
                footer.get(i).place(new UiRect(x, top, widths[i], height));
                x += widths[i] + gap;
            }
            int right = bodyRect.right();
            for (int i = count - 1; i >= footerSpacer; i--) {
                right -= widths[i];
                footer.get(i).place(new UiRect(right, top, widths[i], height));
                right -= gap;
            }
        }
    }

    private int naturalWidth(PanelButton button, int maximum) {
        return Math.max(48, Math.min(Math.max(1, maximum), font.width(button.getMessage()) + 14));
    }

    /** Rect of the fixed action bar; pages never place footer buttons themselves. */
    public final UiRect actionRect() {
        return actionRect;
    }

    protected final boolean hasOverflow() {
        return splitWidth() > 0
                ? leftHeight > leftViewport.height() || rightHeight > rightViewport.height()
                : contentHeight > viewport.height();
    }

    protected final void scrollTo(int value) {
        scroll = PanelGeometry.clampScroll(value, contentHeight, viewport.height());
        relayout();
    }

    protected final void scrollTo(int side, int value) {
        if (side < 0)
            scrollLeft = PanelGeometry.clampScroll(value, leftHeight, leftViewport.height());
        else scrollRight = PanelGeometry.clampScroll(value, rightHeight, rightViewport.height());
        relayout();
    }

    protected final void scrollReset() {
        scroll = 0;
        scrollLeft = 0;
        scrollRight = 0;
    }

    protected final void revealFocus() {
        if (!(getFocused() instanceof AbstractWidget w) || !body.contains(w)) return;
        UiRect clip = viewportOf(w);
        int offset = splitWidth() <= 0 ? scroll : leftSide.contains(w) ? scrollLeft : scrollRight;
        int top = w.getY() - clip.y() + offset;
        int target = PanelGeometry.reveal(offset, top, top + w.getHeight(), clip.height());
        if (splitWidth() > 0) {
            if (leftSide.contains(w)) scrollTo(-1, target);
            else scrollTo(1, target);
        } else scrollTo(target);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
            int step =
                    (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1)
                            * Math.max(24, viewport.height() - 12);
            if (splitWidth() > 0) {
                boolean left =
                        getFocused() instanceof AbstractWidget w && leftSide.contains(w);
                scrollTo(left ? -1 : 1, (left ? scrollLeft : scrollRight) + step);
            } else scrollTo(scroll + step);
            return true;
        }
        boolean handled = super.keyPressed(key, scan, mods);
        revealFocus();
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        int side = sideAt(x, y);
        if (side == Integer.MIN_VALUE) return super.mouseScrolled(x, y, delta);
        if (getFocused() instanceof PanelSlider slider && slider.editing()) return true;
        if (getFocused() instanceof PanelPageSlider slider && slider.editing()) return true;
        if (splitWidth() > 0) {
            if (side < 0) scrollTo(-1, scrollLeft - (int) (delta * 24));
            else scrollTo(1, scrollRight - (int) (delta * 24));
        } else scrollTo(scroll - (int) (delta * 24));
        return true;
    }

    /** -1 left column, 1 right column, 0 single body, MIN_VALUE outside the content shelf. */
    private int sideAt(double x, double y) {
        if (splitWidth() > 0) {
            UiRect[] columns = PanelGeometry.split(bodyRect, splitWidth());
            if (columns[0].contains(x, y)) return -1;
            if (columns[1].contains(x, y)) return 1;
            return Integer.MIN_VALUE;
        }
        return bodyRect.contains(x, y) ? 0 : Integer.MIN_VALUE;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        UiTheme t = theme();
        UiDraw.frame(g, frame, t);
        UiDraw.headerBand(g, frame.x() + 1, frame.y(), frame.width() - 2, TITLE_HEIGHT, t);
        var emblem = UiThemes.emblem(faction());
        if (emblem != null)
            UiDraw.badge(g, frame.x() + 12, frame.y() + 5, emblem, 14, 18, t.accent());
        g.drawString(
                font,
                clippedTitle,
                frame.x() + (emblem == null ? 12 : 32),
                frame.y() + 10,
                t.text(),
                false);
        if (!headerText.getString().isBlank())
            g.drawString(
                    font,
                    headerText,
                    frame.right() - MARGIN - font.width(headerText),
                    frame.y() + 10,
                    t.muted(),
                    false);
        if (bandRect.height() > 0) {
            UiDraw.rect(
                    g, bandRect.x(), bandRect.y(), bandRect.width(), bandRect.height(), t.subBand());
            for (AbstractWidget widget : band) widget.render(g, mx, my, partial);
            if (!bandText.getString().isBlank())
                g.drawString(
                        font,
                        bandText,
                        bandRect.right() - 6 - font.width(bandText),
                        bandRect.y() + (BAND_HEIGHT - 8) / 2,
                        t.muted(),
                        false);
        }
        int ruleY = bandRect.height() > 0 ? bandRect.bottom() + 2 : frame.y() + TITLE_HEIGHT + 3;
        UiDraw.rule(
                g, frame.x() + MARGIN, ruleY, frame.width() - 2 * MARGIN, t.ruleDark(), t.ruleLight());

        watermark(g);
        if (splitWidth() > 0) {
            UiRect[] columns = PanelGeometry.split(bodyRect, splitWidth());
            drawBody(g, t, mx, my, partial, columns[0], leftHeight, scrollLeft, columns[1].x());
            drawBody(g, t, mx, my, partial, columns[1], rightHeight, scrollRight, -1);
        } else drawBody(g, t, mx, my, partial, bodyRect, contentHeight, scroll, -1);

        UiDraw.fade(g, bodyRect.x(), bodyRect.bottom() - 12, bodyRect.width(), 12, t);
        UiDraw.actionBar(
                g, actionRect.x(), actionRect.y(), actionRect.width(), actionRect.height(), t);
        for (PanelButton b : footer) b.render(g, mx, my, partial);
    }

    /** Faction watermark on the shelf floor, under every control. */
    private void watermark(GuiGraphics g) {
        if (bodyRect.width() <= 0 || bodyRect.height() <= 0) return;
        g.enableScissor(bodyRect.x(), bodyRect.y(), bodyRect.right(), bodyRect.bottom());
        try {
            UiDraw.watermark(g, frame, UiThemes.emblem(faction()));
        } finally {
            g.disableScissor();
        }
    }

    private void drawBody(
            GuiGraphics g,
            UiTheme t,
            int mx,
            int my,
            float partial,
            UiRect rect,
            int height,
            int offset,
            int dividerX) {
        if (rect.width() <= 0 || rect.height() <= 0) return;
        UiDraw.shelf(g, rect.x(), rect.y(), rect.width(), rect.height(), t);
        if (dividerX > 0)
            UiDraw.rect(g, dividerX, rect.y(), 1, rect.height(), UiColor.alpha(0xFF000000, .55F));
        g.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());
        try {
            UiRect clip = rect.inset(PADDING_X, PADDING_Y);
            for (AbstractWidget w : visibleBody)
                if (clip.equals(viewportOf(w))) w.render(g, mx, my, partial);
        } finally {
            g.disableScissor();
        }
        if (height > rect.height())
            UiDraw.scrollbar(g, rect.right() + 2, rect.y(), rect.height(), offset, height, t);
    }

    @Override
    public Component getNarrationMessage() {
        return overviewNarration;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
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

/** Shared panel shell, persistent native widgets, bounded scroll and automatic focus reveal. */
public abstract class PanelScreen extends Screen {
    private final PanelGeometry.Size size;
    private final List<AbstractWidget> body = new ArrayList<>(), visibleBody = new ArrayList<>();
    private final List<PanelButton> footer = new ArrayList<>();
    protected UiRect frame = new UiRect(0, 0, 1, 1), viewport = frame;
    private UiLayout.Node content;
    private final Map<InputValue<?>, PanelEditBox> inputs = new IdentityHashMap<>();
    private final Set<InputValue<?>> usedInputs =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private int scroll, contentHeight;
    private Component clippedTitle = Component.empty(), overviewNarration = Component.empty();

    protected PanelScreen(Component title, PanelGeometry.Size size) {
        super(title);
        this.size = size;
    }

    protected abstract UiLayout.Node buildContent();

    protected String faction() {
        return "";
    }

    protected UiTheme theme() {
        return UiThemes.faction(faction());
    }

    protected void buildFooter() {
        footer(Component.translatable("gui.done"), b -> onClose());
    }

    protected PanelButton footer(
            Component label, net.minecraft.client.gui.components.Button.OnPress action) {
        PanelButton b = new PanelButton(label, action, this::theme);
        footer.add(addWidget(b));
        return b;
    }

    protected <T extends AbstractWidget> T body(T widget) {
        body.add(addWidget(widget));
        if (widget instanceof PanelButton b) b.viewport(() -> viewport);
        if (widget instanceof PanelSlider slider) slider.viewport(() -> viewport);
        if (widget instanceof PanelPageSlider slider) slider.viewport(() -> viewport);
        if (widget instanceof PanelEditBox edit) edit.viewport(() -> viewport);
        return widget;
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
        input.setResponder(value -> {});
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
        footer.clear();
        usedInputs.clear();
        frame = PanelGeometry.frame(size, width, height);
        content = buildContent();
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
        clippedTitle =
                Component.literal(
                        font.plainSubstrByWidth(
                                title.getString(),
                                Math.max(
                                        1,
                                        frame.width()
                                                - (UiThemes.emblem(faction()) == null ? 24 : 42))));
        var overview = title.copy();
        for (AbstractWidget widget : body)
            if (widget instanceof PanelText text && !text.getMessage().getString().isBlank())
                overview.append(". ").append(text.getMessage());
        overviewNarration = overview;
        int margin = Math.min(12, Math.max(0, (frame.width() - 1) / 2));
        int innerWidth = Math.max(1, frame.width() - 2 * margin),
                count = footer.size(),
                gap = 6,
                bw = count == 0 ? 0 : Math.max(1, (innerWidth - gap * (count - 1)) / count);
        int footerHeight = 20;
        for (PanelButton b : footer) footerHeight = Math.max(footerHeight, b.measureHeight(bw));
        int footerTop = Math.max(frame.y(), frame.bottom() - 12 - footerHeight),
                top = Math.min(frame.bottom(), frame.y() + 32),
                bottom = Math.max(top, footerTop - 12);
        viewport = new UiRect(frame.x() + margin, top, innerWidth, Math.max(0, bottom - top));
        if (content != null) {
            contentHeight = content.measure(viewport.width());
            scroll = PanelGeometry.clampScroll(scroll, contentHeight, viewport.height());
            content.place(
                    new UiRect(
                            viewport.x(), viewport.y() - scroll, viewport.width(), contentHeight));
        }
        visibleBody.clear();
        for (AbstractWidget w : body)
            if (w.visible && viewport.intersects(w.getY(), w.getY() + w.getHeight()))
                visibleBody.add(w);
        if (getFocused() instanceof PanelEditBox edit && !edit.fullyVisible()) {
            edit.setFocused(false);
            setFocused(null);
        }
        for (int i = 0; i < count; i++)
            footer.get(i)
                    .place(new UiRect(viewport.x() + i * (bw + gap), footerTop, bw, footerHeight));
    }

    protected final boolean hasOverflow() {
        return contentHeight > viewport.height();
    }

    protected final void scrollTo(int value) {
        scroll = PanelGeometry.clampScroll(value, contentHeight, viewport.height());
        relayout();
    }

    protected final void revealFocus() {
        if (getFocused() instanceof AbstractWidget w && body.contains(w)) {
            int top = w.getY() - viewport.y() + scroll;
            scrollTo(PanelGeometry.reveal(scroll, top, top + w.getHeight(), viewport.height()));
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
            scrollTo(
                    scroll
                            + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1)
                                    * Math.max(24, viewport.height() - 12));
            return true;
        }
        boolean handled = super.keyPressed(key, scan, mods);
        revealFocus();
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (viewport.contains(x, y)) {
            if (getFocused() instanceof PanelSlider slider && slider.editing()) return true;
            if (getFocused() instanceof PanelPageSlider slider && slider.editing()) return true;
            scrollTo(scroll - (int) (delta * 24));
            return true;
        }
        return super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (viewport.contains(x, y)) return super.mouseClicked(x, y, button);
        for (PanelButton b : footer)
            if (b.mouseClicked(x, y, button)) {
                setFocused(b);
                setDragging(button == 0);
                return true;
            }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        UiTheme t = theme();
        UiDraw.frame(g, frame, t);
        var emblem = UiThemes.emblem(faction());
        if (emblem != null)
            UiDraw.badge(g, frame.x() + 12, frame.y() + 4, emblem, 12, 16, t.accent());
        g.drawString(
                font,
                clippedTitle,
                frame.x() + (emblem == null ? 12 : 30),
                frame.y() + 9,
                t.text(),
                false);
        if (viewport.height() > 0) {
            g.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
            try {
                UiDraw.rect(
                        g,
                        viewport.x(),
                        viewport.y(),
                        viewport.width(),
                        viewport.height(),
                        t.inset());
                UiDraw.watermark(g, frame, emblem);
                for (AbstractWidget w : visibleBody) w.render(g, mx, my, partial);
            } finally {
                g.disableScissor();
            }
        }
        if (contentHeight > viewport.height() && viewport.height() > 0) {
            int thumb = Math.min(18, viewport.height()), travel = viewport.height() - thumb;
            int y = viewport.y() + travel * scroll / Math.max(1, contentHeight - viewport.height());
            UiDraw.rect(g, viewport.right() + 2, y, 2, thumb, t.muted());
        }
        UiDraw.rule(g, viewport.x(), viewport.bottom() + 4, viewport.width(), 0xFF070D12, t.edge());
        for (PanelButton b : footer) b.render(g, mx, my, partial);
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

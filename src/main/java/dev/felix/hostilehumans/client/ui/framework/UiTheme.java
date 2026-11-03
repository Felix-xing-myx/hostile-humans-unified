package dev.felix.hostilehumans.client.ui.framework;

/**
 * Immutable field-terminal tokens derived from the reference overview (界面总览.html). Faction is
 * supplied by data; widgets never branch on a faction id and never invent a second palette.
 *
 * <p>{@code head/top/mid/bottom} are the vertical panel gradient, {@code inset} is the recessed
 * shelf, {@code surface} is the resting control face. Everything else is derived so a faction only
 * has to name its accent.
 */
public record UiTheme(
        int head,
        float headTint,
        int top,
        int mid,
        int bottom,
        int inset,
        int surface,
        int edge,
        int text,
        int muted,
        int disabled,
        int danger,
        int accent) {
    /** Panel top before the accent tint is applied (reference: #1A2733). */
    public static final int BASE_HEAD = 0xFF1A2733;
    public static final int BASE_TOP = 0xFF15212A;
    public static final int BASE_MID = 0xFF0E171E;
    public static final int BASE_BOTTOM = 0xFF090F13;

    public UiTheme withAccent(int color) {
        return withAccentAndTint(color, headTint);
    }

    /** Faction accent plus its own header wash ratio; both come from theme data. */
    public UiTheme withAccentAndTint(int color, float tint) {
        float t = Math.max(0F, Math.min(1F, tint));
        return new UiTheme(
                UiColor.mix(BASE_HEAD, color, t),
                t,
                top,
                mid,
                bottom,
                inset,
                surface,
                edge,
                text,
                muted,
                disabled,
                danger,
                color);
    }

    /** Rebuilds a theme from an explicit base palette, keeping the current accent. */
    public UiTheme withBase(
            float headTint,
            int top,
            int mid,
            int bottom,
            int inset,
            int surface,
            int edge,
            int text,
            int muted,
            int disabled,
            int danger) {
        float t = Math.max(0F, Math.min(1F, headTint));
        return new UiTheme(
                UiColor.mix(BASE_HEAD, accent, t),
                t,
                top,
                mid,
                bottom,
                inset,
                surface,
                edge,
                text,
                muted,
                disabled,
                danger,
                accent);
    }

    public static final UiTheme DEFAULT =
            new UiTheme(
                    UiColor.mix(BASE_HEAD, 0xFFF2C66D, .09F),
                    .09F,
                    BASE_TOP,
                    BASE_MID,
                    BASE_BOTTOM,
                    0xFF0B141A,
                    0xFF1D252B,
                    0xFF41525F,
                    0xFFE6EBEF,
                    0xFF929FA9,
                    0xFF5F707C,
                    0xFFE49B92,
                    0xFFF2C66D);

    // ---- control states -------------------------------------------------------

    public int controlFace() {
        return surface;
    }

    public int controlEdge() {
        return UiColor.mix(surface, 0xFFFFFFFF, .12F);
    }

    public int controlHover() {
        return UiColor.mix(surface, 0xFFFFFFFF, .045F);
    }

    public int selectedFace() {
        return UiColor.mix(inset, accent, .14F);
    }

    public int selectedEdge() {
        return UiColor.mix(inset, accent, .55F);
    }

    public int selectedSheen() {
        return UiColor.alpha(accent, .16F);
    }

    public int dangerFace() {
        return UiColor.mix(inset, danger, .10F);
    }

    public int dangerEdge() {
        return UiColor.mix(inset, danger, .42F);
    }

    /** Readable but plainly inert: a 2.2% face made the label look absent, not disabled. */
    public int disabledFace() {
        return UiColor.mix(inset, 0xFFFFFFFF, .05F);
    }

    public int disabledEdge() {
        return UiColor.mix(inset, 0xFFFFFFFF, .10F);
    }

    /** Disabled label colour; keeps ~5:1 on the disabled face instead of 3.4:1. */
    public int disabledText() {
        return UiColor.mix(disabled, muted, .55F);
    }

    // ---- structure ------------------------------------------------------------

    public int ruleDark() {
        return 0xFF070D12;
    }

    public int ruleLight() {
        return UiColor.mix(inset, 0xFFFFFFFF, .045F);
    }

    public int groupBand() {
        return UiColor.mix(inset, 0xFFFFFFFF, .038F);
    }

    public int groupEdge() {
        return UiColor.mix(inset, 0xFFFFFFFF, .085F);
    }

    public int subBand() {
        return UiColor.mix(top, 0xFFFFFFFF, .035F);
    }

    /** Translucent so the panel grain keeps showing through the action bar. */
    public int actionBar() {
        return UiColor.alpha(UiColor.mix(bottom, 0x00000000, .45F), .86F);
    }

    public int bodyShadow() {
        return UiColor.alpha(0xFF000000, .55F);
    }

    public int bodyEdge() {
        return UiColor.alpha(0xFF000000, .22F);
    }

    public int track() {
        return 0xFF0A1218;
    }

    public int trackEdge() {
        return UiColor.mix(0xFF0A1218, 0xFFFFFFFF, .06F);
    }

    public int meterWell() {
        return 0xFF070D12;
    }

    public int meterEdge() {
        return UiColor.mix(0xFF070D12, 0xFFFFFFFF, .09F);
    }

    public int tickWell() {
        return UiColor.mix(inset, 0xFFFFFFFF, .13F);
    }

    public int outline() {
        return 0xFF05080B;
    }

    public int ring() {
        return UiColor.alpha(0xFF41525F, .42F);
    }

    public int badgeFace() {
        return 0xFF1B262F;
    }

    public int badgeEdge() {
        return UiColor.mix(0xFF1B262F, 0xFFFFFFFF, .10F);
    }

    public int positive() {
        return 0xFF95D7AB;
    }

    public int negative() {
        return 0xFFE59A94;
    }

    public int neutral() {
        return 0xFFD7DFE7;
    }
}
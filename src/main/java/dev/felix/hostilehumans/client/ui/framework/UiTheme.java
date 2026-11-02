package dev.felix.hostilehumans.client.ui.framework;

/** Immutable field-terminal tokens. Faction is supplied by data, never by widgets. */
public record UiTheme(
        int top,
        int bottom,
        int inset,
        int surface,
        int edge,
        int text,
        int muted,
        int disabled,
        int danger,
        int accent) {
    public UiTheme withAccent(int color) {
        return new UiTheme(top, bottom, inset, surface, edge, text, muted, disabled, danger, color);
    }

    public static final UiTheme DEFAULT =
            new UiTheme(
                    0xFF15212A,
                    0xFF0C151B,
                    0xFF0B141A,
                    0xFF1B2731,
                    0xFF41525F,
                    0xFFE6EBEF,
                    0xFF929FA9,
                    0xFF5F707C,
                    0xFFE49B92,
                    0xFFF2C66D);
}

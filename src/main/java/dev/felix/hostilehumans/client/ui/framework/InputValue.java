package dev.felix.hostilehumans.client.ui.framework;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Typed local edit draft. Invalid/partial text is retained but cannot become a submitted value. */
public final class InputValue<T> {
    private final Function<String, T> parser;
    private final int maximumLength;
    private String text, original;
    private T parsed;

    private InputValue(String initial, Function<String, T> parser, int maximumLength) {
        this.parser = parser;
        this.maximumLength = maximumLength;
        original = initial;
        set(initial);
    }

    public static InputValue<String> text(String initial, int maximum, boolean required) {
        return new InputValue<>(
                initial,
                s -> {
                    String v = s.trim();
                    if (v.length() > maximum || required && v.isEmpty())
                        throw new IllegalArgumentException();
                    return v;
                },
                maximum);
    }

    public static InputValue<Integer> integer(int initial, int minimum, int maximum) {
        return new InputValue<>(
                Integer.toString(initial),
                s -> {
                    int v = Integer.parseInt(s.trim());
                    if (v < minimum || v > maximum) throw new IllegalArgumentException();
                    return v;
                },
                20);
    }

    public static InputValue<UUID> uuid(UUID initial) {
        return new InputValue<>(
                initial == null ? "" : initial.toString(), s -> UUID.fromString(s.trim()), 36);
    }

    public void set(String value) {
        text = value;
        try {
            parsed = parser.apply(value);
        } catch (RuntimeException invalid) {
            parsed = null;
        }
    }

    public int maximumLength() {
        return maximumLength;
    }

    public String text() {
        return text;
    }

    public Optional<T> value() {
        return Optional.ofNullable(parsed);
    }

    public boolean valid() {
        return parsed != null;
    }

    public boolean dirty() {
        return !text.equals(original);
    }

    public void accepted() {
        original = text;
    }
}

package dev.felix.hostilehumans.soldier;

import java.util.Objects;
import java.util.UUID;

/** Server-read representative, not a squad sum or an average. No client entity lookup. */
public record CommandHealthSnapshot(
        UUID human, String name, float current, float maximum, boolean mixed) {
    private static final UUID NONE = new UUID(0, 0);
    private static final CommandHealthSnapshot EMPTY =
            new CommandHealthSnapshot(NONE, "", 0, 0, false);

    public CommandHealthSnapshot {
        Objects.requireNonNull(human);
        Objects.requireNonNull(name);
        if (name.length() > 256
                || !Float.isFinite(current)
                || !Float.isFinite(maximum)
                || current < 0
                || maximum < 0
                || (!human.equals(NONE) && maximum <= 0)
                || human.equals(NONE) && (current != 0 || maximum != 0 || mixed))
            throw new IllegalArgumentException("invalid command health snapshot");
    }

    public static CommandHealthSnapshot empty() {
        return EMPTY;
    }

    public static CommandHealthSnapshot sample(
            UUID human, String name, float current, float maximum) {
        if (human == null
                || name == null
                || !Float.isFinite(current)
                || !Float.isFinite(maximum)
                || current < 0
                || maximum <= 0) return EMPTY;
        return new CommandHealthSnapshot(
                human,
                name.length() > 256 ? name.substring(0, 256) : name,
                current,
                maximum,
                false);
    }

    public boolean available() {
        return !human.equals(NONE) && maximum > 0;
    }

    public CommandHealthSnapshot compare(CommandHealthSnapshot other) {
        boolean differs =
                mixed
                        || available() != other.available()
                        || Math.abs(current - other.current) > 1e-4
                        || Math.abs(maximum - other.maximum) > 1e-4;
        return available()
                ? new CommandHealthSnapshot(human, name, current, maximum, differs)
                : this;
    }
}

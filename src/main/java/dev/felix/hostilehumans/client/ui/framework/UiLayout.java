package dev.felix.hostilehumans.client.ui.framework;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/** Persistent non-interactive layout nodes. Measurement and placement never run in render. */
public final class UiLayout {
    public interface Node {
        int measure(int width);

        void place(UiRect bounds);
    }

    private UiLayout() {}

    public static Node leaf(IntUnaryOperator measure, Consumer<UiRect> place) {
        return new Node() {
            public int measure(int w) {
                return Math.max(0, measure.applyAsInt(Math.max(1, w)));
            }

            public void place(UiRect b) {
                place.accept(b);
            }
        };
    }

    public static Node column(int gap, List<Node> children) {
        return new Node() {
            public int measure(int w) {
                int h = 0;
                for (Node n : children) h += n.measure(w);
                return h + Math.max(0, children.size() - 1) * gap;
            }

            public void place(UiRect b) {
                int y = b.y();
                for (Node n : children) {
                    int h = n.measure(b.width());
                    n.place(new UiRect(b.x(), y, b.width(), h));
                    y += h + gap;
                }
            }
        };
    }

    public static Node row(int gap, List<Node> children) {
        return grid(children.size(), gap, 120, children);
    }

    public static Node grid(int columns, int gap, List<Node> children) {
        return grid(columns, gap, 56, children);
    }

    public static Node grid(int columns, int gap, int minimumCell, List<Node> children) {
        return new Node() {
            private int columns(int w) {
                return Math.max(
                        1,
                        Math.min(
                                Math.max(1, columns),
                                Math.max(1, (w + gap) / (minimumCell + gap))));
            }

            public int measure(int w) {
                int cols = columns(w), cell = Math.max(1, (w - (cols - 1) * gap) / cols), h = 0;
                for (int i = 0; i < children.size(); i += cols) {
                    int line = 0;
                    for (int j = i; j < Math.min(i + cols, children.size()); j++)
                        line = Math.max(line, children.get(j).measure(cell));
                    h += line + gap;
                }
                return Math.max(0, h - gap);
            }

            public void place(UiRect b) {
                int cols = columns(b.width()),
                        cell = Math.max(1, (b.width() - (cols - 1) * gap) / cols),
                        y = b.y();
                for (int i = 0; i < children.size(); i += cols) {
                    int h = 0;
                    for (int j = i; j < Math.min(i + cols, children.size()); j++)
                        h = Math.max(h, children.get(j).measure(cell));
                    for (int j = i; j < Math.min(i + cols, children.size()); j++)
                        children.get(j)
                                .place(new UiRect(b.x() + (j - i) * (cell + gap), y, cell, h));
                    y += h + gap;
                }
            }
        };
    }

    /** Tabs retain the selected child tree; callers own typed page state. */
    public static Node tabs(Supplier<Node> selected) {
        return new Node() {
            public int measure(int w) {
                return selected.get().measure(w);
            }

            public void place(UiRect b) {
                selected.get().place(b);
            }
        };
    }
}

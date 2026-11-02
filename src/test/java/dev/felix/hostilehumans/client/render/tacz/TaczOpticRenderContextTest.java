package dev.felix.hostilehumans.client.render.tacz;

/** Render scope nesting, failure cleanup and thread ownership without Minecraft or TaCZ. */
public final class TaczOpticRenderContextTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        Object index = new Object(), model = new Object(), other = new Object();
        check(!TaczOpticRenderContext.active());
        try (var scope = new TaczOpticRenderContext(index, model, 0.75F)) {
            check(TaczOpticRenderContext.modelFor(index) == model);
            check(TaczOpticRenderContext.modelFor(other) == null);
            check(TaczOpticRenderContext.aimFor(model, 0) == 0.75F);
            check(TaczOpticRenderContext.aimFor(other, 0.2F) == 0.2F);
            try {
                try (var nested = new TaczOpticRenderContext(other, other, 2)) {
                    check(TaczOpticRenderContext.modelFor(index) == null);
                    check(TaczOpticRenderContext.aimFor(other, 0) == 1);
                    throw new IllegalStateException("broken optic resource");
                }
            } catch (IllegalStateException expected) { }
            check(TaczOpticRenderContext.modelFor(index) == model);
            var leaked = new java.util.concurrent.atomic.AtomicBoolean();
            var thread = new Thread(() -> leaked.set(TaczOpticRenderContext.active()));
            thread.start(); thread.join();
            check(!leaked.get());
        }
        check(!TaczOpticRenderContext.active());
        check(TaczOpticRenderContext.modelFor(index) == null);
        try (var scope = new TaczOpticRenderContext(index, model, Float.NaN)) {
            check(TaczOpticRenderContext.aimFor(model, 1) == 0);
        }
        System.out.println("TaczOpticRenderContextTest passed");
    }
}

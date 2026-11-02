package dev.felix.hostilehumans.client.render.tacz;

/** A synchronous render-only scope. It never changes a player, gun operator or item. */
public final class TaczOpticRenderContext implements AutoCloseable {
    private static final ThreadLocal<TaczOpticRenderContext> CURRENT = new ThreadLocal<>();
    private final TaczOpticRenderContext previous;
    private final Object index, model;
    private final float aim;

    TaczOpticRenderContext(Object index, Object model, float aim) {
        this.previous = CURRENT.get();
        this.index = index;
        this.model = model;
        this.aim = Float.isFinite(aim) ? Math.max(0, Math.min(1, aim)) : 0;
        CURRENT.set(this);
    }

    public static boolean active() { return CURRENT.get() != null; }
    public static Object modelFor(Object index) {
        var scope = CURRENT.get();
        return scope != null && scope.index == index ? scope.model : null;
    }
    public static float aimFor(Object model, float nativeAim) {
        var scope = CURRENT.get();
        return scope != null && scope.model == model ? scope.aim : nativeAim;
    }
    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}

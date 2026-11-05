package dev.felix.hostilehumans.damage;

/** One bounded receipt per entity. A nested/new hurt invalidates the preceding receipt. */
public final class DamagePipelineState {
    private long sequence;
    private Object source;
    private int tick;
    private long recordedSequence;
    private double fraction;
    private java.lang.ref.WeakReference<Object> customFirst, customSecond;
    private int customTick;
    private float customNormalMultiplier;
    private boolean customBlock;
    private long nextMobileWeaknessTick;

    boolean tryStartMobileWeakness(long now) {
        if (now < nextMobileWeaknessTick) return false;
        nextMobileWeaknessTick = now + 90;
        return true;
    }

    public float beginCustomShield(Object candidate, int tick) {
        boolean normal = customFirst != null && customFirst.get() == candidate;
        customBlock = consumeCustomShield(candidate, tick);
        return !customBlock ? 1 : normal ? customNormalMultiplier : .5F;
    }

    public boolean customBlock() { return customBlock; }
    public void end() { clear(); customBlock = false; }
    public void begin() { sequence++; clear(); }
    public void clear() { source = null; fraction = 0; }
    /** Native TaCZ has two hurt calls after one successful custom percentage block. */
    public void markCustomShield(Object first, Object second, int tick, float normalMultiplier) {
        customFirst = new java.lang.ref.WeakReference<>(first);
        customSecond = new java.lang.ref.WeakReference<>(second);
        customTick = tick;
        customNormalMultiplier = normalMultiplier;
    }
    public boolean consumeCustomShield(Object candidate, int tick) {
        if (tick != customTick) { customFirst = null; customSecond = null; return false; }
        if (candidate == null) return false;
        if (customFirst != null && customFirst.get() == candidate) { customFirst = null; return true; }
        if (customSecond != null && customSecond.get() == candidate) { customSecond = null; return true; }
        return false;
    }
    public void record(Object source, int tick, double fraction) {
        this.source = source; this.tick = tick; this.fraction = fraction; recordedSequence = sequence;
    }
    public double consume(Object source, int tick, double fallback) {
        double result = this.source != null && this.source == source && this.tick == tick && recordedSequence == sequence ? fraction : fallback;
        clear();
        return result;
    }
    public interface Access {
        DamagePipelineState hostileHumansUnified$damageState();
    }
}

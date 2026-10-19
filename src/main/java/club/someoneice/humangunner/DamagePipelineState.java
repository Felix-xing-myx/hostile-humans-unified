package club.someoneice.humangunner;

/** One bounded receipt per entity. A nested/new hurt invalidates the preceding receipt. */
public final class DamagePipelineState {
    private long sequence;
    private Object source;
    private int tick;
    private long recordedSequence;
    private double fraction;
    public void begin() { sequence++; clear(); }
    public void clear() { source = null; fraction = 0; }
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

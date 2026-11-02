package dev.felix.hostilehumans.perception;

/** A rolling negative body observation may span several granted batches. */
public final class NegativeObservationSweep {
    private final java.util.BitSet tested = new java.util.BitSet();
    private int size = -1;
    private long signature;
    public void reset() { tested.clear(); size = -1; }
    public boolean negative(int sampleCount, int start, int count) { return negative(sampleCount,start,count,0); }
    public boolean negative(int sampleCount, int start, int count, long sampleSignature) {
        if (sampleCount < 1) return true;
        if (size != sampleCount || signature != sampleSignature) { tested.clear(); size = sampleCount; signature = sampleSignature; }
        for (int i = 0; i < count; i++) tested.set(Math.floorMod(start + i, sampleCount));
        if (tested.cardinality() < sampleCount) return false;
        tested.clear();
        return true;
    }
}

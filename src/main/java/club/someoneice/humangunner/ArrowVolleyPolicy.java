package club.someoneice.humangunner;

/** The vanilla multishot half-angle is ten degrees. Enchantment adds two, never multiplies. */
final class ArrowVolleyPolicy {
    private ArrowVolleyPolicy() { }
    static float[] angles(boolean triple, boolean multishot, int arrows) {
        if (arrows <= 0) return new float[0];
        if (triple && arrows < 3) return new float[] {0};
        if (triple) return multishot ? new float[] {0, -4, 4, -8, 8} : new float[] {0, -4, 4};
        return multishot ? new float[] {0, -10, 10} : new float[] {0};
    }
    static int cost(boolean triple, int arrows) { return triple && arrows >= 3 ? 3 : arrows > 0 ? 1 : 0; }
}

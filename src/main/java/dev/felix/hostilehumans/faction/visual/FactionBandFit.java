package dev.felix.hostilehumans.faction.visual;

/** Limb-local fit shared by cloth and trim; chest and leggings are independent armor shells. */
final class FactionBandFit {
    record Fit(float outer, float inner, float depth) {}
    private FactionBandFit() {}
    static Fit arm(boolean slim, boolean armored, float layer) {
        // Bare arms include the vanilla skin sleeve (0.25px); armor uses its 1px dilation.
        float shell = (armored ? 1.12F : .37F) + layer;
        return new Fit(-(slim ? 2 : 3) - shell, 1 + shell, 2 + shell);
    }
    static Fit leg(boolean leggings, boolean boots, float layer) {
        float half = 2 + (boots ? 1.12F : leggings ? .62F : .37F) + layer;
        return new Fit(-half, half, half);
    }
}

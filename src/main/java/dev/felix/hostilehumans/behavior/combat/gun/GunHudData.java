package dev.felix.hostilehumans.behavior.combat.gun;

import net.minecraft.resources.ResourceLocation;

/** Read-only weapon facts, supplied by the same backend that owns ammunition. */
public record GunHudData(String backend, ResourceLocation gun, int loaded, int capacity, String mode, boolean energy) {
    public static final GunHudData EMPTY = new GunHudData("", new ResourceLocation("minecraft", "air"), 0, 0, "", false);
    public GunHudData {
        if (backend == null || backend.length() > 16 || gun == null || loaded < 0 || capacity < 0 || mode == null || mode.length() > 32)
            throw new IllegalArgumentException("Invalid weapon HUD data");
    }
    public boolean present() { return !backend.isEmpty(); }
}

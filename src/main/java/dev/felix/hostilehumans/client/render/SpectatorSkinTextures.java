package dev.felix.hostilehumans.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Owned, bounded derived skins for the isolated spectator model. Original textures are never edited. */
@Mod.EventBusSubscriber(modid = "hostile_humans_unified", value = Dist.CLIENT)
public final class SpectatorSkinTextures {
    private static final int MAX_ENTRIES = 40;
    private static final long MAX_PIXELS = 4L * 1024 * 1024;
    private static final Map<ResourceLocation, Entry> CACHE = new LinkedHashMap<>(16, .75F, true);
    private static long pixels;

    private record Entry(ResourceLocation texture, long pixels) {}

    private SpectatorSkinTextures() {}

    public static ResourceLocation resolve(ResourceLocation original) {
        Entry cached = CACHE.get(original);
        if (cached != null) return cached.texture();
        Entry created = create(original);
        CACHE.put(original, created);
        pixels += created.pixels();
        while (CACHE.size() > MAX_ENTRIES || pixels > MAX_PIXELS) {
            var iterator = CACHE.entrySet().iterator();
            Entry oldest = iterator.next().getValue();
            iterator.remove();
            release(oldest);
        }
        return created.texture();
    }

    private static Entry create(ResourceLocation original) {
        NativeImage image = null;
        DynamicTexture texture = null;
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(original)
                    .orElseThrow(() -> new IOException("Missing skin " + original));
            try (var input = resource.open()) { image = NativeImage.read(input); }
            int width = image.getWidth(), height = image.getHeight();
            if (width != height || width < 64 || width > 1024 || width % 64 != 0)
                throw new IOException("Expected a square 64-grid skin up to 1024px: " + width + "x" + height);
            double factor = width / 64.0;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int color = image.getPixelRGBA(x, y);
                int masked = SpectatorArmMask.pixel(color, (x + .5) / factor, (y + .5) / factor);
                if (masked != color) image.setPixelRGBA(x, y, masked);
            }
            texture = new DynamicTexture(image);
            image = null; // DynamicTexture now owns and closes this NativeImage.
            ResourceLocation generated = Minecraft.getInstance().getTextureManager()
                    .register("hostile_humans_spectator_skin", texture);
            texture = null; // The texture manager owns it after registration.
            return new Entry(generated, (long) width * height);
        } catch (IOException | RuntimeException | LinkageError failure) {
            ClientAnimationCompatibility.warnOnce("Spectator skin " + original,
                    "Cannot create arm fade; using original skin until resource refresh", failure);
            // Failed lookups are bounded/cacheable too; do not retry file IO every frame.
            return new Entry(original, 0);
        } finally {
            if (texture != null) texture.close();
            if (image != null) image.close();
        }
    }

    private static void release(Entry entry) {
        pixels -= entry.pixels();
        if (entry.pixels() > 0) Minecraft.getInstance().getTextureManager().release(entry.texture());
    }

    public static void clear() {
        for (Entry entry : CACHE.values()) release(entry);
        CACHE.clear();
        pixels = 0;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}

package dev.felix.hostilehumans.client.ui.framework;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Resource-pack palette; parse once per reload/open, never during widget painting. */
public final class UiThemes {
    private static final ResourceLocation PALETTE =
            new ResourceLocation("hostile_humans_unified", "gui/theme.json");
    private static UiTheme base = UiTheme.DEFAULT;
    private static Map<String, UiTheme> themes = Map.of();
    private static boolean loaded, shadowAvailable, surfaceAvailable;
    private static Map<String, ResourceLocation> emblems = Map.of();
    private static UiTheme[] colors = {UiTheme.DEFAULT};

    private UiThemes() {}

    public static void reload() {
        shadowAvailable =
                Minecraft.getInstance()
                        .getResourceManager()
                        .getResource(
                                new ResourceLocation(
                                        "hostile_humans_unified", "textures/gui/panel/shadow.png"))
                        .isPresent();
        surfaceAvailable =
                Minecraft.getInstance()
                        .getResourceManager()
                        .getResource(
                                new ResourceLocation(
                                        "hostile_humans_unified", "textures/gui/panel/surface.png"))
                        .isPresent();
        base = UiTheme.DEFAULT;
        Map<String, Integer> colors =
                new HashMap<>(
                        Map.of(
                                "hostile_humans_unified:crimson",
                                0xFFDC8A76,
                                "hostile_humans_unified:azure",
                                0xFF80B3DD,
                                "hostile_humans_unified:verdant",
                                0xFF91C99D,
                                "hostile_humans_unified:ashen",
                                0xFFC0B3CF));
        Map<String, ResourceLocation> icons = new HashMap<>();
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(PALETTE);
            if (resource.isPresent())
                try (var reader =
                        new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    base =
                            new UiTheme(
                                    color(json, "top", base.top()),
                                    color(json, "bottom", base.bottom()),
                                    color(json, "inset", base.inset()),
                                    color(json, "surface", base.surface()),
                                    color(json, "edge", base.edge()),
                                    color(json, "text", base.text()),
                                    color(json, "muted", base.muted()),
                                    color(json, "disabled", base.disabled()),
                                    color(json, "danger", base.danger()),
                                    base.accent());
                    if (json.has("emblems"))
                        for (var entry : json.getAsJsonObject("emblems").entrySet())
                            try {
                                ResourceLocation icon =
                                        new ResourceLocation(entry.getValue().getAsString());
                                if (Minecraft.getInstance()
                                        .getResourceManager()
                                        .getResource(icon)
                                        .isPresent()) icons.put(entry.getKey(), icon);
                            } catch (RuntimeException ignored) {
                            }
                    if (json.has("factions"))
                        for (var entry : json.getAsJsonObject("factions").entrySet())
                            try {
                                colors.put(entry.getKey(), parse(entry.getValue().getAsString()));
                            } catch (RuntimeException ignored) {
                            }
                }
        } catch (Exception ignored) {
            base = UiTheme.DEFAULT;
        }
        Map<String, UiTheme> parsed = new HashMap<>();
        for (var entry : colors.entrySet())
            parsed.put(entry.getKey(), base.withAccent(entry.getValue()));
        themes = Map.copyOf(parsed);
        emblems = Map.copyOf(icons);
        UiTheme[] palette = new UiTheme[parsed.size() + 2];
        palette[0] = base;
        palette[1] = base.withAccent(0xFF93BAC8);
        int i = 2;
        for (UiTheme theme : parsed.values()) palette[i++] = theme;
        UiThemes.colors = palette;
        loaded = true;
    }

    private static int color(JsonObject json, String key, int fallback) {
        try {
            return json.has(key) ? parse(json.get(key).getAsString()) : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int parse(String value) {
        String hex = value.replace("#", "");
        return (int) (Long.parseLong(hex, 16) | (hex.length() == 6 ? 0xFF000000L : 0));
    }

    public static boolean surfaceAvailable() {
        if (!loaded) reload();
        return surfaceAvailable;
    }

    public static boolean shadowAvailable() {
        if (!loaded) reload();
        return shadowAvailable;
    }

    public static ResourceLocation emblem(String faction) {
        if (!loaded) reload();
        return emblems.get(faction);
    }

    public static UiTheme colored(int accent) {
        if (!loaded) reload();
        for (UiTheme theme : colors) if (theme.accent() == accent) return theme;
        return base;
    }

    public static UiTheme faction(String faction) {
        if (!loaded) reload();
        return themes.getOrDefault(faction, base);
    }
}

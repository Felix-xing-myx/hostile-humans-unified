package dev.felix.hostilehumans.client.ui.framework;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Resource-pack palette. Parsed once per reload/open, never during widget painting.
 *
 * <p>Faction identity is data, not code: {@code theme.json} supplies the shared base tokens, a per
 * faction accent and emblem, and the header tint ratio. Widgets only ever ask for a theme.
 */
public final class UiThemes {
    private static final ResourceLocation PALETTE =
            new ResourceLocation("hostile_humans_unified", "gui/theme.json");

    /** Group markers shared by every panel; 16x16 white masks, tinted by the drawing layer. */
    public static final ResourceLocation MARKER_MOVE = marker("move"),
            MARKER_GUARD = marker("guard"),
            MARKER_RANGE = marker("range"),
            MARKER_FIGHT = marker("fight"),
            MARKER_PREFER = marker("prefer");
    private static final String[] FACTION_IDS = {"crimson", "azure", "verdant", "ashen"};
    private static final int[] FACTION_COLORS = {
        0xFFDC8A76, 0xFF80B3DD, 0xFF91C99D, 0xFFC0B3CF
    };

    private static UiTheme base = UiTheme.DEFAULT;
    private static Map<String, UiTheme> themes = Map.of();
    private static Map<String, ResourceLocation> emblems = Map.of();
    private static UiTheme[] colors = {UiTheme.DEFAULT};
    private static boolean loaded, surfaceAvailable, animation = true;

    private UiThemes() {}

    public static void reload() {
        var resources = Minecraft.getInstance().getResourceManager();
        surfaceAvailable =
                resources
                        .getResource(
                                new ResourceLocation(
                                        "hostile_humans_unified",
                                        "textures/gui/panel/surface.png"))
                        .isPresent();
        base = UiTheme.DEFAULT;
        animation = true;
        Map<String, Integer> factionColors = new HashMap<>();
        Map<String, Float> factionTints = new HashMap<>();
        Map<String, ResourceLocation> icons = new HashMap<>();
        for (int i = 0; i < FACTION_IDS.length; i++)
            factionColors.put("hostile_humans_unified:" + FACTION_IDS[i], FACTION_COLORS[i]);
        try {
            var resource = resources.getResource(PALETTE);
            if (resource.isPresent())
                try (var reader =
                        new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    base =
                            base.withBase(
                                    number(json, "headTint", .09F),
                                    color(json, "top", base.top()),
                                    color(json, "mid", base.mid()),
                                    color(json, "bottom", base.bottom()),
                                    color(json, "inset", base.inset()),
                                    color(json, "surface", base.surface()),
                                    color(json, "edge", base.edge()),
                                    color(json, "text", base.text()),
                                    color(json, "muted", base.muted()),
                                    color(json, "disabled", base.disabled()),
                                    color(json, "danger", base.danger()));
                    if (json.has("animation"))
                        animation = json.get("animation").getAsBoolean();
                    if (json.has("emblems"))
                        for (var entry : json.getAsJsonObject("emblems").entrySet())
                            add(icons, entry.getKey(), entry.getValue().getAsString());
                    if (json.has("factions"))
                        for (var entry : json.getAsJsonObject("factions").entrySet()) {
                            JsonObject faction = entry.getValue().getAsJsonObject();
                            try {
                                factionColors.put(
                                        entry.getKey(), parse(faction.get("accent").getAsString()));
                            } catch (RuntimeException ignored) {
                            }
                            if (faction.has("headTint"))
                                factionTints.put(
                                        entry.getKey(), faction.get("headTint").getAsFloat());
                            if (faction.has("emblem"))
                                add(icons, entry.getKey(), faction.get("emblem").getAsString());
                        }
                }
        } catch (Exception ignored) {
            base = UiTheme.DEFAULT;
        }
        Map<String, UiTheme> parsed = new HashMap<>();
        for (var entry : factionColors.entrySet()) {
            parsed.put(
                    entry.getKey(),
                    base.withAccentAndTint(
                            entry.getValue(),
                            factionTints.getOrDefault(entry.getKey(), base.headTint())));
        }
        themes = Map.copyOf(parsed);
        emblems = Map.copyOf(icons);
        UiTheme[] palette = new UiTheme[parsed.size() + 2];
        palette[0] = base;
        palette[1] = base.withAccent(0xFF93BAC8);
        int i = 2;
        for (UiTheme theme : parsed.values()) palette[i++] = theme;
        colors = palette;
        loaded = true;
    }

    private static void add(Map<String, ResourceLocation> icons, String key, String path) {
        try {
            ResourceLocation icon = new ResourceLocation(path);
            if (Minecraft.getInstance().getResourceManager().getResource(icon).isPresent())
                icons.put(key, icon);
        } catch (RuntimeException ignored) {
        }
    }

    private static int color(JsonObject json, String key, int fallback) {
        try {
            return json.has(key) ? parse(json.get(key).getAsString()) : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float number(JsonObject json, String key, float fallback) {
        try {
            return json.has(key) ? Math.max(0F, Math.min(1F, json.get(key).getAsFloat())) : fallback;
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

    /** Honours the pack-level motion switch; decorations stop animating when it is disabled. */
    public static boolean animation() {
        if (!loaded) reload();
        return animation;
    }

    public static ResourceLocation emblem(String faction) {
        if (!loaded) reload();
        return emblems.get(faction);
    }

    public static ResourceLocation marker(String name) {
        return new ResourceLocation("hostile_humans_unified", "textures/gui/icon/" + name + ".png");
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
package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.sound.SoundManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/** Client-only companion to the optional TaCZ integration. */
final class TaczReloadSoundClient {
    private static final Map<ReloadCueKey, List<ReloadCue>> RELOAD_CUES = new HashMap<>();
    private static final List<PendingReload> PENDING_RELOADS = new ArrayList<>();
    private static Field displayField;
    private static Method animationLocationMethod;

    private TaczReloadSoundClient() {}

    static void register() {
        MinecraftForge.EVENT_BUS.addListener(TaczReloadSoundClient::onReload);
        MinecraftForge.EVENT_BUS.addListener(TaczReloadSoundClient::onClientTick);
    }

    private static void onReload(GunReloadEvent event) {
        if (!event.getLogicalSide().isClient() || !(event.getEntity() instanceof Human human)) return;
        ItemStack weapon = event.getGunItemStack();
        IGun gun = IGun.getIGunOrNull(weapon);
        if (gun == null) return;
        ResourceLocation gunId = gun.getGunId(weapon);
        if (gunId == null) return;
        var index = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
        if (index == null) return;
        boolean empty = index.getGunData().getBolt() == Bolt.OPEN_BOLT
                ? gun.getCurrentAmmoCount(weapon) <= 0
                : !gun.hasBulletInBarrel(weapon);
        TimelessAPI.getGunDisplay(weapon).ifPresent(display -> {
            String sound = empty ? SoundManager.RELOAD_EMPTY_SOUND
                    : SoundManager.RELOAD_TACTICAL_SOUND;
            ResourceLocation configuredSound = display.getSounds(sound);
            if (hasSoundResource(configuredSound)) {
                // Preserve the pack's authored one-shot when it exists.
                SoundPlayManager.playAnimationSound(human, configuredSound,
                        1.0F, 1.0F, GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
                return;
            }

            // Many current TaCZ gun packs no longer ship a single
            // `*_reload_empty.ogg` / `*_reload_tactical.ogg` file. Instead,
            // their reload animation contains time-stamped sound_effects
            // entries (mag-out, mag-in, bolt, etc.). TaCZ plays those while
            // rendering its own gun model, but a Human's held gun is rendered
            // through Minecraft's item layer, so those cues otherwise never
            // run. Replay that exact animation sound track for the NPC.
            ResourceLocation animationFile = getAnimationFile(display);
            if (animationFile == null) return;
            ReloadCueKey key = new ReloadCueKey(animationFile, empty);
            List<ReloadCue> cues = RELOAD_CUES.computeIfAbsent(key,
                    ignored -> readReloadCues(animationFile, empty));
            Minecraft minecraft = Minecraft.getInstance();
            if (cues.isEmpty() || minecraft.level == null) return;
            PENDING_RELOADS.add(new PendingReload(human, minecraft.level.getGameTime(), cues));
        });
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING_RELOADS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            PENDING_RELOADS.clear();
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        Iterator<PendingReload> iterator = PENDING_RELOADS.iterator();
        while (iterator.hasNext()) {
            PendingReload pending = iterator.next();
            Human human = pending.human;
            if (!human.isAlive() || human.level() != minecraft.level) {
                iterator.remove();
                continue;
            }
            double elapsedSeconds = Math.max(0L, gameTime - pending.startGameTime) / 20.0D;
            while (pending.nextCue < pending.cues.size()
                    && pending.cues.get(pending.nextCue).timeSeconds <= elapsedSeconds + 0.05D) {
                ReloadCue cue = pending.cues.get(pending.nextCue++);
                SoundPlayManager.playAnimationSound(human, cue.sound,
                        1.0F, 1.0F, GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
            }
            if (pending.nextCue >= pending.cues.size()) iterator.remove();
        }
    }

    private static ResourceLocation getAnimationFile(Object displayInstance) {
        try {
            if (displayField == null) {
                displayField = displayInstance.getClass().getDeclaredField("display");
                displayField.setAccessible(true);
            }
            Object display = displayField.get(displayInstance);
            if (display == null) return null;
            if (animationLocationMethod == null) {
                animationLocationMethod = display.getClass().getMethod("getAnimationLocation");
            }
            Object value = animationLocationMethod.invoke(display);
            if (!(value instanceof ResourceLocation animationId)) return null;
            String path = animationId.getPath();
            if (path.startsWith("animations/")) {
                if (!path.endsWith(".animation.json")) path += ".animation.json";
            } else {
                path = "animations/" + path + ".animation.json";
            }
            return new ResourceLocation(animationId.getNamespace(), path);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static List<ReloadCue> readReloadCues(ResourceLocation animationFile, boolean empty) {
        Minecraft minecraft = Minecraft.getInstance();
        var resource = minecraft.getResourceManager().getResource(animationFile).orElse(null);
        if (resource == null) return List.of();
        String[] animationNames = empty
                ? new String[]{"reload_empty", "reload"}
                : new String[]{"reload_tactical", "reload"};
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject animations = root.getAsJsonObject("animations");
            if (animations == null) return List.of();
            JsonObject animation = null;
            for (String name : animationNames) {
                if (animations.has(name) && animations.get(name).isJsonObject()) {
                    animation = animations.getAsJsonObject(name);
                    break;
                }
            }
            if (animation == null || !animation.has("sound_effects")
                    || !animation.get("sound_effects").isJsonObject()) return List.of();

            List<ReloadCue> cues = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry
                    : animation.getAsJsonObject("sound_effects").entrySet()) {
                double timeSeconds;
                try {
                    timeSeconds = Double.parseDouble(entry.getKey());
                } catch (NumberFormatException ignored) {
                    continue;
                }
                JsonElement value = entry.getValue();
                if (value.isJsonObject()) value = value.getAsJsonObject().get("effect");
                if (value == null || value.isJsonNull()) continue;
                if (value.isJsonArray()) {
                    for (JsonElement effect : value.getAsJsonArray()) addCue(cues, timeSeconds, effect);
                } else {
                    addCue(cues, timeSeconds, value);
                }
            }
            cues.sort(Comparator.comparingDouble(ReloadCue::timeSeconds));
            return List.copyOf(cues);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static void addCue(List<ReloadCue> cues, double timeSeconds, JsonElement effect) {
        if (!effect.isJsonPrimitive() || !effect.getAsJsonPrimitive().isString()) return;
        ResourceLocation sound = ResourceLocation.tryParse(effect.getAsString());
        if (hasSoundResource(sound)) cues.add(new ReloadCue(timeSeconds, sound));
    }

    private static boolean hasSoundResource(ResourceLocation sound) {
        if (sound == null) return false;
        ResourceLocation file = new ResourceLocation(sound.getNamespace(),
                "tacz_sounds/" + sound.getPath() + ".ogg");
        return Minecraft.getInstance().getResourceManager().getResource(file).isPresent();
    }

    private record ReloadCueKey(ResourceLocation animationFile, boolean empty) {}
    private record ReloadCue(double timeSeconds, ResourceLocation sound) {}

    private static final class PendingReload {
        private final Human human;
        private final long startGameTime;
        private final List<ReloadCue> cues;
        private int nextCue;

        private PendingReload(Human human, long startGameTime, List<ReloadCue> cues) {
            this.human = human;
            this.startGameTime = startGameTime;
            this.cues = cues;
        }
    }
}

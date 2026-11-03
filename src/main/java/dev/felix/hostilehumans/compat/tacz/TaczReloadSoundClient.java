package dev.felix.hostilehumans.compat.tacz;

import dev.felix.hostilehumans.entity.Human;


import dev.felix.hostilehumans.client.render.ClientAnimationCompatibility;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.sound.SoundManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
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
public final class TaczReloadSoundClient {
    private static final Map<ReloadCueKey, List<ReloadCue>> RELOAD_CUES = new HashMap<>();
    private static final List<PendingReload> PENDING_RELOADS = new ArrayList<>();

    private TaczReloadSoundClient() {}

    static void register() {
        TaczMonitorInput.register();
        MinecraftForge.EVENT_BUS.addListener(TaczReloadSoundClient::onReload);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) -> {
            if (event.getEntity() instanceof Human human)
                dev.felix.hostilehumans.client.render.tacz.NativeReloadAudio.tickBolt(human);
        });
        MinecraftForge.EVENT_BUS.addListener(TaczReloadSoundClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> reset());
        ClientAnimationCompatibility.registerReloadSoundReset(TaczReloadSoundClient::reset);
    }

    private static void reset() {
        dev.felix.hostilehumans.client.render.tacz.NativeReloadAudio.reset();
        for (var pending : PENDING_RELOADS) stop(pending);
        PENDING_RELOADS.clear();
        RELOAD_CUES.clear();
    }

    private static void onReload(GunReloadEvent event) {
        if (!event.getLogicalSide().isClient() || !(event.getEntity() instanceof Human human)) return;
        PENDING_RELOADS.removeIf(pending -> {
            if(pending.human!=human) return false;
            stop(pending);return true;
        });
        if (dev.felix.hostilehumans.client.render.tacz.NativeReloadAudio.start(human, event.getGunItemStack())) return;
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
                Minecraft minecraft=Minecraft.getInstance();
                if(minecraft.level==null) return;
                PendingReload pending=new PendingReload(human,minecraft.level.getGameTime(),List.of(),gunId);
                pending.oneShot = configuredSound;
                PENDING_RELOADS.add(pending);
                return;
            }

            // Many current TaCZ gun packs no longer ship a single
            // `*_reload_empty.ogg` / `*_reload_tactical.ogg` file. Instead,
            // their reload animation contains time-stamped sound_effects
            // entries (mag-out, mag-in, bolt, etc.). TaCZ plays those while
            // rendering its own gun model, but a Human's held gun is rendered
            // through Minecraft's item layer, so those cues otherwise never
            // run. Replay that exact animation sound track for the NPC.
            ResourceLocation animationFile = getAnimationFile(weapon);
            if (animationFile == null) return;
            ReloadCueKey key = new ReloadCueKey(animationFile, empty);
            List<ReloadCue> cues = RELOAD_CUES.computeIfAbsent(key,
                    ignored -> readReloadCues(animationFile, empty));
            Minecraft minecraft = Minecraft.getInstance();
            if (cues.isEmpty() || minecraft.level == null) return;
            PENDING_RELOADS.add(new PendingReload(human, minecraft.level.getGameTime(), cues,gunId));
        });
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) dev.felix.hostilehumans.client.render.tacz.NativeReloadAudio.tick();
        if (event.phase != TickEvent.Phase.END || PENDING_RELOADS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) return;
        if (minecraft.level == null) {
            for(var pending:PENDING_RELOADS) stop(pending);
            PENDING_RELOADS.clear();
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        Iterator<PendingReload> iterator = PENDING_RELOADS.iterator();
        while (iterator.hasNext()) {
            PendingReload pending = iterator.next();
            Human human = pending.human;
            if (!human.isAlive() || human.isRemoved() || human.level() != minecraft.level
                    ||gameTime-pending.startGameTime>3&&human.getTaczReloadVisual().isEmpty()) {
                stop(pending);iterator.remove();continue;
            }
            var timeline = human.getTaczReloadVisual();
            if (timeline.isEmpty() || !timeline.contains("WorkRate")
                    || !pending.gunId.toString().equals(timeline.getString("Gun"))
                    || !pending.initialized && timeline.getBoolean("Complete")) {
                if (gameTime-pending.startGameTime>10) { stop(pending); iterator.remove(); }
                continue;
            }
            if (pending.initialized && (pending.session != timeline.getLong("Session") || pending.timelineStart != timeline.getLong("Start"))) {
                stop(pending); iterator.remove(); continue;
            }
            pending.initialized = true; pending.session = timeline.getLong("Session"); pending.timelineStart = timeline.getLong("Start");
            double rate = timeline.getDouble("WorkRate"), work = timeline.getDouble("Work");
            if (!Double.isFinite(rate) || rate <= 0 || !Double.isFinite(work) || work < 0) continue;
            // Only interpolate the server checkpoint. Client faction/default config never decides timing.
            double elapsedSeconds = dev.felix.hostilehumans.client.render.tacz.TaczPresentationResources.workSeconds(human, 0);
            if (pending.oneShot != null) {
                play(pending, pending.oneShot);
                pending.oneShot = null;
            }
            while (pending.nextCue < pending.cues.size()
                    && pending.cues.get(pending.nextCue).timeSeconds <= elapsedSeconds + 0.05D) {
                ReloadCue cue = pending.cues.get(pending.nextCue++);
                play(pending,cue.sound);
            }
            if (timeline.getBoolean("Complete")) { pending.sounds.clear(); iterator.remove(); }
        }
    }

    private static void play(PendingReload pending,ResourceLocation sound) {
        var owned = ClientAnimationCompatibility.playTaczReloadSound(pending.human, sound,
                1F, 1F, GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
        if (owned != null) pending.sounds.add(owned);
    }
    private static void stop(PendingReload pending) {
        for(var sound:pending.sounds) Minecraft.getInstance().getSoundManager().stop(sound);
        pending.sounds.clear();
    }

    private static ResourceLocation getAnimationFile(ItemStack weapon) {
        try {
            return resolveAnimationFile(weapon);
        } catch (RuntimeException | LinkageError error) {
            ClientAnimationCompatibility.warnOnce("TaCZ reload resources",
                    "Cannot resolve public gun display data; retaining native sound", error);
            return null;
        }
    }

    private static ResourceLocation resolveAnimationFile(ItemStack weapon) {
        IGun gun = IGun.getIGunOrNull(weapon);
        if (gun == null) return null;
        // Public pack data, including the actual default display and custom display fallback.
        // Older TaCZ instances do not retain a private GunDisplayInstance.display field.
        var index = TimelessAPI.getCommonGunIndex(gun.getGunId(weapon)).orElse(null);
        if (index == null || index.getPojo() == null) return null;
        ResourceLocation requested = gun.getGunDisplayId(weapon);
        var assets = ClientAssetsManager.INSTANCE;
        var display = requested == null || DefaultAssets.DEFAULT_GUN_DISPLAY_ID.equals(requested)
                ? null : assets.getGunDisplay(requested);
        if (display == null) display = assets.getGunDisplay(index.getPojo().getDisplay());
        ResourceLocation animationId = display == null ? null : display.getAnimationLocation();
        if (animationId == null) return null;
        String path = animationId.getPath();
        if (path.startsWith("animations/")) {
            if (!path.endsWith(".animation.json")) path += ".animation.json";
        } else {
            path = "animations/" + path + ".animation.json";
        }
        return new ResourceLocation(animationId.getNamespace(), path);
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
        } catch (Exception exception) {
            ClientAnimationCompatibility.warnOnce("TaCZ reload sounds",
                    "Cannot parse reload cues from " + animationFile, exception);
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
        private boolean initialized;
        private long session, timelineStart;
        private final ResourceLocation gunId;
        private ResourceLocation oneShot;
        private final List<net.minecraft.client.resources.sounds.SoundInstance> sounds=new ArrayList<>();

        private PendingReload(Human human, long startGameTime, List<ReloadCue> cues,ResourceLocation gunId) {
            this.human = human;
            this.startGameTime = startGameTime;
            this.cues = cues;
            this.gunId=gunId;
        }
    }
}

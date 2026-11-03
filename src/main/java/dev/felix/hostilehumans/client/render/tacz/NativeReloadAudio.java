package dev.felix.hostilehumans.client.render.tacz;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.sound.SoundManager;
import dev.felix.hostilehumans.client.render.ClientAnimationCompatibility;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import dev.felix.hostilehumans.entity.Human;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Invoked only by TaczReloadSoundClient: a shared plan, never an additional event/audio owner. */
public final class NativeReloadAudio {
    private static final Map<ResourceLocation, JsonObject> FILES = new HashMap<>();
    private static final Map<Human, BoltAudio> BOLTS = new java.util.WeakHashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    private NativeReloadAudio() {}

    public static boolean start(Human human, ItemStack weapon) {
        cancel(human);
        try {
            var display = TaczPresentationResources.display(weapon);
            IGun gun = IGun.getIGunOrNull(weapon);
            if (gun == null || display == null || display.getAnimationLocation() == null
                    || !TaczPresentationResources.canSample(display)) return false;
            PENDING.add(new Pending(human, gun.getGunId(weapon), display, human.level().getGameTime()));
            return true;
        } catch (RuntimeException | LinkageError failure) {
            ClientAnimationCompatibility.warnOnce("TaCZ reload plan audio", "Native audio plan unavailable; retaining legacy audio", failure);
            return false;
        }
    }
    public static void reset() {
        for (var pending : PENDING) stop(pending);
        PENDING.clear(); BOLTS.clear(); FILES.clear();
    }
    public static void tickBolt(Human human) {
        if (!human.level().isClientSide || !human.isAlive()) return;
        ItemStack weapon = human.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(weapon);
        if (gun == null) { BOLTS.remove(human); return; }
        boolean active = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(human).getSynIsBolting();
        BoltAudio audio = BOLTS.get(human);
        var gunId = gun.getGunId(weapon);
        if (audio != null && !audio.gunId.equals(gunId)) {
            BOLTS.remove(human);
            audio = null;
        }
        if (audio == null && active) {
            audio = new BoltAudio(gunId, human.level().getGameTime(), boltCues(weapon));
            BOLTS.put(human, audio);
        }
        if (audio == null) return;
        double elapsed = (human.level().getGameTime() - audio.start) / 20D;
        while (audio.next < audio.cues.size() && audio.cues.get(audio.next).time <= elapsed + .025) {
            ClientAnimationCompatibility.playTaczReloadSound(human, audio.cues.get(audio.next++).sound,
                    1F, 1F, GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
        }
        if (!active && audio.next == audio.cues.size()) BOLTS.remove(human);
    }

    private static List<Cue> boltCues(ItemStack weapon) {
        try {
            var display = TaczPresentationResources.display(weapon);
            if (display == null) return List.of();
            var animation = display.getAnimationLocation();
            List<Cue> cues = new ArrayList<>();
            if (animation != null) {
                var clip = animations(animation).get("bolt");
                if (clip != null && clip.isJsonObject()) readCues(cues, clip.getAsJsonObject(), 0);
            }
            if (cues.isEmpty()) {
                var sound = display.getSounds(SoundManager.BOLT_SOUND);
                if (hasSoundResource(sound)) cues.add(new Cue(0, sound));
            }
            cues.sort(Comparator.comparingDouble(Cue::time));
            return List.copyOf(cues);
        } catch (RuntimeException | LinkageError failure) {
            ClientAnimationCompatibility.warnOnce("TaCZ bolt audio", "Cannot resolve authored bolt cues", failure);
            return List.of();
        }
    }

    private static JsonObject animations(ResourceLocation id) {
        if (FILES.size() >= 256 && !FILES.containsKey(id)) FILES.clear();
        return FILES.computeIfAbsent(id, NativeReloadAudio::readAnimations);
    }

    private static void readCues(List<Cue> cues, JsonObject clip, double offset) {
        if (!clip.has("sound_effects") || !clip.get("sound_effects").isJsonObject()) return;
        for (var entry : clip.getAsJsonObject("sound_effects").entrySet()) {
            double time;
            try { time = offset + Double.parseDouble(entry.getKey()); }
            catch (NumberFormatException ignored) { continue; }
            addEffect(cues, time, entry.getValue());
        }
    }

    private static void addEffect(List<Cue> cues, double time, JsonElement effect) {
        if (effect == null) return;
        if (effect.isJsonObject()) addEffect(cues, time, effect.getAsJsonObject().get("effect"));
        else if (effect.isJsonArray()) for (var value : effect.getAsJsonArray()) addEffect(cues, time, value);
        else addCue(cues, time, effect);
    }

    private static void cancel(Human human) {
        PENDING.removeIf(pending -> { if (pending.human != human) return false; stop(pending); return true; });
    }
    public static void tick() {
        var client = Minecraft.getInstance();
        if (client.level == null) { reset(); return; }
        if (client.isPaused()) return;
        Iterator<Pending> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            Pending pending = iterator.next();
            Human human = pending.human;
            CompoundTag timeline = human.getTaczReloadVisual();
            if (human.level() != client.level || !human.isAlive() || human.isRemoved()
                    || client.level.getGameTime() - pending.received > 3 && timeline.isEmpty()) {
                stop(pending); iterator.remove(); continue;
            }
            if (!timeline.contains("Empty") || !timeline.contains("WorkRate")
                    || !pending.gunId.toString().equals(timeline.getString("Gun"))
                    || !pending.initialized && timeline.getBoolean("Complete")) {
                if (client.level.getGameTime() - pending.received > 10) { stop(pending); iterator.remove(); }
                continue;
            }
            if (pending.initialized && (pending.start != timeline.getLong("Start") || pending.session != timeline.getLong("Session"))) {
                stop(pending); iterator.remove(); continue;
            }
            double work = TaczPresentationResources.workSeconds(human, 0);
            if (!pending.initialized || pending.start != timeline.getLong("Start") || pending.session != timeline.getLong("Session")) {
                stop(pending); initialize(pending, timeline);
                pending.start = timeline.getLong("Start"); pending.session = timeline.getLong("Session"); pending.initialized = true;
            }
            var phase = TaczPresentationResources.phase(timeline);
            if (pending.plan != null && pending.plan.cyclic() && phase != null && phase.valid()
                    && pending.phaseIndex != phase.index() && (!phase.finishing() || work >= phase.start())) {
                pending.phaseIndex = phase.index();
                double boundary = pending.plan.phasePresentationStart(phase);
                // Early finishing/cancel-with-ending skips abandoned feeding cues, it cannot replay them.
                while (pending.next < pending.cues.size() && pending.cues.get(pending.next).time < boundary) pending.next++;
            }
            double position = position(pending, work, timeline);
            if (!Double.isFinite(position)) continue;
            if (!pending.positioned) {
                pending.positioned = true;
                // This is intentionally based on real admitted game ticks, not accelerated native work.
                // It also covers metadata arriving several ticks after the accepted reload notification.
                if (client.level.getGameTime() - pending.received > 3) {
                    while (pending.next < pending.cues.size() && pending.cues.get(pending.next).time < position) pending.next++;
                    pending.oneShot = null;
                }
            }
            if (pending.oneShot != null) { play(pending, pending.oneShot); pending.oneShot = null; }
            while (pending.next < pending.cues.size() && pending.cues.get(pending.next).time <= position + .025)
                play(pending, pending.cues.get(pending.next++).sound);
            // Final cues and their natural tails belong to this completed session, not the next held weapon.
            if (timeline.getBoolean("Complete")) { pending.sounds.clear(); iterator.remove(); }
        }
    }
    private static double position(Pending pending, double work, CompoundTag timeline) {
        return pending.plan == null ? work : pending.plan.presentationTime(work, timeline.getInt("NativeTicks") / 20D,
                TaczPresentationResources.phase(timeline));
    }
    private static void initialize(Pending pending, CompoundTag timeline) {
        pending.next = 0; pending.phaseIndex = 0; pending.positioned = false;
        pending.cues = List.of(); pending.plan = null; pending.oneShot = null;
        try {
            // Acquire the fallback before parsing any unrelated clip. A malformed optional cue cannot
            // swallow a configured native one-shot after this class claimed the reload event.
            var display = pending.display;
            if (display != null && display.getSounds() != null) {
                ResourceLocation sound = display.getSounds().get(timeline.getBoolean("Empty")
                        ? SoundManager.RELOAD_EMPTY_SOUND : SoundManager.RELOAD_TACTICAL_SOUND);
                if (hasSoundResource(sound)) pending.oneShot = sound;
            }
            JsonObject animations = animations(pending.animation);
            Map<String, Double> lengths = new HashMap<>();
            for (var entry : animations.entrySet()) if (entry.getValue().isJsonObject()) {
                var clip = entry.getValue().getAsJsonObject();
                try {
                    if (clip.has("animation_length")) {
                        double length = clip.get("animation_length").getAsDouble();
                        if (Double.isFinite(length) && length > 0) lengths.put(entry.getKey(), length);
                    }
                } catch (RuntimeException ignored) { /* An unrelated malformed clip is not this action. */ }
            }
            var plan = TaczPresentationResources.plan(lengths, timeline);
            if (plan.supported()) {
                List<Cue> cues = new ArrayList<>();
                for (var segment : plan.segments()) {
                    var clip = animations.getAsJsonObject(segment.animation());
                    if (clip != null) readCues(cues, clip, segment.start());
                }
                cues.sort(Comparator.comparingDouble(Cue::time));
                pending.plan = plan; pending.cues = List.copyOf(cues);
                if (!cues.isEmpty()) pending.oneShot = null; // One owner and one sound representation.
            }
        } catch (RuntimeException | LinkageError failure) {
            ClientAnimationCompatibility.warnOnce("TaCZ reload plan audio", "Cannot resolve authored reload cues", failure);
        }
    }
    private static JsonObject readAnimations(ResourceLocation id) {
        String path = id.getPath();
        if (!path.startsWith("animations/")) path = "animations/" + path;
        if (!path.endsWith(".animation.json")) path += ".animation.json";
        var file = new ResourceLocation(id.getNamespace(), path);
        var resource = Minecraft.getInstance().getResourceManager().getResource(file).orElse(null);
        if (resource == null) return new JsonObject();
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            return root.has("animations") ? root.getAsJsonObject("animations") : new JsonObject();
        } catch (Exception failure) {
            ClientAnimationCompatibility.warnOnce("TaCZ reload plan audio", "Cannot parse " + file, failure);
            return new JsonObject();
        }
    }
    private static void addCue(List<Cue> cues, double time, JsonElement effect) {
        if (!effect.isJsonPrimitive() || !effect.getAsJsonPrimitive().isString() || !Double.isFinite(time) || time < 0) return;
        var sound = ResourceLocation.tryParse(effect.getAsString());
        if (hasSoundResource(sound)) cues.add(new Cue(time, sound));
    }
    private static boolean hasSoundResource(ResourceLocation sound) {
        return sound != null && Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(
                sound.getNamespace(), "tacz_sounds/" + sound.getPath() + ".ogg")).isPresent();
    }
    private static void play(Pending pending, ResourceLocation sound) {
        var owned = ClientAnimationCompatibility.playTaczReloadSound(pending.human, sound, 1F, 1F,
                GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
        if (owned != null) pending.sounds.add(owned);
    }
    private static void stop(Pending pending) {
        for (var sound : pending.sounds) Minecraft.getInstance().getSoundManager().stop(sound);
        pending.sounds.clear();
    }
    private record Cue(double time, ResourceLocation sound) {}
    private static final class BoltAudio {
        final ResourceLocation gunId;
        final long start;
        final List<Cue> cues;
        int next;

        BoltAudio(ResourceLocation gunId, long start, List<Cue> cues) {
            this.gunId = gunId;
            this.start = start;
            this.cues = cues;
        }
    }
    private static final class Pending {
        final Human human;
        final ResourceLocation gunId, animation;
        final GunDisplay display;
        final long received;
        final List<net.minecraft.client.resources.sounds.SoundInstance> sounds = new ArrayList<>();
        long start, session;
        boolean initialized;
        boolean positioned;
        int next;
        int phaseIndex;
        List<Cue> cues = List.of();
        NativeReloadPlan plan;
        ResourceLocation oneShot;
        Pending(Human human, ResourceLocation gunId, GunDisplay display, long received) {
            this.human = human; this.gunId = gunId; this.display = display;
            this.animation = display.getAnimationLocation(); this.received = received;
        }
    }
}

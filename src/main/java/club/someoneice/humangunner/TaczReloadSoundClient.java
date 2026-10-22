package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.client.renderer.ClientAnimationCompatibility;
import com.tacz.guns.api.TimelessAPI;
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
final class TaczReloadSoundClient {
    private static final Map<ReloadCueKey, List<ReloadCue>> RELOAD_CUES = new HashMap<>();
    private static final List<PendingReload> PENDING_RELOADS = new ArrayList<>();

    private TaczReloadSoundClient() {}

    static void register() {
        MinecraftForge.EVENT_BUS.addListener(TaczReloadSoundClient::onReload);
        MinecraftForge.EVENT_BUS.addListener(TaczReloadSoundClient::onClientTick);
    }

    private static void onReload(GunReloadEvent event) {
        if (!event.getLogicalSide().isClient() || !(event.getEntity() instanceof Human human)) return;
        if(human.isUsingItem()&&SpartanEquipmentCompat.isShield(human.getUseItem())) return;
        PENDING_RELOADS.removeIf(pending -> {
            if(pending.human!=human) return false;
            stop(pending);return true;
        });
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
                play(pending,configuredSound);
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
            ResourceLocation animationFile = getAnimationFile(display);
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
        if (event.phase != TickEvent.Phase.END || PENDING_RELOADS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
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
            IGun held=IGun.getIGunOrNull(human.getMainHandItem());
            if (!human.isAlive() || human.level() != minecraft.level || held==null
                    ||!pending.gunId.equals(held.getGunId(human.getMainHandItem()))
                    ||human.isUsingItem()&&SpartanEquipmentCompat.isShield(human.getUseItem())
                    ||gameTime-pending.startGameTime>3&&human.getTaczReloadVisual().isEmpty()) {
                stop(pending);iterator.remove();continue;
            }
            pending.elapsed+=Math.max(0,gameTime-pending.lastTick)*GunCombatProfile.reloadWork(human,human.getMainHandItem())/20;
            pending.lastTick=gameTime;
            double elapsedSeconds=pending.elapsed;
            while (pending.nextCue < pending.cues.size()
                    && pending.cues.get(pending.nextCue).timeSeconds <= elapsedSeconds + 0.05D) {
                ReloadCue cue = pending.cues.get(pending.nextCue++);
                play(pending,cue.sound);
            }
            // Keep one-shot/cue handles until the real reload ends, so stowing
            // this NPC's gun can silence only these sounds without touching players.
        }
    }

    private static void play(PendingReload pending,ResourceLocation sound) {
        try {
            Class<?> manager=Class.forName("com.tacz.guns.client.sound.SoundPlayManager");
            java.lang.reflect.Method method;
            Class<?>[] signature={net.minecraft.world.entity.Entity.class,ResourceLocation.class,float.class,float.class,int.class};
            try { method=manager.getMethod("playAnimationSound",signature); }
            catch(NoSuchMethodException oldApi) { method=manager.getMethod("playClientSound",signature); }
            if(!net.minecraft.client.resources.sounds.SoundInstance.class.isAssignableFrom(method.getReturnType())) {
                ClientAnimationCompatibility.warnOnce("TaCZ reload sounds","Native playback has no cancellable sound handle",
                        new IllegalStateException(method.toString()));
                return;
            }
            Object instance=method.invoke(null,pending.human,sound,1F,1F,GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get());
            if(instance instanceof net.minecraft.client.resources.sounds.SoundInstance owned) pending.sounds.add(owned);
        } catch(ReflectiveOperationException|LinkageError failure) {
            ClientAnimationCompatibility.warnOnce("TaCZ reload sounds","Cannot obtain isolated NPC sound handle",failure);
        }
    }
    private static void stop(PendingReload pending) {
        for(var sound:pending.sounds) Minecraft.getInstance().getSoundManager().stop(sound);
        pending.sounds.clear();
    }

    private static ResourceLocation getAnimationFile(Object displayInstance) {
        ResourceLocation animationId = ClientAnimationCompatibility.reloadAnimationLocation(displayInstance);
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
        private final ResourceLocation gunId;
        private long lastTick;
        private double elapsed;
        private final List<net.minecraft.client.resources.sounds.SoundInstance> sounds=new ArrayList<>();

        private PendingReload(Human human, long startGameTime, List<ReloadCue> cues,ResourceLocation gunId) {
            this.human = human;
            this.startGameTime = startGameTime;
            this.cues = cues;
            this.gunId=gunId;
            this.lastTick=startGameTime;
        }
    }
}

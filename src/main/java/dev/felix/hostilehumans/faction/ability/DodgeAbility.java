package dev.felix.hostilehumans.faction.ability;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.damage.HumanDamagePipeline;
import dev.felix.hostilehumans.damage.HumanOwnerProtection;
import dev.felix.hostilehumans.progression.TierAttributes;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.WeakHashMap;
import dev.felix.hostilehumans.audio.ModSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.Difficulty;

/** Event-only dodge. Shared immutable chance table, listener-local sound throttling. */
final class DodgeAbility implements FactionAbility {
    private static final int MIN_SOUND_COOLDOWN = 24;
    // Share the audio gate across definitions; one Human must not play multiple dodge cues.
    // This is server-event state only, with weak keys so unloaded Humans are not retained.
    private static final Map<Human, Long> LAST_SOUND = new WeakHashMap<>();
    private final Map<String, Double> chances;
    private final int soundCooldown;
    private static final class Observation {
        long seen, eligible, dodged;
        double chance;
        String reason = "尚未接到攻击";
    }
    private final Map<Human, Observation> observations = new WeakHashMap<>();
    @Override public String describe(Human h) {
        Observation o = observations.get(h);
        return o == null ? "闪避：尚未接到攻击" : String.format(java.util.Locale.ROOT,
                "闪避 检查%d · 合格%d · 成功%d · 概率%.0f%% · %s", o.seen, o.eligible, o.dodged, o.chance * 100, o.reason);
    }

    DodgeAbility(JsonObject definition) {
        JsonObject ranks = definition.has("chance_by_tier") ? definition.getAsJsonObject("chance_by_tier") : new JsonObject();
        var values = new java.util.LinkedHashMap<String, Double>();
        for (String rank : java.util.List.of("roamer", "tier1", "tier2", "tier3")) {
            double chance = ranks.has(rank) ? ranks.get(rank).getAsDouble() : 0;
            if (!Double.isFinite(chance) || chance < 0 || chance > 1)
                throw new IllegalArgumentException("Invalid dodge chance: " + rank);
            values.put(rank, chance);
        }
        chances = Map.copyOf(values);
        int configuredSoundCooldown = definition.has("sound_cooldown_ticks")
                ? definition.get("sound_cooldown_ticks").getAsInt() : MIN_SOUND_COOLDOWN;
        if (configuredSoundCooldown < 0 || configuredSoundCooldown > 200)
            throw new IllegalArgumentException("Invalid dodge sound cooldown");
        soundCooldown = Math.max(MIN_SOUND_COOLDOWN, configuredSoundCooldown);
    }

    @Override public boolean evade(IncomingAttack hit) {
        Human h = hit.human(); var source = hit.source();
        Observation o = observations.computeIfAbsent(h, ignored -> new Observation()); o.seen++;
        o.chance = chances.getOrDefault(TierAttributes.key(h), 0.0);
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == h) {
            o.reason = "无直接活体攻击者"; return false;
        }
        // Political neutrality is not damage immunity. Reuse the actual incoming protection contract.
        if (HumanOwnerProtection.blocksDamage(h, source) || attacker instanceof Human other
                && (h.level().getDifficulty() == Difficulty.PEACEFUL || HumanRuntime.shouldBlockHumanDamage(other, h))) {
            o.reason = "攻击本已受伤害保护过滤"; return false;
        }
        if (!HumanDamagePipeline.physical(source) || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            o.reason = "环境、持续或非闪避伤害"; return false;
        }
        if (o.chance <= 0) { o.reason = "本阶概率为零"; return false; }
        o.eligible++;
        if (h.getRandom().nextDouble() >= o.chance) { o.reason = "本次抽签未触发"; return false; }
        o.dodged++; o.reason = "本次攻击已闪避";
        long now = h.level().getGameTime();
        Long lastSound = LAST_SOUND.get(h);
        if (lastSound == null || now < lastSound || now - lastSound >= soundCooldown) {
            h.level().playSound(null, h.getX(), h.getY(), h.getZ(), ModSoundEvents.DODGE.get(),
                    SoundSource.HOSTILE, 1.0F, 1.0F);
            LAST_SOUND.put(h, now);
        }
        return true;
    }
}

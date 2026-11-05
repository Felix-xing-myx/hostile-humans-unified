package dev.felix.hostilehumans.faction.ability;

import com.google.gson.JsonObject;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.faction.HumanFactions;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** A nearby group grants one timed resistance effect; members nearing expiry initiate a collective refresh. */
final class GroupResistanceAbility implements FactionAbility {
    private final double radius;
    private final int minimumOthers;
    private final int durationTicks;
    private final int intervalTicks;
    private final int refreshBelowTicks;

    GroupResistanceAbility(JsonObject definition) {
        radius = definition.has("radius") ? definition.get("radius").getAsDouble() : 5;
        minimumOthers = integer(definition, "minimum_other_members", 2, 1, 64);
        durationTicks = integer(definition, "duration_ticks", 400, 1, 72000);
        intervalTicks = integer(definition, "check_interval_ticks", 20, 1, 1200);
        refreshBelowTicks = integer(definition, "refresh_below_ticks", Math.min(100,durationTicks-1), 0, durationTicks-1);
        if (!Double.isFinite(radius) || radius <= 0 || radius > 32)
            throw new IllegalArgumentException("Invalid group resistance radius");
    }

    private static int integer(JsonObject definition, String name, int fallback, int minimum, int maximum) {
        double value = definition.has(name) ? definition.get(name).getAsDouble() : fallback;
        if (!Double.isFinite(value) || value != Math.rint(value) || value < minimum || value > maximum)
            throw new IllegalArgumentException("Invalid group resistance " + name);
        return (int) value;
    }

    @Override public void tick(Human human) {
        MobEffectInstance effect=human.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (!needsFormationCheck(effect==null?0:effect.getDuration())) return;
        if (Math.floorMod(human.level().getGameTime() + human.getId(), intervalTicks) != 0) return;
        String faction = HumanFactions.id(human);
        if (faction.isEmpty()) return;
        double radiusSquared = radius * radius;
        var members = human.level().getEntitiesOfClass(Human.class, human.getBoundingBox().inflate(radius),
                other -> other != human && other.isAlive() && !other.isRemoved()
                        && faction.equals(HumanFactions.id(other)) && human.distanceToSqr(other) <= radiusSquared);
        if (members.size() < minimumOthers) return;
        apply(human);
        for (Human member : members) apply(member);
    }

    private void apply(Human human) {
        MobEffectInstance effect=human.getEffect(MobEffects.DAMAGE_RESISTANCE);
        // One qualifying member renews all ordinary resistance in this formation, not only expiring members.
        // Native addEffect keeps hidden effects and emits the normal server/client synchronization.
        if (effect==null || extendsResistance(effect.getAmplifier(),effect.getDuration()))
            human.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 0));
    }

    // A negative duration is native infinite duration; it never requests or receives an extension.
    boolean needsFormationCheck(int remainingTicks){return remainingTicks>=0 && remainingTicks<=refreshBelowTicks;}
    boolean extendsResistance(int amplifier,int remainingTicks){return amplifier==0 && remainingTicks>=0 && remainingTicks<durationTicks;}

    @Override public String describe(Human human) {
        var effect=human.getEffect(MobEffects.DAMAGE_RESISTANCE);
        return effect!=null && !needsFormationCheck(effect.getDuration())
                ? "结阵抗性：已生效，等待成员临期后集体续期" : "结阵抗性：检测附近同阵营成员，满足结阵则集体续期";
    }
}

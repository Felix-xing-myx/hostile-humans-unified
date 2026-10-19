package club.someoneice.humangunner;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;

/** Requires Minecraft test runtime, but no live entity or server. */
public final class SpecialtyPersistenceTest {
    public static void main(String[] args) {
        CompoundTag current = new CompoundTag();
        double[] values = SpecialtyAttributes.baseline(); values[0] = .85; values[9] = 1.25;
        SpecialtyAttributes.store(current, 1.05, values, 123456);
        CompoundTag before = current.copy();
        check(SpecialtyAttributes.retainSaved(current), "existing payload recognized");
        check(current.equals(before), "reload does not reroll");
        close(SpecialtyAttributes.storedMultiplier(current, SpecialtyAttributes.Dimension.HEALTH), .85, "saved health");
        close(SpecialtyAttributes.storedMultiplier(current, SpecialtyAttributes.Dimension.ACCURACY), 1.25, "positive accuracy");

        CompoundTag legacy = new CompoundTag();
        ListTag ten = new ListTag();
        for (int i = 0; i < 10; i++) ten.add(FloatTag.valueOf(i == 9 ? .8F : 1));
        legacy.put("multipliers", ten); legacy.putInt("allocation_schema", 1); legacy.putFloat("budget", 1);
        legacy.putInt("experience_percent", 117); legacy.putLong("allocation_seed", 99);
        check(SpecialtyAttributes.retainSaved(legacy), "explicit legacy recognized");
        check(legacy.getInt("allocation_schema") == 3 && legacy.getList("multipliers", Tag.TAG_FLOAT).size() == 13, "legacy expanded");
        close(SpecialtyAttributes.storedMultiplier(legacy, SpecialtyAttributes.Dimension.ACCURACY), 1.25, "legacy spread converted once");
        close(SpecialtyAttributes.storedMultiplier(legacy, SpecialtyAttributes.Dimension.REGENERATION), 1, "legacy regeneration baseline");
        check(legacy.getInt("experience_percent") == 117 && legacy.getLong("allocation_seed") == 99, "other growth preserved");
        CompoundTag migrated = legacy.copy(); SpecialtyAttributes.retainSaved(legacy);
        check(legacy.equals(migrated), "idempotent migration");

        CompoundTag eleven = new CompoundTag();
        ListTag old = new ListTag();
        for(int i=0;i<11;i++)old.add(FloatTag.valueOf((float)(.8+i*.025)));
        eleven.put("multipliers",old);eleven.putInt("allocation_schema",2);eleven.putFloat("budget",1.12F);
        SpecialtyAttributes.retainSaved(eleven);
        for(int i=0;i<11;i++)close(eleven.getList("multipliers",Tag.TAG_FLOAT).getFloat(i),old.getFloat(i),"old value exact");
        close(SpecialtyAttributes.storedMultiplier(eleven, SpecialtyAttributes.Dimension.ATTACK_SPEED),1,"old speed one");
        close(SpecialtyAttributes.storedMultiplier(eleven, SpecialtyAttributes.Dimension.ARMOR_PENETRATION),0,"old penetration zero");
        for (int schema : new int[]{0, 99}) {
            CompoundTag future = new CompoundTag(); future.putInt("allocation_schema", schema);
            future.put("multipliers", ten.copy()); future.putString("future_metadata", "keep me");
            CompoundTag exact = future.copy();
            check(SpecialtyAttributes.retainSaved(future) && future.equals(exact), "unknown payload preserved");
            close(SpecialtyAttributes.storedMultiplier(future, SpecialtyAttributes.Dimension.ACCURACY), 1, "unknown effective baseline");
        }
        CompoundTag invalid = current.copy(); invalid.getList("multipliers", Tag.TAG_FLOAT).set(4, FloatTag.valueOf(Float.NaN));
        CompoundTag exact = invalid.copy(); SpecialtyAttributes.retainSaved(invalid);
        check(invalid.equals(exact), "invalid payload retained");
        close(SpecialtyAttributes.storedMultiplier(invalid, SpecialtyAttributes.Dimension.HEALTH), 1, "whole invalid vector neutralized");

        SpecialtyConfig defaults = SpecialtyConfig.defaults();
        RandomSource random = RandomSource.create(123);
        double total = 0; int high = 0;
        for (int i = 0; i < 100000; i++) {
            double budget = SpecialtyAttributes.sampleBudget(random, defaults);
            check(budget >= .8 && budget <= 1.3, "truncated budget bounds");
            total += budget; if (budget > 1.15) high++;
        }
        check(Math.abs(total / 100000 - 1.05) < .002, "budget mean");
        check(Math.abs(high / 100000.0 - .1544) < .006, "resampling distribution");
        var degenerate = new SpecialtyConfig(true, 2, 0, .75, 1.25, 0, .3, .25, .5, 1.8);
        close(SpecialtyAttributes.sampleBudget(random, degenerate), 1.25, "bounded sigma-zero fallback");
        System.out.println("SpecialtyPersistenceTest: all checks passed");
    }
    private static void close(double actual, double expected, String message) { check(Math.abs(actual-expected)<1e-6, message); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;

/** Server-owned birth snapshots and cultivation. UI clients never evaluate this policy. */
public final class HumanGrowth {
    static final String STATE = HumanGunner.MOD_ID + ":growth";
    private static final java.util.Set<Human> LOADED = java.util.Collections.synchronizedSet(
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
    private static final java.util.Map<Human, UnifiedConfig> CONFIGS = new java.util.WeakHashMap<>();
    private HumanGrowth() { }

    /** Loading an existing entity must not reroll its birth state. Not persisted as a new-spawn flag. */
    public static void onLoad(Human human) {
        if (!human.level().isClientSide) LOADED.add(human);
    }

    static void onJoin(Human human, boolean loadedFromDisk) {
        if (loadedFromDisk) LOADED.add(human);
        else LOADED.remove(human); // /summon NBT uses readAdditionalSaveData too, but is a fresh birth.
    }

    public static void markBirth(Human human, ServerLevelAccessor level) {
        if (human.level().isClientSide || human.getPersistentData().contains(STATE)) return;
        long time = PlayerProgressionData.timeNear(level, human.blockPosition());
        long day = time < 0 ? 0 : NaturalSpawnProgression.dayAt(time);
        GrowthConfig config = UnifiedConfig.get().growth();
        CompoundTag state = new CompoundTag();
        state.putInt("schema", 1);
        state.putLong("birth_day", day);
        state.putInt("level", config.stageAt(TierAttributes.key(human), day));
        int low = (int) Math.round(config.experienceMinPercent());
        int high = (int) Math.round(config.experienceMaxPercent());
        state.putInt("experience_percent", human.getRandom().nextInt(low, high + 1));
        state.putBoolean("new_health", true);
        human.getPersistentData().put(STATE, state);
    }

    static CompoundTag state(Human human) {
        if (!(human.level() instanceof ServerLevel level)) throw new IllegalStateException("Growth state is server-only");
        if (!human.getPersistentData().contains(STATE)) {
            if (!LOADED.remove(human)) markBirth(human, level);
            else {
                CompoundTag state = new CompoundTag();
                state.putInt("schema", 1);
                String birthday = HumanGunner.MOD_ID + ":equipment_spawn_day";
                long day = human.getPersistentData().getLong(birthday);
                state.putLong("birth_day", day);
                state.putInt("level", human.hasOwner() || !human.getPersistentData().contains(birthday)
                        ? 5 : UnifiedConfig.get().growth().stageAt(TierAttributes.key(human), day));
                state.putInt("experience_percent", 100);
                human.getPersistentData().put(STATE, state);
            }
        } else LOADED.remove(human);
        CompoundTag state = human.getPersistentData().getCompound(STATE);
        state.putInt("level", Math.max(0, Math.min(5, state.getInt("level"))));
        int cap = (int) Math.round(UnifiedConfig.get().growth().experienceCapPercent());
        state.putInt("experience_percent", Math.max(1, Math.min(cap, state.getInt("experience_percent"))));
        if (state.getInt("level") == 5) state.putDouble("stage_progress", 0);
        if (state.getInt("experience_percent") == cap) state.putDouble("experience_progress", 0);
        return state;
    }

    /** Non-firearm outgoing damage only; health and firearms have separate curves. */
    static double multiplier(Human human) {
        GrowthConfig config = UnifiedConfig.get().growth();
        if (!config.enabled()) return 1;
        CompoundTag state = state(human);
        // The stage result is computed first, then the experience percentage is applied.
        return config.damageStagePercent(state.getInt("level")) / 100.0
                * (state.getInt("experience_percent") / 100.0);
    }

    static double gunSpreadMultiplier(Human human) {
        GrowthConfig config = UnifiedConfig.get().growth();
        return config.enabled() ? config.gunSpreadMultiplier(state(human).getInt("experience_percent")) : 1;
    }

    static double gunDamageMultiplier(Human human) {
        GrowthConfig config = UnifiedConfig.get().growth();
        return config.enabled() ? config.gunDamageMultiplier(state(human).getInt("experience_percent")) : 1;
    }

    /** Both firearm backends apply experience once, independently of attribute growth stages. */
    static float scaleGunDamage(Human human, float nativeDamage, double configuredMultiplier) {
        return nativeDamage * (float) configuredMultiplier * (float) gunDamageMultiplier(human);
    }

    static double fullHealth(Human human) {
        double staged = TierAttributes.of(human).maxHealth();
        GrowthConfig config = UnifiedConfig.get().growth();
        if (!config.enabled()) return staged;
        CompoundTag state = state(human);
        staged *= config.stagePercent(state.getInt("level")) / 100.0;
        return staged * state.getInt("experience_percent") / 100.0;
    }

    static void tick(Human human) {
        UnifiedConfig config = UnifiedConfig.get();
        boolean changed = CONFIGS.put(human, config) != config;
        if (changed) TierAttributes.applyCombatBase(human);
        boolean rewarded = GrowthRewards.claim(human);
        if (changed && config.growth().enabled() && human.hasOwner()) award(human, 0);
        if (changed || rewarded || human.tickCount % 20 == 0) GrowthHealth.apply(human);
    }

    static void award(Human human, double reward) {
        GrowthConfig config = UnifiedConfig.get().growth();
        if (!config.enabled() || !human.hasOwner() || !human.isAlive()) return;
        CompoundTag state = state(human);
        GrowthProgression.Result next = GrowthProgression.advance(config, state.getInt("level"),
                state.getInt("experience_percent"), progress(state, "stage_progress"),
                progress(state, "experience_progress"), reward);
        state.putInt("level", next.level());
        state.putInt("experience_percent", next.experience());
        state.putDouble("stage_progress", next.stageProgress());
        state.putDouble("experience_progress", next.experienceProgress());
    }

    static double progress(CompoundTag tag, String key) {
        double value = tag.getDouble(key);
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }

    static double rounded(double value) { return GrowthProgression.rounded(value); }

    static GrowthSnapshot snapshot(Human human) {
        return snapshotState(state(human));
    }

    /** Last saved server state; absent records stay unknown rather than inventing a birth. */
    static GrowthSnapshot savedSnapshot(CompoundTag persistentData) {
        return persistentData.contains(STATE, net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? snapshotState(persistentData.getCompound(STATE)) : null;
    }

    private static GrowthSnapshot snapshotState(CompoundTag saved) {
        GrowthConfig config = UnifiedConfig.get().growth();
        CompoundTag state = saved.copy();
        int stage = Math.max(0, Math.min(5, state.getInt("level")));
        int cap = (int) Math.round(config.experienceCapPercent());
        state.putInt("experience_percent", Math.max(1, Math.min(cap, state.getInt("experience_percent"))));
        if (stage == 5) state.putDouble("stage_progress", 0);
        if (state.getInt("experience_percent") == cap) state.putDouble("experience_progress", 0);
        return new GrowthSnapshot(config.enabled(), stage, (int) Math.round(config.stagePercent(stage)),
                state.getInt("experience_percent"), (int) Math.round(config.experienceMinPercent()),
                (int) Math.round(config.experienceCapPercent()), progress(state, "stage_progress"),
                stage == 5 ? 0 : config.stageCost(stage), progress(state, "experience_progress"), state.getInt("experience_percent") >= config.experienceCapPercent()
                        ? 0 : config.experienceCost(state.getInt("experience_percent")));
    }
}

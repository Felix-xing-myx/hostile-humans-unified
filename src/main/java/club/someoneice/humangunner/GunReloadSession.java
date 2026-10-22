package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Paid native schedules bind one state object, session and real owned weapon. */
final class GunReloadSession {
    private static final java.util.UUID RUNTIME = java.util.UUID.randomUUID();
    private static final java.util.Map<Human,Attempt> STARTING = new java.util.WeakHashMap<>();
    private record Cycle(CompoundTag state, java.lang.ref.WeakReference<com.craftix.hostile_humans.entity.data.HumanData> inventory,
                         java.lang.ref.WeakReference<ItemStack> weapon, long startedTick) { }
    private static final java.util.Map<Human,java.util.Map<String,Cycle>> CYCLES = new java.util.WeakHashMap<>();
    private static final java.util.Set<Human> TICKING = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static final class Attempt {
        final ItemStack gun;
        final String key;
        boolean cancelled;
        CompoundTag state;
        Attempt(ItemStack gun,String key) { this.gun=gun;this.key=key; }
    }
    private static Attempt enter(Human human,ItemStack gun,String key) {
        if(STARTING.containsKey(human)) return null;
        Attempt attempt=new Attempt(gun,key);STARTING.put(human,attempt);return attempt;
    }
    private static boolean live(Human human,Attempt attempt) {
        return STARTING.get(human)==attempt&&!attempt.cancelled;
    }
    private GunReloadSession() { }

    static CompoundTag begin(Human human, ItemStack gun, String key, ListTag steps) {
        if (TICKING.contains(human)) return null;
        Attempt attempt=enter(human,gun,key);
        if(attempt==null) return null;
        try { return beginPaid(human, gun, key, steps,attempt); }
        catch (RuntimeException | LinkageError failure) {
            HumanGunner.LOGGER.warn("NPC reload start failed; no stale state will be restored", failure);
            return null;
        } finally { STARTING.remove(human); }
    }

    private static CompoundTag beginPaid(Human human, ItemStack gun, String key, ListTag steps,Attempt attempt) {
        if(!live(human,attempt)) return null;
        var authority = GunReloadAuthority.capture(human, gun);
        if (authority == null || steps.isEmpty() || !live(human,attempt)) return null;
        Tag previousState = human.getPersistentData().get(key);
        if(previousState instanceof CompoundTag activeState && activeState.hasUUID("weapon")) {
            ItemStack activeGun=HumanEquipmentExchange.locate(human,new HumanEquipmentExchange.Handle(
                    activeState.getUUID("weapon"),human.getUUID()));
            if(!activeGun.isEmpty()&&matches(human,activeGun,key,activeState)) return null;
        }
        Tag previousMarker = gun.hasTag() ? gun.getTag().get(key) : null;
        Tag markerSnapshot = previousMarker == null ? null : previousMarker.copy();
        java.util.function.BooleanSupplier unchanged = () -> live(human,attempt)&&authority.validSource()
                && human.getPersistentData().get(key) == previousState
                && (gun.hasTag() ? gun.getTag().get(key) : null) == previousMarker
                && java.util.Objects.equals(previousMarker, markerSnapshot)&&live(human,attempt);
        ListTag schedule = steps.copy();
        double total = 0;
        for (Tag row : schedule) {
            if (!(row instanceof CompoundTag step)) return null;
            total += Math.max(1, step.getInt("ticks"));
        }
        if (!Double.isFinite(total) || total <= 0 || !unchanged.getAsBoolean()
                || !authority.pay(unchanged) || !unchanged.getAsBoolean()) return null;
        CompoundTag state = new CompoundTag();
        state.putUUID("weapon", authority.handle.identity());
        state.putUUID("owner", human.getUUID());
        state.putUUID("transaction", java.util.UUID.randomUUID());
        state.putLong("session", authority.session);
        state.putUUID("runtime", RUNTIME);
        state.put("steps", schedule);
        state.putDouble("total_work", total);
        state.putDouble("work", 0);
        state.putDouble("progress", 0);
        state.putInt("step", 0);
        state.putInt("ticks", (int) Math.ceil(total));
        attempt.state=state;
        gun.getOrCreateTag().putUUID(key, state.getUUID("transaction"));
        human.getPersistentData().put(key, state);
        CYCLES.computeIfAbsent(human, ignored -> new java.util.HashMap<>()).put(key,
                new Cycle(state, new java.lang.ref.WeakReference<>(authority.inventory),
                        new java.lang.ref.WeakReference<>(gun), human.level().getGameTime()));
        return state;
    }

    static boolean matches(Human human, ItemStack gun, String key) {
        Tag existing = human.getPersistentData().get(key);
        return existing instanceof CompoundTag state && matches(human, gun, key, state);
    }

    static boolean matches(Human human, ItemStack gun, String key, CompoundTag state) {
        Cycle cycle = CYCLES.getOrDefault(human, java.util.Map.of()).get(key);
        return state!=null && human.getPersistentData().get(key) == state
                && cycle != null && cycle.state() == state && cycle.inventory().get() == human.getData()
                && state.hasUUID("runtime") && RUNTIME.equals(state.getUUID("runtime"))
                && state.hasUUID("weapon") && state.hasUUID("owner")
                && human.getUUID().equals(state.getUUID("owner")) && transaction(gun, key, state)
                && GunReloadAuthority.validIdentity(human, gun, new HumanEquipmentExchange.Handle(
                        state.getUUID("weapon"), human.getUUID()), state.getLong("session"))
                && human.getPersistentData().get(key) == state && transaction(gun,key,state);
    }

    /** The complete native step callback is guarded, not just its clock calculation. */
    static void runTick(Human human, ItemStack gun, String key,
                        java.util.function.BiConsumer<CompoundTag,Double> step) {
        if (!TICKING.add(human)) return;
        Tag existing = human.getPersistentData().get(key);
        CompoundTag state = existing instanceof CompoundTag tag ? tag : null;
        try {
            if (!matches(human, gun, key, state)) return;
            double work = advance(human, gun, key);
            if (work < 0 || !matches(human, gun, key, state)) return;
            step.accept(state, work);
        } catch (RuntimeException | LinkageError failure) {
            cancel(human, gun, key, state);
            HumanGunner.LOGGER.warn("NPC reload step failed; original transaction cancelled", failure);
        } finally { TICKING.remove(human); }
    }

    static double advance(Human human, ItemStack gun, String key) {
        Tag existing = human.getPersistentData().get(key);
        if (!(existing instanceof CompoundTag state)) return -1;
        if (!matches(human, gun, key, state)) {
            cancel(human, gun, key,state);
            return -1;
        }
        long now = human.level().getGameTime();
        if (state.contains("last_tick") && state.getLong("last_tick") == now) return state.getDouble("work");
        state.putLong("last_tick", now);
        double total = state.getDouble("total_work");
        if (!Double.isFinite(total) || total <= 0) { cancel(human, gun, key,state); return -1; }
        double rate = GunCombatProfile.reloadWork(human, gun);
        if (!matches(human, gun, key, state)) { cancelState(human, gun, key, state); return -1; }
        double progress = GunDoctrine.advance(state.getDouble("progress"), total, rate);
        double work = progress * total;
        state.putDouble("progress", progress);
        state.putDouble("work", work);
        state.putDouble("work_per_tick", rate);
        state.putInt("ticks", (int) Math.ceil(Math.max(0, total - work - 1e-8)));
        return work;
    }

    /** Read the exact execution checkpoint. In particular, do not call matches(), which advances authority time. */
    static java.util.List<ActionProgressSnapshot> progress(Human human, String key) {
        Cycle cycle = CYCLES.getOrDefault(human, java.util.Map.of()).get(key);
        if (human.level().isClientSide || cycle == null) return java.util.List.of();
        var state = cycle.state(); ItemStack weapon = cycle.weapon().get();
        var authority = HumanBehaviorControl.peekActions(human);
        String location = ActionProgressSnapshot.location(human, weapon);
        boolean valid = authority != null && authority.session() == state.getLong("session")
                && human.getData() == cycle.inventory().get() && human.getPersistentData().get(key) == state
                && transaction(weapon, key, state) && !location.equals("missing") && !location.equals("ambiguous")
                && human.isAlive() && !human.isRemoved() && !human.isNoAi() && !HumanInventorySession.isEditing(human);
        double work = state.getDouble("work"), total = state.getDouble("total_work");
        double rate = state.contains("work_per_tick") ? state.getDouble("work_per_tick") : -1;
        if (!Double.isFinite(work) || work < 0 || !Double.isFinite(total) || total <= 0) { valid = false; work = total = -1; }
        if (!Double.isFinite(rate) || rate <= 0) rate = -1;
        long sampled = state.contains("last_tick") ? state.getLong("last_tick") : cycle.startedTick();
        String detail = "阶段 " + state.getInt("step") + "/" + state.getList("steps", Tag.TAG_COMPOUND).size()
                + "；余时按最后执行速率估计；原枪 " + (state.hasUUID("weapon") ? state.getUUID("weapon") : "unknown");
        return java.util.List.of(new ActionProgressSnapshot("gun_reload", ActionProgressSnapshot.item(weapon), location,
                valid ? "running" : "invalid", sampled, work, total, rate,
                Math.max(0, sampled - cycle.startedTick()), valid ? ActionProgressSnapshot.remaining(work,total,rate) : -1,
                ActionProgressSnapshot.clip(detail, ActionProgressSnapshot.DETAIL_LENGTH)));
    }

    static boolean complete(Human human, ItemStack gun, String key, CompoundTag state, Runnable commit) {
        if (state==null || human.getPersistentData().get(key)!=state || !transaction(gun,key,state)) return false;
        Attempt attempt=enter(human,gun,key);
        if(attempt==null) return false;
        attempt.state=state;
        try {
            ListTag steps = state.getList("steps", Tag.TAG_COMPOUND);
            if (!live(human,attempt) || !matches(human, gun, key, state) || steps.isEmpty()
                    || state.getInt("step") < steps.size() || !Double.isFinite(state.getDouble("progress"))
                    || state.getDouble("progress") < 1 - 1e-8) {
                cancelState(human, gun, key, state);
                return false;
            }
            var handle = new HumanEquipmentExchange.Handle(state.getUUID("weapon"), human.getUUID());
            long session = state.getLong("session");
            var inventory = human.getData();
            // Settle this exact state before entering an external energy capability.
            cancelState(human, gun, key, state);
            if (!live(human,attempt) || human.getPersistentData().contains(key)
                    || human.getData() != inventory || !GunReloadAuthority.validIdentity(human, gun, handle, session)||!live(human,attempt)) return false;
            commit.run();
            return live(human,attempt) && !human.getPersistentData().contains(key)
                    && human.getData() == inventory && GunReloadAuthority.validIdentity(human, gun, handle, session)&&live(human,attempt);
        } catch (RuntimeException | LinkageError failure) {
            // An opaque capability may have partially committed. Do not retry it
            // or write a snapshot over a weapon now moved/edited by its callback.
            HumanGunner.LOGGER.warn("NPC reload completion failed; original transaction stays settled", failure);
            return false;
        } finally { STARTING.remove(human); }
    }

    static void cancel(Human human, ItemStack gun, String key) {
        Attempt attempt=STARTING.get(human);
        if(attempt!=null&&attempt.gun==gun&&attempt.key.equals(key)) attempt.cancelled=true;
        Tag existing = human.getPersistentData().get(key);
        if (existing instanceof CompoundTag state && transaction(gun, key, state))
            cancelState(human, gun, key, state);
    }

    static void cancel(Human human,ItemStack gun,String key,CompoundTag expectedState) {
        if(expectedState==null) return;
        Attempt attempt=STARTING.get(human);
        if(attempt!=null&&attempt.gun==gun&&attempt.key.equals(key)&&attempt.state==expectedState)
            attempt.cancelled=true;
        cancelState(human,gun,key,expectedState);
    }

    private static void cancelState(Human human, ItemStack gun, String key, CompoundTag state) {
        if (state==null || human.getPersistentData().get(key) != state || !transaction(gun,key,state)) return;
        // Editing/lost weapons are never rewritten just to clean an old marker.
        if (matches(human, gun, key, state)) gun.getTag().remove(key);
        if (human.getPersistentData().get(key) == state) human.getPersistentData().remove(key);
        java.util.Map<String,Cycle> cycles = CYCLES.get(human);
        if (cycles != null) {
            Cycle cycle = cycles.get(key);
            if (cycle != null && cycle.state() == state) cycles.remove(key);
            if (cycles.isEmpty()) CYCLES.remove(human);
        }
    }

    private static boolean transaction(ItemStack gun, String key, CompoundTag state) {
        return gun != null && !gun.isEmpty() && gun.hasTag() && gun.getTag().hasUUID(key)
                && state.hasUUID("transaction") && state.getUUID("transaction").equals(gun.getTag().getUUID(key));
    }
}

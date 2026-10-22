package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

/** Shared paid clock for native NPC step schedules. Only the original real weapon can commit steps. */
final class GunReloadSession {
    private static final java.util.UUID RUNTIME=java.util.UUID.randomUUID();
    private static final java.util.Set<Human> STARTING=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private GunReloadSession() {}
    static CompoundTag begin(Human human,ItemStack gun,String key,ListTag steps) {
        if(!STARTING.add(human)) return null;
        try { return beginPaid(human,gun,key,steps); }
        finally { STARTING.remove(human); }
    }
    private static CompoundTag beginPaid(Human human,ItemStack gun,String key,ListTag steps) {
        var handle=HumanEquipmentExchange.bind(human,gun);
        if(handle==null||steps.isEmpty()||!HumanAmmo.canReload(human,gun)) return null;
        double total=0;
        for(var step:steps) total+=Math.max(1,((CompoundTag)step).getInt("ticks"));
        if(!Double.isFinite(total)||total<=0||!HumanAmmo.consumeMagazines(human,gun)) return null;
        CompoundTag state=new CompoundTag();
        state.putUUID("weapon",handle.identity());state.putUUID("owner",human.getUUID());
        state.putUUID("transaction",java.util.UUID.randomUUID());
        state.putLong("session",HumanBehaviorControl.actions(human).session());
        state.putUUID("runtime",RUNTIME);
        state.put("steps",steps);state.putDouble("total_work",total);state.putDouble("work",0);
        state.putDouble("progress",0);state.putInt("step",0);state.putInt("ticks",(int)Math.ceil(total));
        gun.getOrCreateTag().putUUID(key,state.getUUID("transaction"));
        human.getPersistentData().put(key,state);
        return state;
    }
    static boolean matches(Human human,ItemStack gun,String key) {
        var state=human.getPersistentData().getCompound(key);
        return state.hasUUID("runtime")&&RUNTIME.equals(state.getUUID("runtime"))
                &&state.getLong("session")==HumanBehaviorControl.actions(human).session()
                &&!gun.isEmpty()&&state.hasUUID("weapon")&&state.hasUUID("owner")
                &&human.getUUID().equals(state.getUUID("owner"))&&gun.hasTag()&&gun.getTag().hasUUID(key)
                &&state.hasUUID("transaction")&&state.getUUID("transaction").equals(gun.getTag().getUUID(key))
                &&HumanEquipmentExchange.locate(human,new HumanEquipmentExchange.Handle(state.getUUID("weapon"),human.getUUID()))==gun;
    }
    static double advance(Human human,ItemStack gun,String key) {
        if(!matches(human,gun,key)||!human.isAlive()||human.isRemoved()||human.isNoAi()||HumanInventorySession.isEditing(human)) {
            cancel(human,gun,key);return -1;
        }
        var state=human.getPersistentData().getCompound(key);
        long now=human.level().getGameTime();
        if(state.contains("last_tick")&&state.getLong("last_tick")==now) return state.getDouble("work");
        state.putLong("last_tick",now);
        double total=state.getDouble("total_work");
        if(!Double.isFinite(total)||total<=0) { cancel(human,gun,key);return -1; }
        double progress=GunDoctrine.advance(state.getDouble("progress"),total,GunCombatProfile.reloadWork(human,gun));
        double work=progress*total;
        state.putDouble("progress",progress);state.putDouble("work",work);
        state.putInt("ticks",(int)Math.ceil(Math.max(0,total-work-1e-8)));
        return work;
    }
    static void complete(Human human,ItemStack gun,String key,Runnable commit) {
        if(!STARTING.add(human)) return;
        try {
            cancel(human,gun,key);
            commit.run();
        } finally { STARTING.remove(human); }
    }
    static void cancel(Human human,ItemStack gun,String key) {
        if(gun!=null&&gun.hasTag()) gun.getTag().remove(key);
        human.getPersistentData().remove(key);
    }
}
